package com.nhdeveloper.nhconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.text.font.FontWeight
import com.nhdeveloper.nhconnect.ui.IconTile
import com.nhdeveloper.nhconnect.ui.SectionCard
import com.nhdeveloper.nhconnect.ui.auroraHeader
import com.nhdeveloper.nhconnect.ui.theme.NHMint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.nhconnect.data.AppSettings
import com.nhdeveloper.nhconnect.data.Languages
import com.nhdeveloper.nhconnect.data.ThemeMode
import com.nhdeveloper.nhconnect.data.UserProfile
import com.nhdeveloper.nhconnect.data.repo.ErrorText
import com.nhdeveloper.nhconnect.ui.Avatar
import com.nhdeveloper.nhconnect.ui.ErrorText as ErrorLine
import com.nhdeveloper.nhconnect.ui.GreenGradient
import com.nhdeveloper.nhconnect.ui.LanguagePickerDialog
import com.nhdeveloper.nhconnect.ui.LocalContainer
import com.nhdeveloper.nhconnect.ui.NHTextField
import com.nhdeveloper.nhconnect.ui.SectionLabel
import kotlinx.coroutines.launch

@Composable
fun SettingsTab(myUid: String, onOpenProfile: () -> Unit, onOpenProfileOf: (String) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val s by c.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val me by remember { c.userRepository.profile(myUid) }.collectAsStateWithLifecycle(initialValue = null as UserProfile?)
    val blocked by remember { c.userRepository.blocked(myUid) }.collectAsStateWithLifecycle(initialValue = emptySet())
    var dialog by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().auroraHeader(32.dp).statusBarsPadding().clickable(onClick = onOpenProfile).padding(horizontal = 22.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(me, 68.dp)
            Column(Modifier.padding(start = 16.dp)) {
                Text(me?.displayName ?: "…", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("@${me?.username ?: ""}", color = Color.White.copy(alpha = 0.85f))
                Text("${Languages.byCode(me?.language)?.flag ?: ""} ${Languages.nameOf(me?.language)}", color = NHMint, style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionLabel("Chat")
        SectionCard {
            SwitchRow(Icons.Filled.Translate, "Auto-translate messages", "Into ${Languages.nameOf(me?.language)}", s.autoTranslate) { scope.launch { c.settingsRepository.setAutoTranslate(it) } }
            SwitchRow(Icons.Filled.Visibility, "Show original by default", "Original text first, translation on tap", s.showOriginalByDefault) { scope.launch { c.settingsRepository.setShowOriginalDefault(it) } }
            Action(Icons.Filled.Language, "Preferred language", Languages.nameOf(me?.language)) { dialog = "language" }
        }

        SectionLabel("Notifications")
        SectionCard {
            SwitchRow(Icons.Filled.Notifications, "Message notifications", "Push notifications for new messages", s.notificationsEnabled) { scope.launch { c.settingsRepository.setNotifications(myUid, it) } }
            SwitchRow(Icons.Filled.Visibility, "Show message preview", "Off: only says \"New message\"", s.notificationPreview) { scope.launch { c.settingsRepository.setNotificationPreview(myUid, it) } }
        }

        SectionLabel("Appearance")
        SectionCard { Action(Icons.Filled.Palette, "Theme", s.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }) { dialog = "theme" } }

        SectionLabel("Privacy")
        SectionCard {
            SwitchRow(Icons.Filled.Visibility, "Online status & last seen", "If off, others cannot see when you are online", s.sharePresence) { scope.launch { c.settingsRepository.setSharePresence(it) } }
            Action(Icons.Filled.Block, "Blocked users", "${blocked.size} blocked") { dialog = "blocked" }
        }
        Text(
            "Messages are protected in transit (TLS) and at rest by Google's infrastructure encryption. They are NOT end-to-end encrypted: " +
                "the server must read text to translate it.",
            Modifier.padding(horizontal = 26.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionLabel("Account")
        SectionCard {
            Action(Icons.AutoMirrored.Filled.Logout, "Log out", "") { dialog = "logout" }
            Action(Icons.Filled.DeleteForever, "Delete account", "Permanently removes your profile and chats", danger = true) { password = ""; error = null; dialog = "delete" }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(36.dp))
    }

    when (dialog) {
        "language" -> LanguagePickerDialog(me?.language ?: "en", { l -> c.userRepository.updateProfile(myUid, mapOf("language" to l.code)) }, { dialog = null })
        "theme" -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { m ->
                        Row(Modifier.fillMaxWidth().clickable { scope.launch { c.settingsRepository.setTheme(m) }; dialog = null }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(s.themeMode == m, { scope.launch { c.settingsRepository.setTheme(m) }; dialog = null })
                            Text(m.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = { TextButton({ dialog = null }) { Text("Close") } }
        )
        "blocked" -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text("Blocked users") },
            text = {
                if (blocked.isEmpty()) Text("You have not blocked anyone.")
                else Column { blocked.forEach { uid -> BlockedRow(uid, onOpenProfileOf) { c.userRepository.unblock(myUid, uid) } } }
            },
            confirmButton = { TextButton({ dialog = null }) { Text("Close") } }
        )
        "logout" -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text("Log out?") },
            confirmButton = { TextButton({ dialog = null; scope.launch { c.authRepository.logout() } }) { Text("Log out") } },
            dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } }
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text("Delete account?") },
            text = {
                Column {
                    Text("This permanently deletes your profile, username and conversations. Enter your password to confirm.")
                    NHTextField(password, { password = it }, "Password", password = true, modifier = Modifier.padding(top = 12.dp))
                    ErrorLine(error)
                }
            },
            confirmButton = {
                TextButton(enabled = password.isNotEmpty(), onClick = {
                    scope.launch {
                        runCatching { c.authRepository.deleteAccount(password) }
                            .onSuccess { dialog = null }.onFailure { error = ErrorText.friendly(it) }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BlockedRow(uid: String, onOpen: (String) -> Unit, onUnblock: () -> Unit) {
    val c = LocalContainer.current
    val p by remember(uid) { c.userRepository.profile(uid) }.collectAsStateWithLifecycle(initialValue = null as UserProfile?)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(p?.displayName ?: "…", Modifier.weight(1f))
        TextButton(onUnblock) { Text("Unblock") }
    }
}

@Composable
private fun SwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}
