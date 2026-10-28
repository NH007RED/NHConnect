package com.nhdeveloper.nhconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.nhconnect.data.Countries
import com.nhdeveloper.nhconnect.data.Languages
import com.nhdeveloper.nhconnect.data.PresenceInfo
import com.nhdeveloper.nhconnect.data.UserProfile
import com.nhdeveloper.nhconnect.ui.Avatar
import com.nhdeveloper.nhconnect.ui.CountryPickerDialog
import com.nhdeveloper.nhconnect.ui.Format
import com.nhdeveloper.nhconnect.ui.auroraHeader
import com.nhdeveloper.nhconnect.ui.IconTile
import com.nhdeveloper.nhconnect.ui.SectionCard
import com.nhdeveloper.nhconnect.ui.LanguagePickerDialog
import com.nhdeveloper.nhconnect.ui.LocalContainer
import com.nhdeveloper.nhconnect.ui.NHTextField
import com.nhdeveloper.nhconnect.ui.PickerField
import com.nhdeveloper.nhconnect.ui.theme.NHGreen
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(uid: String, onBack: () -> Unit, onMessage: (String) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val me = c.authRepository.currentUid ?: ""
    val isMe = uid == me
    val profile by remember(uid) { c.userRepository.profile(uid) }.collectAsStateWithLifecycle(initialValue = null as UserProfile?)
    val presence by remember(uid) { c.userRepository.presence(uid) }.collectAsStateWithLifecycle(initialValue = PresenceInfo())
    val blocked by remember { c.userRepository.blocked(me) }.collectAsStateWithLifecycle(initialValue = emptySet())
    var pickLanguage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val p = profile
    var busy by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                val bytes = com.nhdeveloper.nhconnect.media.ImageCompressor.profilePhoto(ctx, uri)
                c.mediaRepository.setProfilePhoto(me, bytes)
            }.onSuccess { note = "Profile picture updated." }.onFailure { note = "Could not update the picture." }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().auroraHeader(40.dp).statusBarsPadding().padding(bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) } }
            Avatar(p, 112.dp, online = presence.online && p?.missing != true, modifier = if (isMe) Modifier.clip(CircleShape).clickable { picker.launch("image/*") } else Modifier)
            Spacer(Modifier.height(10.dp))
            Text(p?.displayName ?: "…", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            if (p != null && !p.missing) Text("@${p.username}", color = Color.White.copy(alpha = 0.85f))
            if (!isMe && p?.missing != true) Text(
                if (presence.online) "Online" else Format.lastSeen(presence.lastSeen),
                color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall
            )
            if (p != null && !p.missing) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("${Countries.byCode(p.country)?.flag ?: ""} ${Countries.byCode(p.country)?.name ?: p.country}")
                    Pill("${Languages.byCode(p.language)?.flag ?: ""} ${Languages.nameOf(p.language)}")
                }
            }
        }
        if (p == null) return@Column
        if (p.missing) {
            Text("This account no longer exists.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
        SectionCard {
            if (isMe) {
                Action(Icons.Filled.CameraAlt, "Change profile picture", if (busy) "Compressing and uploading…" else "Pick a photo; it is compressed and stored in the database") { if (!busy) picker.launch("image/*") }
                if ((p.photoVer > 0L) || p.photoUrl != null) Action(Icons.Filled.Delete, "Remove profile picture", "", danger = true) {
                    scope.launch { runCatching { c.mediaRepository.removeProfilePhoto(me) }.onSuccess { note = "Profile picture removed." } }
                }
                Action(Icons.Filled.Translate, "Change display language", Languages.nameOf(p.language)) { pickLanguage = true }
                Action(Icons.Filled.Edit, "Edit profile", "Name and country") { editing = true }
            } else {
                Action(Icons.Filled.ChatBubble, "Send message", "They read it in ${Languages.nameOf(p.language)}") { onMessage(uid) }
                if (uid in blocked) Action(Icons.Filled.Block, "Unblock user", "") { c.userRepository.unblock(me, uid) }
                else Action(Icons.Filled.Block, "Block user", "They can no longer message you", danger = true) { c.userRepository.block(me, uid); note = "User blocked" }
                Action(Icons.Filled.Flag, "Report user", "Tell us what is wrong", danger = true) { reporting = true }
            }
            note?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }

    if (pickLanguage && p != null) LanguagePickerDialog(p.language, { l ->
        c.userRepository.updateProfile(me, mapOf("language" to l.code)); note = "Language updated. New messages will be translated for you in ${l.englishName}."
    }, { pickLanguage = false })
    if (editing && p != null) EditProfileDialog(p, { editing = false }) { fields -> c.userRepository.updateProfile(me, fields); editing = false }
    if (reporting) ReportDialog({ reporting = false }) { reason ->
        scope.launch {
            runCatching { c.userRepository.report(me, uid, reason, com.nhdeveloper.nhconnect.domain.ChatIds.chatId(me, uid)) }
                .onSuccess { note = "Report sent. Thank you." }.onFailure { note = "Could not send the report." }
        }
        reporting = false
    }
}

@Composable
private fun Pill(text: String) {
    Text(text, Modifier.background(Color.White.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.Medium)
}

@Composable
fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, if (danger) MaterialTheme.colorScheme.error else NHGreen)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditProfileDialog(p: UserProfile, onDismiss: () -> Unit, onSave: (Map<String, Any?>) -> Unit) {
    var name by remember { mutableStateOf(p.displayName) }
    var country by remember { mutableStateOf(p.country) }
    var pick by remember { mutableStateOf(false) }
    val valid = name.trim().length in 1..50
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NHTextField(name, { name = it }, "Display name")
                PickerField("Country", "${Countries.byCode(country)?.flag ?: ""} ${Countries.byCode(country)?.name ?: country}", { pick = true })
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(mapOf("displayName" to name.trim(), "country" to country))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
    if (pick) CountryPickerDialog(country, { country = it }, { pick = false })
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val reasons = listOf("Spam", "Harassment or abuse", "Inappropriate content", "Impersonation", "Other")
    var sel by remember { mutableStateOf(reasons.first()) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Report user") },
        text = {
            Column {
                reasons.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { sel = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(sel == r, { sel = r }); Text(r)
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSubmit(sel) }) { Text("Submit") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
