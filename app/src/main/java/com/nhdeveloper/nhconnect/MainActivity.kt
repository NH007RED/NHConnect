package com.nhdeveloper.nhconnect

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nhdeveloper.nhconnect.ui.nav.AppRoot
import com.nhdeveloper.nhconnect.ui.theme.NHConnectTheme
import com.nhdeveloper.nhconnect.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val deepLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        val app = application as NHConnectApp
        setContent {
            if (!app.firebaseReady) SetupNeeded() else AppRoot(app.container, deepLink)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** FCM notification taps arrive as intent extras (peerId is the sender's uid). */
    private fun handle(i: Intent?) {
        i?.getStringExtra("peerId")?.takeIf { it.isNotBlank() }?.let { deepLink.value = it }
    }
}

@androidx.compose.runtime.Composable
private fun SetupNeeded() {
    NHConnectTheme(ThemeMode.SYSTEM) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(28.dp)) {
            Text("Firebase is not configured", style = MaterialTheme.typography.headlineMedium)
            Text(
                "\nAdd your google-services.json to the app/ folder (package com.nhdeveloper.nhconnect), then rebuild.\n\n" +
                    "Follow README.md, step 2: Firebase setup.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
