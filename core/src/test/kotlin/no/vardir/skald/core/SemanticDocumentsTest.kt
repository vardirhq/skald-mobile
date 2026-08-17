package no.vardir.skald.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import no.vardir.skald.core.text.Insertions
import no.vardir.skald.core.text.Markdown

class SemanticDocumentsTest {
    @Test
    fun `aside keeps ordinary blocks as children`() {
        val blocks = Markdown.parse(
            """:::aside
## Context

This matters.

- [ ] Follow up
:::
"""
        )
        val aside = assertIs<Markdown.Block.Container>(blocks.single())
        assertEquals(Markdown.ContainerKind.Aside, aside.kind)
        assertEquals(1, aside.startLine)
        assertEquals(7, aside.endLine)
        assertIs<Markdown.Block.Heading>(aside.children[0])
        assertIs<Markdown.Block.Paragraph>(aside.children[1])
        val tasks = assertIs<Markdown.Block.Tasks>(aside.children[2])
        assertEquals(6, tasks.items.single().line)
    }

    @Test
    fun `gallery supports media blocks`() {
        val gallery = assertIs<Markdown.Block.Container>(
            Markdown.parse(":::gallery\n\n![](a.jpg)\n\n![](b.jpg)\n\n:::").single()
        )
        assertEquals(Markdown.ContainerKind.Gallery, gallery.kind)
        assertEquals(2, gallery.children.size)
        assertTrue(gallery.children.all { it is Markdown.Block.Paragraph })
    }

    @Test
    fun `code fence markers do not close semantic container`() {
        val aside = assertIs<Markdown.Block.Container>(
            Markdown.parse(":::aside\n```text\n:::\n```\nAfter code\n:::").single()
        )
        assertEquals(2, aside.children.size)
        assertEquals(":::", assertIs<Markdown.Block.Code>(aside.children[0]).text)
    }

    @Test
    fun `nested containers are not interpreted in v1`() {
        val outer = assertIs<Markdown.Block.Container>(
            Markdown.parse(":::group\n:::aside\nNested\n:::\n:::").single()
        )
        assertTrue(outer.children.none { it is Markdown.Block.Container })
    }

    @Test
    fun `unclosed container remains readable markdown`() {
        val blocks = Markdown.parse(":::aside\nStill readable")
        assertTrue(blocks.none { it is Markdown.Block.Container })
        val text = blocks.filterIsInstance<Markdown.Block.Paragraph>()
            .joinToString(" ") { Markdown.plainText(it.content) }
        assertTrue(":::aside" in text)
        assertTrue("Still readable" in text)
    }

    @Test
    fun `semantic insertion wraps selection without rewriting it`() {
        val source = "Before\n\n## Selected\n\n- one\n- two\n\nAfter"
        val selected = "## Selected\n\n- one\n- two"
        val start = source.indexOf(selected)
        val edit = Insertions.apply(
            source,
            start,
            start + selected.length,
            Insertions.semanticContainer("aside", "Supporting context"),
        )
        assertTrue(":::aside\n\n$selected\n\n:::" in edit.text)
    }
}
