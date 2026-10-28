package com.nhdeveloper.nhconnect.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.nhdeveloper.nhconnect.data.ThemeMode

val NHGreen = Color(0xFF0A8F4B)
val NHGreenDark = Color(0xFF053D2B)
val NHGreenLight = Color(0xFF2BD17E)
val NHMint = Color(0xFFB8F5D4)
val NHGold = Color(0xFFF6C453)
val NHBlueTick = Color(0xFF3FC1F5)

private val Light = lightColorScheme(
    primary = NHGreen, onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF6E7), onPrimaryContainer = Color(0xFF05301D),
    secondary = NHGreenLight, onSecondary = Color(0xFF00391B),
    background = Color(0xFFF2F7F4), onBackground = Color(0xFF111B16),
    surface = Color.White, onSurface = Color(0xFF111B16),
    surfaceVariant = Color(0xFFE6EFE9), onSurfaceVariant = Color(0xFF55665C),
    outline = Color(0xFFC3D2C8), error = Color(0xFFD13438)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF3FDC93), onPrimary = Color(0xFF00361C),
    primaryContainer = Color(0xFF0C4A2C), onPrimaryContainer = Color(0xFFD2F7DF),
    secondary = Color(0xFF2BD17E), onSecondary = Color(0xFF00361C),
    background = Color(0xFF07100C), onBackground = Color(0xFFE4EFE8),
    surface = Color(0xFF111D17), onSurface = Color(0xFFE4EFE8),
    surfaceVariant = Color(0xFF1A2A22), onSurfaceVariant = Color(0xFFA7BBAF),
    outline = Color(0xFF34473C), error = Color(0xFFFF8A80)
)

private val NHTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 33.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp)
)

private val NHShapes = Shapes(
    small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(28.dp)
)

@Composable
fun NHConnectTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Every screen starts with a green header, so status-bar icons stay light.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = NHTypography, shapes = NHShapes, content = content)
}
