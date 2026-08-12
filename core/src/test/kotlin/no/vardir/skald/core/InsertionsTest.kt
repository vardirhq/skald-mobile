package no.vardir.skald.core

import no.vardir.skald.core.text.Insertions
import kotlin.test.Test
import kotlin.test.assertEquals

class InsertionsTest {
    @Test
    fun `selection replaces the editable placeholder`() {
        val edit = Insertions.apply(
            "Make this clear",
            5,
            9,
            Insertions.Template("**bold text**", "bold text", block = false),
        )
        assertEquals("Make **this** clear", edit.text)
        assertEquals(edit.start, edit.end)
    }

    @Test
    fun `empty insertion selects its placeholder`() {
        val edit = Insertions.apply("", 0, 0, Insertions.Template("## Heading", "Heading"))
        assertEquals("## Heading\n", edit.text)
        assertEquals(3, edit.start)
        assertEquals(10, edit.end)
    }

    @Test
    fun `block insertion is separated from prose`() {
        val edit = Insertions.apply("BeforeAfter", 6, 6, Insertions.Template("- [ ] Task", "Task"))
        assertEquals("Before\n\n- [ ] Task\n\nAfter", edit.text)
        assertEquals(14, edit.start)
        assertEquals(18, edit.end)
    }
}
