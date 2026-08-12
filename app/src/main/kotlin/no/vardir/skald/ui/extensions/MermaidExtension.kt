package no.vardir.skald.ui.extensions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.extensions.BuiltInExtensions
import no.vardir.skald.core.text.Insertions
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
    val diagram = remember(source) {
        // Renderer extensions must never be able to take down the note screen.
        runCatching { MermaidFlowchart.parse(source) }.getOrNull()
    }
    if (diagram == null) {
        MermaidSourceFallback(source)
        return
    }
    if (diagram.nodes.size > MAX_RENDERED_NODES || diagram.edges.size > MAX_RENDERED_EDGES) {
        MermaidSourceFallback(source, "This flowchart is too large for the mobile preview.")
        return
    }

    val colors = Skald.colors
    val ranks = remember(diagram) { diagramRanks(diagram) }
    val displayedRanks = if (
        diagram.direction == MermaidFlowchart.Direction.RL ||
        diagram.direction == MermaidFlowchart.Direction.BT
    ) ranks.reversed() else ranks
    val nodeLabels = remember(diagram) { diagram.nodes.associate { it.id to it.label } }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .background(colors.bg1, RoundedCornerShape(Skald.metrics.r3))
            .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(Skald.metrics.r3))
            .padding(12.dp),
    ) {
        Eyebrow("Mermaid · flowchart", Modifier.padding(bottom = 10.dp), colors.accent)
        when (diagram.direction) {
            MermaidFlowchart.Direction.LR,
            MermaidFlowchart.Direction.RL,
            -> HorizontalFlow(displayedRanks, diagram.direction)

            MermaidFlowchart.Direction.TB,
            MermaidFlowchart.Direction.BT,
            -> VerticalFlow(displayedRanks, diagram.direction)
        }

        if (diagram.edges.isNotEmpty()) {
            Column(
                Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                diagram.edges.forEach { edge ->
                    val arrow = if (edge.style == MermaidFlowchart.EdgeStyle.Line) "—" else "→"
                    val relation = buildString {
                        append(nodeLabels[edge.from] ?: edge.from)
                        append(' ')
                        append(arrow)
                        edge.label?.let { append(" ").append(it).append(" ") } ?: append(' ')
                        append(nodeLabels[edge.to] ?: edge.to)
                    }
                    Text(relation, style = Skald.type.meta, color = colors.tx3)
                }
            }
        }
    }
}

@Composable
private fun HorizontalFlow(
    ranks: List<List<MermaidFlowchart.Node>>,
    direction: MermaidFlowchart.Direction,
) {
    val colors = Skald.colors
    val arrow = if (direction == MermaidFlowchart.Direction.RL) "←" else "→"
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ranks.forEachIndexed { index, nodes ->
            if (index > 0) Text(arrow, style = Skald.type.body, color = colors.accent)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                nodes.forEach { MermaidNode(it) }
            }
        }
    }
}

@Composable
private fun VerticalFlow(
    ranks: List<List<MermaidFlowchart.Node>>,
    direction: MermaidFlowchart.Direction,
) {
    val colors = Skald.colors
    val arrow = if (direction == MermaidFlowchart.Direction.BT) "↑" else "↓"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ranks.forEachIndexed { index, nodes ->
            if (index > 0) {
                Text(
                    arrow,
                    modifier = Modifier.fillMaxWidth(),
                    style = Skald.type.body,
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                nodes.forEach { MermaidNode(it) }
            }
        }
    }
}

@Composable
private fun MermaidNode(node: MermaidFlowchart.Node) {
    val colors = Skald.colors
    Box(
        Modifier
            .widthIn(min = 116.dp, max = 180.dp)
            .background(colors.bg3, RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, colors.accentLine), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            node.label,
            style = Skald.type.body,
            color = colors.tx0,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MermaidSourceFallback(
    source: String,
    message: String = "This Mermaid diagram type is not supported on Android yet.",
) {
    val colors = Skald.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .background(colors.bg1, RoundedCornerShape(Skald.metrics.r3))
            .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(Skald.metrics.r3))
            .padding(12.dp),
    ) {
        Eyebrow("Mermaid · source", Modifier.padding(bottom = 6.dp), colors.tx3)
        Text(message, style = Skald.type.meta, color = colors.tx3)
        Text(source, style = Skald.type.code, color = colors.tx1, modifier = Modifier.padding(top = 9.dp))
    }
}

private const val MAX_RENDERED_NODES = 80
private const val MAX_RENDERED_EDGES = 160

/** Stable topological layers. Cyclic nodes are kept together in a final layer. */
private fun diagramRanks(diagram: MermaidFlowchart.Diagram): List<List<MermaidFlowchart.Node>> {
    val nodesById = diagram.nodes.associateBy { it.id }
    val indegree = diagram.nodes.associate { it.id to 0 }.toMutableMap()
    val outgoing = diagram.edges.groupBy { it.from }
    diagram.edges.forEach { edge ->
        if (edge.from in nodesById && edge.to in nodesById) {
            indegree[edge.to] = (indegree[edge.to] ?: 0) + 1
        }
    }

    val queue = ArrayDeque(diagram.nodes.map { it.id }.filter { indegree[it] == 0 })
    val rank = diagram.nodes.associate { it.id to 0 }.toMutableMap()
    val visited = mutableSetOf<String>()
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (!visited.add(current)) continue
        outgoing[current].orEmpty().forEach { edge ->
            if (edge.to !in nodesById) return@forEach
            rank[edge.to] = max(rank[edge.to] ?: 0, (rank[current] ?: 0) + 1)
            indegree[edge.to] = (indegree[edge.to] ?: 1) - 1
            if (indegree[edge.to] == 0) queue.add(edge.to)
        }
    }

    val acyclic = diagram.nodes.filter { it.id in visited }.groupBy { rank[it.id] ?: 0 }.toSortedMap()
    val result = acyclic.values.map { it.toList() }.toMutableList()
    diagram.nodes.filter { it.id !in visited }.takeIf { it.isNotEmpty() }?.let(result::add)
    return result.ifEmpty { listOf(diagram.nodes) }
}
