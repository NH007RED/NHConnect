package com.nhdeveloper.nhconnect.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.nhdeveloper.nhconnect.media.B64
import com.nhdeveloper.nhconnect.p2p.Phase
import com.nhdeveloper.nhconnect.p2p.TransferState
import com.nhdeveloper.nhconnect.media.ImageCache
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nhdeveloper.nhconnect.data.ChatMessage
import com.nhdeveloper.nhconnect.data.Languages
import com.nhdeveloper.nhconnect.data.SendState
import com.nhdeveloper.nhconnect.domain.DisplayDecision
import com.nhdeveloper.nhconnect.domain.MessageDisplay
import com.nhdeveloper.nhconnect.ui.Avatar
import com.nhdeveloper.nhconnect.ui.Format
import com.nhdeveloper.nhconnect.ui.GreenGradient
import com.nhdeveloper.nhconnect.ui.BubbleGradient
import com.nhdeveloper.nhconnect.ui.auroraHeader
import com.nhdeveloper.nhconnect.ui.chatWallpaper
import com.nhdeveloper.nhconnect.ui.theme.NHMint
import com.nhdeveloper.nhconnect.ui.LocalContainer
import com.nhdeveloper.nhconnect.ui.theme.NHBlueTick
import com.nhdeveloper.nhconnect.ui.theme.NHGreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface Row2 {
    data class Date(val label: String) : Row2
    data object Unread : Row2
    data class Msg(val m: ChatMessage) : Row2
}

private val QuickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
private val EmojiGrid = ("😀😁😂🤣😊😍😘😎🤔😅😭😡👍👎👏🙏💪🔥🎉❤️💚💔✨⭐🌹" +
    "😴🤗🙄😬🥳😇🤝👋🙌🤲🏠🍕☕🎂🌍✈️📞💬").let { s ->
    val out = ArrayList<String>()
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        var end = i + Character.charCount(cp)
        // keep variation selectors / ZWJ sequences attached to the base emoji
        while (end < s.length && (s[end] == '\uFE0F' || s[end] == '\u200D')) end++
        out.add(s.substring(i, end))
        i = end
    }
    out
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(peerUid: String, onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val c = LocalContainer.current
    val vm: ChatViewModel = viewModel(key = "chat_$peerUid", factory = viewModelFactory { initializer { ChatViewModel(c, peerUid) } })
    val messages by vm.messages.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val peer by vm.peer.collectAsStateWithLifecycle()
    val myProfile by vm.myProfile.collectAsStateWithLifecycle()
    val presence by vm.presence.collectAsStateWithLifecycle()
    val typing by vm.peerTyping.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val toggled by vm.toggled.collectAsStateWithLifecycle()
    val firstUnread by vm.firstUnreadId.collectAsStateWithLifecycle()
    val replyTo by vm.replyTo.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val blocked by vm.blockedUids.collectAsStateWithLifecycle()

    val viewerLang = myProfile?.language ?: "en"
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var emojiOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<ChatMessage?>(null) }
    var forwardText by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<ChatMessage?>(null) }
    var viewerImage by remember { mutableStateOf<ImageBitmap?>(null) }
    val pending by vm.pendingImage.collectAsStateWithLifecycle()
    val preparing by vm.preparing.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    var riskyFor by remember { mutableStateOf<ChatMessage?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.sendFile(uri) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.prepareImage(uri) }

    LaunchedEffect(info) { info?.let { snackbar.showSnackbar(it); vm.info.value = null } }

    // Mark incoming messages as read only while the screen is actually visible (RESUMED).
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(messages, lifecycleState) { if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) vm.markRead() }

    val items = remember(messages, firstUnread) {
        val out = ArrayList<Row2>()
        var prev: ChatMessage? = null
        messages.forEach { m ->
            if (prev == null || !Format.sameDay(prev!!.createdAt, m.createdAt)) out.add(Row2.Date(Format.dateLabel(m.createdAt)))
            if (m.id == firstUnread) out.add(Row2.Unread)
            out.add(Row2.Msg(m)); prev = m
        }
        out
    }
    val listState = rememberLazyListState()
    // Auto-scroll for new messages when near the bottom (or when I sent it).
    val lastId = messages.lastOrNull()?.id
    LaunchedEffect(lastId) {
        if (items.isNotEmpty()) {
            val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= items.size - 3 } ?: true
            val mine = messages.lastOrNull()?.senderId == vm.me
            if (nearBottom || mine) listState.animateScrollToItem(items.size - 1)
        }
    }
    // Pagination: reaching the top loads one older page.
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    LaunchedEffect(atTop, hasMore, messages.size) { if (atTop && hasMore && messages.isNotEmpty()) vm.loadOlder() }

    val needRetranslate = remember(messages, viewerLang, settings.autoTranslate) {
        messages.filter { m ->
            MessageDisplay.decide(m, vm.me, viewerLang, settings.autoTranslate, settings.showOriginalByDefault, false).needsRetranslate
        }
    }
    val peerBlocked = peerUid in blocked

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().auroraHeader(26.dp).statusBarsPadding().padding(start = 4.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onOpenProfile(peerUid) }, verticalAlignment = Alignment.CenterVertically) {
                    Avatar(peer, 46.dp, online = presence.online && peer?.missing != true)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(peer?.displayName ?: "…", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val status = when {
                            peer?.missing == true -> ""
                            typing -> "typing…"
                            presence.online -> "Online"
                            else -> Format.lastSeen(presence.lastSeen)
                        }
                        if (status.isNotEmpty()) Text(status, fontSize = 12.sp, color = NHMint)
                    }
                }
                if (peer != null && peer?.missing != true) {
                    Text(
                        "${Languages.byCode(viewerLang)?.code?.uppercase() ?: ""} ⇄ ${peer?.language?.uppercase() ?: ""}",
                        Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
                    )
                }
            }
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
                if (needRetranslate.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).clickable { vm.retranslateOlder(needRetranslate) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Translate, null, Modifier.size(18.dp), tint = NHGreen)
                        Text(
                            "${needRetranslate.size} older message(s) are not in ${Languages.nameOf(viewerLang)}. Tap to translate.",
                            Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (peerBlocked) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("You blocked this user.", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton({ vm.unblock() }) { Text("Unblock") }
                    }
                } else {
                    Composer(
                        draft = draft,
                        onDraft = { draft = it; vm.onDraftChanged(it) },
                        replyTo = replyTo, editing = editing,
                        emojiOpen = emojiOpen,
                        onToggleEmoji = { emojiOpen = !emojiOpen; if (emojiOpen) focus.clearFocus() },
                        onCancelContext = { vm.replyTo.value = null; vm.editing.value = null; if (editing != null) draft = "" },
                        onAttach = { picker.launch("image/*") },
                        onAttachFile = { filePicker.launch(arrayOf("*/*")) },
                        onSend = {
                            if (draft.isNotBlank()) { vm.send(draft); draft = ""; emojiOpen = false }
                        }
                    )
                    AnimatedVisibility(emojiOpen) {
                        LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surface)) {
                            gridItems(EmojiGrid) { e ->
                                Text(e, Modifier.clickable { draft += e; vm.onDraftChanged(draft) }.padding(8.dp), fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(MaterialTheme.colorScheme.background).chatWallpaper(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))) {
            if (messages.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.Translate, null, Modifier.size(48.dp), tint = NHGreen)
                    Spacer(Modifier.height(12.dp))
                    Text("Say hello 👋", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Write in your own language. ${peer?.displayName ?: "Your friend"} will read it in ${Languages.nameOf(peer?.language)}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hasMore && messages.isNotEmpty()) item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                }
                items(items, key = { when (it) { is Row2.Date -> "d_${it.label}_${items.indexOf(it)}"; Row2.Unread -> "unread"; is Row2.Msg -> it.m.id } }) { row ->
                    when (row) {
                        is Row2.Date -> Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(
                                row.label,
                                Modifier.shadow(3.dp, RoundedCornerShape(14.dp), clip = false).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row2.Unread -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(1.dp).background(NHGreen.copy(alpha = 0.4f)))
                            Text("  Unread messages  ", style = MaterialTheme.typography.bodySmall, color = NHGreen, fontWeight = FontWeight.Bold)
                            Box(Modifier.weight(1f).height(1.dp).background(NHGreen.copy(alpha = 0.4f)))
                        }
                        is Row2.Msg -> {
                            val m = row.m
                            val decision = MessageDisplay.decide(m, vm.me, viewerLang, settings.autoTranslate, settings.showOriginalByDefault, m.id in toggled)
                            val quoted = m.replyToId?.let { id -> messages.firstOrNull { it.id == id } }
                            MessageBubble(
                                m = m, mine = m.senderId == vm.me, decision = decision, viewerLang = viewerLang, quoted = quoted,
                                quotedText = quoted?.let { q -> if (q.deleted) "Deleted message" else if (q.isImage) "📷 " + q.originalText.ifBlank { "Photo" } else if (q.isFile) "📎 " + (q.fileName ?: "File") else q.originalText },
                                onLongPress = { optionsFor = m },
                                onOpenImage = { viewerImage = it },
                                transfer = transfers[m.id],
                                onDownload = { if (isRisky(m.fileName)) riskyFor = m else vm.download(m) },
                                onOpenFile = { uri ->
                                    runCatching {
                                        ctx.startActivity(
                                            android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, m.fileMime ?: "*/*")
                                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                                onToggle = { vm.toggleOriginal(m.id) },
                                onRetryTranslation = { vm.requestTranslation(m) },
                                onRetrySend = { vm.retrySend(m) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ----- message options sheet -----
    optionsFor?.let { m ->
        val decision = MessageDisplay.decide(m, vm.me, viewerLang, settings.autoTranslate, settings.showOriginalByDefault, m.id in toggled)
        val sheet = rememberModalBottomSheetState()
        val mine = m.senderId == vm.me
        ModalBottomSheet(onDismissRequest = { optionsFor = null }, sheetState = sheet) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                if (!m.deleted) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        QuickReactions.forEach { e ->
                            Text(e, Modifier.clip(CircleShape).clickable { vm.react(m, e); optionsFor = null }.padding(6.dp), fontSize = 26.sp)
                        }
                    }
                    if (decision.canToggle) {
                        Text("Translation Options", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Show translation", Modifier.weight(1f))
                            Switch(decision.showingTranslation, { vm.toggleOriginal(m.id) })
                        }
                        TextBlock("Original (${Languages.nameOf(m.detectedLanguage ?: m.sourceLanguage)})", m.originalText)
                        TextBlock("Translation (${Languages.nameOf(m.targetLanguage)})", m.translatedText.orEmpty())
                        Spacer(Modifier.height(6.dp))
                    }
                    SheetAction(Icons.Filled.ContentCopy, "Copy") {
                        clipboard.setText(AnnotatedString(decision.text)); optionsFor = null
                    }
                    SheetAction(Icons.AutoMirrored.Filled.Reply, "Reply") { vm.replyTo.value = m; vm.editing.value = null; optionsFor = null }
                    if (!m.isImage && !m.isFile) SheetAction(Icons.AutoMirrored.Filled.ArrowForward, "Forward") { forwardText = m.originalText; optionsFor = null }
                    if (mine) {
                        if (!m.isImage && !m.isFile) SheetAction(Icons.Filled.Edit, "Edit") { vm.editing.value = m; vm.replyTo.value = null; draft = m.originalText; optionsFor = null }
                        SheetAction(Icons.Filled.Delete, "Delete", danger = true) { deleteFor = m; optionsFor = null }
                    }
                } else Text("This message was deleted.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    forwardText?.let { text -> ForwardDialog(vm.me, text, onPick = { vm.forward(it, text); forwardText = null }, onDismiss = { forwardText = null }) }

    deleteFor?.let { m ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete message?") },
            text = { Text("It will be removed for everyone in this chat.") },
            confirmButton = { TextButton({ vm.delete(m); deleteFor = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ deleteFor = null }) { Text("Cancel") } }
        )
    }
    riskyFor?.let { m ->
        AlertDialog(
            onDismissRequest = { riskyFor = null },
            title = { Text("Download this file?") },
            text = { Text("\"${m.fileName}\" can run code on your phone (an installable or executable file). Only download it if you know and trust the sender.") },
            confirmButton = { TextButton({ vm.download(m); riskyFor = null }) { Text("Download anyway", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ riskyFor = null }) { Text("Cancel") } }
        )
    }
    if (preparing) androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                Text("  Compressing photo…")
            }
        }
    }
    pending?.let { img ->
        var caption by remember(img) { mutableStateOf("") }
        androidx.compose.ui.window.Dialog(onDismissRequest = { vm.cancelImage() }) {
            Column(Modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Image(
                    bitmap = img.preview, contentDescription = "Photo preview", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).clip(RoundedCornerShape(16.dp))
                )
                Text(
                    "Compressed to ${img.image.bytes.size / 1024} KB · ${img.image.width}×${img.image.height}",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.OutlinedTextField(
                    value = caption, onValueChange = { caption = it.take(1000) }, placeholder = { Text("Add a caption (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(16.dp), maxLines = 3
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.cancelImage() }) { Text("Cancel") }
                    TextButton({ vm.sendImage(caption) }) { Text("Send", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    viewerImage?.let { bmp ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewerImage = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewerImage = null }, contentAlignment = Alignment.Center) {
                Image(bitmap = bmp, contentDescription = "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                IconButton({ viewerImage = null }, Modifier.align(Alignment.TopEnd).statusBarsPadding()) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            }
        }
    }
}

private val RiskyExt = setOf("apk", "apks", "xapk", "exe", "msi", "bat", "cmd", "sh", "js", "jar", "scr", "vbs", "ps1")
private fun isRisky(name: String?): Boolean = name?.substringAfterLast('.', "")?.lowercase() in RiskyExt

@Composable
private fun FileCard(
    m: ChatMessage, mine: Boolean, t: TransferState?, fg: Color, sub: Color,
    onDownload: () -> Unit, onOpen: (android.net.Uri) -> Unit
) {
    Column(Modifier.width(250.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = if (mine) 0.18f else 0.07f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.InsertDriveFile, null, tint = if (mine) Color.White else NHGreen)
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(m.fileName ?: "File", color = fg, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Format.size(m.fileSize), color = sub, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        val progress = if (t != null && t.total > 0) (t.done.toFloat() / t.total).coerceIn(0f, 1f) else 0f
        when {
            t == null && mine -> Text("Sent as a link. Keep NH Connect open so they can download it.", color = sub, style = MaterialTheme.typography.bodySmall)
            t == null -> androidx.compose.material3.Button(onDownload, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); Text("  Download")
            }
            t.phase == Phase.WAITING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Text("  Contacting sender…", color = sub, style = MaterialTheme.typography.bodySmall)
            }
            t.phase == Phase.CONNECTING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Text("  Connecting directly…", color = sub, style = MaterialTheme.typography.bodySmall)
            }
            t.phase == Phase.TRANSFERRING -> Column {
                androidx.compose.material3.LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                Text(
                    (if (t.outgoing) "Sending " else "Downloading ") + "${(progress * 100).toInt()}% · ${Format.size(t.done)} / ${Format.size(t.total)}",
                    Modifier.padding(top = 4.dp), color = sub, style = MaterialTheme.typography.bodySmall
                )
            }
            t.phase == Phase.DONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (t.outgoing) "Sent ✓" else if (t.savedUri != null) "Saved to Downloads/NH Connect ✓" else "Saved ✓",
                    Modifier.weight(1f), color = sub, style = MaterialTheme.typography.bodySmall
                )
                if (t.savedUri != null) TextButton({ onOpen(t.savedUri) }) { Text("Open") }
            }
            else -> Column {
                Text(t.note ?: "Transfer failed", color = if (mine) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (!mine) TextButton(onDownload) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun ChatImage(m: ChatMessage, onOpen: (ImageBitmap) -> Unit) {
    val c = LocalContainer.current
    val key = "m_${m.id}"
    val full by produceState<ImageBitmap?>(initialValue = ImageCache.get(key), m.id) {
        if (value == null) c.mediaRepository.chatImage(m.chatId, m.id).collect { b -> if (b != null) value = ImageCache.decode(key, b) }
    }
    val thumb = remember(m.mediaThumb) {
        m.mediaThumb?.let { t -> runCatching { BitmapFactory.decodeByteArray(B64.dec(t), 0, B64.dec(t).size)?.asImageBitmap() }.getOrNull() }
    }
    val ratio = if (m.mediaW > 0 && m.mediaH > 0) (m.mediaW.toFloat() / m.mediaH).coerceIn(0.6f, 1.8f) else 1f
    val shown = full ?: thumb
    Box(
        Modifier.width(240.dp).aspectRatio(ratio).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.08f))
            .clickable(enabled = full != null) { full?.let(onOpen) },
        contentAlignment = Alignment.Center
    ) {
        if (shown != null) Image(bitmap = shown, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (full == null) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Color.White)
    }
}

@Composable
private fun TextBlock(label: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = NHGreen)
        Text(text)
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(label, Modifier.padding(start = 16.dp), color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    m: ChatMessage, mine: Boolean, decision: DisplayDecision, viewerLang: String,
    quoted: ChatMessage?, quotedText: String?,
    onLongPress: () -> Unit, onOpenImage: (ImageBitmap) -> Unit, transfer: TransferState?, onDownload: () -> Unit, onOpenFile: (android.net.Uri) -> Unit, onToggle: () -> Unit, onRetryTranslation: () -> Unit, onRetrySend: () -> Unit
) {
    val fg = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
    val sub = if (mine) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (mine) NHMint else NHGreen
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = if (mine) 22.dp else 6.dp, bottomEnd = if (mine) 6.dp else 22.dp)
    val base = Modifier.widthIn(max = 310.dp)
        .shadow(3.dp, shape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x33000000)).clip(shape)
    val bg = if (mine) base.background(BubbleGradient) else base.background(MaterialTheme.colorScheme.surface)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(bg.combinedClickable(onClick = {}, onLongClick = onLongPress).padding(horizontal = 14.dp, vertical = 9.dp)) {
            if (quotedText != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = if (mine) 0.16f else 0.06f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(4.dp).height(34.dp).background(accent))
                    Text(quotedText, Modifier.padding(8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = fg)
                }
            }
            if (decision.deleted) {
                Text("🚫 This message was deleted", fontStyle = FontStyle.Italic, color = sub)
            } else {
                if (m.isImage) ChatImage(m, onOpenImage)
                if (m.isFile) FileCard(m, mine, transfer, fg, sub, onDownload, onOpenFile)
                if (decision.text.isNotBlank()) Text(decision.text, Modifier.padding(top = if (m.isImage) 6.dp else 0.dp), style = MaterialTheme.typography.bodyLarge, color = fg)
                // translation state (never hides the original)
                when {
                    decision.translating -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp, color = accent)
                        Text("  Translating…", style = MaterialTheme.typography.bodySmall, color = sub)
                    }
                    decision.failed -> Text(
                        "Translation unavailable — tap to retry.",
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)).clickable(onClick = onRetryTranslation).padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                    )
                    decision.needsRetranslate -> Text(
                        "Translate to ${Languages.nameOf(viewerLang)}",
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(NHGreen.copy(alpha = 0.12f)).clickable(onClick = onRetryTranslation).padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = NHGreen, fontWeight = FontWeight.SemiBold
                    )
                    decision.canToggle -> Row(
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(NHGreen.copy(alpha = 0.12f)).clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Translate, null, Modifier.size(12.dp), tint = NHGreen)
                        Text(
                            if (decision.showingTranslation) "  Translated from ${Languages.nameOf(m.detectedLanguage ?: m.sourceLanguage)} · View original"
                            else "  Original · View translation",
                            style = MaterialTheme.typography.bodySmall, color = NHGreen, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (m.reactions.isNotEmpty()) {
                Text(
                    m.reactions.values.joinToString(" "),
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = if (mine) 0.18f else 0.06f)).padding(horizontal = 6.dp, vertical = 1.dp),
                    fontSize = 14.sp
                )
            }
            Row(Modifier.align(Alignment.End).padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (m.editedAt != null && !m.deleted) Text("edited  ", style = MaterialTheme.typography.bodySmall, color = sub)
                Text(Format.time(m.createdAt), style = MaterialTheme.typography.bodySmall, color = sub)
                if (mine && !m.deleted) {
                    val mod = Modifier.padding(start = 4.dp).size(16.dp)
                    when {
                        m.sendState == SendState.FAILED -> Icon(Icons.Filled.ErrorOutline, "Failed. Tap to retry", mod.clickable(onClick = onRetrySend), tint = Color(0xFFFFB4AB))
                        m.sendState == SendState.SENDING -> Icon(Icons.Filled.Schedule, "Sending", mod, tint = sub)
                        m.readAt != null -> Icon(Icons.Filled.DoneAll, "Read", mod, tint = NHBlueTick)
                        m.deliveredAt != null -> Icon(Icons.Filled.DoneAll, "Delivered", mod, tint = sub)
                        else -> Icon(Icons.Filled.Done, "Sent", mod, tint = sub)
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String, onDraft: (String) -> Unit, replyTo: ChatMessage?, editing: ChatMessage?,
    emojiOpen: Boolean, onToggleEmoji: () -> Unit, onCancelContext: () -> Unit, onAttach: () -> Unit, onAttachFile: () -> Unit, onSend: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        val ctx = editing ?: replyTo
        if (ctx != null) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp).shadow(3.dp, RoundedCornerShape(16.dp), clip = false).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).height(44.dp).background(NHGreen))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(if (editing != null) "Editing message" else "Replying", style = MaterialTheme.typography.bodySmall, color = NHGreen, fontWeight = FontWeight.Bold)
                    Text(ctx.originalText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onCancelContext, Modifier.size(34.dp)) { Icon(Icons.Filled.Close, "Cancel") }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Row(
                Modifier.weight(1f).shadow(6.dp, RoundedCornerShape(28.dp), clip = false, ambientColor = Color(0x44000000), spotColor = Color(0x44000000))
                    .clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onToggleEmoji) { Icon(if (emojiOpen) Icons.Filled.Keyboard else Icons.Filled.EmojiEmotions, "Emoji", tint = NHGreen) }
                TextField(
                    value = draft, onValueChange = onDraft, modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message…") }, maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    )
                )
                IconButton(onAttachFile) { Icon(Icons.Filled.AttachFile, "Share a file", tint = NHGreen) }
                IconButton(onAttach) { Icon(Icons.Filled.Image, "Send photo", tint = NHGreen) }
            }
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier.size(54.dp).shadow(10.dp, CircleShape, clip = false, ambientColor = NHGreen, spotColor = NHGreen)
                    .clip(CircleShape).background(GreenGradient).clickable(onClick = onSend),
                contentAlignment = Alignment.Center
            ) { Icon(if (editing != null) Icons.Filled.Done else Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White) }
        }
    }
}

@Composable
private fun ForwardDialog(me: String, text: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val chats by remember { c.chatListRepository.observe(me) }.collectAsStateWithLifecycle(initialValue = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward to…") },
        text = {
            if (chats.isEmpty()) Text("You have no other chats yet.")
            else LazyColumn(Modifier.height(320.dp)) {
                items(chats, key = { it.chatId }) { s ->
                    val p by remember(s.peerId) { c.userRepository.profile(s.peerId) }.collectAsStateWithLifecycle(initialValue = null as com.nhdeveloper.nhconnect.data.UserProfile?)
                    Row(Modifier.fillMaxWidth().clickable { onPick(s.peerId) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p, 40.dp)
                        Text(p?.displayName ?: "…", Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
