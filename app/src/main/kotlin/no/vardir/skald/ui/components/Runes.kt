package no.vardir.skald.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.SchemaName
import no.vardir.skald.ui.theme.Skald

/**
 * The schema marks: monoline geometric runes drawn on a 24×24 grid with one
 * consistent stroke, so a list of notes reads as a rhythm of shapes rather than
 * a wall of titles. Ported stroke for stroke from `runes.jsx`.
 */
@Composable
fun Rune(
    schema: SchemaName,
    size: Dp = 16.dp,
    tint: Color = Skald.colors.toneFor(schema),
    modifier: Modifier = Modifier,
) {
    val strokeWidth = when {
        size <= 14.dp -> 1.9f
        size <= 20.dp -> 1.7f
        else -> 1.5f
    }
    Canvas(modifier = modifier.size(size)) {
        val scale = this.size.minDimension / 24f
        val stroke = Stroke(
            width = strokeWidth * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawRune(schema, tint, scale, stroke)
    }
}

private fun DrawScope.drawRune(schema: SchemaName, tint: Color, scale: Float, stroke: Stroke) {
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(tint, Offset(x1 * scale, y1 * scale), Offset(x2 * scale, y2 * scale), stroke.width, stroke.cap)

    fun poly(vararg points: Pair<Float, Float>) {
        val path = Path()
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) path.moveTo(x * scale, y * scale) else path.lineTo(x * scale, y * scale)
        }
        drawPath(path, tint, style = stroke)
    }

    when (schema) {
        // nauthiz — a stave crossed once
        SchemaName.Note -> {
            line(9f, 4f, 9f, 20f)
            line(4.5f, 15.5f, 14.5f, 8.5f)
        }
        // a pennant — a thing in motion
        SchemaName.Project -> {
            line(8f, 4f, 8f, 20f)
            poly(8f to 5f, 16f to 9f, 8f to 13f)
        }
        // raidho — bowl and leg, a character on the road
        SchemaName.Person -> {
            line(8f, 4f, 8f, 20f)
            poly(8f to 4f, 15f to 7.5f, 8f to 11.5f)
            line(8f, 11.5f, 15.5f, 20f)
        }
        // tiwaz — an arrow pointing up at this day
        SchemaName.Daily -> {
            line(12f, 5f, 12f, 20f)
            line(6.5f, 10.5f, 12f, 5f)
            line(17.5f, 10.5f, 12f, 5f)
        }
        // mannaz — two staves bound by a V, a thinking self
        SchemaName.Idea -> {
            line(5f, 4f, 5f, 20f)
            line(19f, 4f, 19f, 20f)
            line(5f, 5f, 12f, 12.5f)
            line(19f, 5f, 12f, 12.5f)
        }
        // berkana — two bows, where things grow from
        SchemaName.Source -> {
            line(8f, 4f, 8f, 20f)
            poly(8f to 4f, 15f to 7.5f, 8f to 11f)
            poly(8f to 11.5f, 15f to 15.5f, 8f to 19f)
        }
        // thurisaz — a single hammer-stroke, a craft mark
        SchemaName.Code -> {
            line(8f, 4f, 8f, 20f)
            poly(8f to 8f, 15.5f to 12f, 8f to 16f)
        }
        // othala — a hearth, a place
        SchemaName.Place -> {
            poly(12f to 4f, 17f to 9f, 12f to 14f, 7f to 9f, 12f to 4f)
            line(9.5f, 12f, 6.5f, 20f)
            line(14.5f, 12f, 17.5f, 20f)
        }
    }
}
