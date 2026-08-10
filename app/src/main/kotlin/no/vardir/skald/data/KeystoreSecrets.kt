package no.vardir.skald.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import no.vardir.skald.core.sync.RootSecrets
import no.vardir.skald.core.sync.SecretStore
import org.json.JSONObject

/**
 * The three secrets — device token, content key, and (only on the device that
 * provisioned the root) the root token — held in Keystore-backed storage.
 *
 * Never in the vault: a vault folder is the thing most likely to end up in a
 * cloud drive, and the relay cannot help anyone who loses the content key.
 */
class KeystoreSecrets(context: Context) : SecretStore {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences? by lazy {
        runCatching {
            val key = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "skald-sync-secrets",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ) as SharedPreferences
        }.getOrNull()
    }

    override val protected: Boolean get() = prefs != null

    override fun requireStore() {
        checkNotNull(prefs) {
            "This device has no working keystore, so Skald has nowhere safe to keep a sync credential. " +
                "A root it cannot keep the credentials for is a root nobody can use again."
        }
    }

    override fun load(key: String): RootSecrets? {
        val raw = prefs?.getString(key, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            RootSecrets(
                deviceToken = obj.getString("deviceToken"),
                contentKey = obj.getString("contentKey"),
                rootToken = obj.optString("rootToken").ifEmpty { null },
            )
        }.getOrNull()
    }

    override fun save(key: String, secrets: RootSecrets) {
        val store = checkNotNull(prefs) { "No keystore is available on this device" }
        val obj = JSONObject()
            .put("deviceToken", secrets.deviceToken)
            .put("contentKey", secrets.contentKey)
        secrets.rootToken?.let { obj.put("rootToken", it) }
        store.edit().putString(key, obj.toString()).apply()
    }

    override fun forget(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }
}
