package no.vardir.skald.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import no.vardir.skald.core.model.NoteThemeSpec

/**
 * Apply the portable color subset of a desktop note theme to the native Compose
 * reading surface. CSS selectors, layout declarations and unsupported token
 * values are ignored; the existing Skald palette remains the fallback.
 */
@Composable
fun NoteThemeSurface(
    theme: NoteThemeSpec?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val base = Skald.colors
    val colors = remember(base, theme) {
        if (theme == null) base else base.copy(
            bg1 = theme.color("--note-code-bg") ?: base.bg1,
            bg2 = theme.color("--note-bg") ?: base.bg2,
            tx0 = theme.color("--note-tx-heading") ?: base.tx0,
            tx1 = theme.color("--note-tx") ?: base.tx1,
            tx2 = theme.color("--note-tx-muted") ?: base.tx2,
            accent = theme.color("--note-accent") ?: base.accent,
            accentBright = theme.color("--note-link") ?: theme.color("--note-accent") ?: base.accentBright,
            err = theme.color("--note-link-missing") ?: theme.color("--note-task-blocked") ?: base.err,
            blue = theme.color("--note-task-working") ?: base.blue,
            warn = theme.color("--note-task-overdue") ?: base.warn,
        ).let { resolved ->
            // Derive semantic surfaces from the selected accent instead of
            // accepting arbitrary CSS alpha/blending rules.
            resolved.copy(
                accentGhost = resolved.accent.copy(alpha = 0.10f),
                accentLine = resolved.accent.copy(alpha = 0.34f),
            )
        }
    }
    val background = theme?.color("--note-bg") ?: Color.Transparent
    CompositionLocalProvider(LocalSkaldColors provides colors) {
        Box(modifier.fillMaxWidth().background(background)) { content() }
    }
}

private fun NoteThemeSpec.color(token: String): Color? = tokens[token]?.let(::parsePortableColor)

/** Hex colors are deliberately the first portable value grammar. `var()`, rgb(),
 * named colors and CSS color functions fall back until Android can define their
 * semantics without pretending it is a browser. */
private fun parsePortableColor(raw: String): Color? {
    val value = raw.trim()
    if (!value.startsWith('#')) return null
    val hex = value.drop(1)
    return when (hex.length) {
        3 -> {
            val r = "${hex[0]}${hex[0]}".toIntOrNull(16) ?: return null
            val g = "${hex[1]}${hex[1]}".toIntOrNull(16) ?: return null
            val b = "${hex[2]}${hex[2]}".toIntOrNull(16) ?: return null
            Color(r, g, b)
        }
        6 -> {
            val rgb = hex.toLongOrNull(16) ?: return null
            Color((rgb shr 16 and 0xff).toInt(), (rgb shr 8 and 0xff).toInt(), (rgb and 0xff).toInt())
        }
        8 -> {
            // CSS #RRGGBBAA, not Android's #AARRGGBB.
            val rgba = hex.toLongOrNull(16) ?: return null
            Color(
                red = (rgba shr 24 and 0xff).toInt(),
                green = (rgba shr 16 and 0xff).toInt(),
                blue = (rgba shr 8 and 0xff).toInt(),
                alpha = (rgba and 0xff).toInt(),
            )
        }
        else -> null
    }
}
