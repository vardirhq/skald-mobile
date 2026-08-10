package no.vardir.skald.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.vardir.skald.core.model.Density
import no.vardir.skald.core.model.SchemaName
import no.vardir.skald.core.model.ThemeName

/**
 * The design tokens from `styles/tokens.css`, one for one.
 *
 * Editor-native and dark-first: surfaces run darkest (activity) to lightest
 * (popover), text runs brightest to faintest, and the accent is a single teal
 * that everything else is built around. Keeping the same names as the CSS is
 * deliberate — the two builds are meant never to drift.
 */
@Immutable
data class SkaldColors(
    // Surfaces — darkest (activity) to lightest (popover)
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val bg3: Color,
    val bg4: Color,
    val bgPop: Color,

    // Hairlines
    val line: Color,
    val line2: Color,
    val line3: Color,

    // Text
    val tx0: Color,
    val tx1: Color,
    val tx2: Color,
    val tx3: Color,
    val tx4: Color,

    // Accent
    val accent: Color,
    val accentBright: Color,
    val accentDim: Color,
    val accentGhost: Color,
    val accentLine: Color,
    val onAccent: Color,

    // Syntax tones — what the schema runes are coloured by
    val teal: Color,
    val blue: Color,
    val purple: Color,
    val green: Color,
    val orange: Color,
    val red: Color,
    val yellow: Color,
    val gray: Color,

    // Status
    val ok: Color,
    val warn: Color,
    val err: Color,
    val info: Color,

    val selection: Color,
    val isDark: Boolean,
) {
    /** Schema → tone, exactly as the CSS maps them. */
    fun toneFor(schema: SchemaName): Color = when (schema) {
        SchemaName.Note -> teal
        SchemaName.Project -> orange
        SchemaName.Person -> blue
        SchemaName.Daily -> green
        SchemaName.Idea -> purple
        SchemaName.Source -> red
        SchemaName.Code -> yellow
        SchemaName.Place -> gray
    }
}

/** "Midnight" — deep blue-black. The default. */
private val Midnight = SkaldColors(
    bg0 = Color(0xFF0A0C10),
    bg1 = Color(0xFF0D1015),
    bg2 = Color(0xFF111620),
    bg3 = Color(0xFF161C27),
    bg4 = Color(0xFF1C2330),
    bgPop = Color(0xFF1A212D),
    line = Color(0xFF1D2531),
    line2 = Color(0xFF28323F),
    line3 = Color(0xFF38465A),
    tx0 = Color(0xFFEAEEF4),
    tx1 = Color(0xFFC3CCD8),
    tx2 = Color(0xFF8A95A4),
    tx3 = Color(0xFF5E6A7A),
    tx4 = Color(0xFF3F4A59),
    accent = Color(0xFF6AE0C6),
    accentBright = Color(0xFF8AF0D8),
    accentDim = Color(0xFF3F8A7C),
    accentGhost = Color(0x226AE0C6),
    accentLine = Color(0x576AE0C6),
    onAccent = Color(0xFF062019),
    teal = Color(0xFF6AE0C6),
    blue = Color(0xFF6CB2FF),
    purple = Color(0xFFB69CFF),
    green = Color(0xFF84D99A),
    orange = Color(0xFFF0A878),
    red = Color(0xFFF08A7C),
    yellow = Color(0xFFE6CD7A),
    gray = Color(0xFF5E6A7A),
    ok = Color(0xFF84D99A),
    warn = Color(0xFFE6CD7A),
    err = Color(0xFFF08A7C),
    info = Color(0xFF6CB2FF),
    selection = Color(0x296AE0C6),
    isDark = true,
)

/** "Slate" — neutral graphite. Same accent, quieter ground. */
private val Slate = Midnight.copy(
    bg0 = Color(0xFF0C0D0E),
    bg1 = Color(0xFF121315),
    bg2 = Color(0xFF17191C),
    bg3 = Color(0xFF1E2125),
    bg4 = Color(0xFF25292E),
    bgPop = Color(0xFF212429),
    line = Color(0xFF24272C),
    line2 = Color(0xFF31363D),
    line3 = Color(0xFF434A53),
    tx0 = Color(0xFFECEDEF),
    tx1 = Color(0xFFC6C9CD),
    tx2 = Color(0xFF8B9098),
    tx3 = Color(0xFF61666E),
    tx4 = Color(0xFF41464D),
    gray = Color(0xFF61666E),
)

/** "Daybreak" — the light surface. */
private val Daybreak = SkaldColors(
    bg0 = Color(0xFFE7E9EE),
    bg1 = Color(0xFFEEF0F4),
    bg2 = Color(0xFFFAFBFC),
    bg3 = Color(0xFFF2F4F8),
    bg4 = Color(0xFFE6EAF1),
    bgPop = Color(0xFFFFFFFF),
    line = Color(0xFFDDE1E8),
    line2 = Color(0xFFCBD2DC),
    line3 = Color(0xFFAAB3C0),
    tx0 = Color(0xFF1C2330),
    tx1 = Color(0xFF38414F),
    tx2 = Color(0xFF5E6878),
    tx3 = Color(0xFF8A94A3),
    tx4 = Color(0xFFB3BBC7),
    accent = Color(0xFF119E84),
    accentBright = Color(0xFF0E8A73),
    accentDim = Color(0xFF7FCDBD),
    accentGhost = Color(0x1A119E84),
    accentLine = Color(0x4D119E84),
    onAccent = Color(0xFFFFFFFF),
    teal = Color(0xFF119E84),
    blue = Color(0xFF2F74D0),
    purple = Color(0xFF8155D6),
    green = Color(0xFF3F9D57),
    orange = Color(0xFFC2722C),
    red = Color(0xFFD35D50),
    yellow = Color(0xFFB58A1E),
    gray = Color(0xFF8A94A3),
    ok = Color(0xFF3F9D57),
    warn = Color(0xFFB58A1E),
    err = Color(0xFFD35D50),
    info = Color(0xFF2F74D0),
    selection = Color(0x21119E84),
    isDark = false,
)

fun colorsFor(theme: ThemeName): SkaldColors = when (theme) {
    ThemeName.Midnight -> Midnight
    ThemeName.Slate -> Slate
    ThemeName.Daybreak -> Daybreak
}

/**
 * Type. The desktop sets Hanken Grotesk for UI and JetBrains Mono for code; the
 * platform families stand in for them here, so the app carries no font binaries
 * and the scale is what does the work. Swapping in the real families is a change
 * to these two lines.
 */
@Immutable
data class SkaldFonts(val ui: FontFamily = FontFamily.Default, val mono: FontFamily = FontFamily.Monospace)

@Immutable
class SkaldType(fonts: SkaldFonts) {
    /** 38sp, the logbook date. */
    val display = TextStyle(fontFamily = fonts.ui, fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
    val title = TextStyle(fontFamily = fonts.ui, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    val screenTitle = TextStyle(fontFamily = fonts.ui, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    val heading = TextStyle(fontFamily = fonts.ui, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = fonts.ui, fontSize = 15.5.sp, lineHeight = 26.sp)
    val row = TextStyle(fontFamily = fonts.ui, fontSize = 14.5.sp, lineHeight = 20.sp)
    val small = TextStyle(fontFamily = fonts.ui, fontSize = 12.5.sp, lineHeight = 18.sp)

    /** The chrome voice: mono, small, wide-tracked, upper-cased at the call site. */
    val eyebrow = TextStyle(fontFamily = fonts.mono, fontSize = 11.sp, letterSpacing = 1.4.sp)
    val meta = TextStyle(fontFamily = fonts.mono, fontSize = 11.sp)
    val metaSmall = TextStyle(fontFamily = fonts.mono, fontSize = 10.sp, letterSpacing = 0.9.sp)
    val code = TextStyle(fontFamily = fonts.mono, fontSize = 13.sp, lineHeight = 20.sp)
    val stat = TextStyle(fontFamily = fonts.ui, fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

/** Density, as the CSS defines it — the phone is fixed to comfortable by default. */
@Immutable
data class SkaldMetrics(
    val pad: Dp,
    val gap: Dp,
    val rowHeight: Dp,
) {
    val r1: Dp get() = 4.dp
    val r2: Dp get() = 6.dp
    val r3: Dp get() = 9.dp
    val r4: Dp get() = 13.dp

    /** Cards and sheets round harder on a phone than they do in a window. */
    val card: Dp get() = 14.dp
    val sheet: Dp get() = 20.dp
}

fun metricsFor(density: Density): SkaldMetrics = when (density) {
    Density.Compact -> SkaldMetrics(pad = 14.dp, gap = 14.dp, rowHeight = 42.dp)
    Density.Regular -> SkaldMetrics(pad = 18.dp, gap = 20.dp, rowHeight = 48.dp)
    Density.Cozy -> SkaldMetrics(pad = 22.dp, gap = 28.dp, rowHeight = 54.dp)
}

val LocalSkaldColors = staticCompositionLocalOf { Midnight }
val LocalSkaldType = staticCompositionLocalOf { SkaldType(SkaldFonts()) }
val LocalSkaldMetrics = staticCompositionLocalOf { metricsFor(Density.Regular) }

object Skald {
    val colors: SkaldColors
        @Composable @ReadOnlyComposable get() = LocalSkaldColors.current
    val type: SkaldType
        @Composable @ReadOnlyComposable get() = LocalSkaldType.current
    val metrics: SkaldMetrics
        @Composable @ReadOnlyComposable get() = LocalSkaldMetrics.current
}

@Composable
fun SkaldTheme(
    theme: ThemeName = ThemeName.Midnight,
    density: Density = Density.Regular,
    content: @Composable () -> Unit,
) {
    // The vault's own setting wins outright. Skald is dark-first by design, and
    // a surface you chose should not change because the sun went down.
    CompositionLocalProvider(
        LocalSkaldColors provides colorsFor(theme),
        LocalSkaldType provides SkaldType(SkaldFonts()),
        LocalSkaldMetrics provides metricsFor(density),
        content = content,
    )
}
