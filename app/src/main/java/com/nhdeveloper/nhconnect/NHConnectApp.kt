package com.nhdeveloper.nhconnect

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp

class NHConnectApp : Application() {
    /** False until app/google-services.json is added; the UI shows a setup screen instead of crashing. */
    val firebaseReady: Boolean get() = FirebaseApp.getApps(this).isNotEmpty()

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("messages", getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        if (firebaseReady) container // eager init so persistence + presence start immediately
    }
}
