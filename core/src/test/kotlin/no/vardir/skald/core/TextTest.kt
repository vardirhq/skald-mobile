package no.vardir.skald.core

import no.vardir.skald.core.model.SchemaName
import no.vardir.skald.core.model.TaskPriority
import no.vardir.skald.core.model.TaskStatus
import no.vardir.skald.core.text.Attachments
import no.vardir.skald.core.text.Frontmatter
import no.vardir.skald.core.text.Fuzzy
import no.vardir.skald.core.text.Markdown
import no.vardir.skald.core.text.Notes
import no.vardir.skald.core.text.Tasks
import no.vardir.skald.core.text.Wikilinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrontmatterTest {

    @Test
    fun `reads scalars, inline arrays and block lists`() {
        val raw = """
            ---
            title: Skald design rationale
            schema: Note
            tags: [design, vision]
            people:
              - Ada
              - Björn
            pinned: true
            count: 12
            ---

            Body starts here.
        """.trimIndent()

        val parsed = Frontmatter.parse(raw)
        assertTrue(parsed.hasFrontmatter)
        assertEquals("Skald design rationale", parsed.frontmatter["title"])
        assertEquals(listOf("design", "vision"), parsed.frontmatter["tags"])
        assertEquals(listOf("Ada", "Björn"), parsed.frontmatter["people"])
        assertEquals(true, parsed.frontmatter["pinned"])
        assertEquals(12L, parsed.frontmatter["count"])
        assertEquals("Body starts here.", parsed.body.trim())
    }

    @Test
    fun `a note without frontmatter is all body`() {
        val parsed = Frontmatter.parse("# Just a title\n\ntext")
        assertFalse(parsed.hasFrontmatter)
        assertEquals(0, parsed.bodyStartLine)
        assertEquals("# Just a title\n\ntext", parsed.body)
    }

    @Test
    fun `body line offset points at the first body line`() {
        val raw = "---\ntitle: A\n---\n\nline one\n"
        val parsed = Frontmatter.parse(raw)
        // Lines 1..3 are the fence; the body starts on line 4 (0-based 3).
        assertEquals(3, parsed.bodyStartLine)
        assertEquals("line one", raw.split("\n")[parsed.bodyStartLine + 1])
    }

    @Test
    fun `a date value is not coerced to a number`() {
        val parsed = Frontmatter.parse("---\ncreated: 2026-05-12\n---\nx")
        assertEquals("2026-05-12", parsed.frontmatter["created"])
    }

    @Test
    fun `serialize round-trips through parse`() {
        val fm = linkedMapOf<String, Any?>("title" to "On runes", "schema" to "Idea", "tags" to listOf("norse", "type"))
        val raw = Frontmatter.serialize(fm, "The body.\n")
        val parsed = Frontmatter.parse(raw)
        assertEquals("On runes", parsed.frontmatter["title"])
        assertEquals(listOf("norse", "type"), parsed.frontmatter["tags"])
        assertEquals("The body.", parsed.body.trim())
    }

    @Test
    fun `a value needing quotes survives the round trip`() {
        val fm = linkedMapOf<String, Any?>("title" to "Stack: decisions, 2026")
        val parsed = Frontmatter.parse(Frontmatter.serialize(fm, "x"))
        assertEquals("Stack: decisions, 2026", parsed.frontmatter["title"])
    }
}

class TasksTest {

    @Test
    fun `extracts status, priority, due and tags`() {
        val body = """
            - [ ] Wire Yjs awareness @due(2026-5-3) @p(high) #sync #editor
            - [x] Refactor the watcher
            - [ ] Migrate auth @status(blocked)
            - [ ] Rebuild the graph @status(working) @p(low)
            not a task
        """.trimIndent()

        val tasks = Tasks.extract(body)
        assertEquals(4, tasks.size)

        assertEquals("Wire Yjs awareness", tasks[0].content)
        assertEquals(TaskStatus.Open, tasks[0].status)
        assertEquals(TaskPriority.High, tasks[0].priority)
        assertEquals("2026-05-03", tasks[0].due)
        assertEquals(listOf("sync", "editor"), tasks[0].tags)

        assertEquals(TaskStatus.Done, tasks[1].status)
        assertEquals(TaskStatus.Blocked, tasks[2].status)
        assertEquals(TaskStatus.Working, tasks[3].status)
        assertEquals(TaskPriority.Low, tasks[3].priority)
    }

    @Test
    fun `a checked box is done even when status says otherwise`() {
        val tasks = Tasks.extract("- [x] Done @status(working)")
        assertEquals(TaskStatus.Done, tasks[0].status)
    }

    @Test
    fun `line numbers carry the body offset`() {
        val tasks = Tasks.extract("intro\n- [ ] a thing", lineOffset = 4)
        assertEquals(6, tasks[0].line)
    }

    @Test
    fun `updating a line preserves untouched metadata and indentation`() {
        val raw = "intro\n  - [ ] Ship it @due(2026-06-01) @p(high) #release\ntail"
        val updated = Tasks.updateLine(raw, 2, Tasks.Edits(status = TaskStatus.Done))
        assertEquals("  - [x] Ship it @due(2026-06-01) @p(high) #release", updated.split("\n")[1])
        assertEquals("tail", updated.split("\n")[2])

        val reparsed = Tasks.extract(updated).first()
        assertEquals(TaskStatus.Done, reparsed.status)
        assertEquals("2026-06-01", reparsed.due)
        assertEquals(listOf("release"), reparsed.tags)
    }

    @Test
    fun `clearing a due date removes the token`() {
        val raw = "- [ ] Ship it @due(2026-06-01)"
        val updated = Tasks.updateLine(raw, 1, Tasks.Edits(due = Tasks.DueEdit.Clear))
        assertEquals("- [ ] Ship it", updated)
    }

    @Test
    fun `a stale line number leaves the note untouched`() {
        val raw = "# Heading\n\nnot a task"
        assertEquals(raw, Tasks.updateLine(raw, 3, Tasks.Edits(status = TaskStatus.Done)))
        assertEquals(raw, Tasks.updateLine(raw, 99, Tasks.Edits(status = TaskStatus.Done)))
    }

    @Test
    fun `working status round-trips through a rewrite`() {
        val line = Tasks.formatLine("Draft the schema", TaskStatus.Working, "2026-06-02", TaskPriority.High, listOf("schema"))
        val parsed = Tasks.extract(line).first()
        assertEquals(TaskStatus.Working, parsed.status)
        assertEquals(TaskPriority.High, parsed.priority)
        assertEquals("2026-06-02", parsed.due)
        assertEquals("Draft the schema", parsed.content)
    }

    @Test
    fun `tags are edited like every other piece of metadata`() {
        val raw = "- [ ] Ship it @due(2026-06-01) #release"
        val retagged = Tasks.updateLine(raw, 1, Tasks.Edits(tags = listOf("release", "editor")))
        assertEquals("- [ ] Ship it @due(2026-06-01) #release #editor", retagged)

        val cleared = Tasks.updateLine(raw, 1, Tasks.Edits(tags = emptyList()))
        assertEquals("- [ ] Ship it @due(2026-06-01)", cleared)

        // Left alone, they survive an edit to something else entirely.
        val renamed = Tasks.updateLine(raw, 1, Tasks.Edits(content = "Ship it properly"))
        assertEquals(listOf("release"), Tasks.extract(renamed).first().tags)
    }

    @Test
    fun `one line parses on its own, for a sheet that edits one thread`() {
        val task = Tasks.parseLine("  - [ ] Ship it @p(high) #release")
        assertNotNull(task)
        assertEquals(TaskPriority.High, task.priority)
        assertEquals("Ship it", task.content)
        assertNull(Tasks.parseLine("not a task at all"))
    }
}

class WikilinksTest {

    private val notes = listOf(
        Wikilinks.Linkable("Notes/Why local-first.md", "Why local-first"),
        Wikilinks.Linkable("Projects/Jörmungandr.md", "Jörmungandr API rewrite"),
        Wikilinks.Linkable("Archive/Why local-first.md", "Why local-first (old)"),
    )

    @Test
    fun `parses target, heading and display`() {
        val parts = Wikilinks.parse("Folder/Note#Section|the display")
        assertEquals("Folder/Note", parts.target)
        assertEquals("Section", parts.heading)
        assertEquals("the display", parts.display)
    }

    @Test
    fun `a heading-only link displays both parts`() {
        assertEquals("Note › Section", Wikilinks.parse("Note#Section").display)
    }

    @Test
    fun `targets skip code spans and fences`() {
        val body = """
            A real [[Link]] here.
            `[[Not a link]]`
            ```
            [[Also not]]
            ```
            And [[Link]] again, plus [[Other]].
        """.trimIndent()
        assertEquals(listOf("Link", "Other"), Wikilinks.targets(body))
        assertEquals(3, Wikilinks.count(body))
    }

    @Test
    fun `folder-qualified links beat bare names`() {
        val index = Wikilinks.buildIndex(notes)
        assertEquals("Notes/Why local-first.md", index.resolve("Notes/Why local-first"))
        assertEquals("Archive/Why local-first.md", index.resolve("Archive/Why local-first.md"))
        // A bare stem is ambiguous, and the title tier is more specific than the
        // stem tier — so the note actually titled "Why local-first" wins.
        assertEquals("Notes/Why local-first.md", index.resolve("why local-first"))
    }

    @Test
    fun `a title resolves too`() {
        val index = Wikilinks.buildIndex(notes)
        assertEquals("Projects/Jörmungandr.md", index.resolve("Jörmungandr API rewrite"))
        assertNull(index.resolve("nothing named this"))
    }

    @Test
    fun `renaming keeps heading and display`() {
        val body = "See [[Old name#Part|as written]] and [[Old name]]."
        val renamed = Wikilinks.rename(body, "Old name", "New name")
        assertEquals("See [[New name#Part|as written]] and [[New name]].", renamed)
    }

    @Test
    fun `the shortest target that still resolves is the one worth writing`() {
        val index = Wikilinks.buildIndex(notes)
        // Nothing else answers to it, so the bare name is enough.
        assertEquals("Jörmungandr", Wikilinks.shortestTarget("Projects/Jörmungandr.md", index))
        // Two notes share this file name, so the loser has to name its folder.
        val bare = index.resolve("Why local-first")
        val other = notes.map { it.path }.first { it != bare && it.endsWith("Why local-first.md") }
        assertEquals(other.removeSuffix(".md"), Wikilinks.shortestTarget(other, index))
    }

    @Test
    fun `retarget keeps the shape the author wrote`() {
        assertEquals("Note", Wikilinks.retarget("Old", "Folder/Note.md"))
        assertEquals("Folder/Note", Wikilinks.retarget("Dir/Old", "Folder/Note.md"))
        assertEquals("Note.md", Wikilinks.retarget("Old.md", "Folder/Note.md"))
        assertEquals("/Note", Wikilinks.retarget("/Old", "Folder/Note.md"))
    }

    @Test
    fun `snippet centres on the mention`() {
        val body = "padding ".repeat(30) + "[[Target]] tail"
        val snippet = Wikilinks.snippetAround(body, "Target", radius = 20)
        assertTrue(snippet.contains("[[Target]]"))
        assertTrue(snippet.startsWith("…"))
    }
}

class NotesTest {

    @Test
    fun `schema comes from frontmatter first`() {
        assertEquals(SchemaName.Idea, Notes.inferSchema(mapOf("schema" to "idea"), "Anything", "Projects"))
    }

    @Test
    fun `a date-shaped title is a daily`() {
        assertEquals(SchemaName.Daily, Notes.inferSchema(emptyMap(), "2026-05-28", "Anywhere"))
    }

    @Test
    fun `the folder decides when nothing else does`() {
        assertEquals(SchemaName.Project, Notes.inferSchema(emptyMap(), "Jörmungandr", "Projects"))
        assertEquals(SchemaName.Person, Notes.inferSchema(emptyMap(), "Ada", "Folk"))
        assertEquals(SchemaName.Note, Notes.inferSchema(emptyMap(), "Loose", "Whatever"))
    }

    @Test
    fun `headings skip fenced code`() {
        val body = "# One\n```\n# Not a heading\n```\n## Two"
        val headings = Notes.headings(body)
        assertEquals(listOf("One", "Two"), headings.map { it.text })
        assertEquals(listOf(1, 2), headings.map { it.level })
    }

    @Test
    fun `excerpt strips markup and wikilink pipes`() {
        val excerpt = Notes.excerpt("# Title\n\nSome **bold** and [[Target|shown]] text.")
        assertEquals("Some bold and shown text.", excerpt)
    }

    @Test
    fun `safe file name drops path separators`() {
        assertEquals("Notes and things", Notes.safeFileName("Notes/and\\things"))
    }
}

class FuzzyTest {

    @Test
    fun `a substring beats a scattered match`() {
        val direct = Fuzzy.match("skald", "Skald design rationale")
        val scattered = Fuzzy.match("skald", "Some kind of alderman")
        assertNotNull(direct)
        assertNotNull(scattered)
        assertTrue(direct.score > scattered.score)
    }

    @Test
    fun `a missing character is no match`() {
        assertNull(Fuzzy.match("zzz", "Skald"))
    }

    @Test
    fun `highlight segments cover the whole string`() {
        val hit = Fuzzy.match("des", "Skald design")!!
        val segments = Fuzzy.highlight("Skald design", hit.indices)
        assertEquals("Skald design", segments.joinToString("") { it.text })
        assertEquals("des", segments.filter { it.hit }.joinToString("") { it.text })
    }
}

class MarkdownTest {

    @Test
    fun `parses the block kinds the editor renders`() {
        val body = """
            # Title

            A paragraph with **bold**, `code` and a [[Wikilink]].

            ## Section

            > [!Premise] The graph is not the point.

            > A plain quote.

            - [ ] An open thread @due(2026-06-01)
            - [x] A finished one

            - a bullet
            - another

            1. first
            2. second

            ```kotlin
            val x = 1
            ```

            ---
        """.trimIndent()

        val blocks = Markdown.parse(body)
        val kinds = blocks.map { it::class.simpleName }
        assertEquals(
            listOf("Heading", "Paragraph", "Heading", "Callout", "Quote", "Tasks", "Bullets", "Numbers", "Code", "Rule"),
            kinds,
        )

        val callout = blocks.filterIsInstance<Markdown.Block.Callout>().single()
        assertEquals("Premise", callout.label)

        val tasks = blocks.filterIsInstance<Markdown.Block.Tasks>().single()
        assertEquals(2, tasks.items.size)
        assertEquals(TaskStatus.Done, tasks.items[1].status)
        assertEquals("2026-06-01", tasks.items[0].due)

        val code = blocks.filterIsInstance<Markdown.Block.Code>().single()
        assertEquals("kotlin", code.lang)
        assertEquals("val x = 1", code.text)
    }

    @Test
    fun `task line numbers point at the raw file`() {
        // Body starts at raw line 5 (0-based offset 4).
        val blocks = Markdown.parse("intro\n\n- [ ] a thread", lineOffset = 4)
        val tasks = blocks.filterIsInstance<Markdown.Block.Tasks>().single()
        assertEquals(7, tasks.items.single().line)
    }

    @Test
    fun `inline parsing keeps text around tokens`() {
        val inlines = Markdown.inline("before **bold** after")
        assertEquals("before bold after", Markdown.plainText(inlines))
        assertTrue(inlines.any { it is Markdown.Inline.Strong })
    }

    @Test
    fun `tables retain cells formatting and alignment`() {
        val table = Markdown.parse(
            "| Name | State | Notes |\n| :--- | :---: | ---: |\n| **Skald** | ready | `local|first` |"
        ).filterIsInstance<Markdown.Block.Table>().single()

        assertEquals(listOf("Name", "State", "Notes"), table.headers.map(Markdown::plainText))
        assertEquals(
            listOf(Markdown.TableAlignment.Left, Markdown.TableAlignment.Center, Markdown.TableAlignment.Right),
            table.alignments,
        )
        assertTrue(table.rows.single().first().single() is Markdown.Inline.Strong)
        assertEquals("local|first", Markdown.plainText(table.rows.single().last()))
    }

    @Test
    fun `an image is distinguished from a link`() {
        val inlines = Markdown.inline("![alt](map.png) and [label](file.pdf)")
        assertTrue(inlines.any { it is Markdown.Inline.Image })
        assertTrue(inlines.any { it is Markdown.Inline.Link })
    }
}

class AttachmentsTest {

    @Test
    fun `resolves relative to the note`() {
        assertEquals(
            "Projects/Attachments/map.png",
            Attachments.resolvePath("Projects/Jörmungandr.md", "Attachments/map.png"),
        )
        assertEquals(
            "Attachments/map.png",
            Attachments.resolvePath("Projects/Jörmungandr.md", "../Attachments/map.png"),
        )
    }

    @Test
    fun `refuses paths that escape the vault or hide in dot-skald`() {
        assertNull(Attachments.resolvePath("Note.md", "../outside.png"))
        assertNull(Attachments.resolvePath("Note.md", ".skald/history/x.md"))
    }

    @Test
    fun `external targets are not attachments`() {
        assertTrue(Attachments.isExternal("https://example.com/a.png"))
        assertTrue(Attachments.isExternal("#heading"))
        assertFalse(Attachments.isExternal("Attachments/a.png"))
        assertEquals(1, Attachments.links("[a](local.png) [b](https://x/y.png)").size)
    }

    @Test
    fun `written markdown round-trips back to the same path`() {
        val markdown = Attachments.markdownFor(
            notePath = "Projects/Jörmungandr.md",
            attachmentPath = "Attachments/a map.png",
            displayName = "a map.png",
            kind = no.vardir.skald.core.model.AttachmentKind.Image,
        )
        val link = Attachments.links(markdown).single()
        assertTrue(link.embedded)
        assertEquals("Attachments/a map.png", Attachments.resolvePath("Projects/Jörmungandr.md", link.target))
    }
}
