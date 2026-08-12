package no.vardir.skald.core.text

/**
 * The portable Mermaid subset Android renders natively.
 *
 * Flowcharts are the starter inserted by both Skald clients and the dominant
 * phone use case. Unknown diagram families remain readable fenced source
 * rather than being uploaded to a third-party rendering service.
 */
object MermaidFlowchart {
    enum class Direction { TB, BT, LR, RL }
    enum class EdgeStyle { Arrow, Line, Dotted, Thick }

    data class Node(val id: String, val label: String)
    data class Edge(val from: String, val to: String, val label: String?, val style: EdgeStyle)
    data class Diagram(val direction: Direction, val nodes: List<Node>, val edges: List<Edge>)

    private val HEADER = Regex("""^(?:flowchart|graph)\s+(TB|TD|BT|LR|RL)\s*$""", RegexOption.IGNORE_CASE)
    private val NODE = Regex("""^([A-Za-z_][A-Za-z0-9_-]*)(?:\[([^]]+)])?(?:\(([^)]+)\))?(?:\{([^}]+)})?$""")
    private val EDGE = Regex(
        """^(.+?)\s*(-->|---|-.->|==>)\s*(?:\|([^|]+)\|\s*)?(.+?)$"""
    )

    fun parse(source: String): Diagram? {
        val statements = source.lines()
            .flatMap { it.substringBefore("%%").split(';') }
            .map(String::trim)
            .filter(String::isNotEmpty)
        val header = statements.firstOrNull()?.let(HEADER::matchEntire) ?: return null
        val direction = when (header.groupValues[1].uppercase()) {
            "TD", "TB" -> Direction.TB
            "BT" -> Direction.BT
            "LR" -> Direction.LR
            "RL" -> Direction.RL
            else -> return null
        }
        val nodes = linkedMapOf<String, Node>()
        val edges = mutableListOf<Edge>()

        fun remember(raw: String): Node? {
            val match = NODE.matchEntire(raw.trim()) ?: return null
            val id = match.groupValues[1]
            val label = match.groupValues.drop(2).firstOrNull(String::isNotEmpty)
                ?.trim()?.removeSurrounding("\"") ?: id
            return Node(id, label).also { candidate ->
                val previous = nodes[id]
                nodes[id] = if (previous == null || previous.label == id) candidate else previous
            }
        }

        for (statement in statements.drop(1)) {
            val edge = EDGE.matchEntire(statement)
            if (edge == null) {
                remember(statement) ?: continue
                continue
            }
            val from = remember(edge.groupValues[1]) ?: continue
            val to = remember(edge.groupValues[4]) ?: continue
            edges += Edge(
                from = from.id,
                to = to.id,
                label = edge.groupValues[3].trim().ifEmpty { null },
                style = when (edge.groupValues[2]) {
                    "---" -> EdgeStyle.Line
                    "-.->" -> EdgeStyle.Dotted
                    "==>" -> EdgeStyle.Thick
                    else -> EdgeStyle.Arrow
                },
            )
        }
        if (nodes.isEmpty()) return null
        return Diagram(direction, nodes.values.toList(), edges)
    }
}
