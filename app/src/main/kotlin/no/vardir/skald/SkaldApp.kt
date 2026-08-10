package no.vardir.skald

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import no.vardir.skald.data.VaultRepository

class SkaldApp : Application() {

    /** Outlives any screen: the vault, its index, and the sync engine. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: VaultRepository by lazy { VaultRepository(this, scope) }
}
