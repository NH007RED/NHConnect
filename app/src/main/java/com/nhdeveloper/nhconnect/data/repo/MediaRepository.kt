package com.nhdeveloper.nhconnect.data.repo

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.nhdeveloper.nhconnect.data.local.MediaEntity
import com.nhdeveloper.nhconnect.data.local.MessageDao
import com.nhdeveloper.nhconnect.media.B64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Images are stored compressed (base64 JPEG) in Realtime Database, never in Firebase Storage:
 *   chatMedia/{chatId}/{messageId}  -> chat images
 *   userPhotos/{uid}                -> profile pictures (256 px)
 * Everything is cached in Room, so each image is downloaded once.
 */
class MediaRepository(private val db: FirebaseDatabase, private val dao: MessageDao, private val scope: CoroutineScope) {

    /** Emits the full image bytes as soon as they exist (from cache, or when the sender's upload lands). */
    fun chatImage(chatId: String, messageId: String): Flow<ByteArray?> = flow {
        val cached = dao.getMedia("m_$messageId")
        if (cached != null) { emit(cached.bytes); return@flow }
        emitAll(callbackFlow {
            val ref = db.getReference("chatMedia/$chatId/$messageId/data")
            val l = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val str = snapshot.getValue(String::class.java) ?: return
                    val bytes = runCatching { B64.dec(str) }.getOrNull() ?: return
                    scope.launch { dao.putMedia(MediaEntity("m_$messageId", bytes)) }
                    trySend(bytes)
                    close()
                }
                override fun onCancelled(error: DatabaseError) { close() }
            }
            ref.addValueEventListener(l)
            awaitClose { ref.removeEventListener(l) }
        })
    }

    suspend fun saveLocalChatImage(messageId: String, bytes: ByteArray) = dao.putMedia(MediaEntity("m_$messageId", bytes))

    /** Profile picture for a given version; downloaded once, then served from Room. */
    fun profilePhoto(uid: String, version: Long): Flow<ByteArray?> = flow {
        val key = "p_${uid}_$version"
        dao.getMedia(key)?.let { emit(it.bytes); return@flow }
        val str = runCatching { db.getReference("userPhotos/$uid/data").get().await().getValue(String::class.java) }.getOrNull()
        val bytes = str?.let { runCatching { B64.dec(it) }.getOrNull() }
        if (bytes != null) dao.putMedia(MediaEntity(key, bytes))
        emit(bytes)
    }

    suspend fun setProfilePhoto(uid: String, bytes: ByteArray) {
        val version = System.currentTimeMillis()
        db.getReference("userPhotos/$uid").setValue(mapOf("data" to B64.enc(bytes), "updatedAt" to ServerValue.TIMESTAMP)).await()
        db.getReference("users/$uid").updateChildren(mapOf("photoVer" to version)).await()
        dao.putMedia(MediaEntity("p_${uid}_$version", bytes))
    }

    suspend fun removeProfilePhoto(uid: String) {
        db.getReference("userPhotos/$uid").removeValue().await()
        db.getReference("users/$uid").updateChildren(mapOf("photoVer" to 0, "photoUrl" to null)).await()
    }
}
