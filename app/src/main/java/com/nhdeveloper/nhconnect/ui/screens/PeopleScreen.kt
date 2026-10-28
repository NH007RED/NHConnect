package com.nhdeveloper.nhconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.nhconnect.data.Countries
import com.nhdeveloper.nhconnect.data.Languages
import com.nhdeveloper.nhconnect.data.PresenceInfo
import com.nhdeveloper.nhconnect.data.UserProfile
import com.nhdeveloper.nhconnect.data.repo.UserRepository
import com.nhdeveloper.nhconnect.ui.Avatar
import com.nhdeveloper.nhconnect.ui.EmptyState
import com.nhdeveloper.nhconnect.ui.ErrorText
import com.nhdeveloper.nhconnect.ui.LocalContainer
import com.nhdeveloper.nhconnect.ui.NHTextField
import com.nhdeveloper.nhconnect.ui.auroraHeader
import kotlinx.coroutines.delay

/** Loads every registered user page by page (alphabetical by username). */
class UserPager(private val repo: UserRepository, private val me: String) {
    val users = mutableStateListOf<UserProfile>()
    var hasMore by mutableStateOf(true)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var lastLower: String? = null
    private var lastKey: String? = null
    private val seen = HashSet<String>()

    suspend fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        error = null
        try {
            val page = repo.listUsers(lastLower, lastKey)
            lastLower = page.lastLower
            lastKey = page.lastKey
            page.users.filter { it.uid != me && seen.add(it.uid) }.forEach { users.add(it) }
            hasMore = !page.exhausted
        } catch (e: Exception) {
            error = "Could not load users. Check your connection."
        } finally {
            loading = false
        }
    }
}

@Composable
fun rememberUserPager(myUid: String): UserPager {
    val c = LocalContainer.current
    val pager = remember(myUid) { UserPager(c.userRepository, myUid) }
    LaunchedEffect(pager) { pager.loadMore() }
    return pager
}

/** One registered user: avatar (with online dot), name, @username, country and language. */
@Composable
fun UserRow(u: UserProfile, onClick: () -> Unit) {
    val c = LocalContainer.current
    val presence by remember(u.uid) { c.userRepository.presence(u.uid) }.collectAsStateWithLifecycle(initialValue = PresenceInfo())
    Row(
        Modifier.fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(22.dp), clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(u, 52.dp, online = presence.online)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(u.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("@${u.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text("${Countries.byCode(u.country)?.flag ?: ""} ${Languages.byCode(u.language)?.englishName ?: u.language}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** All registered users, plus username search. */
@Composable
fun PeopleTab(myUid: String, onOpenChat: (String) -> Unit) {
    val c = LocalContainer.current
    val pager = rememberUserPager(myUid)
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val q = query.trim().removePrefix("@")

    LaunchedEffect(query) {
        error = null
        if (q.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350) // debounce typing; the previous search is cancelled automatically
        searching = true
        runCatching { c.userRepository.searchByUsername(query, myUid) }
            .onSuccess { results = it }
            .onFailure { error = "Search failed. Check your connection and try again." }
        searching = false
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxWidth().auroraHeader(32.dp).statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 24.dp)) {
            Text("People", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text("Everyone on NH Connect. Tap to start chatting.", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            NHTextField(query, { query = it }, "Search @username", leading = Icons.Filled.Search)
        }
        ErrorText(error)
        if (q.length >= 2) {
            when {
                searching -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                results.isEmpty() && error == null -> EmptyState(Icons.Filled.PersonSearch, "No users found", "Check the spelling of the username and try again.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(results, key = { it.uid }) { u -> UserRow(u) { onOpenChat(u.uid) } }
                }
            }
        } else {
            if (pager.users.isEmpty() && !pager.loading && !pager.hasMore) {
                EmptyState(Icons.Filled.PersonSearch, "No other users yet", "When more people register they will appear here.")
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pager.users, key = { it.uid }) { u -> UserRow(u) { onOpenChat(u.uid) } }
                if (pager.hasMore) item(key = "more") {
                    LaunchedEffect(pager.users.size) { pager.loadMore() }
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                }
                if (pager.error != null) item(key = "err") { ErrorText(pager.error) }
            }
        }
    }
}
