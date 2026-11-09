package app.yadavar.shamsi.widget

import androidx.compose.ui.graphics.Color

/** Colors used by the home-screen widget for one background choice. */
data class WidgetPalette(
    val bg: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val line: Color,
    val selected: Color,
    val dark: Boolean,
    /** Text color of reminders whose time has passed. */
    val overdue: Color,
    /** Text color of reminders due today. */
    val today: Color,
)

data class WidgetBgOption(val id: String, val label: String, val swatch: Color)

private class WidgetSpec(
    val id: String,
    val label: String,
    val bg: Long,
    val selected: Long,
    val accent: Long,
    val dark: Boolean,
)

// Light backgrounds first, then the dark ones. Every theme brings its own accent color.
private val Specs = listOf(
    WidgetSpec("lavender", "بنفش روشن", 0xFFF7F3FB, 0xFFE6DAF3, 0xFF4A148C, false),
    WidgetSpec("white", "سفید", 0xFFFFFFFF, 0xFFEFE7F7, 0xFF4A148C, false),
    WidgetSpec("mint", "نعنایی", 0xFFE8F5EE, 0xFFCBE8D8, 0xFF1B5E3F, false),
    WidgetSpec("sky", "آبی روشن", 0xFFE7F1FB, 0xFFCFE3F7, 0xFF0D47A1, false),
    WidgetSpec("peach", "هلویی", 0xFFFDEEE4, 0xFFF9D9C3, 0xFF9C3D0B, false),
    WidgetSpec("rose", "صورتی", 0xFFFBE9EF, 0xFFF5CCD9, 0xFF8E1A47, false),
    WidgetSpec("lemon", "لیمویی", 0xFFFBF8DE, 0xFFF1EAB0, 0xFF6B5A00, false),
    WidgetSpec("aqua", "فیروزه‌ای", 0xFFE3F6F6, 0xFFC2E8E8, 0xFF00656B, false),
    WidgetSpec("sand", "شنی", 0xFFF5EFE6, 0xFFE8DCC8, 0xFF6D4C2B, false),
    WidgetSpec("cloud", "خاکستری روشن", 0xFFEDEFF2, 0xFFD9DEE5, 0xFF37474F, false),
    WidgetSpec("dark", "تیره بنفش", 0xFF1F1B29, 0xFF3B3050, 0xFFD1B3F5, true),
    WidgetSpec("midnight", "نیمه‌شب", 0xFF121A2B, 0xFF24365A, 0xFF9CC3FF, true),
    WidgetSpec("forest", "جنگلی", 0xFF14211A, 0xFF264636, 0xFF8FE0B0, true),
    WidgetSpec("graphite", "گرافیتی", 0xFF1B1C1F, 0xFF34363C, 0xFFE0E0E6, true),
    WidgetSpec("wine", "شرابی", 0xFF2A1520, 0xFF4F2A3C, 0xFFF4A6C2, true),
)

val WidgetBackgrounds: List<WidgetBgOption> = Specs.map { WidgetBgOption(it.id, it.label, Color(it.bg)) }

private val LightText = Color(0xFF2A2133)
private val LightMuted = Color(0xFF6B6178)
private val DarkText = Color(0xFFF1ECF8)
private val DarkMuted = Color(0xFFA79FB5)

// Past = red, today = green; the shades are picked to stay readable on light and on dark backgrounds.
private val LightOverdue = Color(0xFFC62828)
private val LightToday = Color(0xFF1B7F4B)
private val DarkOverdue = Color(0xFFFF8A80)
private val DarkToday = Color(0xFF6FDBA3)

fun widgetPalette(id: String): WidgetPalette {
    val spec = Specs.firstOrNull { it.id == id } ?: Specs.first()
    val accent = Color(spec.accent)
    return if (spec.dark) {
        WidgetPalette(
            bg = Color(spec.bg),
            text = DarkText,
            muted = DarkMuted,
            accent = accent,
            line = accent.copy(alpha = 0.4f),
            selected = Color(spec.selected),
            dark = true,
            overdue = DarkOverdue,
            today = DarkToday,
        )
    } else {
        WidgetPalette(
            bg = Color(spec.bg),
            text = LightText,
            muted = LightMuted,
            accent = accent,
            line = accent.copy(alpha = 0.35f),
            selected = Color(spec.selected),
            dark = false,
            overdue = LightOverdue,
            today = LightToday,
        )
    }
}
