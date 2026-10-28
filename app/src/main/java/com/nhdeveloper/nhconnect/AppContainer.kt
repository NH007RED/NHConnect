package com.nhdeveloper.nhconnect

import android.app.Application
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.nhdeveloper.nhconnect.data.local.AppDatabase
import com.nhdeveloper.nhconnect.data.repo.AuthRepository
import com.nhdeveloper.nhconnect.data.repo.AuthState
import com.nhdeveloper.nhconnect.data.repo.ChatListRepository
import com.nhdeveloper.nhconnect.data.repo.ChatRepository
import com.nhdeveloper.nhconnect.data.repo.MediaRepository
import com.nhdeveloper.nhconnect.data.repo.PresenceManager
import com.nhdeveloper.nhconnect.data.repo.SettingsRepository
import com.nhdeveloper.nhconnect.data.repo.UserRepository
import com.nhdeveloper.nhconnect.p2p.P2PManager
import com.nhdeveloper.nhconnect.sync.PendingSendWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** Hand-wired dependency container (no DI framework, so it builds cleanly on any toolchain). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: FirebaseDatabase = run {
        val d = if (BuildConfig.FIREBASE_DATABASE_URL.isBlank()) FirebaseDatabase.getInstance()
        else FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL)
        // Offline queue: writes made without network are persisted and replayed on reconnect.
        runCatching { d.setPersistenceEnabled(true) }
        d
    }
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val room = Room.databaseBuilder(app, AppDatabase::class.java, "nhconnect.db")
        .fallbackToDestructiveMigration(true).build()

    val settingsRepository = SettingsRepository(app, database)
    val userRepository = UserRepository(database, scope)
    val authRepository = AuthRepository(firebaseAuth, database, room)
    val chatListRepository = ChatListRepository(database)
    val chatRepository = ChatRepository(database, firebaseAuth, room.messages(), scope) { PendingSendWorker.enqueue(app) }
    val mediaRepository = MediaRepository(database, room.messages(), scope)
    val p2pManager = P2PManager(app, database, room.messages(), scope)
    val presenceManager = PresenceManager(database, authRepository, settingsRepository, scope)

    init {
        presenceManager.start()
        PendingSendWorker.enqueue(app)
        // When signed in: register the push token, publish notification prefs, and mark the latest incoming
        // message of each chat as delivered while the app is running.
        scope.launch {
            authRepository.state.flatMapLatest { st ->
                if (st is AuthState.Ready) {
                    p2pManager.startInbox(st.uid) // from now on this phone answers download requests
                    runCatching { authRepository.registerPushToken() }
                    runCatching { settingsRepository.pushNotificationPrefs(st.uid) }
                    chatListRepository.observe(st.uid)
                } else { p2pManager.stopInbox(); emptyFlow() }
            }.collect { list ->
                val me = authRepository.currentUid ?: return@collect
                list.filter { it.unread > 0 && it.lastSenderId != me && it.lastMessageId.isNotBlank() }
                    .forEach { chatRepository.markDelivered(it.chatId, me, it.lastMessageId) }
            }
        }
    }
}
