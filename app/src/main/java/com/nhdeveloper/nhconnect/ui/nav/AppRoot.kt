package com.nhdeveloper.nhconnect.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nhdeveloper.nhconnect.ui.GreenGradient
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nhdeveloper.nhconnect.AppContainer
import com.nhdeveloper.nhconnect.data.AppSettings
import com.nhdeveloper.nhconnect.data.repo.AuthState
import com.nhdeveloper.nhconnect.ui.LocalContainer
import com.nhdeveloper.nhconnect.ui.screens.ChatScreen
import com.nhdeveloper.nhconnect.ui.screens.ForgotPasswordScreen
import com.nhdeveloper.nhconnect.ui.screens.HomeTab
import com.nhdeveloper.nhconnect.ui.screens.OnboardingScreen
import com.nhdeveloper.nhconnect.ui.screens.PeopleTab
import com.nhdeveloper.nhconnect.ui.screens.ProfileScreen
import com.nhdeveloper.nhconnect.ui.screens.SettingsTab
import com.nhdeveloper.nhconnect.ui.screens.SignInScreen
import com.nhdeveloper.nhconnect.ui.screens.SignUpScreen
import com.nhdeveloper.nhconnect.ui.screens.SplashScreen
import com.nhdeveloper.nhconnect.ui.screens.VerifyEmailScreen
import com.nhdeveloper.nhconnect.ui.theme.NHConnectTheme
import kotlinx.coroutines.flow.MutableStateFlow

private val AuthRoutes = setOf("onboarding", "signin", "signup", "forgot")

@Composable
fun AppRoot(container: AppContainer, deepLink: MutableStateFlow<String?>) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    NHConnectTheme(settings.themeMode) {
        CompositionLocalProvider(LocalContainer provides container) { Routes(container, settings, deepLink) }
    }
}

@Composable
private fun Routes(container: AppContainer, settings: AppSettings, deepLink: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val auth by container.authRepository.state.collectAsStateWithLifecycle()
    val route by nav.currentBackStackEntryAsState()
    val current = route?.destination?.route
    val link by deepLink.collectAsStateWithLifecycle()

    fun reset(to: String) = nav.navigate(to) { popUpTo(0) { inclusive = true }; launchSingleTop = true }

    // Keep the navigation graph consistent with the sign-in state.
    LaunchedEffect(auth, current) {
        if (current == null || current == "splash") return@LaunchedEffect
        when (val a = auth) {
            is AuthState.Ready -> if (current in AuthRoutes || current == "verify") reset("main")
            is AuthState.SignedOut -> if (current !in AuthRoutes) reset(if (settings.onboardingDone) "signin" else "onboarding")
            is AuthState.NeedsVerification -> if (current != "verify") reset("verify")
            AuthState.Loading -> Unit
        }
    }
    // Notification tap -> open that conversation.
    LaunchedEffect(link, auth) {
        val peer = link
        if (peer != null && auth is AuthState.Ready && current != null && current != "splash") {
            nav.navigate("chat/$peer") { launchSingleTop = true }
            deepLink.value = null
        }
    }

    NavHost(nav, startDestination = "splash") {
        composable("splash") {
            SplashScreen(authReady = auth !is AuthState.Loading) {
                when (auth) {
                    is AuthState.Ready -> reset("main")
                    is AuthState.NeedsVerification -> reset("verify")
                    else -> reset(if (settings.onboardingDone) "signin" else "onboarding")
                }
            }
        }
        composable("onboarding") { OnboardingScreen { route -> nav.navigate(route) { popUpTo("onboarding") { inclusive = true } } } }
        composable("signin") { SignInScreen(onSignUp = { nav.navigate("signup") }, onForgot = { nav.navigate("forgot") }) }
        composable("signup") { SignUpScreen(onBack = { nav.popBackStack() }, onSignIn = { nav.popBackStack() }) }
        composable("forgot") { ForgotPasswordScreen { nav.popBackStack() } }
        composable("verify") { (auth as? AuthState.NeedsVerification)?.let { VerifyEmailScreen(it) } }
        composable("main") { (auth as? AuthState.Ready)?.let { MainScreen(it.uid, nav) } }
        composable("chat/{uid}") { e ->
            val uid = e.arguments?.getString("uid") ?: return@composable
            ChatScreen(uid, onBack = { nav.popBackStack() }, onOpenProfile = { nav.navigate("profile/$it") })
        }
        composable("profile/{uid}") { e ->
            val uid = e.arguments?.getString("uid") ?: return@composable
            ProfileScreen(uid, onBack = { nav.popBackStack() }, onMessage = { nav.navigate("chat/$it") { popUpTo("main") } })
        }
    }
}

@Composable
private fun MainScreen(myUid: String, nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(Icons.Filled.ChatBubble to "Chats", Icons.Filled.People to "People", Icons.Filled.Settings to "Settings")
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Floating pill navigation
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(horizontal = 26.dp, vertical = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(34.dp), clip = false, ambientColor = Color(0x55000000), spotColor = Color(0x55000000))
                        .clip(RoundedCornerShape(34.dp)).background(MaterialTheme.colorScheme.surface).padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { i, (icon, label) ->
                        val sel = tab == i
                        Row(
                            Modifier.clip(RoundedCornerShape(28.dp))
                                .background(if (sel) GreenGradient else SolidColor(Color.Transparent))
                                .clickable { tab = i }
                                .padding(horizontal = if (sel) 20.dp else 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, label, tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            AnimatedVisibility(sel) { Text("  $label", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(bottom = pad.calculateBottomPadding())) {
            when (tab) {
                0 -> HomeTab(myUid, { nav.navigate("chat/$it") }, { nav.navigate("profile/$myUid") }, { tab = 1 })
                1 -> PeopleTab(myUid) { nav.navigate("chat/$it") }
                else -> SettingsTab(myUid, { nav.navigate("profile/$myUid") }, { nav.navigate("profile/$it") })
            }
        }
    }
}
