package no.vardir.skald.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.GraphNode
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.ui.components.EmptyState
import no.vardir.skald.ui.components.Eyebrow
import no.vardir.skald.ui.components.Rune
import no.vardir.skald.ui.theme.Skald
import kotlin.math.sqrt

/**
 * The Constellation. Stars are notes, sized by how many edges they carry;
 * hairline edges light up around whatever is selected. The layout is authored
 * and persisted rather than simulated, so the map is a place you return to.
 */
@Composable
fun ConstellationScreen(
    snapshot: VaultSnapshot,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    val nodes = snapshot.graph.nodes
    if (nodes.isEmpty()) {
        EmptyState("No stars yet", "Link two notes with [[a wikilink]] and the map draws itself.", modifier)
        return
    }

    var selected by remember(nodes.size) { mutableStateOf(nodes.maxByOrNull { it.deg }?.path) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    val byPath = remember(nodes) { nodes.associateBy { it.path } }
    val node = selected?.let { byPath[it] } ?: nodes.first()

    Box(modifier.fillMaxSize().background(colors.bg2)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        scale = (scale * zoomChange).coerceIn(0.6f, 4f)
                        pan += panChange
                    }
                }
                .pointerInput(nodes) {
                    detectTapGestures { tap ->
                        // Nearest star wins, within a thumb's reach of the tap.
                        val hit = nodes.minByOrNull { star ->
                            val p = project(star, size.width.toFloat(), size.height.toFloat(), scale, pan)
                            (p - tap).getDistanceSquared()
                        }
                        if (hit != null) {
                            val p = project(hit, size.width.toFloat(), size.height.toFloat(), scale, pan)
                            if ((p - tap).getDistance() < 56f) selected = hit.path
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            for (edge in snapshot.graph.edges) {
                val a = byPath[edge.from] ?: continue
                val b = byPath[edge.to] ?: continue
                val active = selected == edge.from || selected == edge.to
                drawLine(
                    color = if (active) colors.accent.copy(alpha = 0.65f) else colors.tx0.copy(alpha = 0.13f),
                    start = project(a, w, h, scale, pan),
                    end = project(b, w, h, scale, pan),
                    strokeWidth = if (active) 1.6f else 0.9f,
                )
            }

            for (star in nodes) {
                val centre = project(star, w, h, scale, pan)
                val radius = (3.5f + sqrt(star.deg.toFloat()) * 2.1f) * scale.coerceAtMost(2f)
                val tone = colors.toneFor(star.schema)
                if (selected == star.path) {
                    drawCircle(colors.accent.copy(alpha = 0.16f), radius * 3.4f, centre)
                }
                drawCircle(tone, radius, centre)
                drawCircle(colors.bg2, radius, centre, style = Stroke(2f))
            }
        }

        // The inspector, sitting where the design puts it: quiet, at the bottom.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.bgPop)
                .border(BorderStroke(1.dp, colors.line2), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Rune(node.schema, 13.dp)
                Eyebrow(node.schema.name, color = colors.accent)
            }
            Text(
                node.label,
                style = Skald.type.heading,
                color = colors.tx0,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp, bottom = 8.dp),
            )
            Text(
                snapshot.byPath[node.path]?.excerpt?.take(120)?.ifEmpty { null }
                    ?: "A ${node.schema.name.lowercase()} linked to ${node.deg} other notes.",
                style = Skald.type.small,
                color = colors.tx3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("links ${node.deg}", style = Skald.type.meta, color = colors.tx3)
                Text(
                    "cluster ${snapshot.constellations.firstOrNull { node.path in it.nodes }?.name ?: "—"}",
                    style = Skald.type.meta,
                    color = colors.tx3,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Open ›",
                    style = Skald.type.meta,
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenNote(node.path) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Normalized [0,1] star position → a point on screen, under the current pan and zoom. */
private fun project(node: GraphNode, w: Float, h: Float, scale: Float, pan: Offset): Offset =
    Offset(
        (node.x * w - w / 2f) * scale + w / 2f + pan.x,
        (node.y * h - h / 2f) * scale + h / 2f + pan.y,
    )
