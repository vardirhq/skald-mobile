package no.vardir.skald.core

import no.vardir.skald.core.model.NoteMeta
import no.vardir.skald.core.model.SchemaName
import no.vardir.skald.core.text.Search
import no.vardir.skald.core.text.Tags
import no.vardir.skald.core.text.Templates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTagsTemplatesTest {
    private val notes = listOf(
        NoteMeta(
            path = "Projects/Skald.md",
            title = "Skald",
            folder = "Projects",
            schema = SchemaName.Project,
            tags = listOf("android", "search"),
            body = "First line\nThe mobile vault contains the whole Markdown body.",
            bodyStartLine = 5,
            updated = 100,
        ),
        NoteMeta(
            path = "People/Chris.md",
            title = "Chris",
            folder = "People",
            schema = SchemaName.Person,
            tags = listOf("android"),
            body = "Maintains Skald.",
            updated = 50,
        ),
    )

    @Test
    fun `full text search returns body snippet and raw line`() {
        val hit = Search.find(notes, "whole Markdown", now = 100).single()
        assertEquals("Projects/Skald.md", hit.path)
        assertEquals(6, hit.line)
        assertTrue(hit.snippet.contains("whole Markdown"))
    }

    @Test
    fun `structured filters compose`() {
        assertEquals(
            listOf("Projects/Skald.md"),
            Search.find(notes, "schema:project tag:android folder:Projects", now = 100).map { it.path },
        )
        assertTrue(Search.find(notes, "folder:Missing", now = 100).isEmpty())
    }

    @Test
    fun `inline tags ignore code and merge case insensitively`() {
        val inline = Tags.extract("#Visible `#hidden`\n```kt\n#alsoHidden\n```\n#nested/tag")
        assertEquals(listOf("Visible", "nested/tag"), inline)
        assertEquals(listOf("Visible", "other"), Tags.merge(inline, listOf("visible", "other")))
    }

    @Test
    fun `schema placeholders are deterministic`() {
        assertEquals("# A title\n\n2026-08-11", Templates.render("# {{title}}\n\n{{date}}", "A title", "2026-08-11"))
    }
}
