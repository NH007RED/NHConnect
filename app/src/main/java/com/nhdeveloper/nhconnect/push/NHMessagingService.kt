package com.nhdeveloper.nhconnect.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nhdeveloper.nhconnect.NHConnectApp
import kotlinx.coroutines.launch

class NHMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val app = application as? NHConnectApp ?: return
        if (!app.firebaseReady) return
        val uid = app.container.authRepository.currentUid ?: return
        app.container.scope.launch { runCatching { app.container.authRepository.saveToken(uid, token) } }
    }

    // The backend sends "notification" messages: Android shows them itself while the app is in the background and
    // delivers the tap (with chatId/peerId extras) to MainActivity. In the foreground the live listeners update the UI.
    override fun onMessageReceived(message: RemoteMessage) = Unit
}
