package no.vardir.skald.core

import no.vardir.skald.core.text.Formatting
import no.vardir.skald.core.text.LiveMarkdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveMarkdownTest {

    private fun kinds(body: String) = LiveMarkdown.split(body).map { it.kind }

    @Test
    fun `an empty body is one blank block to write into`() {
        val blocks = LiveMarkdown.split("")
        assertEquals(1, blocks.size)
        assertEquals(LiveMarkdown.Kind.Blank, blocks[0].kind)
    }

    @Test
    fun `each kind of block is cut out whole`() {
        val body = """
            # Title

            A paragraph that
            wraps onto two lines.

            - one
            - two

            - [ ] a thread
            - [x] a done one

            > quoted

            ```kt
            val x = 1
            ```

            ---
        """.trimIndent()

        assertEquals(
            listOf(
                LiveMarkdown.Kind.Heading,
                LiveMarkdown.Kind.Blank,
                LiveMarkdown.Kind.Paragraph,
                LiveMarkdown.Kind.Blank,
                LiveMarkdown.Kind.List,
                LiveMarkdown.Kind.Blank,
                LiveMarkdown.Kind.Task,
                LiveMarkdown.Kind.Blank,
                LiveMarkdown.Kind.Quote,
                LiveMarkdown.Kind.Blank,
                LiveMarkdown.Kind.Code,
                LiveMarkdown.Kind.Blank,
                LiveMarkdown.Kind.Rule,
            ),
            kinds(body),
        )
    }

    @Test
    fun `a fenced block keeps its Markdown-looking lines`() {
        val body = "```\n# not a heading\n- not a list\n```"
        val blocks = LiveMarkdown.split(body)
        assertEquals(1, blocks.size)
        assertEquals(body, blocks[0].raw)
    }

    @Test
    fun `a block knows the lines it came from`() {
        val body = "one\n\ntwo\nthree"
        val blocks = LiveMarkdown.split(body)
        assertEquals(2 to 3, blocks[2].startLine to blocks[2].endLine)
        assertEquals("two\nthree", blocks[2].raw)
    }

    @Test
    fun `a line and column survive a round trip`() {
        val raw = "alpha\nbeta\ngamma"
        val position = LiveMarkdown.positionAt(raw, 8)
        assertEquals(LiveMarkdown.Position(1, 2), position)
        assertEquals(8, LiveMarkdown.offsetAt(raw, position.line, position.col))
    }

    @Test
    fun `a column past the end of its line lands at the end of it`() {
        assertEquals(5, LiveMarkdown.offsetAt("alpha\nbeta", 0, 99))
    }

    @Test
    fun `enter in a paragraph ends the block and opens the next`() {
        val edit = LiveMarkdown.enter(LiveMarkdown.Kind.Paragraph, "one two", 3)
        assertEquals("one\n\n two", edit.raw)
        assertEquals(5, edit.caret)
    }

    @Test
    fun `enter in a list opens the next item`() {
        val edit = LiveMarkdown.enter(LiveMarkdown.Kind.List, "- one", 5)
        assertEquals("- one\n- ", edit.raw)
        assertEquals(8, edit.caret)
    }

    @Test
    fun `enter in a numbered list counts on`() {
        val edit = LiveMarkdown.enter(LiveMarkdown.Kind.List, "1. one\n2. two", 13)
        assertEquals("1. one\n2. two\n3. ", edit.raw)
    }

    @Test
    fun `enter after a ticked thread opens an empty box`() {
        val edit = LiveMarkdown.enter(LiveMarkdown.Kind.Task, "- [x] done", 10)
        assertEquals("- [x] done\n- [ ] ", edit.raw)
    }

    @Test
    fun `enter on an empty item leaves the list`() {
        val edit = LiveMarkdown.enter(LiveMarkdown.Kind.List, "- one\n- ", 8)
        assertEquals("- one\n\n", edit.raw)
        assertEquals(7, edit.caret)
    }

    @Test
    fun `enter inside a fence is only a newline`() {
        val edit = LiveMarkdown.enter(LiveMarkdown.Kind.Code, "```\nval x = 1\n```", 13)
        assertEquals("```\nval x = 1\n\n```", edit.raw)
        assertEquals(14, edit.caret)
    }

    @Test
    fun `enter in a quote keeps quoting`() {
        val edit = LiveMarkdown.enter(LiveMarkdown.Kind.Quote, "> said the raven", 16)
        assertEquals("> said the raven\n> ", edit.raw)
    }

    @Test
    fun `a soft break is written as two trailing spaces`() {
        val edit = LiveMarkdown.softBreak(LiveMarkdown.Kind.Paragraph, "one two", 3)
        assertEquals("one  \n two", edit.raw)
        assertEquals(6, edit.caret)
    }

    @Test
    fun `a typed newline is recognised by the shape of the edit`() {
        assertEquals(3, LiveMarkdown.insertedNewline("one two", "one\n two", 4))
        assertNull(LiveMarkdown.insertedNewline("one two", "one three", 9))
        assertNull(LiveMarkdown.insertedNewline("one two", "one tXwo", 6))
    }

    @Test
    fun `replacing a block splices its lines back into the body`() {
        val body = "# Title\n\nold text\n\nrest"
        val block = LiveMarkdown.split(body)[2]
        assertEquals("# Title\n\nnew\ntext\n\nrest", LiveMarkdown.replaceBlock(body, block, "new\ntext"))
    }

    @Test
    fun `an emptied block leaves a blank line rather than closing the gap`() {
        val body = "one\n\ntwo"
        val block = LiveMarkdown.split(body)[2]
        assertEquals("one\n\n", LiveMarkdown.replaceBlock(body, block, ""))
    }

    @Test
    fun `a body goes back under the frontmatter it was cut from`() {
        val content = "---\ntitle: One\n---\nold body"
        assertEquals("---\ntitle: One\n---\nnew body", LiveMarkdown.replaceBody(content, 3, "new body"))
    }

    @Test
    fun `a body without frontmatter is the whole file`() {
        assertEquals("new body", LiveMarkdown.replaceBody("old body", 0, "new body"))
    }

    @Test
    fun `backspace at the top of a block closes the gap above it`() {
        val body = "one\n\ntwo"
        val blocks = LiveMarkdown.split(body)
        val (joined, caret) = LiveMarkdown.joinWithPrevious(body, blocks, 2)!!
        assertEquals("one\ntwo", joined)
        assertEquals(LiveMarkdown.Position(1, 0), caret)
    }

    @Test
    fun `backspace against an adjacent block folds the two together`() {
        val body = "# Title\nparagraph"
        val blocks = LiveMarkdown.split(body)
        val (joined, caret) = LiveMarkdown.joinWithPrevious(body, blocks, 1)!!
        assertEquals("# Titleparagraph", joined)
        assertEquals(LiveMarkdown.Position(0, 7), caret)
    }

    @Test
    fun `nothing is above the first block`() {
        assertNull(LiveMarkdown.joinWithPrevious("one", LiveMarkdown.split("one"), 0))
    }

    @Test
    fun `a tap maps rendered text back to the source under it`() {
        val raw = "A **bold** word here"
        val offset = LiveMarkdown.sourceOffsetFromRendered(raw, "A bold word")
        assertEquals("A **bold** word", raw.substring(0, offset))
    }

    @Test
    fun `a tap on a list item steps over its bullet`() {
        val raw = "- first\n- second"
        val offset = LiveMarkdown.sourceOffsetFromRendered(raw, "first\nsec")
        assertEquals("- first\n- sec", raw.substring(0, offset))
    }
}

class FormattingTest {

    @Test
    fun `bold wraps a selection and unwraps it again`() {
        val on = Formatting.toggleMark("a word here", 2, 6, Formatting.Mark.Bold)
        assertEquals("a **word** here", on.text)
        assertEquals("word", on.text.substring(on.start, on.end))

        val off = Formatting.toggleMark(on.text, on.start, on.end, Formatting.Mark.Bold)
        assertEquals("a word here", off.text)
        assertEquals("word", off.text.substring(off.start, off.end))
    }

    @Test
    fun `bold with nothing selected leaves the caret between the halves`() {
        val edit = Formatting.toggleMark("a ", 2, 2, Formatting.Mark.Bold)
        assertEquals("a ****", edit.text)
        assertEquals(4, edit.start)
        assertEquals(4, edit.end)
    }

    @Test
    fun `a selection inside the marks unwraps too`() {
        val edit = Formatting.toggleMark("a **word** here", 4, 8, Formatting.Mark.Bold)
        assertEquals("a word here", edit.text)
    }

    @Test
    fun `italics on a bold word does not eat one of its stars`() {
        val edit = Formatting.toggleMark("a **word** here", 4, 8, Formatting.Mark.Italic)
        assertEquals("a ***word*** here", edit.text)
    }

    @Test
    fun `code and strike toggle the same way`() {
        assertEquals("`x`", Formatting.toggleMark("x", 0, 1, Formatting.Mark.Code).text)
        assertEquals("~~x~~", Formatting.toggleMark("x", 0, 1, Formatting.Mark.Strike).text)
    }

    @Test
    fun `a link keeps the selection as its label and points the caret at the url`() {
        val edit = Formatting.link("see Skald now", 4, 9)
        assertEquals("see [Skald]() now", edit.text)
        assertEquals("see [Skald](", edit.text.substring(0, edit.start))
    }

    @Test
    fun `a wikilink wraps the selection and keeps it selected`() {
        val edit = Formatting.wikilink("see Skald", 4, 9)
        assertEquals("see [[Skald]]", edit.text)
        assertEquals("Skald", edit.text.substring(edit.start, edit.end))
    }

    @Test
    fun `bullets go on every line the selection touches, and come off again`() {
        val text = "one\ntwo\nthree"
        val on = Formatting.toggleLine(text, 1, 9, Formatting.LineStyle.Bullet)
        assertEquals("- one\n- two\n- three", on.text)

        val off = Formatting.toggleLine(on.text, on.start, on.end, Formatting.LineStyle.Bullet)
        assertEquals(text, off.text)
    }

    @Test
    fun `numbering counts from one`() {
        val edit = Formatting.toggleLine("one\ntwo\nthree", 0, 13, Formatting.LineStyle.Numbered)
        assertEquals("1. one\n2. two\n3. three", edit.text)
    }

    @Test
    fun `a list kind replaces another rather than stacking on it`() {
        val bullets = "- one\n- two"
        val edit = Formatting.toggleLine(bullets, 0, 11, Formatting.LineStyle.Task)
        assertEquals("- [ ] one\n- [ ] two", edit.text)
    }

    @Test
    fun `a thread goes back to a plain bullet`() {
        val edit = Formatting.toggleLine("- [x] done", 0, 10, Formatting.LineStyle.Bullet)
        assertEquals("- done", edit.text)
    }

    @Test
    fun `quoting is independent of the list marker`() {
        val edit = Formatting.toggleLine("- one", 0, 5, Formatting.LineStyle.Quote)
        assertEquals("> - one", edit.text)
        assertEquals("- one", Formatting.toggleLine(edit.text, 2, 7, Formatting.LineStyle.Quote).text)
    }

    @Test
    fun `the selection keeps its words when a marker goes on`() {
        val edit = Formatting.toggleLine("one\ntwo", 0, 3, Formatting.LineStyle.Bullet)
        assertEquals("one", edit.text.substring(edit.start, edit.end))
    }

    @Test
    fun `heading cycles up to three and then back to prose`() {
        var edit = Formatting.cycleHeading("Title", 2, 2)
        assertEquals("# Title", edit.text)
        edit = Formatting.cycleHeading(edit.text, edit.start, edit.end)
        assertEquals("## Title", edit.text)
        edit = Formatting.cycleHeading(edit.text, edit.start, edit.end)
        assertEquals("### Title", edit.text)
        edit = Formatting.cycleHeading(edit.text, edit.start, edit.end)
        assertEquals("Title", edit.text)
    }

    @Test
    fun `a heading cycles only the line the caret is on`() {
        val edit = Formatting.cycleHeading("one\ntwo", 5, 5)
        assertEquals("one\n# two", edit.text)
    }

    @Test
    fun `a fence wraps the selection and keeps it selected`() {
        val edit = Formatting.fence("val x = 1", 0, 9)
        assertEquals("```\nval x = 1\n```", edit.text)
        assertEquals("val x = 1", edit.text.substring(edit.start, edit.end))
    }

    @Test
    fun `a rule lands on a line of its own`() {
        val edit = Formatting.rule("before", 6, 6)
        assertEquals("before\n\n---\n\n", edit.text)
        assertEquals(edit.text.length, edit.start)
    }
}
