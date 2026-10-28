package com.nhdeveloper.nhconnect.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhdeveloper.nhconnect.R
import com.nhdeveloper.nhconnect.data.Countries
import com.nhdeveloper.nhconnect.data.Languages
import com.nhdeveloper.nhconnect.ui.LocalContainer
import com.nhdeveloper.nhconnect.ui.NHTextField
import com.nhdeveloper.nhconnect.ui.PrimaryButton
import com.nhdeveloper.nhconnect.ui.aurora
import com.nhdeveloper.nhconnect.ui.auroraHeader
import com.nhdeveloper.nhconnect.ui.theme.NHGreen
import com.nhdeveloper.nhconnect.ui.theme.NHGreenLight
import com.nhdeveloper.nhconnect.ui.theme.NHMint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(authReady: Boolean, onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.7f) }
    var minTimePassed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(800)) }
        launch { scale.animateTo(1f, tween(1000)) }
        delay(1800)
        minTimePassed = true
    }
    LaunchedEffect(minTimePassed, authReady) { if (minTimePassed && authReady) onDone() }

    val t = rememberInfiniteTransition(label = "rings")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "ring")
    val dots by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "dots")

    Box(Modifier.fillMaxSize().aurora()) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 0.40f)
            for (i in 0..2) {
                val p = (ring + i / 3f) % 1f
                drawCircle(Color.White.copy(alpha = (1f - p) * 0.22f), radius = 70.dp.toPx() + p * 150.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
            }
        }
        Column(Modifier.fillMaxSize().alpha(alpha.value), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.9f))
            Box(
                Modifier.scale(scale.value).size(150.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Image(painterResource(R.drawable.ic_logo), null, Modifier.size(120.dp)) }
            Spacer(Modifier.height(26.dp))
            Text("NH Connect", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(4.dp))
            Text("Connect Beyond Borders", color = NHMint, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            Text("CHAT  •  TRANSLATE  •  TOGETHER", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    val on = dots.toInt() == i
                    Box(Modifier.width(if (on) 26.dp else 8.dp).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = if (on) 1f else 0.4f)))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("by NH Developer", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            Spacer(Modifier.height(36.dp))
        }
    }
}

private enum class Step { WELCOME, LANGUAGE, COUNTRY }

/** Welcome + feature explanation -> language -> country. Choices pre-fill the sign-up form. */
@Composable
fun OnboardingScreen(onFinish: (route: String) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.WELCOME) }
    var lang by remember { mutableStateOf("en") }
    var country by remember { mutableStateOf("IN") }
    var query by remember { mutableStateOf("") }

    fun finish(route: String) {
        scope.launch {
            c.settingsRepository.setDraft(lang, country)
            c.settingsRepository.setOnboardingDone()
            onFinish(route)
        }
    }

    AnimatedContent(
        targetState = step, label = "onboarding",
        transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut()) }
    ) { s ->
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (s) {
                Step.WELCOME -> {
                    Box(Modifier.fillMaxWidth().height(330.dp).auroraHeader(40.dp)) {
                        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                DemoBubble("Hello, how are you?", "English", fromMe = false)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                DemoBubble("হ্যালো, তুমি কেমন আছো?", "বাংলা", fromMe = true)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                DemoBubble("मैं ठीक हूँ, धन्यवाद!", "हिन्दी", fromMe = false)
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 26.dp).navigationBarsPadding()) {
                        Spacer(Modifier.height(22.dp))
                        Text("Chat in your language.", style = MaterialTheme.typography.headlineMedium)
                        Text("Everyone reads in their own.", style = MaterialTheme.typography.headlineMedium, color = NHGreen)
                        Spacer(Modifier.height(18.dp))
                        Feature(Icons.Filled.Translate, "Real-time translation", "The original is always one tap away.")
                        Feature(Icons.Filled.Lock, "Secure & private", "Firebase Authentication and strict rules.")
                        Feature(Icons.Filled.Public, "Global friends", "Find people by username, talk across borders.")
                        Spacer(Modifier.weight(1f))
                        PrimaryButton("Get Started", { step = Step.LANGUAGE })
                        TextButton(onClick = { finish("signin") }, modifier = Modifier.fillMaxWidth()) { Text("Already have an account? Login", fontWeight = FontWeight.SemiBold) }
                    }
                }
                Step.LANGUAGE -> Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
                    Spacer(Modifier.height(20.dp))
                    StepDots(1)
                    Spacer(Modifier.height(18.dp))
                    Text("Select Your Language", style = MaterialTheme.typography.headlineMedium)
                    Text("Messages are translated into this language for you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(Languages.all) { l ->
                            ChoiceCard(l.flag, l.nativeName, l.englishName, l.code == lang) { lang = l.code }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Next", { step = Step.COUNTRY })
                    Spacer(Modifier.height(12.dp))
                }
                Step.COUNTRY -> Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
                    Spacer(Modifier.height(20.dp))
                    StepDots(2)
                    Spacer(Modifier.height(18.dp))
                    Text("Where are you from?", style = MaterialTheme.typography.headlineMedium)
                    Text("Shown on your profile. Translation depends on language, not country.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    NHTextField(query, { query = it }, "Search country", leading = Icons.Filled.Search)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(Countries.all.filter { it.name.contains(query, true) }) { co ->
                            ChoiceCard(co.flag, co.name, co.code, co.code == country) { country = co.code }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Create account", { finish("signup") })
                    TextButton(onClick = { finish("signin") }, modifier = Modifier.fillMaxWidth()) { Text("I already have an account") }
                }
            }
        }
    }
}

@Composable
private fun StepDots(active: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(Modifier.height(6.dp).width(if (i == active) 28.dp else 12.dp).clip(CircleShape).background(if (i <= active) NHGreen else MaterialTheme.colorScheme.outline))
        }
    }
}

@Composable
private fun ChoiceCard(flag: String, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth()
            .shadow(if (selected) 8.dp else 2.dp, shape, clip = false, ambientColor = NHGreen, spotColor = NHGreen)
            .clip(shape).background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .then(if (selected) Modifier.border(2.dp, NHGreen, shape) else Modifier)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) { Text(flag, fontSize = 26.sp) }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (selected) NHGreen else Color.Transparent)
                .then(if (selected) Modifier else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)),
            contentAlignment = Alignment.Center
        ) { if (selected) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = Color.White) }
    }
}

@Composable
private fun DemoBubble(text: String, lang: String, fromMe: Boolean) {
    Column(
        Modifier.shadow(8.dp, RoundedCornerShape(20.dp), clip = false).clip(RoundedCornerShape(20.dp))
            .background(if (fromMe) Color(0xFFD9F9C7) else Color.White).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, color = Color(0xFF16201A), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text("Translated · $lang", color = NHGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        com.nhdeveloper.nhconnect.ui.IconTile(icon, NHGreen, 44.dp)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
