package no.vardir.skald.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.vardir.skald.core.model.LogoVariant
import no.vardir.skald.ui.theme.Skald

/**
 * The mark: a right-angle "S" traced through six nodes. It reads as the letter
 * and as a small graph at the same time, which is the whole idea. The inner
 * pivot node carries the accent.
 */
@Composable
fun SkaldLogo(
    variant: LogoVariant = LogoVariant.Sigil,
    size: Dp = 22.dp,
    withWordmark: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension / 24f
            val stroke = Stroke(1.7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

            when (variant) {
                LogoVariant.Sigil -> {
                    val points = listOf(
                        17f to 5f, 7f to 5f, 7f to 11.5f, 17f to 11.5f, 17f to 19f, 7f to 19f,
                    )
                    val path = Path()
                    points.forEachIndexed { i, (x, y) ->
                        if (i == 0) path.moveTo(x * s, y * s) else path.lineTo(x * s, y * s)
                    }
                    drawPath(path, colors.tx1, style = stroke)
                    points.forEachIndexed { i, (x, y) ->
                        val accent = i == 3
                        val centre = Offset(x * s, y * s)
                        drawCircle(if (accent) colors.accent else colors.bg1, (if (accent) 2.1f else 1.5f) * s, centre)
                        drawCircle(
                            if (accent) colors.accent else colors.tx1,
                            (if (accent) 2.1f else 1.5f) * s,
                            centre,
                            style = Stroke(1.3f * s),
                        )
                    }
                }

                LogoVariant.Monogram -> {
                    drawRoundRect(
                        color = colors.bg3,
                        topLeft = Offset(2.5f * s, 2.5f * s),
                        size = Size(19f * s, 19f * s),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.5f * s),
                    )
                    drawRoundRect(
                        color = colors.line3,
                        topLeft = Offset(2.5f * s, 2.5f * s),
                        size = Size(19f * s, 19f * s),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.5f * s),
                        style = Stroke(1.3f * s),
                    )
                    // The letterform itself is drawn by the wordmark beside it;
                    // at this size a glyph inside the box would only muddy.
                    drawCircle(colors.accent, 2.4f * s, Offset(12f * s, 12f * s))
                }

                LogoVariant.Bracket -> {
                    val left = Path().apply {
                        moveTo(9f * s, 4f * s)
                        cubicTo(6f * s, 4f * s, 6.5f * s, 11f * s, 4f * s, 12f * s)
                        cubicTo(6.5f * s, 13f * s, 6f * s, 20f * s, 9f * s, 20f * s)
                    }
                    val right = Path().apply {
                        moveTo(15f * s, 4f * s)
                        cubicTo(18f * s, 4f * s, 17.5f * s, 11f * s, 20f * s, 12f * s)
                        cubicTo(17.5f * s, 13f * s, 18f * s, 20f * s, 15f * s, 20f * s)
                    }
                    drawPath(left, colors.tx1, style = stroke)
                    drawPath(right, colors.tx1, style = stroke)
                    drawCircle(colors.accent, 2f * s, Offset(12f * s, 12f * s))
                }
            }
        }

        if (withWordmark) {
            Text(
                "SKALD",
                style = Skald.type.eyebrow.copy(fontSize = 13.sp, letterSpacing = 2.2.sp),
                color = colors.tx1,
            )
        }
    }
}
