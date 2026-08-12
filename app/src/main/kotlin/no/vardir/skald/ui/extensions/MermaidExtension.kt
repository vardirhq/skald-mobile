package no.vardir.skald.ui.extensions

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.extensions.BuiltInExtensions
import no.vardir.skald.core.text.Insertions
import no.vardir.skald.core.text.Markdown
import no.vardir.skald.core.text.MermaidFlowchart
import no.vardir.skald.ui.components.Eyebrow
import no.vardir.skald.ui.theme.Skald
import kotlin.math.max

val mermaidRendererExtension = RendererExtension(
    descriptor = BuiltInExtensions.Mermaid,
    markdownComponents = emptyList(),
    codeFences = listOf(
        CodeFenceContribution("mermaid") { block -> MermaidDiagram(block.text) },
    ),
    editorInsertions = listOf(
        EditorInsertionContribution(
            id = "mermaid.diagram",
            label = "Mermaid diagram",
            description = "Insert a locally rendered flowchart",
            glyph = "◇",
            keywords = setOf("flowchart", "graph", "chart"),
            template = Insertions.Template(
                "```mermaid\nflowchart LR\n    Idea --> Build\n    Build --> Release\n```\n",
                placeholder = "Idea",
            ),
        ),
    ),
)

@Composable
private fun MermaidDiagram(source: String) {
    val diagram = remember(source) { MermaidFlowchart.parse(source) }
    if (diagram == null) {
        MermaidSourceFallback(source)
        return
    }
    val colors = Skald.colors
    val layout = remember(diagram) { layout(diagram) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .background(colors.bg1, RoundedCornerShape(Skald.metrics.r3))
            .border(androidx.compose.foundation.BorderStroke(1.dp, colors.line), RoundedCornerShape(Skald.metrics.r3))
            .padding(12.dp),
    ) {
        Eyebrow("Mermaid · flowchart", Modifier.padding(bottom = 8.dp), colors.accent)
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Canvas(
                Modifier
                    .size(layout.width.dp, layout.height.dp)
                    .semantics { contentDescription = "Mermaid flowchart with ${diagram.nodes.size} nodes" },
            ) {
                val unit = density
                val nodeWidth = NODE_WIDTH * unit
                val nodeHeight = NODE_HEIGHT * unit
                val positions = layout.nodes.associate { it.node.id to Offset(it.x * unit, it.y * unit) }
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colors.tx0.toArgb()
                    textSize = 13f * unit
                    textAlign = Paint.Align.CENTER
                }
                val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colors.tx3.toArgb()
                    textSize = 10f * unit
                    textAlign = Paint.Align.CENTER
                }

                for (edge in diagram.edges) {
                    val from = positions[edge.from] ?: continue
                    val to = positions[edge.to] ?: continue
                    val (start, end) = when (diagram.direction) {
                        MermaidFlowchart.Direction.LR -> Offset(from.x + nodeWidth / 2, from.y) to Offset(to.x - nodeWidth / 2, to.y)
                        MermaidFlowchart.Direction.RL -> Offset(from.x - nodeWidth / 2, from.y) to Offset(to.x + nodeWidth / 2, to.y)
                        MermaidFlowchart.Direction.TB -> Offset(from.x, from.y + nodeHeight / 2) to Offset(to.x, to.y - nodeHeight / 2)
                        MermaidFlowchart.Direction.BT -> Offset(from.x, from.y - nodeHeight / 2) to Offset(to.x, to.y + nodeHeight / 2)
                    }
                    val strokeWidth = if (edge.style == MermaidFlowchart.EdgeStyle.Thick) 3f * unit else 1.5f * unit
                    val effect = if (edge.style == MermaidFlowchart.EdgeStyle.Dotted) {
                        PathEffect.dashPathEffect(floatArrayOf(5f * unit, 5f * unit))
                    } else null
                    drawLine(colors.tx3, start, end, strokeWidth, StrokeCap.Round, effect)
                    if (edge.style != MermaidFlowchart.EdgeStyle.Line) drawArrow(end, start, colors.tx3, unit)
                    edge.label?.let { label ->
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(label, (start.x + end.x) / 2, (start.y + end.y) / 2 - 5f * unit, edgePaint)
                        }
                    }
                }

                for (placed in layout.nodes) {
                    val center = positions.getValue(placed.node.id)
                    drawRoundRect(
                        color = colors.bg3,
                        topLeft = Offset(center.x - nodeWidth / 2, center.y - nodeHeight / 2),
                        size = Size(nodeWidth, nodeHeight),
                        cornerRadius = CornerRadius(10f * unit),
                    )
                    drawRoundRect(
                        color = colors.accentLine,
                        topLeft = Offset(center.x - nodeWidth / 2, center.y - nodeHeight / 2),
                        size = Size(nodeWidth, nodeHeight),
                        cornerRadius = CornerRadius(10f * unit),
                        style = Stroke(1f * unit),
                    )
                    val label = placed.node.label.let { if (it.length > 24) it.take(23) + "…" else it }
                    drawIntoCanvas { canvas ->
                        val baseline = center.y - (textPaint.ascent() + textPaint.descent()) / 2
                        canvas.nativeCanvas.drawText(label, center.x, baseline, textPaint)
                    }
                }
            }
        }
    }
}

@Composable
private fun MermaidSourceFallback(source: String) {
    val colors = Skald.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .background(colors.bg1, RoundedCornerShape(Skald.metrics.r3))
            .border(androidx.compose.foundation.BorderStroke(1.dp, colors.line), RoundedCornerShape(Skald.metrics.r3))
            .padding(12.dp),
    ) {
        Eyebrow("Mermaid · source", Modifier.padding(bottom = 6.dp), colors.tx3)
        Text("This Mermaid diagram type is not supported on Android yet.", style = Skald.type.meta, color = colors.tx3)
        Text(source, style = Skald.type.code, color = colors.tx1, modifier = Modifier.padding(top = 9.dp))
    }
}

private const val NODE_WIDTH = 142f
private const val NODE_HEIGHT = 56f
private const val RANK_GAP = 76f
private const val NODE_GAP = 34f
private const val PAD = 18f

private data class PlacedNode(val node: MermaidFlowchart.Node, val x: Float, val y: Float)
private data class DiagramLayout(val width: Float, val height: Float, val nodes: List<PlacedNode>)

private fun layout(diagram: MermaidFlowchart.Diagram): DiagramLayout {
    val indegree = diagram.nodes.associate { it.id to 0 }.toMutableMap()
    diagram.edges.forEach { edge -> indegree[edge.to] = (indegree[edge.to] ?: 0) + 1 }
    val queue = ArrayDeque(indegree.filterValues { it == 0 }.keys)
    val rank = diagram.nodes.associate { it.id to 0 }.toMutableMap()
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (edge in diagram.edges.filter { it.from == current }) {
            rank[edge.to] = max(rank[edge.to] ?: 0, (rank[current] ?: 0) + 1)
            indegree[edge.to] = (indegree[edge.to] ?: 1) - 1
            if (indegree[edge.to] == 0) queue.add(edge.to)
        }
    }
    val grouped = diagram.nodes.groupBy { rank[it.id] ?: 0 }.toSortedMap()
    val horizontal = diagram.direction == MermaidFlowchart.Direction.LR || diagram.direction == MermaidFlowchart.Direction.RL
    val rankCount = max(1, grouped.size)
    val laneCount = max(1, grouped.values.maxOfOrNull { it.size } ?: 1)
    val naturalWidth = if (horizontal) rankCount * NODE_WIDTH + (rankCount - 1) * RANK_GAP else laneCount * NODE_WIDTH + (laneCount - 1) * NODE_GAP
    val naturalHeight = if (horizontal) laneCount * NODE_HEIGHT + (laneCount - 1) * NODE_GAP else rankCount * NODE_HEIGHT + (rankCount - 1) * RANK_GAP
    val width = naturalWidth + PAD * 2
    val height = naturalHeight + PAD * 2
    val placed = mutableListOf<PlacedNode>()
    grouped.entries.forEachIndexed { rankIndex, (_, nodes) ->
        nodes.forEachIndexed { laneIndex, node ->
            var x = if (horizontal) PAD + NODE_WIDTH / 2 + rankIndex * (NODE_WIDTH + RANK_GAP)
                else PAD + NODE_WIDTH / 2 + laneIndex * (NODE_WIDTH + NODE_GAP)
            var y = if (horizontal) PAD + NODE_HEIGHT / 2 + laneIndex * (NODE_HEIGHT + NODE_GAP)
                else PAD + NODE_HEIGHT / 2 + rankIndex * (NODE_HEIGHT + RANK_GAP)
            if (diagram.direction == MermaidFlowchart.Direction.RL) x = width - x
            if (diagram.direction == MermaidFlowchart.Direction.BT) y = height - y
            placed += PlacedNode(node, x, y)
        }
    }
    return DiagramLayout(width, height, placed)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(
    tip: Offset,
    from: Offset,
    color: androidx.compose.ui.graphics.Color,
    unit: Float,
) {
    val angle = kotlin.math.atan2(tip.y - from.y, tip.x - from.x)
    val length = 9f * unit
    val spread = 0.55f
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - length * kotlin.math.cos(angle - spread), tip.y - length * kotlin.math.sin(angle - spread))
        lineTo(tip.x - length * kotlin.math.cos(angle + spread), tip.y - length * kotlin.math.sin(angle + spread))
        close()
    }
    drawPath(path, color)
}
