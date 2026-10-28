package com.nhdeveloper.nhconnect.data.repo

import com.google.firebase.database.FirebaseDatabase
import com.nhdeveloper.nhconnect.data.ChatSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class ChatListRepository(private val db: FirebaseDatabase) {
    /** The summaries are maintained by Cloud Functions; clients only read them. */
    fun observe(me: String): Flow<List<ChatSummary>> =
        valueFlow(db, "userChats/$me") { snap ->
            snap.children.mapNotNull { Parsers.summary(it) }.sortedByDescending { it.lastMessageAt }
        }.catch { emit(emptyList()) }

    fun setFavorite(me: String, chatId: String, value: Boolean) {
        db.getReference("userChats/$me/$chatId/favorite").setValue(value)
    }

    fun setMuted(me: String, chatId: String, value: Boolean) {
        db.getReference("userChats/$me/$chatId/muted").setValue(value)
    }
}
