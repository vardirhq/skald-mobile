package no.vardir.skald.core.extensions.github

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class GitHubRelease(
    val name: String,
    val tag: String,
    val url: String,
    val publishedAt: String? = null,
)

@Serializable
data class GitHubWorkflow(
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val url: String,
)

@Serializable
data class GitHubRepositoryCard(
    val repo: String,
    val url: String,
    val name: String,
    val owner: String,
    val description: String? = null,
    val visibility: String,
    val defaultBranch: String,
    val language: String? = null,
    val license: String? = null,
    val stars: Int,
    val forks: Int,
    val openIssues: Int,
    val openPullRequests: Int? = null,
    val latestRelease: GitHubRelease? = null,
    val workflow: GitHubWorkflow? = null,
    val fetchedAt: Long,
    val stale: Boolean = false,
)

@Serializable
data class GitHubCacheEntry(
    val etag: String? = null,
    val card: GitHubRepositoryCard,
)

data class GitHubDeviceLogin(
    val userCode: String,
    val verificationUri: String,
    val expiresAt: Long,
)

data class GitHubAuthStatus(
    val configured: Boolean,
    val connected: Boolean,
    val login: String? = null,
    val secretsProtected: Boolean,
    val installUrl: String? = null,
    val deviceLogin: GitHubDeviceLogin? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/** GitHub response parsing kept Android-free so it can be unit tested. */
object GitHubApi {
    val json = Json { ignoreUnknownKeys = true }

    fun repository(
        repo: String,
        body: String,
        pullsLink: String?,
        pullsBody: String?,
        releaseBody: String?,
        runsBody: String?,
        fetchedAt: Long,
    ): GitHubRepositoryCard {
        val raw = json.parseToJsonElement(body).jsonObject
        val fallbackOwner = repo.substringBefore('/')
        val fallbackName = repo.substringAfter('/')
        val url = raw.string("html_url") ?: "https://github.com/$repo"
        val private = raw["private"]?.jsonPrimitive?.booleanOrNull == true
        val release = releaseBody?.objectOrNull()?.let { value ->
            GitHubRelease(
                name = value.string("name") ?: value.string("tag_name") ?: "Latest release",
                tag = value.string("tag_name").orEmpty(),
                url = value.string("html_url") ?: "$url/releases",
                publishedAt = value.string("published_at"),
            )
        }
        val run = runsBody?.objectOrNull()
            ?.get("workflow_runs")
            ?.let { runCatching { it.jsonArray }.getOrNull() }
            ?.firstOrNull()
            ?.let { runCatching { it.jsonObject }.getOrNull() }

        return GitHubRepositoryCard(
            repo = repo,
            url = url,
            name = raw.string("name") ?: fallbackName,
            owner = raw["owner"]?.let { runCatching { it.jsonObject.string("login") }.getOrNull() } ?: fallbackOwner,
            description = raw.string("description"),
            visibility = if (private) "private" else raw.string("visibility")?.takeIf { it == "internal" } ?: "public",
            defaultBranch = raw.string("default_branch") ?: "main",
            language = raw.string("language"),
            license = raw["license"]?.let {
                runCatching {
                    val license = it.jsonObject
                    license.string("spdx_id")?.takeUnless { value -> value == "NOASSERTION" }
                        ?: license.string("name")
                }.getOrNull()
            },
            stars = raw.int("stargazers_count"),
            forks = raw.int("forks_count"),
            openIssues = raw.int("open_issues_count"),
            openPullRequests = pullsBody?.arrayOrNull()?.let { pageCount(it.size, pullsLink) },
            latestRelease = release,
            workflow = run?.let {
                GitHubWorkflow(
                    name = it.string("name") ?: "Workflow",
                    status = it.string("status") ?: "unknown",
                    conclusion = it.string("conclusion"),
                    url = it.string("html_url") ?: "$url/actions",
                )
            },
            fetchedAt = fetchedAt,
        )
    }

    fun pageCount(firstPageCount: Int, link: String?): Int {
        val last = link?.split(',')
            ?.firstOrNull { it.contains("rel=\"last\"") }
            ?.let { Regex("[?&]page=(\\d+)").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        return last ?: firstPageCount
    }

    private fun String.objectOrNull(): JsonObject? =
        runCatching { json.parseToJsonElement(this).jsonObject }.getOrNull()

    private fun String.arrayOrNull(): JsonArray? =
        runCatching { json.parseToJsonElement(this).jsonArray }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int =
        get(key)?.jsonPrimitive?.intOrNull ?: get(key)?.jsonPrimitive?.longOrNull?.toInt() ?: 0
}
