package no.vardir.skald.core

import no.vardir.skald.core.extensions.github.GitHubApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubApiTest {
    @Test
    fun `repository response becomes a portable card`() {
        val card = GitHubApi.repository(
            repo = "vardirhq/skald",
            body = """{
                "name":"skald","owner":{"login":"vardirhq"},
                "html_url":"https://github.com/vardirhq/skald","description":"A vault",
                "private":false,"visibility":"public","default_branch":"main","language":"TypeScript",
                "license":{"spdx_id":"MIT"},"stargazers_count":12,"forks_count":3,"open_issues_count":4
            }""",
            pullsLink = "<https://api.github.com/repos/vardirhq/skald/pulls?per_page=1&page=7>; rel=\"last\"",
            pullsBody = "[{}]",
            releaseBody = """{"name":"One","tag_name":"v1.0.0","html_url":"https://example/release"}""",
            runsBody = """{"workflow_runs":[{"name":"CI","status":"completed","conclusion":"success","html_url":"https://example/ci"}]}""",
            fetchedAt = 123L,
        )

        assertEquals("vardirhq/skald", card.repo)
        assertEquals("A vault", card.description)
        assertEquals(7, card.openPullRequests)
        assertEquals("MIT", card.license)
        assertEquals("v1.0.0", card.latestRelease?.tag)
        assertEquals("success", card.workflow?.conclusion)
        assertEquals(123L, card.fetchedAt)
    }

    @Test
    fun `missing optional endpoints do not break the main card`() {
        val card = GitHubApi.repository(
            repo = "owner/repo",
            body = """{"private":true,"open_issues_count":0}""",
            pullsLink = null,
            pullsBody = null,
            releaseBody = null,
            runsBody = null,
            fetchedAt = 1L,
        )

        assertEquals("private", card.visibility)
        assertEquals("owner", card.owner)
        assertEquals("repo", card.name)
        assertNull(card.openPullRequests)
        assertNull(card.latestRelease)
        assertNull(card.workflow)
    }

    @Test
    fun `pull request count uses the last page when present`() {
        assertEquals(1, GitHubApi.pageCount(1, null))
        assertEquals(
            42,
            GitHubApi.pageCount(
                1,
                "<https://api.github.com/repos/o/r/pulls?page=2>; rel=\"next\", " +
                    "<https://api.github.com/repos/o/r/pulls?page=42>; rel=\"last\"",
            ),
        )
    }
}
