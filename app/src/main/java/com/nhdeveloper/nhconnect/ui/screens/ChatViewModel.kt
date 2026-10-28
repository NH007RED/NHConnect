package com.nhdeveloper.nhconnect.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhdeveloper.nhconnect.AppContainer
import com.nhdeveloper.nhconnect.data.AppSettings
import com.nhdeveloper.nhconnect.data.ChatMessage
import com.nhdeveloper.nhconnect.data.PresenceInfo
import com.nhdeveloper.nhconnect.data.UserProfile
import com.nhdeveloper.nhconnect.data.repo.ChatRepository
import com.nhdeveloper.nhconnect.domain.ChatIds
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nhdeveloper.nhconnect.media.CompressedImage
import com.nhdeveloper.nhconnect.p2p.P2PManager
import com.nhdeveloper.nhconnect.media.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val c: AppContainer, val peerUid: String) : ViewModel() {
    val me: String = c.authRepository.currentUid ?: ""
    val chatId: String = ChatIds.chatId(me, peerUid)

    private val limit = MutableStateFlow(ChatRepository.PAGE_SIZE)
    private val exhausted = MutableStateFlow(false)
    private val loadingOlder = MutableStateFlow(false)

    val messages: StateFlow<List<ChatMessage>> = limit
        .flatMapLatest { c.chatRepository.observeLocal(chatId, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasMore: StateFlow<Boolean> = combine(exhausted, messages, limit) { ex, list, l -> !ex || list.size >= l }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val settings: StateFlow<AppSettings> = c.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val myProfile: StateFlow<UserProfile?> = c.userRepository.profile(me)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val peer: StateFlow<UserProfile?> = c.userRepository.profile(peerUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val presence: StateFlow<PresenceInfo> = c.userRepository.presence(peerUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresenceInfo())
    val peerTyping: StateFlow<Boolean> = c.chatRepository.observeTyping(chatId, peerUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val blockedUids: StateFlow<Set<String>> = c.userRepository.blocked(me)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _toggled = MutableStateFlow<Set<String>>(emptySet())
    val toggled: StateFlow<Set<String>> = _toggled

    private val _firstUnread = MutableStateFlow<String?>(null)
    val firstUnreadId: StateFlow<String?> = _firstUnread
    private var unreadCaptured = false

    val replyTo = MutableStateFlow<ChatMessage?>(null)
    val editing = MutableStateFlow<ChatMessage?>(null)
    val info = MutableStateFlow<String?>(null)

    private var typingJob: Job? = null
    private var isTyping = false

    init {
        // Newest page + receipts stay synced while this screen exists; cancelled with viewModelScope.
        viewModelScope.launch { c.chatRepository.syncChat(chatId, me) }
        viewModelScope.launch {
            messages.collect { list ->
                if (!unreadCaptured && list.isNotEmpty()) {
                    unreadCaptured = true
                    _firstUnread.value = list.firstOrNull { it.recipientId == me && it.readAt == null && !it.deleted }?.id
                }
            }
        }
    }

    fun loadOlder() {
        val oldest = messages.value.firstOrNull() ?: return
        if (loadingOlder.value) return
        loadingOlder.value = true
        viewModelScope.launch {
            runCatching { c.chatRepository.loadOlder(chatId, oldest.id) }
                .onSuccess { got -> if (got < ChatRepository.PAGE_SIZE) exhausted.value = true }
            limit.value += ChatRepository.PAGE_SIZE
            loadingOlder.value = false
        }
    }

    fun markRead() {
        val list = messages.value
        if (list.any { it.recipientId == me && it.readAt == null }) c.chatRepository.markRead(chatId, me, list)
    }

    fun toggleOriginal(id: String) { _toggled.value = _toggled.value.let { if (id in it) it - id else it + id } }

    fun onDraftChanged(text: String) {
        if (text.isBlank()) { stopTyping(); return }
        if (!isTyping) { isTyping = true; c.chatRepository.setTyping(chatId, me, true) }
        typingJob?.cancel()
        typingJob = viewModelScope.launch { delay(4_000); stopTyping() }
    }

    private fun stopTyping() {
        typingJob?.cancel()
        if (isTyping) { isTyping = false; c.chatRepository.setTyping(chatId, me, false) }
    }

    fun send(text: String) {
        val my = myProfile.value ?: return
        val other = peer.value ?: return
        if (other.missing) { info.value = "This account no longer exists."; return }
        val edit = editing.value
        stopTyping()
        if (edit != null) {
            runCatching { c.chatRepository.edit(chatId, edit.id, text) }.onFailure { info.value = it.message }
            editing.value = null
            return
        }
        val reply = replyTo.value?.id
        replyTo.value = null
        viewModelScope.launch {
            runCatching { c.chatRepository.send(chatId, me, peerUid, my.language, other.language, text, reply) }
                .onFailure { info.value = it.message ?: "Could not send" }
        }
    }

    // ---- images ----
    class PreparedImage(val image: CompressedImage, val preview: ImageBitmap)

    val pendingImage = MutableStateFlow<PreparedImage?>(null)
    val preparing = MutableStateFlow(false)

    /** Compresses the picked photo (max 1024 px JPEG, <= ~230 KB) before showing the send preview. */
    fun prepareImage(uri: Uri) {
        viewModelScope.launch {
            preparing.value = true
            runCatching {
                val img = ImageCompressor.chatImage(c.app, uri)
                val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size).asImageBitmap() }
                PreparedImage(img, bmp)
            }.onSuccess { pendingImage.value = it }.onFailure { info.value = "Could not read this image." }
            preparing.value = false
        }
    }

    fun cancelImage() { pendingImage.value = null }

    fun sendImage(caption: String) {
        val my = myProfile.value ?: return
        val other = peer.value ?: return
        val prepared = pendingImage.value ?: return
        if (other.missing) { info.value = "This account no longer exists."; return }
        pendingImage.value = null
        val reply = replyTo.value?.id
        replyTo.value = null
        viewModelScope.launch {
            runCatching { c.chatRepository.sendImage(chatId, me, peerUid, my.language, other.language, caption, prepared.image, reply) }
                .onFailure { info.value = it.message ?: "Could not send the image" }
        }
    }

    // ---- file sharing (phone-to-phone) ----
    val transfers = c.p2pManager.transfers

    fun sendFile(uri: Uri) {
        val my = myProfile.value ?: return
        val other = peer.value ?: return
        if (other.missing) { info.value = "This account no longer exists."; return }
        val reply = replyTo.value?.id
        replyTo.value = null
        viewModelScope.launch {
            runCatching {
                val cr = c.app.contentResolver
                var name = "file"
                var size = 0L
                withContext(Dispatchers.IO) {
                    cr.query(uri, null, null, null, null)?.use { cur ->
                        if (cur.moveToFirst()) {
                            val ni = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val si = cur.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (ni >= 0) name = cur.getString(ni) ?: name
                            if (si >= 0 && !cur.isNull(si)) size = cur.getLong(si)
                        }
                    }
                }
                require(size > 0) { "This file is empty or cannot be read." }
                require(size <= P2PManager.MAX_BYTES) { "File is too large (max 200 MB)." }
                runCatching { cr.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                c.chatRepository.sendFile(chatId, me, peerUid, my.language, other.language, uri, name, size, cr.getType(uri), reply)
            }.onFailure { info.value = it.message ?: "Could not share the file" }
        }
    }

    fun download(m: ChatMessage) {
        c.p2pManager.download(me, m.chatId, m.id, m.senderId, m.fileName ?: "file", m.fileSize, m.fileMime)
    }

    fun retrySend(m: ChatMessage) { viewModelScope.launch { c.chatRepository.retrySend(m.id) } }
    fun requestTranslation(m: ChatMessage) { c.chatRepository.requestTranslation(chatId, m.id, me) }

    /** After switching the display language: re-translate recent incoming messages that are in another language. */
    fun retranslateOlder(candidates: List<ChatMessage>) {
        candidates.takeLast(40).forEach { c.chatRepository.requestTranslation(chatId, it.id, me) }
    }

    fun delete(m: ChatMessage) { c.chatRepository.delete(chatId, m.id) }
    fun react(m: ChatMessage, emoji: String) {
        val mine = m.reactions[me]
        c.chatRepository.react(chatId, m.id, me, if (mine == emoji) null else emoji)
    }

    fun forward(targetPeerUid: String, text: String) {
        viewModelScope.launch {
            runCatching {
                val mine = c.userRepository.profileOnce(me)
                val other = c.userRepository.profileOnce(targetPeerUid)
                require(!other.missing) { "That account no longer exists" }
                c.chatRepository.send(ChatIds.chatId(me, targetPeerUid), me, targetPeerUid, mine.language, other.language, text, null)
            }.onSuccess { info.value = "Message forwarded" }.onFailure { info.value = it.message ?: "Could not forward" }
        }
    }

    fun block() { c.userRepository.block(me, peerUid) }
    fun unblock() { c.userRepository.unblock(me, peerUid) }

    override fun onCleared() {
        stopTyping()
        super.onCleared()
    }
}
