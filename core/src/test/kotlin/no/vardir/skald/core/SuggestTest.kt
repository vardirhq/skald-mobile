package no.vardir.skald.core

import no.vardir.skald.core.model.SchemaName
import no.vardir.skald.core.text.Dates
import no.vardir.skald.core.text.Frontmatter
import no.vardir.skald.core.text.Suggest
import no.vardir.skald.core.text.Wikilinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TODAY = "2026-06-01" // a Monday

class DatesTest {

    @Test
    fun `reads the words a person actually types`() {
        assertEquals("2026-06-01", Dates.parse("today", TODAY))
        assertEquals("2026-06-02", Dates.parse("tomorrow", TODAY))
        assertEquals("2026-06-02", Dates.parse("TMR", TODAY))
        assertEquals("2026-06-04", Dates.parse("+3", TODAY))
        assertEquals("2026-06-04", Dates.parse("in 3 days", TODAY))
        assertEquals("2026-06-15", Dates.parse("2w", TODAY))
    }

    @Test
    fun `a weekday means the next one, and next pushes it a week further`() {
        assertEquals("2026-06-05", Dates.parse("friday", TODAY))
        assertEquals("2026-06-12", Dates.parse("next friday", TODAY))
        // Today is Monday, so a bare "monday" is today and "next week" is not.
        assertEquals(TODAY, Dates.parse("mon", TODAY))
        assertEquals("2026-06-08", Dates.parse("next week", TODAY))
    }

    @Test
    fun `a day and month that has passed means next year`() {
        assertEquals("2026-06-24", Dates.parse("24/6", TODAY))
        assertEquals("2027-01-04", Dates.parse("4.1", TODAY))
    }

    @Test
    fun `nonsense is nothing rather than a wrong guess`() {
        assertNull(Dates.parse("someday", TODAY))
        assertNull(Dates.parse("", TODAY))
        assertNull(Dates.parse("2026-13-40", TODAY))
    }

    @Test
    fun `labels read the way a person would say them`() {
        assertEquals("Today", Dates.label(TODAY, TODAY))
        assertEquals("Tomorrow", Dates.label("2026-06-02", TODAY))
        assertEquals("Fri", Dates.label("2026-06-05", TODAY))
        assertEquals("24 Jun", Dates.label("2026-06-24", TODAY))
        assertEquals("4 Jan 2027", Dates.label("2027-01-04", TODAY))
    }

    @Test
    fun `the chips never offer the same day twice`() {
        val saturday = "2026-06-06"
        val choices = Dates.dueChoices(saturday)
        assertEquals(choices.map { it.iso }.distinct(), choices.map { it.iso })
        assertNull(choices.first().iso)
    }
}

class SuggestTriggerTest {

    @Test
    fun `typing the second bracket closes the pair`() {
        val edit = Suggest.autoClose("see [", "see [[", 6)
        assertNotNull(edit)
        assertEquals("see [[]]", edit.text)
        assertEquals(6, edit.start)
    }

    @Test
    fun `a lone bracket is left alone, and a closed pair is not doubled`() {
        assertNull(Suggest.autoClose("see ", "see [", 5))
        assertNull(Suggest.autoClose("see []]", "see [[]]", 6))
    }

    @Test
    fun `an open wikilink is a trigger, a closed one is not`() {
        val open = Suggest.triggerAt("see [[road", 10)
        assertNotNull(open)
        assertEquals(Suggest.Kind.Wikilink, open.kind)
        assertEquals("road", open.query)
        assertEquals(6, open.start)
        assertTrue(!open.closed)

        val closed = Suggest.triggerAt("see [[road]]", 10)
        assertNotNull(closed)
        assertTrue(closed.closed)

        assertNull(Suggest.triggerAt("see [[road]] and more", 21))
    }

    @Test
    fun `a tag triggers, a heading does not`() {
        val tag = Suggest.triggerAt("- [ ] thing #edi", 16)
        assertNotNull(tag)
        assertEquals(Suggest.Kind.Tag, tag.kind)
        assertEquals("edi", tag.query)

        assertNull(Suggest.triggerAt("#", 1))
        assertNull(Suggest.triggerAt("# Heading", 9))
    }

    @Test
    fun `an at sign offers the tokens, and its parentheses offer their values`() {
        val token = Suggest.triggerAt("- [ ] thing @du", 15)
        assertNotNull(token)
        assertEquals(Suggest.Kind.Token, token.kind)

        val due = Suggest.triggerAt("- [ ] thing @due(", 17)
        assertNotNull(due)
        assertEquals(Suggest.Kind.Due, due.kind)

        val priority = Suggest.triggerAt("- [ ] thing @p(hi", 17)
        assertNotNull(priority)
        assertEquals(Suggest.Kind.Priority, priority.kind)
        assertEquals("hi", priority.query)
    }

    @Test
    fun `a trigger belongs to the line the caret is on`() {
        val text = "[[Older]]\nnow [[ro"
        val trigger = Suggest.triggerAt(text, text.length)
        assertNotNull(trigger)
        assertEquals("ro", trigger.query)
        assertEquals(text.length - 2, trigger.start)
    }
}

class SuggestCandidateTest {

    // Two notes with the same file name *and* the same title: the case that
    // forces a link to carry its folder.
    private val vault = listOf(
        Suggest.NoteRef("Projects/Roadmap.md", "Roadmap", SchemaName.Project, updated = 30),
        Suggest.NoteRef("Archive/Roadmap.md", "Roadmap", SchemaName.Project, updated = 10),
        Suggest.NoteRef("Ideas/Runes.md", "Runes", SchemaName.Idea, updated = 20),
    )

    private val vocab = Suggest.Vocabulary(
        notes = vault,
        tags = listOf("editor", "design"),
        todayIso = TODAY,
        index = Wikilinks.buildIndex(vault.map { Wikilinks.Linkable(it.path, it.title) }),
    )

    @Test
    fun `a link offers the shortest target that still resolves`() {
        val trigger = Suggest.triggerAt("[[runes", 7)!!
        val hit = Suggest.candidates(trigger, vocab).first()
        assertEquals("Runes", hit.insert)
        assertEquals("Ideas/Runes.md", hit.detail)
    }

    @Test
    fun `the note a bare name cannot reach is offered folder-qualified`() {
        val trigger = Suggest.triggerAt("[[roadmap", 9)!!
        val inserts = Suggest.candidates(trigger, vocab).map { it.insert }
        // The bare name resolves to exactly one of them; the other has to say
        // which folder it is in, or the link would land on its namesake.
        assertEquals(listOf("Roadmap", "Projects/Roadmap"), inserts)
        assertEquals("Archive/Roadmap.md", vocab.index!!.resolve("Roadmap"))
    }

    @Test
    fun `an empty query offers the notes touched most recently`() {
        val trigger = Suggest.triggerAt("[[", 2)!!
        assertEquals("Projects/Roadmap.md", Suggest.candidates(trigger, vocab).first().detail)
    }

    @Test
    fun `a path matches as readily as a title`() {
        val trigger = Suggest.triggerAt("[[ideas/ru", 10)!!
        assertEquals("Runes", Suggest.candidates(trigger, vocab).first().insert)
    }

    @Test
    fun `a tag nobody has used yet is still offered`() {
        val trigger = Suggest.triggerAt("thing #saga", 11)!!
        val hits = Suggest.candidates(trigger, vocab)
        assertEquals("saga", hits.first().insert)
        assertEquals("new tag", hits.first().detail)
    }

    @Test
    fun `the due token opens its own value picker`() {
        val trigger = Suggest.triggerAt("- [ ] thing @d", 14)!!
        val due = Suggest.candidates(trigger, vocab).first()
        assertEquals("due()", due.insert)

        val line = "- [ ] thing @d"
        val edit = Suggest.accept(line, trigger, due)
        assertEquals("- [ ] thing @due()", edit.text)
        assertEquals(17, edit.start)

        // …and the caret now sits where the dates are offered.
        val inside = Suggest.triggerAt(edit.text, edit.start)
        assertNotNull(inside)
        assertEquals(Suggest.Kind.Due, inside.kind)
    }

    @Test
    fun `a typed date is offered before the chips`() {
        val trigger = Suggest.triggerAt("- [ ] thing @due(fri", 20)!!
        val first = Suggest.candidates(trigger, vocab).first()
        assertEquals("2026-06-05", first.insert)
        assertEquals("Fri", first.label)
    }
}

class SuggestAcceptTest {

    private val trigger = { text: String -> Suggest.triggerAt(text, text.length)!! }

    @Test
    fun `accepting a link writes the brackets it still needs`() {
        val text = "see [[road"
        val edit = Suggest.accept(text, trigger(text), Suggest.Candidate("Projects/Roadmap", "Roadmap"))
        assertEquals("see [[Projects/Roadmap]]", edit.text)
        assertEquals(edit.text.length, edit.start)
    }

    @Test
    fun `an already-closed link is stepped over rather than closed twice`() {
        val text = "see [[road]]"
        val at = text.indexOf("]]")
        val edit = Suggest.accept(text, Suggest.triggerAt(text, at)!!, Suggest.Candidate("Roadmap", "Roadmap"))
        assertEquals("see [[Roadmap]]", edit.text)
        assertEquals(edit.text.length, edit.start)
    }

    @Test
    fun `a tag lands with a space after it`() {
        val text = "- [ ] thing #edi"
        val edit = Suggest.accept(text, trigger(text), Suggest.Candidate("editor", "#editor"))
        assertEquals("- [ ] thing #editor ", edit.text)
    }

    @Test
    fun `a due value closes its parenthesis`() {
        val text = "- [ ] thing @due("
        val edit = Suggest.accept(text, trigger(text), Suggest.Candidate("2026-06-02", "Tomorrow"))
        assertEquals("- [ ] thing @due(2026-06-02)", edit.text)
    }

    @Test
    fun `a value typed over is replaced, not appended to`() {
        val text = "- [ ] thing @due(fri)"
        val at = text.indexOf(')')
        val edit = Suggest.accept(text, Suggest.triggerAt(text, at)!!, Suggest.Candidate("2026-06-05", "Fri"))
        assertEquals("- [ ] thing @due(2026-06-05)", edit.text)
    }
}

class FrontmatterEditTest {

    @Test
    fun `a field is set without touching the body`() {
        val raw = "---\ntitle: Roadmap\ntags: [design]\n---\n\nBody stays.\n"
        val out = Frontmatter.apply(raw, mapOf("schema" to "Project"))
        val parsed = Frontmatter.parse(out)
        assertEquals("Project", parsed.frontmatter["schema"])
        assertEquals("Roadmap", parsed.frontmatter["title"])
        assertEquals("Body stays.", parsed.body.trim())
    }

    @Test
    fun `a note with no frontmatter gains a block`() {
        val out = Frontmatter.apply("# Just a heading\n", mapOf("schema" to "Idea"))
        assertTrue(out.startsWith("---\nschema: Idea\n---\n"), out)
        assertEquals("# Just a heading", Frontmatter.parse(out).body.trim())
    }

    @Test
    fun `removing the last field removes the block itself`() {
        val out = Frontmatter.apply("---\nschema: Idea\n---\n\nBody.\n", remove = setOf("schema"))
        assertEquals("Body.", out.trim())
    }

    @Test
    fun `the keys a note is read by come first`() {
        val raw = "---\nauthor: Ada\n---\n\nBody.\n"
        val out = Frontmatter.apply(raw, mapOf("schema" to "Note", "title" to "A"))
        assertEquals(listOf("title", "schema", "author"), Frontmatter.parse(out).frontmatter.keys.toList())
    }

    @Test
    fun `an existing key keeps its place`() {
        val raw = "---\nauthor: Ada\ntitle: A\n---\n\nBody.\n"
        val out = Frontmatter.apply(raw, mapOf("author" to "Björn"))
        assertEquals(listOf("title", "author"), Frontmatter.parse(out).frontmatter.keys.toList())
        assertEquals("Björn", Frontmatter.parse(out).frontmatter["author"])
    }
}
