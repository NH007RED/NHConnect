package com.nhdeveloper.nhconnect.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.nhconnect.data.ChatSummary
import com.nhdeveloper.nhconnect.data.UserProfile
import com.nhdeveloper.nhconnect.ui.Avatar
import com.nhdeveloper.nhconnect.ui.Chip
import com.nhdeveloper.nhconnect.ui.EmptyState
import com.nhdeveloper.nhconnect.ui.Format
import com.nhdeveloper.nhconnect.ui.GreenGradient
import com.nhdeveloper.nhconnect.ui.LocalContainer
import com.nhdeveloper.nhconnect.ui.SkeletonList
import com.nhdeveloper.nhconnect.ui.theme.NHGreen
import com.nhdeveloper.nhconnect.ui.theme.NHGold
import com.nhdeveloper.nhconnect.ui.auroraHeader
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private enum class Filter { ALL, UNREAD, FAVORITES }

@OptIn(ExperimentalFoundationApi::class, ExperimentalCoroutinesApi::class)
@Composable
fun HomeTab(myUid: String, onOpenChat: (String) -> Unit, onOpenMyProfile: () -> Unit, onNewChat: () -> Unit) {
    val c = LocalContainer.current

    // Android 13+ notification permission, requested once when the chat list first opens.
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    val chatsFlow = remember(myUid) { c.chatListRepository.observe(myUid) }
    val chats by chatsFlow.collectAsStateWithLifecycle(initialValue = null as List<ChatSummary>?)
    val peerProfiles by remember(chats) {
        val list = chats.orEmpty()
        if (list.isEmpty()) flowOf(emptyMap<String, UserProfile>())
        else combine(list.map { c.userRepository.profile(it.peerId) }) { arr -> arr.associateBy { it.uid } }
    }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val me by remember(myUid) { c.userRepository.profile(myUid) }.collectAsStateWithLifecycle(initialValue = null as UserProfile?)

    val pager = rememberUserPager(myUid)
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().auroraHeader(32.dp).statusBarsPadding().padding(start = 22.dp, end = 18.dp, top = 14.dp, bottom = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NH Connect", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Hi ${me?.displayName?.substringBefore(' ') ?: ""}, chat in your language",
                            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Avatar(me, 44.dp, modifier = Modifier.clip(CircleShape).clickable(onClick = onOpenMyProfile))
                }
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search users or chats...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(28.dp), clip = false).clip(RoundedCornerShape(28.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color(0xFF16201A), unfocusedTextColor = Color(0xFF16201A),
                        focusedPlaceholderColor = Color(0xFF6B7A70), unfocusedPlaceholderColor = Color(0xFF6B7A70),
                        focusedLeadingIconColor = NHGreen, unfocusedLeadingIconColor = Color(0xFF6B7A70)
                    )
                )
            }
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("All", filter == Filter.ALL) { filter = Filter.ALL }
                Chip("Unread", filter == Filter.UNREAD) { filter = Filter.UNREAD }
                Chip("Favorites", filter == Filter.FAVORITES) { filter = Filter.FAVORITES }
            }

            val all = chats
            when {
                all == null -> SkeletonList()
                else -> {
                    val shown = all.filter { s ->
                        val p = peerProfiles[s.peerId]
                        val matchesQuery = query.isBlank() ||
                            p?.displayName?.contains(query, true) == true ||
                            p?.username?.contains(query.removePrefix("@"), true) == true ||
                            s.lastMessageText.contains(query, true)
                        val matchesFilter = when (filter) {
                            Filter.ALL -> true
                            Filter.UNREAD -> s.unread > 0
                            Filter.FAVORITES -> s.favorite
                        }
                        matchesQuery && matchesFilter
                    }
                    val chatPeers = all.map { it.peerId }.toSet()
                    val others = if (filter == Filter.ALL) pager.users.filter {
                        it.uid !in chatPeers && (query.isBlank() || it.displayName.contains(query, true) || it.username.contains(query.removePrefix("@"), true))
                    } else emptyList()
                    if (shown.isEmpty() && others.isEmpty() && !pager.hasMore) {
                        EmptyState(
                            Icons.Filled.ChatBubble,
                            if (all.isEmpty()) "No conversations yet" else "Nothing here",
                            if (all.isEmpty()) "Tap the green button to find someone by username and say hello."
                            else "No chats match this filter."
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(shown, key = { "c_" + it.chatId }) { s -> ChatRow(myUid, s, peerProfiles[s.peerId], onOpenChat) }
                            if (filter == Filter.ALL && (others.isNotEmpty() || pager.hasMore)) {
                                item(key = "people_header") {
                                    Text(
                                        "PEOPLE ON NH CONNECT", Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp),
                                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                items(others, key = { "u_" + it.uid }) { u -> UserRow(u) { onOpenChat(u.uid) } }
                                if (pager.hasMore) item(key = "people_more") {
                                    LaunchedEffect(pager.users.size) { pager.loadMore() }
                                    Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 16.dp).size(62.dp)
                .shadow(14.dp, CircleShape, clip = false, ambientColor = NHGreen, spotColor = NHGreen)
                .clip(CircleShape).background(GreenGradient).clickable(onClick = onNewChat),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Edit, "New chat", tint = Color.White) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(myUid: String, s: ChatSummary, peer: UserProfile?, onOpen: (String) -> Unit) {
    val c = LocalContainer.current
    val presence by remember(s.peerId) { c.userRepository.presence(s.peerId) }
        .collectAsStateWithLifecycle(initialValue = com.nhdeveloper.nhconnect.data.PresenceInfo())
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(22.dp), clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
                .combinedClickable(onClick = { onOpen(s.peerId) }, onLongClick = { menu = true })
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(peer, 56.dp, online = presence.online && peer?.missing != true)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(peer?.displayName ?: "…", Modifier.weight(1f, fill = false), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.favorite) Icon(Icons.Filled.Star, null, Modifier.padding(start = 4.dp).size(14.dp), tint = NHGold)
                    if (s.muted) Icon(Icons.Filled.NotificationsOff, null, Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(Format.listTime(s.lastMessageAt), style = MaterialTheme.typography.bodySmall, color = if (s.unread > 0) NHGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (s.lastSenderId == myUid) "You: " else "") + s.lastMessageText.ifBlank { "Message deleted" },
                        Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
                    )
                    if (s.unread > 0) {
                        Box(Modifier.padding(start = 8.dp).size(24.dp).clip(CircleShape).background(GreenGradient), contentAlignment = Alignment.Center) {
                            Text(if (s.unread > 99) "99+" else s.unread.toString(), color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (s.favorite) "Remove from favorites" else "Add to favorites") },
                onClick = { c.chatListRepository.setFavorite(myUid, s.chatId, !s.favorite); menu = false }
            )
            DropdownMenuItem(
                text = { Text(if (s.muted) "Unmute notifications" else "Mute notifications") },
                onClick = { c.chatListRepository.setMuted(myUid, s.chatId, !s.muted); menu = false }
            )
        }
    }
}
