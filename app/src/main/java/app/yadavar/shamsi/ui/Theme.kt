package app.yadavar.shamsi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One selectable accent color of the app. */
data class AccentOption(val id: String, val label: String, val color: Color)

val AccentOptions = listOf(
    AccentOption("purple", "بنفش", Color(0xFF6750A4)),
    AccentOption("indigo", "نیلی", Color(0xFF3F51B5)),
    AccentOption("blue", "آبی", Color(0xFF1B6EF3)),
    AccentOption("cyan", "فیروزه‌ای", Color(0xFF00839A)),
    AccentOption("teal", "سبزآبی", Color(0xFF0F9B8E)),
    AccentOption("green", "سبز", Color(0xFF2E8B4F)),
    AccentOption("lime", "لیمویی", Color(0xFF7C9A12)),
    AccentOption("amber", "کهربایی", Color(0xFFC4841D)),
    AccentOption("orange", "نارنجی", Color(0xFFE06A1A)),
    AccentOption("red", "قرمز", Color(0xFFD63B3B)),
    AccentOption("rose", "صورتی", Color(0xFFD4527A)),
    AccentOption("brown", "قهوه‌ای", Color(0xFF8D5B3E)),
    AccentOption("slate", "خاکستری", Color(0xFF5F6B7A)),
)

fun accentColor(name: String): Color = AccentOptions.firstOrNull { it.id == name }?.color ?: AccentOptions.first().color

/** Text size choices of the app itself (the widget has its own size setting). */
data class TextSizeOption(val id: String, val label: String, val scale: Float)

val TextSizeOptions = listOf(
    TextSizeOption("sm", "کوچک", 0.9f),
    TextSizeOption("md", "متوسط", 1.0f),
    TextSizeOption("lg", "بزرگ", 1.15f),
    TextSizeOption("xl", "خیلی بزرگ", 1.3f),
)

private fun textScale(id: String): Float = TextSizeOptions.firstOrNull { it.id == id }?.scale ?: 1f

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Slightly taller line heights: Persian script with diacritics and numerals reads better with more air. */
private val AppTypography: Typography = run {
    val base = Typography()
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 18.sp),
    )
}

@Composable
fun YadavarTheme(theme: String, accent: String, textSize: String = "md", content: @Composable () -> Unit) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val primary = accentColor(accent)
    // Text on the main color (buttons, FAB): black on light accents, white on dark ones.
    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
    val scheme = if (dark) {
        // Selected chips and the main buttons share the accent hue.
        val tint = lerp(Color(0xFF181B22), primary, 0.40f)
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = tint,
            secondaryContainer = tint,
            onSecondaryContainer = Color.White,
            background = Color(0xFF101114),
            surface = Color(0xFF181B22),
            surfaceContainer = Color(0xFF22262F),
        )
    } else {
        val tint = lerp(Color.White, primary, 0.16f)
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = tint,
            secondaryContainer = tint,
            onSecondaryContainer = lerp(primary, Color.Black, 0.35f),
            background = Color(0xFFF4F6F8),
            surface = Color(0xFFFFFFFF),
            surfaceContainer = Color(0xFFEEF1F4),
        )
    }
    // The in-app text size: only sp (text) values grow, dp (spacing) stays the same.
    val density = LocalDensity.current
    val scaled = Density(density.density, density.fontScale * textScale(textSize))
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, typography = AppTypography, content = content)
    }
}
