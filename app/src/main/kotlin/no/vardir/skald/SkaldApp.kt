package no.vardir.skald

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import no.vardir.skald.data.AppPrefs
import no.vardir.skald.data.VaultRepository

class SkaldApp : Application() {

    /** Outlives any screen: the vault, its index, and the sync engine. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs: AppPrefs by lazy { AppPrefs(this).also { it.adoptLegacyVault() } }

    @Volatile private var opened: VaultRepository? = null

    /**
     * Built on first use rather than at startup. Constructing a [VaultRepository]
     * is what creates the folder on disk, and before setup has committed a name
     * there is no folder to create — so nothing may touch this until it has.
     */
    val repository: VaultRepository
        get() = opened ?: synchronized(this) {
            opened ?: VaultRepository(this, scope, prefs.vaultDir, prefs.vaultName).also { opened = it }
        }
}
