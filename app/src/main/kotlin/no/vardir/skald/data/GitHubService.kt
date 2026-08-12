package no.vardir.skald.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import no.vardir.skald.core.extensions.github.GitHubApi
import no.vardir.skald.core.extensions.github.GitHubAuthStatus
import no.vardir.skald.core.extensions.github.GitHubCacheEntry
import no.vardir.skald.core.extensions.github.GitHubDeviceLogin
import no.vardir.skald.core.extensions.github.GitHubRepositoryCard
import no.vardir.skald.core.text.GitHubRepository
import org.json.JSONObject

class GitHubService(
    context: Context,
    private val clientId: String,
    private val appSlug: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val appContext = context.applicationContext
    private val credentials = GitHubCredentialStore(appContext)
    private val memory = ConcurrentHashMap<String, GitHubCacheEntry>()
    private var pending: PendingLogin? = null

    private val _status = MutableStateFlow(status())
    val status: StateFlow<GitHubAuthStatus> = _status.asStateFlow()

    suspend fun beginLogin(): GitHubDeviceLogin = withContext(Dispatchers.IO) {
        check(clientId.isNotBlank()) { "GitHub login is not configured in this build" }
        check(credentials.protected) { "Android Keystore is unavailable, so Skald cannot protect a GitHub token" }
        _status.value = status().copy(busy = true, error = null)
        try {
            val response = execute(
                Request.Builder().url("$LOGIN/device/code")
                    .headers(loginHeaders())
                    .post(FormBody.Builder().add("client_id", clientId).build())
                    .build(),
            )
            val value = JSONObject(response.body)
            if (response.code !in 200..299 || !value.has("device_code") || !value.has("user_code")) {
                error(value.optString("error_description", value.optString("error", "GitHub did not start sign-in")))
            }
            val login = GitHubDeviceLogin(
                userCode = value.getString("user_code"),
                verificationUri = value.optString("verification_uri", "$LOGIN/device"),
                expiresAt = System.currentTimeMillis() + value.optLong("expires_in", 900) * 1_000,
            )
            pending = PendingLogin(
                deviceCode = value.getString("device_code"),
                intervalMs = maxOf(5, value.optLong("interval", 5)) * 1_000,
                expiresAt = login.expiresAt,
            )
            _status.value = status().copy(deviceLogin = login, busy = true)
            login
        } catch (error: Throwable) {
            _status.value = status().copy(error = error.message ?: "GitHub sign-in failed")
            throw error
        }
    }

    suspend fun completeLogin(): GitHubAuthStatus = withContext(Dispatchers.IO) {
        val attempt = checkNotNull(pending) { "Start GitHub sign-in first" }
        try {
            while (!attempt.cancelled && System.currentTimeMillis() < attempt.expiresAt) {
                delay(attempt.intervalMs)
                val response = execute(
                    Request.Builder().url("$LOGIN/oauth/access_token")
                        .headers(loginHeaders())
                        .post(
                            FormBody.Builder()
                                .add("client_id", clientId)
                                .add("device_code", attempt.deviceCode)
                                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                                .build(),
                        ).build(),
                )
                val value = JSONObject(response.body)
                when (val error = value.optString("error").ifEmpty { null }) {
                    "authorization_pending" -> continue
                    "slow_down" -> { attempt.intervalMs += 5_000; continue }
                    null -> Unit
                    else -> throw IOException(value.optString("error_description", error))
                }
                val token = value.optString("access_token")
                if (token.isEmpty()) continue
                val saved = GitHubCredentials(
                    accessToken = token,
                    refreshToken = value.optString("refresh_token").ifEmpty { null },
                    expiresAt = value.optLong("expires_in").takeIf { it > 0 }
                        ?.let { System.currentTimeMillis() + it * 1_000 },
                    refreshTokenExpiresAt = value.optLong("refresh_token_expires_in").takeIf { it > 0 }
                        ?.let { System.currentTimeMillis() + it * 1_000 },
                    login = fetchLogin(token),
                )
                credentials.save(saved)
                pending = null
                return@withContext status().also { _status.value = it }
            }
            throw IOException(if (attempt.cancelled) "GitHub sign-in cancelled" else "GitHub sign-in expired")
        } catch (error: Throwable) {
            pending = null
            _status.value = status().copy(error = error.message ?: "GitHub sign-in failed")
            throw error
        }
    }

    fun cancelLogin() {
        pending?.cancelled = true
        pending = null
        _status.value = status()
    }

    fun disconnect() {
        cancelLogin()
        credentials.forget()
        memory.clear()
        _status.value = status()
    }

    suspend fun repository(input: String, force: Boolean = false): GitHubRepositoryCard = withContext(Dispatchers.IO) {
        val repo = GitHubRepository.normalize(input) ?: error("Use a GitHub repository in owner/name form")
        val cached = memory[repo] ?: readPublicCache()[repo]
        if (!force && cached != null && System.currentTimeMillis() - cached.card.fetchedAt < CACHE_MS) {
            return@withContext cached.card
        }

        val token = runCatching { accessToken() }.getOrNull()
        val repositoryResponse = try {
            execute(Request.Builder().url("$API/repos/$repo").headers(apiHeaders(token, cached?.etag)).build())
        } catch (error: IOException) {
            if (cached != null) return@withContext cached.card.copy(stale = true)
            throw error
        }

        if (repositoryResponse.code == 304 && cached != null) {
            val refreshed = cached.copy(card = cached.card.copy(fetchedAt = System.currentTimeMillis(), stale = false))
            remember(repo, refreshed)
            return@withContext refreshed.card
        }
        if (repositoryResponse.code == 404 && token == null) {
            error("Repository unavailable or private — connect GitHub to continue")
        }
        if (repositoryResponse.code !in 200..299) {
            if (repositoryResponse.code in setOf(403, 429) && cached != null) {
                return@withContext cached.card.copy(stale = true)
            }
            val message = runCatching { JSONObject(repositoryResponse.body).optString("message") }.getOrNull()
            error(message?.takeIf { it.isNotBlank() } ?: "GitHub returned ${repositoryResponse.code}")
        }

        val defaultBranch = runCatching {
            JSONObject(repositoryResponse.body).optString("default_branch", "main")
        }.getOrDefault("main")
        val pulls = optional("$API/repos/$repo/pulls?state=open&per_page=1", token)
        val release = optional("$API/repos/$repo/releases/latest", token)
        val runs = optional(
            "$API/repos/$repo/actions/runs?branch=${encode(defaultBranch)}&per_page=1",
            token,
        )
        val card = GitHubApi.repository(
            repo = repo,
            body = repositoryResponse.body,
            pullsLink = pulls?.headers?.get("Link"),
            pullsBody = pulls?.body,
            releaseBody = release?.body,
            runsBody = runs?.body,
            fetchedAt = System.currentTimeMillis(),
        )
        val entry = GitHubCacheEntry(repositoryResponse.headers["ETag"], card)
        remember(repo, entry)
        card
    }

    private fun status(): GitHubAuthStatus {
        val saved = credentials.load()
        return GitHubAuthStatus(
            configured = clientId.isNotBlank(),
            connected = saved != null,
            login = saved?.login,
            secretsProtected = credentials.protected,
            installUrl = appSlug.takeIf { it.isNotBlank() }
                ?.let { "https://github.com/apps/$it/installations/new" },
        )
    }

    private suspend fun accessToken(): String? {
        val saved = credentials.load() ?: return null
        if (saved.expiresAt == null || saved.expiresAt > System.currentTimeMillis() + 60_000) {
            return saved.accessToken
        }
        val refresh = saved.refreshToken ?: run { credentials.forget(); _status.value = status(); return null }
        if (clientId.isBlank()) return null
        val response = execute(
            Request.Builder().url("$LOGIN/oauth/access_token")
                .headers(loginHeaders())
                .post(
                    FormBody.Builder()
                        .add("client_id", clientId)
                        .add("grant_type", "refresh_token")
                        .add("refresh_token", refresh)
                        .build(),
                ).build(),
        )
        val value = JSONObject(response.body)
        val token = value.optString("access_token").ifEmpty {
            credentials.forget()
            _status.value = status()
            return null
        }
        credentials.save(
            GitHubCredentials(
                accessToken = token,
                refreshToken = value.optString("refresh_token").ifEmpty { saved.refreshToken },
                expiresAt = value.optLong("expires_in").takeIf { it > 0 }
                    ?.let { System.currentTimeMillis() + it * 1_000 },
                refreshTokenExpiresAt = value.optLong("refresh_token_expires_in").takeIf { it > 0 }
                    ?.let { System.currentTimeMillis() + it * 1_000 },
                login = saved.login,
            ),
        )
        return token
    }

    private fun fetchLogin(token: String): String? {
        val response = execute(Request.Builder().url("$API/user").headers(apiHeaders(token)).build())
        return if (response.code in 200..299) JSONObject(response.body).optString("login").ifEmpty { null } else null
    }

    private fun optional(url: String, token: String?): HttpResult? = runCatching {
        execute(Request.Builder().url(url).headers(apiHeaders(token)).build()).takeIf { it.code in 200..299 }
    }.getOrNull()

    private fun remember(repo: String, entry: GitHubCacheEntry) {
        memory[repo] = entry
        if (entry.card.visibility != "public") return
        runCatching {
            val values = readPublicCache().toMutableMap().also { it[repo] = entry }
            val file = cacheFile()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(GitHubApi.json.encodeToString(values))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    private fun readPublicCache(): Map<String, GitHubCacheEntry> = runCatching {
        val file = cacheFile()
        if (!file.exists()) emptyMap() else GitHubApi.json.decodeFromString(
            MapSerializer(String.serializer(), GitHubCacheEntry.serializer()),
            file.readText(),
        )
    }.getOrDefault(emptyMap())

    private fun cacheFile(): File = File(appContext.filesDir, "github-public-cache.json")

    private fun execute(request: Request): HttpResult = client.newCall(request).execute().use { response ->
        HttpResult(response.code, response.headers, response.body?.string().orEmpty())
    }

    private fun apiHeaders(token: String?, etag: String? = null): Headers = Headers.Builder()
        .add("Accept", "application/vnd.github+json")
        .add("X-GitHub-Api-Version", API_VERSION)
        .add("User-Agent", "Skald-Mobile")
        .apply { if (token != null) add("Authorization", "Bearer $token") }
        .apply { if (etag != null) add("If-None-Match", etag) }
        .build()

    private fun loginHeaders(): Headers = Headers.Builder()
        .add("Accept", "application/json")
        .add("User-Agent", "Skald-Mobile")
        .build()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private data class HttpResult(val code: Int, val headers: Headers, val body: String)
    private data class PendingLogin(
        val deviceCode: String,
        var intervalMs: Long,
        val expiresAt: Long,
        var cancelled: Boolean = false,
    )

    private companion object {
        const val API = "https://api.github.com"
        const val LOGIN = "https://github.com/login"
        const val API_VERSION = "2022-11-28"
        const val CACHE_MS = 10 * 60_000L
    }
}
