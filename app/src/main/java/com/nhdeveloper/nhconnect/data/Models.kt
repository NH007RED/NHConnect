package com.nhdeveloper.nhconnect.data

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val country: String = "",
    val language: String = "en",
    val photoUrl: String? = null,
    /** > 0 when the user uploaded a picture (stored compressed in Realtime Database under userPhotos/{uid}). */
    val photoVer: Long = 0L,
    val createdAt: Long = 0L,
    /** true when the account no longer exists (deleted) */
    val missing: Boolean = false
) {
    companion object {
        fun deleted(uid: String) = UserProfile(uid = uid, displayName = "Deleted account", missing = true)
    }
}

data class PresenceInfo(val online: Boolean = false, val lastSeen: Long = 0L)

enum class TranslationStatus {
    PENDING, TRANSLATING, COMPLETED, FAILED, SKIPPED;

    companion object {
        fun parse(v: String?): TranslationStatus = when (v?.lowercase()) {
            "translating" -> TRANSLATING
            "completed" -> COMPLETED
            "failed" -> FAILED
            "skipped" -> SKIPPED
            else -> PENDING
        }
    }
}

/** Local-only delivery state of a message this device sent. */
enum class SendState { SENDING, SENT, FAILED }

data class ChatMessage(
    val id: String,
    val chatId: String,
    val senderId: String,
    val recipientId: String,
    val originalText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val translatedText: String? = null,
    val translationStatus: TranslationStatus = TranslationStatus.PENDING,
    val translationError: String? = null,
    val detectedLanguage: String? = null,
    val createdAt: Long,
    val editedAt: Long? = null,
    val replyToId: String? = null,
    val deleted: Boolean = false,
    val reactions: Map<String, String> = emptyMap(),
    val sendState: SendState = SendState.SENT,
    /** Receipt written by the recipient. */
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    /** "text" or "image". Image bytes live in chatMedia/{chatId}/{messageId}; only a tiny thumbnail is in the message. */
    val messageType: String = "text",
    val mediaThumb: String? = null,
    val mediaW: Int = 0,
    val mediaH: Int = 0,
    /** "file" messages carry only metadata; the bytes travel phone-to-phone over WebRTC when the receiver taps Download. */
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val fileMime: String? = null
) {
    val isImage: Boolean get() = messageType == "image"
    val isFile: Boolean get() = messageType == "file"
}

data class ChatSummary(
    val chatId: String,
    val peerId: String,
    val lastMessageId: String,
    val lastMessageText: String,
    val lastMessageAt: Long,
    val lastSenderId: String,
    val unread: Int,
    val favorite: Boolean,
    val muted: Boolean
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoTranslate: Boolean = true,
    val showOriginalByDefault: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val notificationPreview: Boolean = true,
    val sharePresence: Boolean = true,
    val onboardingDone: Boolean = false,
    val draftLanguage: String = "en",
    val draftCountry: String = "IN"
)
