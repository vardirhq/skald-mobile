package no.vardir.skald.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

data class GitHubCredentials(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val refreshTokenExpiresAt: Long? = null,
    val login: String? = null,
)

/** GitHub tokens are never stored in the vault or in ordinary preferences. */
class GitHubCredentialStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences? by lazy {
        runCatching {
            val key = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "skald-github-secrets",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ) as SharedPreferences
        }.getOrNull()
    }

    val protected: Boolean get() = prefs != null

    fun load(): GitHubCredentials? {
        val raw = prefs?.getString(KEY, null) ?: return null
        return runCatching {
            val value = JSONObject(raw)
            GitHubCredentials(
                accessToken = value.getString("accessToken"),
                refreshToken = value.optString("refreshToken").ifEmpty { null },
                expiresAt = value.optLong("expiresAt").takeIf { it > 0 },
                refreshTokenExpiresAt = value.optLong("refreshTokenExpiresAt").takeIf { it > 0 },
                login = value.optString("login").ifEmpty { null },
            )
        }.getOrNull()
    }

    fun save(credentials: GitHubCredentials) {
        val target = checkNotNull(prefs) { "No Android Keystore is available for a GitHub token" }
        val value = JSONObject().put("accessToken", credentials.accessToken)
        credentials.refreshToken?.let { value.put("refreshToken", it) }
        credentials.expiresAt?.let { value.put("expiresAt", it) }
        credentials.refreshTokenExpiresAt?.let { value.put("refreshTokenExpiresAt", it) }
        credentials.login?.let { value.put("login", it) }
        target.edit().putString(KEY, value.toString()).apply()
    }

    fun forget() {
        prefs?.edit()?.remove(KEY)?.apply()
    }

    private companion object {
        const val KEY = "credentials"
    }
}
