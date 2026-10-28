package com.nhdeveloper.nhconnect.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.nhdeveloper.nhconnect.ui.auroraHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhdeveloper.nhconnect.R
import com.nhdeveloper.nhconnect.data.Countries
import com.nhdeveloper.nhconnect.data.Languages
import com.nhdeveloper.nhconnect.data.repo.AuthState
import com.nhdeveloper.nhconnect.data.repo.ErrorText
import com.nhdeveloper.nhconnect.domain.Validators
import com.nhdeveloper.nhconnect.ui.CountryPickerDialog
import com.nhdeveloper.nhconnect.ui.ErrorText as ErrorLine
import com.nhdeveloper.nhconnect.ui.GreenGradient
import com.nhdeveloper.nhconnect.ui.LanguagePickerDialog
import com.nhdeveloper.nhconnect.ui.LocalContainer
import com.nhdeveloper.nhconnect.ui.NHTextField
import com.nhdeveloper.nhconnect.ui.PickerField
import com.nhdeveloper.nhconnect.ui.PrimaryButton
import com.nhdeveloper.nhconnect.ui.theme.NHGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
private fun AuthFrame(title: String, subtitle: String, onBack: (() -> Unit)?, logo: Boolean = false, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.fillMaxWidth().height(if (logo) 290.dp else 220.dp).auroraHeader(0.dp)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.Bottom) {
                if (onBack != null) {
                    IconButton(onClick = onBack, Modifier.padding(start = 0.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                }
                if (logo) {
                    Box(Modifier.size(92.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_logo), null, Modifier.size(74.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(38.dp))
            }
        }
        Column(
            Modifier.offset(y = (-28).dp).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 26.dp)
        ) { content() }
    }
}

@Composable
fun SignInScreen(onSignUp: () -> Unit, onForgot: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AuthFrame("Welcome back", "Sign in to continue chatting", null, logo = true) {
        NHTextField(email, { email = it; error = null }, "Email", leading = Icons.Filled.Email, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(12.dp))
        NHTextField(
            password, { password = it; error = null }, "Password", leading = Icons.Filled.Lock, password = !show,
            trailing = { IconButton({ show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password") } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { TextButton(onForgot) { Text("Forgot password?") } }
        ErrorLine(error)
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Login", {
            if (!Validators.email(email) || password.isEmpty()) { error = "Enter your email and password."; return@PrimaryButton }
            loading = true
            scope.launch {
                runCatching { c.authRepository.login(email, password) }.onFailure { error = ErrorText.friendly(it) }
                loading = false
            }
        }, loading = loading)
        Spacer(Modifier.height(16.dp))
        TextButton(onSignUp, Modifier.fillMaxWidth()) { Text("Don't have an account? Sign Up", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun SignUpScreen(onBack: () -> Unit, onSignIn: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var country by remember { mutableStateOf("IN") }
    var language by remember { mutableStateOf("en") }
    var pickCountry by remember { mutableStateOf(false) }
    var pickLanguage by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val s = c.settingsRepository.settings.first()
        language = s.draftLanguage
        country = s.draftCountry
    }

    AuthFrame("Create Account", "Join NH Connect and start chatting", onBack) {
        NHTextField(name, { name = it }, "Full name", leading = Icons.Filled.Person, error = if (submitted && !Validators.displayName(name)) "Enter your name" else null)
        Spacer(Modifier.height(12.dp))
        NHTextField(
            username, { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(20) }, "Username",
            leading = Icons.Filled.AlternateEmail,
            error = if (submitted && !Validators.username(username)) "3-20 letters, numbers or _" else null
        )
        Spacer(Modifier.height(12.dp))
        NHTextField(email, { email = it }, "Email", leading = Icons.Filled.Email, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            error = if (submitted && !Validators.email(email)) "Enter a valid email" else null)
        Spacer(Modifier.height(12.dp))
        NHTextField(
            password, { password = it }, "Password (min 8 characters)", leading = Icons.Filled.Lock, password = !show,
            trailing = { IconButton({ show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password") } },
            error = if (submitted && !Validators.password(password)) "At least 8 characters" else null
        )
        Spacer(Modifier.height(12.dp))
        val co = Countries.byCode(country)
        PickerField("Country", "${co?.flag ?: ""} ${co?.name ?: country}", { pickCountry = true })
        Spacer(Modifier.height(12.dp))
        val lg = Languages.byCode(language)
        PickerField("Preferred language", "${lg?.flag ?: ""} ${lg?.englishName ?: language}", { pickLanguage = true })
        Spacer(Modifier.height(8.dp))
        ErrorLine(error)
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Sign Up", {
            submitted = true
            if (!Validators.displayName(name) || !Validators.username(username) || !Validators.email(email) || !Validators.password(password)) return@PrimaryButton
            loading = true; error = null
            scope.launch {
                runCatching { c.authRepository.signUp(email, password, name, username, country, language) }
                    .onFailure { error = ErrorText.friendly(it) }
                loading = false
            }
        }, loading = loading)
        TextButton(onSignIn, Modifier.fillMaxWidth()) { Text("Already have an account? Login") }
    }
    if (pickCountry) CountryPickerDialog(country, { country = it }, { pickCountry = false })
    if (pickLanguage) LanguagePickerDialog(language, { language = it.code }, { pickLanguage = false })
}

@Composable
fun VerifyEmailScreen(state: AuthState.NeedsVerification) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldown) { if (cooldown > 0) { delay(1000); cooldown -= 1 } }

    AuthFrame("Verify your email", "We sent a verification link to ${state.email}. Open it, then come back here.", null) {
        PrimaryButton("I've verified my email", {
            checking = true; message = null
            scope.launch {
                runCatching { c.authRepository.checkVerified() }
                    .onSuccess { if (!it) message = "Not verified yet. Check your inbox (and spam folder)." }
                    .onFailure { message = ErrorText.friendly(it) }
                checking = false
            }
        }, loading = checking)
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                cooldown = 30
                scope.launch {
                    runCatching { c.authRepository.resendVerification() }
                        .onSuccess { message = "Verification email sent." }
                        .onFailure { message = ErrorText.friendly(it) }
                }
            },
            enabled = cooldown == 0, modifier = Modifier.fillMaxWidth()
        ) { Text(if (cooldown == 0) "Resend email" else "Resend in ${cooldown}s") }
        if (message != null) Text(message!!, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        TextButton({ scope.launch { c.authRepository.logout() } }, Modifier.fillMaxWidth()) { Text("Use a different account") }
    }
}

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    AuthFrame("Forgot password?", "Enter your email and we'll send you a reset link.", onBack) {
        NHTextField(email, { email = it }, "Email", leading = Icons.Filled.Email, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Send reset link", {
            if (!Validators.email(email)) { message = "Enter a valid email."; isError = true; return@PrimaryButton }
            loading = true
            scope.launch {
                runCatching { c.authRepository.sendPasswordReset(email) }
                    .onSuccess { message = "If an account exists for that email, a reset link is on its way."; isError = false }
                    .onFailure { message = ErrorText.friendly(it); isError = true }
                loading = false
            }
        }, loading = loading)
        Spacer(Modifier.height(12.dp))
        if (message != null) Text(message!!, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
