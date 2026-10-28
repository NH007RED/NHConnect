package com.nhdeveloper.nhconnect.data.repo

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.nhdeveloper.nhconnect.data.PresenceInfo
import com.nhdeveloper.nhconnect.data.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

class UserRepository(private val db: FirebaseDatabase, private val scope: CoroutineScope) {
    private val profiles = ConcurrentHashMap<String, Flow<UserProfile>>()
    private val presences = ConcurrentHashMap<String, Flow<PresenceInfo>>()

    /** Shared per uid so lists with many rows reuse one listener. A deleted account emits UserProfile.deleted(). */
    fun profile(uid: String): Flow<UserProfile> = profiles.getOrPut(uid) {
        valueFlow(db, "users/$uid") { Parsers.profile(uid, it) }
            .catch { emit(UserProfile.deleted(uid)) }
            .shareIn(scope, SharingStarted.WhileSubscribed(30_000), replay = 1)
    }

    fun presence(uid: String): Flow<PresenceInfo> = presences.getOrPut(uid) {
        valueFlow(db, "presence/$uid") { Parsers.presence(it) }
            .catch { emit(PresenceInfo()) }
            .shareIn(scope, SharingStarted.WhileSubscribed(30_000), replay = 1)
    }

    suspend fun profileOnce(uid: String): UserProfile = Parsers.profile(uid, db.getReference("users/$uid").get().await())

    /** Prefix search on the lower-cased username. Only public profile fields are returned. */
    suspend fun searchByUsername(query: String, myUid: String): List<UserProfile> {
        val q = query.trim().lowercase().removePrefix("@")
        if (q.length < 2) return emptyList()
        val snap = db.getReference("users").orderByChild("usernameLower")
            .startAt(q).endAt(q + "\uf8ff").limitToFirst(20).get().await()
        return snap.children.mapNotNull { c -> c.key?.let { Parsers.profile(it, c) } }.filter { it.uid != myUid }
    }

    class UserPage(val users: List<UserProfile>, val lastLower: String?, val lastKey: String?, val exhausted: Boolean)

    /** All registered users, alphabetical by username, in pages (rules allow at most 50 per query). */
    suspend fun listUsers(afterLower: String?, afterKey: String?, limit: Int = 30): UserPage {
        var q = db.getReference("users").orderByChild("usernameLower")
        if (afterLower != null && afterKey != null) q = q.startAfter(afterLower, afterKey)
        val snap = q.limitToFirst(limit).get().await()
        val children = snap.children.toList()
        val users = children.mapNotNull { c -> c.key?.let { Parsers.profile(it, c) } }
        val last = children.lastOrNull()
        return UserPage(users, last?.str("usernameLower"), last?.key, children.size < limit)
    }

    fun updateProfile(uid: String, fields: Map<String, Any?>) {
        db.getReference("users/$uid").updateChildren(fields)
    }

    // ----- blocking & reporting -----

    fun blocked(me: String): Flow<Set<String>> =
        valueFlow(db, "userBlocks/$me") { s -> s.children.mapNotNull { it.key }.toSet() }.catch { emit(emptySet()) }

    fun block(me: String, uid: String) { db.getReference("userBlocks/$me/$uid").setValue(true) }
    fun unblock(me: String, uid: String) { db.getReference("userBlocks/$me/$uid").removeValue() }

    suspend fun report(me: String, reportedUid: String, reason: String, chatId: String? = null) {
        val ref = db.getReference("reports").push()
        val map = HashMap<String, Any?>()
        map["reporterId"] = me
        map["reportedUid"] = reportedUid
        map["reason"] = reason.take(500)
        map["createdAt"] = ServerValue.TIMESTAMP
        if (chatId != null) map["chatId"] = chatId
        ref.setValue(map).await()
    }
}
