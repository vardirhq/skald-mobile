package no.vardir.skald.ui

import no.vardir.skald.core.model.NoteMeta
import no.vardir.skald.core.model.NotePayload
import no.vardir.skald.core.model.SchemaName
import no.vardir.skald.ui.onboarding.SetupState
import no.vardir.skald.ui.onboarding.SetupStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The back button unwinds the shell one surface at a time, and only falls
 * through to the system — closing the app — when nothing is stacked.
 */
class BackNavigationTest {

    private fun note(path: String = "Notes/One.md") = NotePayload(
        meta = NoteMeta(path = path, title = "One", folder = "Notes", schema = SchemaName.Note),
        content = "",
        body = "",
        bodyStartLine = 0,
        backlinks = emptyList(),
        attachments = emptyList(),
    )

    @Test
    fun `the home surface hands the press to the system`() {
        assertNull(UiState(loading = false).backStep)
    }

    @Test
    fun `another tab steps home first`() {
        assertEquals(BackStep.HomeTab, UiState(tab = Tab.Constellation).backStep)
    }

    @Test
    fun `an open note closes before the tab changes`() {
        val ui = UiState(tab = Tab.Notes, openNote = note())
        assertEquals(BackStep.Note, ui.backStep)
    }

    @Test
    fun `settings closes over an open note`() {
        val ui = UiState(openNote = note(), settingsOpen = true)
        assertEquals(BackStep.Settings, ui.backStep)
    }

    @Test
    fun `the sync pane closes before settings does`() {
        val ui = UiState(settingsOpen = true, syncPaneOpen = true)
        assertEquals(BackStep.SyncPane, ui.backStep)
    }

    @Test
    fun `the hall closes over everything else`() {
        val ui = UiState(
            tab = Tab.Threads,
            openNote = note(),
            searchOpen = true,
            settingsOpen = true,
            syncPaneOpen = true,
        )
        assertEquals(BackStep.Search, ui.backStep)
    }

    @Test
    fun `setup walks its steps and leaves from the first one`() {
        assertFalse(SetupState(step = SetupStep.Welcome).canGoBack)
        assertTrue(SetupState(step = SetupStep.Name).canGoBack)
        assertTrue(SetupState(step = SetupStep.Scan).canGoBack)
    }

    @Test
    fun `setup stops going back once the vault folder exists`() {
        assertFalse(SetupState(step = SetupStep.Join, committed = true).canGoBack)
        assertFalse(SetupState(step = SetupStep.Join, busy = true).canGoBack)
        assertFalse(SetupState(step = SetupStep.Working).canGoBack)
    }
}
