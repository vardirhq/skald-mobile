package no.vardir.skald.core

import no.vardir.skald.core.text.MermaidFlowchart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MermaidFlowchartTest {
    @Test
    fun `parses the portable starter flowchart`() {
        val diagram = MermaidFlowchart.parse(
            """
            flowchart LR
                Idea --> Build
                Build --> Release
            """.trimIndent()
        )!!

        assertEquals(MermaidFlowchart.Direction.LR, diagram.direction)
        assertEquals(listOf("Idea", "Build", "Release"), diagram.nodes.map { it.label })
        assertEquals(2, diagram.edges.size)
    }

    @Test
    fun `keeps node labels edge labels and styles`() {
        val diagram = MermaidFlowchart.parse(
            """
            graph TD
                plan[Write plan] -->|then| build(Build app)
                build -.-> ship{Release}
            """.trimIndent()
        )!!

        assertEquals(listOf("Write plan", "Build app", "Release"), diagram.nodes.map { it.label })
        assertEquals("then", diagram.edges.first().label)
        assertEquals(MermaidFlowchart.EdgeStyle.Dotted, diagram.edges.last().style)
    }

    @Test
    fun `leaves other Mermaid families to the source fallback`() {
        assertNull(MermaidFlowchart.parse("sequenceDiagram\nA->>B: hello"))
    }
}
