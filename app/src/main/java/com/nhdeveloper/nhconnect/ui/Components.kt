package com.nhdeveloper.nhconnect.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.nhdeveloper.nhconnect.media.ImageCache
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nhdeveloper.nhconnect.AppContainer
import com.nhdeveloper.nhconnect.data.Countries
import com.nhdeveloper.nhconnect.data.Language
import com.nhdeveloper.nhconnect.data.Languages
import com.nhdeveloper.nhconnect.data.UserProfile
import com.nhdeveloper.nhconnect.ui.theme.NHGreen
import com.nhdeveloper.nhconnect.ui.theme.NHGreenDark
import com.nhdeveloper.nhconnect.ui.theme.NHGreenLight

val LocalContainer = compositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Brand gradient used by headers, buttons and bubbles. */
val GreenGradient = Brush.linearGradient(listOf(Color(0xFF12A860), Color(0xFF067A44), Color(0xFF043E2B)))
val BubbleGradient = Brush.linearGradient(listOf(Color(0xFF14AE63), Color(0xFF08804A)))

/** Gradient + soft decorative orbs. Use on every screen header. */
fun Modifier.aurora(): Modifier = this.background(GreenGradient).drawBehind {
    val m = size.minDimension
    drawCircle(Color.White.copy(alpha = 0.09f), radius = m * 0.55f, center = Offset(size.width * 0.92f, size.height * 0.02f))
    drawCircle(Color.White.copy(alpha = 0.06f), radius = m * 0.42f, center = Offset(size.width * 0.02f, size.height * 0.98f))
    drawCircle(NHGreenLight.copy(alpha = 0.20f), radius = m * 0.26f, center = Offset(size.width * 0.62f, size.height * 0.92f))
}

/** Aurora header with rounded bottom corners. */
fun Modifier.auroraHeader(radius: Dp = 30.dp): Modifier =
    this.clip(RoundedCornerShape(bottomStart = radius, bottomEnd = radius)).aurora()

/** Subtle doodle pattern for the chat background. */
fun Modifier.chatWallpaper(tint: Color): Modifier = this.drawBehind {
    val step = 64.dp.toPx()
    var row = 0
    var y = 18.dp.toPx()
    while (y < size.height) {
        var x = if (row % 2 == 0) 14.dp.toPx() else 46.dp.toPx()
        while (x < size.width) {
            when ((row + (x / step).toInt()) % 3) {
                0 -> drawCircle(tint, radius = 3.dp.toPx(), center = Offset(x, y))
                1 -> {
                    drawLine(tint, Offset(x - 5.dp.toPx(), y), Offset(x + 5.dp.toPx(), y), strokeWidth = 1.6.dp.toPx())
                    drawLine(tint, Offset(x, y - 5.dp.toPx()), Offset(x, y + 5.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                }
                else -> drawCircle(tint, radius = 5.dp.toPx(), center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
            x += step
        }
        y += 40.dp.toPx(); row++
    }
}

@Composable
fun Avatar(profile: UserProfile?, size: Dp = 48.dp, online: Boolean = false, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: "?"
        val ring = if (size >= 36.dp) 2.dp else 0.dp
        Box(
            Modifier.matchParentSize()
                .then(if (ring > 0.dp) Modifier.border(ring, Brush.linearGradient(listOf(NHGreenLight, NHGreenDark)), CircleShape) else Modifier)
                .padding(ring + 2.dp).clip(CircleShape)
        ) {
            val c = LocalContainer.current
            val ver = profile?.photoVer ?: 0L
            val uid = profile?.uid.orEmpty()
            val key = "p_${uid}_$ver"
            val photo by produceState<ImageBitmap?>(initialValue = if (ver > 0) ImageCache.get(key) else null, uid, ver) {
                value = if (ver > 0 && uid.isNotEmpty()) ImageCache.get(key) else null
                if (ver > 0 && uid.isNotEmpty() && value == null) {
                    c.mediaRepository.profilePhoto(uid, ver).collect { bytes -> if (bytes != null) value = ImageCache.decode(key, bytes) }
                }
            }
            val bmp = photo
            if (bmp != null && profile?.missing != true) {
                Image(bitmap = bmp, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            } else if (profile?.photoUrl != null && profile.missing != true) {
                AsyncImage(model = profile.photoUrl, contentDescription = name, modifier = Modifier.matchParentSize())
            } else {
                Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(NHGreenLight, NHGreen, NHGreenDark))), contentAlignment = Alignment.Center) {
                    Text(name.first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
                }
            }
        }
        if (online) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.28f).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface).padding(2.5.dp).clip(CircleShape)
                    .background(Color(0xFF25D366))
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, enabled: Boolean = true) {
    val shape = RoundedCornerShape(28.dp)
    val active = enabled || loading
    Box(
        modifier.fillMaxWidth().height(56.dp)
            .shadow(if (active) 12.dp else 0.dp, shape, clip = false, ambientColor = NHGreen, spotColor = NHGreen)
            .clip(shape)
            .background(if (active) GreenGradient else Brush.linearGradient(listOf(Color(0xFFB9C5BE), Color(0xFFB9C5BE))))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NHGreen, unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedLabelColor = NHGreen, focusedLeadingIconColor = NHGreen, cursorColor = NHGreen
)

@Composable
fun NHTextField(
    value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, leading: ImageVector? = null, trailing: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true, password: Boolean = false, error: String? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine, shape = RoundedCornerShape(18.dp),
        colors = fieldColors(),
        leadingIcon = leading?.let { { Icon(it, null) } }, trailingIcon = trailing,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = keyboardOptions, isError = error != null,
        supportingText = error?.let { { Text(it) } }
    )
}

/** Read-only field that opens a picker dialog when tapped. */
@Composable
fun PickerField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), singleLine = true, colors = fieldColors()
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
fun LanguagePickerDialog(selected: String, onSelect: (Language) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Text("Select language", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                items(Languages.all) { l ->
                    val sel = l.code == selected
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onSelect(l); onDismiss() }.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(l.flag, fontSize = 24.sp)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(l.nativeName, fontWeight = FontWeight.SemiBold)
                            Text(l.englishName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (sel) Icon(Icons.Filled.Check, null, tint = NHGreen)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun CountryPickerDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Text("Select country", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                items(Countries.all) { c ->
                    val sel = c.code == selected
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onSelect(c.code); onDismiss() }.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(c.flag, fontSize = 24.sp)
                        Text(c.name, Modifier.padding(start = 12.dp).weight(1f))
                        if (sel) Icon(Icons.Filled.Check, null, tint = NHGreen)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(GreenGradient), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(36.dp), tint = Color.White)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun SkeletonList(rows: Int = 7) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) {
            Row(
                Modifier.alpha(a).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                Column(Modifier.padding(start = 14.dp)) {
                    Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                    Spacer(Modifier.height(9.dp))
                    Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(), Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

/** Rounded elevated card used to group rows. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .shadow(6.dp, RoundedCornerShape(24.dp), clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp),
        content = content
    )
}

@Composable
fun IconTile(icon: ImageVector, tint: Color = NHGreen, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = tint)
    }
}

@Composable
fun ErrorText(text: String?) {
    if (!text.isNullOrBlank()) Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) NHGreen else MaterialTheme.colorScheme.surface, label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, label = "chipFg")
    Box(
        Modifier.shadow(if (selected) 6.dp else 1.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = NHGreen, spotColor = NHGreen)
            .clip(RoundedCornerShape(20.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp)
    ) { Text(text, color = fg, fontWeight = FontWeight.SemiBold) }
}

val OutlineBorder: BorderStroke @Composable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
