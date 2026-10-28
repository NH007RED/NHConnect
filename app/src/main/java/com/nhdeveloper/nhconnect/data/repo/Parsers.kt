package com.nhdeveloper.nhconnect.data.repo

import com.google.firebase.database.DataSnapshot
import com.nhdeveloper.nhconnect.data.ChatMessage
import com.nhdeveloper.nhconnect.data.ChatSummary
import com.nhdeveloper.nhconnect.data.PresenceInfo
import com.nhdeveloper.nhconnect.data.SendState
import com.nhdeveloper.nhconnect.data.TranslationStatus
import com.nhdeveloper.nhconnect.data.UserProfile

internal fun DataSnapshot.str(name: String): String? = child(name).getValue(String::class.java)
internal fun DataSnapshot.lng(name: String): Long? = child(name).getValue(Long::class.javaObjectType)
internal fun DataSnapshot.bool(name: String): Boolean? = child(name).getValue(Boolean::class.javaObjectType)

object Parsers {
    fun message(chatId: String, s: DataSnapshot): ChatMessage? {
        val id = s.key ?: return null
        val sender = s.str("senderId") ?: return null
        val recipient = s.str("recipientId") ?: return null
        val reactions = HashMap<String, String>()
        s.child("reactions").children.forEach { r ->
            val k = r.key
            val v = r.getValue(String::class.java)
            if (k != null && v != null) reactions[k] = v
        }
        return ChatMessage(
            id = id,
            chatId = chatId,
            senderId = sender,
            recipientId = recipient,
            originalText = s.str("originalText") ?: "",
            sourceLanguage = s.str("sourceLanguage") ?: "en",
            targetLanguage = s.str("targetLanguage") ?: "en",
            translatedText = s.str("translatedText"),
            translationStatus = TranslationStatus.parse(s.str("translationStatus")),
            translationError = s.str("translationError"),
            detectedLanguage = s.str("detectedLanguage"),
            createdAt = s.lng("createdAt") ?: System.currentTimeMillis(),
            editedAt = s.lng("editedAt"),
            replyToId = s.str("replyToMessageId"),
            deleted = s.bool("deleted") == true,
            reactions = reactions,
            sendState = SendState.SENT,
            messageType = s.str("messageType") ?: "text",
            mediaThumb = s.str("mediaThumb"),
            mediaW = (s.lng("mediaW") ?: 0L).toInt(),
            mediaH = (s.lng("mediaH") ?: 0L).toInt(),
            fileName = s.str("fileName"),
            fileSize = s.lng("fileSize") ?: 0L,
            fileMime = s.str("fileMime")
        )
    }

    fun profile(uid: String, s: DataSnapshot): UserProfile {
        if (!s.exists()) return UserProfile.deleted(uid)
        return UserProfile(
            uid = uid,
            displayName = s.str("displayName") ?: "User",
            username = s.str("username") ?: "",
            country = s.str("country") ?: "",
            language = s.str("language") ?: "en",
            photoUrl = s.str("photoUrl"),
            photoVer = s.lng("photoVer") ?: 0L,
            createdAt = s.lng("createdAt") ?: 0L
        )
    }

    fun presence(s: DataSnapshot) = PresenceInfo(s.bool("online") == true, s.lng("lastSeen") ?: 0L)

    fun summary(c: DataSnapshot): ChatSummary? {
        val chatId = c.key ?: return null
        val peer = c.str("peerId") ?: return null
        return ChatSummary(
            chatId = chatId,
            peerId = peer,
            lastMessageId = c.str("lastMessageId") ?: "",
            lastMessageText = c.str("lastMessageText") ?: "",
            lastMessageAt = c.lng("lastMessageAt") ?: 0L,
            lastSenderId = c.str("lastSenderId") ?: "",
            unread = (c.lng("unreadCount") ?: 0L).toInt(),
            favorite = c.bool("favorite") == true,
            muted = c.bool("muted") == true
        )
    }
}
