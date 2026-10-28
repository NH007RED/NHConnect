package com.nhdeveloper.nhconnect.data.repo

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.nhdeveloper.nhconnect.data.ChatMessage
import com.nhdeveloper.nhconnect.data.SendState
import com.nhdeveloper.nhconnect.data.TranslationStatus
import com.nhdeveloper.nhconnect.data.local.MediaEntity
import com.nhdeveloper.nhconnect.data.local.MessageDao
import com.nhdeveloper.nhconnect.media.B64
import com.nhdeveloper.nhconnect.media.CompressedImage
import com.nhdeveloper.nhconnect.data.local.MessageEntity
import com.nhdeveloper.nhconnect.data.local.toDomain
import com.nhdeveloper.nhconnect.data.local.toEntity
import com.nhdeveloper.nhconnect.domain.MessageMerge
import com.nhdeveloper.nhconnect.domain.Validators
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepository(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
    private val dao: MessageDao,
    private val scope: CoroutineScope,
    private val schedulePendingWork: () -> Unit
) {
    companion object {
        const val PAGE_SIZE = 30
        private const val RECEIPT_WINDOW = 100
        private const val TAG = "ChatRepository"
    }

    /** All Room writes go through one lane so listener events cannot overwrite each other out of order. */
    private val serial = Dispatchers.IO.limitedParallelism(1)
    private val receiptCache = ConcurrentHashMap<String, Pair<Long?, Long?>>()
    private val deliveredWritten = ConcurrentHashMap.newKeySet<String>()
    private val readWritten = ConcurrentHashMap.newKeySet<String>()

    private fun msgsRef(chatId: String) = db.getReference("chats/$chatId/messages")

    fun observeLocal(chatId: String, limit: Int): Flow<List<ChatMessage>> =
        dao.observe(chatId, limit).map { rows -> rows.map { it.toDomain() } }

    /**
     * Keeps the newest page of a chat (plus receipts) in sync with the server until the caller is cancelled.
     * Listeners are always removed in `finally` so nothing leaks when the screen closes.
     */
    suspend fun syncChat(chatId: String, me: String) {
        val mq = msgsRef(chatId).orderByKey().limitToLast(PAGE_SIZE)
        val rq = db.getReference("messageReceipts/$chatId").orderByKey().limitToLast(RECEIPT_WINDOW)
        val ml = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, p: String?) = onRemote(chatId, me, s)
            override fun onChildChanged(s: DataSnapshot, p: String?) = onRemote(chatId, me, s)
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            // Fires when an item slides out of the limitToLast window. Messages are soft-deleted, so never delete locally.
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onCancelled(e: DatabaseError) { Log.w(TAG, "messages listener cancelled: ${e.code}") }
        }
        val rl = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, p: String?) = onReceipt(s)
            override fun onChildChanged(s: DataSnapshot, p: String?) = onReceipt(s)
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onCancelled(e: DatabaseError) { Log.w(TAG, "receipts listener cancelled: ${e.code}") }
        }
        rq.addChildEventListener(rl)
        mq.addChildEventListener(ml)
        try {
            awaitCancellation()
        } finally {
            mq.removeEventListener(ml)
            rq.removeEventListener(rl)
        }
    }

    private fun onRemote(chatId: String, me: String, s: DataSnapshot) {
        val remote = Parsers.message(chatId, s) ?: return
        scope.launch(serial) {
            val merged = MessageMerge.merge(dao.get(remote.id)?.toDomain(), remote, receiptCache[remote.id])
            dao.upsert(merged.toEntity())
            if (merged.recipientId == me && merged.deliveredAt == null) markDelivered(chatId, me, merged.id)
        }
    }

    private fun receiptOf(s: DataSnapshot): Pair<Long?, Long?> {
        var d: Long? = null
        var r: Long? = null
        s.children.forEach { c ->
            c.lng("deliveredAt")?.let { d = it }
            c.lng("readAt")?.let { r = it }
        }
        return d to r
    }

    private fun onReceipt(s: DataSnapshot) {
        val id = s.key ?: return
        val rc = receiptOf(s)
        receiptCache[id] = rc
        scope.launch(serial) { dao.updateReceipt(id, rc.first, rc.second) }
    }

    /** Loads one page older than [beforeKey]. Returns how many messages the server returned. */
    suspend fun loadOlder(chatId: String, beforeKey: String): Int {
        val snap = msgsRef(chatId).orderByKey().endBefore(beforeKey).limitToLast(PAGE_SIZE).get().await()
        val list = snap.children.mapNotNull { Parsers.message(chatId, it) }
        if (list.isEmpty()) return 0
        runCatching {
            val rs = db.getReference("messageReceipts/$chatId").orderByKey()
                .startAt(list.first().id).endAt(list.last().id).get().await()
            rs.children.forEach { c -> c.key?.let { receiptCache[it] = receiptOf(c) } }
        }
        withContext(serial) {
            list.forEach { m ->
                val merged = MessageMerge.merge(dao.get(m.id)?.toDomain(), m, receiptCache[m.id])
                dao.upsert(merged.toEntity())
            }
        }
        return list.size
    }

    // ---------- sending ----------

    /**
     * Writes the message locally (SENDING), then pushes it to Realtime Database.
     * The id is generated on the client, so repeating the write after a reconnect can never create a duplicate.
     * Translation happens asynchronously on the server; the message is delivered even if the translator is down.
     */
    suspend fun send(
        chatId: String, senderId: String, recipientId: String,
        sourceLang: String, targetLang: String, text: String, replyTo: String?
    ): String {
        val clean = text.trim()
        require(Validators.messageText(clean)) { "Message is empty or too long" }
        val ref = msgsRef(chatId).push()
        val id = ref.key ?: error("No id")
        val entity = newEntity(id, chatId, senderId, recipientId, sourceLang, targetLang, clean, replyTo)
        withContext(serial) { dao.upsert(entity) }
        dispatch(entity)
        schedulePendingWork()
        return id
    }

    /** Sends an already compressed image (bytes live in chatMedia/..., the message carries a tiny thumbnail). */
    suspend fun sendImage(
        chatId: String, senderId: String, recipientId: String, sourceLang: String, targetLang: String,
        caption: String, image: CompressedImage, replyTo: String?
    ): String {
        val cap = caption.trim().take(1000)
        val ref = msgsRef(chatId).push()
        val id = ref.key ?: error("No id")
        val entity = newEntity(id, chatId, senderId, recipientId, sourceLang, targetLang, cap, replyTo).copy(
            messageType = "image", mediaThumb = B64.enc(image.thumb), mediaW = image.width, mediaH = image.height
        )
        withContext(serial) {
            dao.putMedia(MediaEntity("m_$id", image.bytes)) // sender sees it instantly, even offline
            dao.upsert(entity)
        }
        dispatch(entity)
        schedulePendingWork()
        return id
    }

    /** Offers a file. Only metadata goes to the database; the bytes are streamed phone-to-phone on request. */
    suspend fun sendFile(
        chatId: String, senderId: String, recipientId: String, sourceLang: String, targetLang: String,
        uri: android.net.Uri, name: String, size: Long, mime: String?, replyTo: String?
    ): String {
        val ref = msgsRef(chatId).push()
        val id = ref.key ?: error("No id")
        val entity = newEntity(id, chatId, senderId, recipientId, sourceLang, targetLang, "", replyTo).copy(
            messageType = "file", fileName = name.take(200), fileSize = size, fileMime = mime?.take(100)
        )
        withContext(serial) {
            dao.putShare(com.nhdeveloper.nhconnect.data.local.FileShareEntity(id, uri.toString()))
            dao.upsert(entity)
        }
        dispatch(entity)
        schedulePendingWork()
        return id
    }

    private fun newEntity(
        id: String, chatId: String, senderId: String, recipientId: String,
        sourceLang: String, targetLang: String, text: String, replyTo: String?
    ) = MessageEntity(
        id = id, chatId = chatId, senderId = senderId, recipientId = recipientId,
        originalText = text, sourceLanguage = sourceLang, targetLanguage = targetLang,
        translatedText = null, translationStatus = TranslationStatus.PENDING, translationError = null,
        detectedLanguage = null, createdAt = System.currentTimeMillis(), editedAt = null,
        replyToId = replyTo, deleted = false, reactions = "", sendState = SendState.SENDING,
        deliveredAt = null, readAt = null
    )

    /** Pushes message (unless it already exists on the server) and, for images, the compressed media node. */
    private fun dispatch(e: MessageEntity, skipMessage: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            try {
                var msgTask: com.google.android.gms.tasks.Task<Void>? = null
                if (!skipMessage) {
                    val map = HashMap<String, Any?>()
                    map["senderId"] = e.senderId
                    map["recipientId"] = e.recipientId
                    map["originalText"] = e.originalText
                    map["sourceLanguage"] = e.sourceLanguage
                    map["targetLanguage"] = e.targetLanguage
                    map["translationStatus"] = "pending"
                    map["messageType"] = e.messageType
                    map["createdAt"] = ServerValue.TIMESTAMP
                    if (e.replyToId != null) map["replyToMessageId"] = e.replyToId
                    if (e.messageType == "file") {
                        map["fileName"] = e.fileName
                        map["fileSize"] = e.fileSize
                        if (e.fileMime != null) map["fileMime"] = e.fileMime
                    }
                    if (e.messageType == "image") {
                        map["mediaThumb"] = e.mediaThumb
                        map["mediaW"] = e.mediaW
                        map["mediaH"] = e.mediaH
                    }
                    msgTask = msgsRef(e.chatId).child(e.id).setValue(map)
                }
                var mediaTask: com.google.android.gms.tasks.Task<Void>? = null
                if (e.messageType == "image") {
                    val bytes = dao.getMedia("m_${e.id}")?.bytes
                    if (bytes != null) {
                        mediaTask = db.getReference("chatMedia/${e.chatId}/${e.id}")
                            .setValue(mapOf("data" to B64.enc(bytes), "w" to e.mediaW, "h" to e.mediaH))
                    }
                }
                msgTask?.await()
                mediaTask?.await()
                withContext(serial) { dao.setState(e.id, SendState.SENT) }
            } catch (ex: Exception) {
                // Permission errors will not fix themselves; anything else stays SENDING so the worker retries.
                val denied = ex.message?.contains("Permission denied", true) == true
                withContext(serial) { dao.setState(e.id, if (denied) SendState.FAILED else SendState.SENDING) }
            }
        }
    }

    /** True when the server already holds the message (and its media); otherwise re-sends only what is missing. */
    private suspend fun confirmOrResend(e: MessageEntity): Boolean {
        val exists = try {
            withTimeout(8_000) { msgsRef(e.chatId).child(e.id).child("senderId").get().await().exists() }
        } catch (t: Exception) { return false }
        var mediaOk = true
        if (e.messageType == "image") {
            mediaOk = try {
                withTimeout(8_000) { db.getReference("chatMedia/${e.chatId}/${e.id}/w").get().await().exists() }
            } catch (t: Exception) { return false }
        }
        if (exists && mediaOk) {
            withContext(serial) { dao.setState(e.id, SendState.SENT) }
            return true
        }
        dispatch(e, skipMessage = exists)
        return false
    }

    /** Manual retry of a message that failed. Same id, so it can never be duplicated. */
    suspend fun retrySend(messageId: String) {
        val e = withContext(serial) { dao.get(messageId) } ?: return
        withContext(serial) { dao.setState(messageId, SendState.SENDING) }
        if (!confirmOrResend(e.copy(sendState = SendState.SENDING))) schedulePendingWork()
    }

    /**
     * Called on app start and by the WorkManager job (exponential backoff).
     * Returns true when nothing is left to confirm.
     */
    suspend fun reconcilePending(): Boolean {
        if (auth.currentUser == null) return true
        var allDone = true
        for (e in withContext(serial) { dao.sending() }) {
            if (!confirmOrResend(e)) allDone = false
        }
        return allDone
    }

    // ---------- message actions ----------

    fun edit(chatId: String, messageId: String, newText: String) {
        val clean = newText.trim()
        require(Validators.messageText(clean))
        msgsRef(chatId).child(messageId).updateChildren(
            mapOf("originalText" to clean, "editedAt" to ServerValue.TIMESTAMP)
        )
    }

    fun delete(chatId: String, messageId: String) {
        msgsRef(chatId).child(messageId).updateChildren(mapOf("originalText" to "", "deleted" to true))
        db.getReference("chatMedia/$chatId/$messageId").removeValue() // frees the compressed image (no-op for text)
    }

    fun react(chatId: String, messageId: String, me: String, emoji: String?) {
        val r = msgsRef(chatId).child(messageId).child("reactions").child(me)
        if (emoji == null) r.removeValue() else r.setValue(emoji)
    }

    /** Asks the backend to (re)translate a message for the recipient's CURRENT language. Also used for "tap to retry". */
    fun requestTranslation(chatId: String, messageId: String, me: String) {
        db.getReference("translationRequests/$chatId/$messageId")
            .setValue(mapOf("requestedBy" to me, "requestedAt" to ServerValue.TIMESTAMP))
    }

    // ---------- receipts, typing ----------

    fun markDelivered(chatId: String, me: String, messageId: String) {
        if (messageId.isBlank() || !deliveredWritten.add("$chatId/$messageId")) return
        db.getReference("messageReceipts/$chatId/$messageId/$me/deliveredAt").setValue(ServerValue.TIMESTAMP)
    }

    fun markRead(chatId: String, me: String, messages: List<ChatMessage>) {
        val updates = HashMap<String, Any?>()
        messages.filter { it.recipientId == me && it.readAt == null }.forEach { m ->
            if (readWritten.add("$chatId/${m.id}")) {
                updates["${m.id}/$me/readAt"] = ServerValue.TIMESTAMP
                if (m.deliveredAt == null && !deliveredWritten.contains("$chatId/${m.id}")) {
                    updates["${m.id}/$me/deliveredAt"] = ServerValue.TIMESTAMP
                }
            }
        }
        if (updates.isNotEmpty()) db.getReference("messageReceipts/$chatId").updateChildren(updates)
        db.getReference("userChats/$me/$chatId/unreadCount").setValue(0)
    }

    fun observeTyping(chatId: String, peer: String): Flow<Boolean> =
        valueFlow(db, "typing/$chatId/$peer") { it.getValue(Boolean::class.javaObjectType) == true }

    fun setTyping(chatId: String, me: String, typing: Boolean) {
        val ref = db.getReference("typing/$chatId/$me")
        if (typing) {
            ref.onDisconnect().removeValue() // automatic cleanup if the app dies
            ref.setValue(true)
        } else ref.removeValue()
    }

    suspend fun clearLocal() = withContext(serial) { dao.clearAll(); receiptCache.clear() }
}
