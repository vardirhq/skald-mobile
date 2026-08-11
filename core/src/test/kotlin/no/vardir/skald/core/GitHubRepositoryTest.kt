package no.vardir.skald.core

import no.vardir.skald.core.text.GitHubRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubRepositoryTest {
    @Test
    fun `normalizes desktop repository values`() {
        assertEquals("vardirhq/skald", GitHubRepository.normalize("vardirhq/skald"))
        assertEquals("VardirHQ/Skald", GitHubRepository.normalize("github:VardirHQ/Skald.git"))
        assertEquals("vardirhq/skald", GitHubRepository.normalize("https://github.com/vardirhq/skald/issues/12"))
        assertEquals("https://github.com/vardirhq/skald", GitHubRepository.url("vardirhq/skald"))
    }

    @Test
    fun `rejects unsafe or non-GitHub values`() {
        assertNull(GitHubRepository.normalize("https://gitlab.com/vardirhq/skald"))
        assertNull(GitHubRepository.normalize("../skald"))
        assertNull(GitHubRepository.normalize("vardirhq"))
    }
}
