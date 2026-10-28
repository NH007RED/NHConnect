package com.nhdeveloper.nhconnect.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.nhdeveloper.nhconnect.data.ChatMessage
import com.nhdeveloper.nhconnect.data.SendState
import com.nhdeveloper.nhconnect.data.TranslationStatus
import com.nhdeveloper.nhconnect.domain.Reactions
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages", indices = [Index(value = ["chatId", "createdAt"])])
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val recipientId: String,
    val originalText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val translatedText: String?,
    val translationStatus: TranslationStatus,
    val translationError: String?,
    val detectedLanguage: String?,
    val createdAt: Long,
    val editedAt: Long?,
    val replyToId: String?,
    val deleted: Boolean,
    val reactions: String,
    val sendState: SendState,
    val deliveredAt: Long?,
    val readAt: Long?,
    val messageType: String = "text",
    val mediaThumb: String? = null,
    val mediaW: Int = 0,
    val mediaH: Int = 0,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val fileMime: String? = null
)

/** Local URI of a file this device offered, so it can be streamed when the other person taps Download. */
@Entity(tableName = "file_shares")
class FileShareEntity(@PrimaryKey val messageId: String, val uri: String)

/** Cached image bytes: chat images ("m_<messageId>") and profile pictures ("p_<uid>_<version>"). */
@Entity(tableName = "media")
class MediaEntity(@PrimaryKey val key: String, val bytes: ByteArray)

fun MessageEntity.toDomain() = ChatMessage(
    id = id, chatId = chatId, senderId = senderId, recipientId = recipientId, originalText = originalText,
    sourceLanguage = sourceLanguage, targetLanguage = targetLanguage, translatedText = translatedText,
    translationStatus = translationStatus, translationError = translationError, detectedLanguage = detectedLanguage,
    createdAt = createdAt, editedAt = editedAt, replyToId = replyToId, deleted = deleted,
    reactions = Reactions.decode(reactions), sendState = sendState, deliveredAt = deliveredAt, readAt = readAt,
    messageType = messageType, mediaThumb = mediaThumb, mediaW = mediaW, mediaH = mediaH,
    fileName = fileName, fileSize = fileSize, fileMime = fileMime
)

fun ChatMessage.toEntity() = MessageEntity(
    id = id, chatId = chatId, senderId = senderId, recipientId = recipientId, originalText = originalText,
    sourceLanguage = sourceLanguage, targetLanguage = targetLanguage, translatedText = translatedText,
    translationStatus = translationStatus, translationError = translationError, detectedLanguage = detectedLanguage,
    createdAt = createdAt, editedAt = editedAt, replyToId = replyToId, deleted = deleted,
    reactions = Reactions.encode(reactions), sendState = sendState, deliveredAt = deliveredAt, readAt = readAt,
    messageType = messageType, mediaThumb = mediaThumb, mediaW = mediaW, mediaH = mediaH,
    fileName = fileName, fileSize = fileSize, fileMime = fileMime
)

@Dao
interface MessageDao {
    /** Newest [limit] rows of a chat, returned oldest-first. Keeps memory bounded for long conversations. */
    @Query(
        "SELECT * FROM (SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC, id DESC LIMIT :limit) " +
            "ORDER BY createdAt ASC, id ASC"
    )
    fun observe(chatId: String, limit: Int): Flow<List<MessageEntity>>

    @Upsert suspend fun upsert(e: MessageEntity)
    @Upsert suspend fun upsertAll(list: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE id = :id") suspend fun get(id: String): MessageEntity?
    @Query("SELECT * FROM messages WHERE sendState = 'SENDING'") suspend fun sending(): List<MessageEntity>
    @Query("UPDATE messages SET sendState = :state WHERE id = :id") suspend fun setState(id: String, state: SendState)

    @Query("UPDATE messages SET deliveredAt = COALESCE(:d, deliveredAt), readAt = COALESCE(:r, readAt) WHERE id = :id")
    suspend fun updateReceipt(id: String, d: Long?, r: Long?)

    @Query("DELETE FROM messages") suspend fun clearAll()

    @Query("SELECT * FROM media WHERE `key` = :key") suspend fun getMedia(key: String): MediaEntity?
    @Upsert suspend fun putMedia(m: MediaEntity)
    @Query("DELETE FROM media") suspend fun clearMedia()

    @Upsert suspend fun putShare(s: FileShareEntity)
    @Query("SELECT * FROM file_shares WHERE messageId = :id") suspend fun getShare(id: String): FileShareEntity?
    @Query("DELETE FROM file_shares") suspend fun clearShares()
}

@Database(entities = [MessageEntity::class, MediaEntity::class, FileShareEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
}
