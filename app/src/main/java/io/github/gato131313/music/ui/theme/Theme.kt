package io.github.gato131313.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import io.github.gato131313.music.data.prefs.Settings

@Immutable
data class AppColors(
    val style: String,
    val isDark: Boolean,
    val bg: Color,
    val bg2: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val onAccent: Color,
    /** Relleno del cristal. */
    val glass: Color,
    /** Borde fino del cristal. */
    val glassEdge: Color,
    /** Brillo especular arriba-izquierda. */
    val glassSpec: Color,
    val divider: Color,
    /** El fondo es una ilustración (paisaje) y hay que oscurecer un poco las listas. */
    val illustrated: Boolean,
    /** Desenfoque real activado (se apaga en "Sólido" para teléfonos lentos). */
    val blur: Boolean,
    val background: String,
)

val LocalAppColors = staticCompositionLocalOf { musicDark(Color(0xFFFA2D48)) }
val LocalHaze = staticCompositionLocalOf { HazeState() }

val ACCENTS = listOf(
    0xFFFA2D48, 0xFFFF4F6D, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF00C7BE,
    0xFF30B0FF, 0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF2D92, 0xFFFFFFFF,
)

val BACKGROUNDS = listOf(
    "atardecer" to "Atardecer",
    "bosque" to "Bosque",
    "noche" to "Noche estrellada",
    "oceano" to "Océano",
    "aurora" to "Aurora",
    "desierto" to "Desierto",
)

val STYLES = listOf(
    "music" to "MUSIC (cristal líquido)",
    "paisaje" to "Paisaje",
    "neon" to "Neón",
    "solido" to "Sólido (más rápido)",
)

fun musicDark(accent: Color) = AppColors(
    style = "music", isDark = true,
    bg = Color.Black, bg2 = Color(0xFF1C1C1E),
    text = Color.White, text2 = Color(0x9EFFFFFF), text3 = Color(0x61FFFFFF),
    accent = accent, onAccent = Color.White,
    glass = Color(0x8C262628), glassEdge = Color(0x29FFFFFF), glassSpec = Color(0x66FFFFFF),
    divider = Color(0x1FFFFFFF), illustrated = false, blur = true, background = "none",
)

fun musicLight(accent: Color) = AppColors(
    style = "music", isDark = false,
    bg = Color.White, bg2 = Color(0xFFF2F2F7),
    text = Color.Black, text2 = Color(0x993C3C43), text3 = Color(0x4D3C3C43),
    accent = if (accent == Color.White) Color(0xFFFA2D48) else accent, onAccent = Color.White,
    glass = Color(0x94FFFFFF), glassEdge = Color(0x14000000), glassSpec = Color(0xE6FFFFFF),
    divider = Color(0x1A000000), illustrated = false, blur = true, background = "none",
)

fun paisaje(accent: Color, bg: String) = AppColors(
    style = "paisaje", isDark = true,
    bg = Color(0xFF2B2440), bg2 = Color(0xFF3A3150),
    text = Color.White, text2 = Color(0xB3FFFFFF), text3 = Color(0x66FFFFFF),
    accent = accent, onAccent = Color.White,
    glass = Color(0x6630283F), glassEdge = Color(0x24FFFFFF), glassSpec = Color(0x40FFFFFF),
    divider = Color(0x1AFFFFFF), illustrated = true, blur = true, background = bg,
)

fun neon(accent: Color) = AppColors(
    style = "neon", isDark = true,
    bg = Color(0xFF07070F), bg2 = Color(0xFF12122A),
    text = Color.White, text2 = Color(0xA6E0E6FF), text3 = Color(0x5CE0E6FF),
    accent = accent, onAccent = Color.Black,
    glass = Color(0x7314142E), glassEdge = accent.copy(alpha = 0.35f), glassSpec = Color(0x40FFFFFF),
    divider = Color(0x1F8AA0FF), illustrated = true, blur = true, background = "neon",
)

fun solido(accent: Color, dark: Boolean) = if (dark) AppColors(
    style = "solido", isDark = true,
    bg = Color(0xFF121212), bg2 = Color(0xFF1E1E1E),
    text = Color.White, text2 = Color(0xB3FFFFFF), text3 = Color(0x61FFFFFF),
    accent = accent, onAccent = Color.White,
    glass = Color(0xFF1E1E1E), glassEdge = Color(0x14FFFFFF), glassSpec = Color.Transparent,
    divider = Color(0x14FFFFFF), illustrated = false, blur = false, background = "none",
) else musicLight(accent).copy(style = "solido", blur = false, glass = Color(0xFFF2F2F7), glassSpec = Color.Transparent)

@Composable
fun appColorsFor(s: Settings): AppColors {
    val sysDark = isSystemInDarkTheme()
    val dark = when (s.mode) { "light" -> false; "dark" -> true; else -> sysDark }
    val accent = Color(s.accent.toULong().toLong().toInt())
    return when (s.style) {
        "paisaje" -> paisaje(if (s.accent == 0xFFFA2D48) Color(0xFFFF4F6D) else accent, s.background)
        "neon" -> neon(if (s.accent == 0xFFFA2D48) Color(0xFF00E5FF) else accent)
        "solido" -> solido(accent, dark)
        else -> if (dark) musicDark(accent) else musicLight(accent)
    }
}

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun MusicTheme(colors: AppColors, content: @Composable () -> Unit) {
    val scheme = if (colors.isDark) darkColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, background = colors.bg, onBackground = colors.text,
        surface = colors.bg2, onSurface = colors.text, surfaceVariant = colors.bg2, onSurfaceVariant = colors.text2,
        secondary = colors.accent, outline = colors.divider, surfaceContainerHigh = colors.bg2,
        surfaceContainer = colors.bg2, surfaceContainerHighest = colors.bg2,
    ) else lightColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, background = colors.bg, onBackground = colors.text,
        surface = colors.bg, onSurface = colors.text, surfaceVariant = colors.bg2, onSurfaceVariant = colors.text2,
        secondary = colors.accent, outline = colors.divider, surfaceContainerHigh = colors.bg2,
        surfaceContainer = colors.bg2, surfaceContainerHighest = colors.bg2,
    )
    val haze = remember { HazeState() }
    CompositionLocalProvider(LocalAppColors provides colors, LocalHaze provides haze) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
