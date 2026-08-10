package no.vardir.skald.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.vardir.skald.core.text.Notes
import no.vardir.skald.data.AppPrefs
import no.vardir.skald.data.VaultRepository
import no.vardir.skald.data.vaultFolderName

enum class SetupStep { Welcome, Name, Start, Join, Scan, Sync, Working }

/** How the vault is to begin, which decides what setup does after the folder exists. */
enum class StartMode { Empty, Sample, Join }

data class SetupState(
    val step: SetupStep = SetupStep.Welcome,
    val vaultName: String = "My Vault",
    /** A `gesh://pair` link that opened the app, if setup was reached that way. */
    val invite: String? = null,
    val mode: StartMode? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /**
     * The folder is on disk and its name is fixed, so the steps behind it close.
     * Everything from here is a retry, not a restart.
     */
    val committed: Boolean = false,
    val done: Boolean = false,
) {
    /** What the folder under `vaults/` will be called, shown while it is still a choice. */
    val folderName: String get() = vaultFolderName(vaultName)

    val nameValid: Boolean get() = vaultName.isNotBlank() && Notes.safeFileName(vaultName).isNotEmpty()

    /**
     * Whether a step remains behind this one. Drives both the chrome's arrow and
     * the system back button, so setup cannot offer one route back and not the
     * other — and so a press on the first step still leaves the app.
     */
    val canGoBack: Boolean get() =
        step != SetupStep.Welcome && step != SetupStep.Working && !committed && !busy
}

/**
 * First run. The order here is the point: the vault folder is created empty and
 * a pairing is redeemed *before* anything is written into it, so a phone joining
 * an existing vault has nothing of its own to publish over what is already there.
 */
class OnboardingViewModel(
    private val prefs: AppPrefs,
    /** Supplies the repository once [AppPrefs.openVault] has fixed where it lives. */
    private val repository: () -> VaultRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> get() = _state.asStateFlow()

    private fun edit(transform: (SetupState) -> SetupState) {
        _state.value = transform(_state.value)
    }

    // ---------- navigation ----------

    fun begin() = edit { it.copy(step = SetupStep.Name, error = null) }

    fun setVaultName(name: String) = edit { it.copy(vaultName = name, error = null) }

    /**
     * A pairing link that launched the app skips the question of how to start:
     * the answer is already "join whatever minted this".
     */
    fun offerInvite(uri: String) = edit { if (it.invite == uri) it else it.copy(invite = uri) }

    fun confirmName() {
        val current = _state.value
        if (!current.nameValid) {
            edit { it.copy(error = "Give the vault a name that can be a folder") }
            return
        }
        val invite = current.invite
        if (invite != null) join(invite) else edit { it.copy(step = SetupStep.Start, error = null) }
    }

    fun back() {
        val current = _state.value
        if (current.committed || current.busy) return
        edit {
            it.copy(
                step = when (it.step) {
                    SetupStep.Name -> SetupStep.Welcome
                    SetupStep.Start -> SetupStep.Name
                    SetupStep.Join, SetupStep.Sync -> SetupStep.Start
                    SetupStep.Scan -> SetupStep.Join
                    else -> it.step
                },
                error = null,
            )
        }
    }

    fun dismissError() = edit { it.copy(error = null) }

    // ---------- how to start ----------

    /**
     * Empty and Sample both go on to offer sync, because a vault that starts here
     * is one this phone owns — creating a root is the natural next thing to do
     * with it, and doing it now is one fewer trip into Settings.
     */
    fun chooseEmpty() = edit { it.copy(mode = StartMode.Empty, step = SetupStep.Sync, error = null) }

    fun chooseSample() = edit { it.copy(mode = StartMode.Sample, step = SetupStep.Sync, error = null) }

    fun chooseJoin() = edit { it.copy(mode = StartMode.Join, step = SetupStep.Join, error = null) }

    fun openScanner() = edit { it.copy(step = SetupStep.Scan, error = null) }

    fun closeScanner() = edit { it.copy(step = SetupStep.Join) }

    // ---------- the four ways setup can end ----------

    /** Join a vault that already exists. Nothing is written here until it arrives. */
    fun join(pairingUri: String) = commit(SetupStep.Join) { vault ->
        vault.pairSync(pairingUri.trim())
    }

    /** Own the root from this phone, and publish whatever it starts with. */
    fun createRoot(server: String, handle: String, secret: String) = commit(SetupStep.Sync) { vault ->
        require(server.isNotBlank()) { "A relay address is needed to create a root" }
        seedIfAsked(vault)
        vault.connectSync(server.trim(), handle.trim(), secret.trim())
    }

    /**
     * Local only. Reachable from the sync step and from the join step, so a relay
     * that cannot be reached is never a dead end on first run — sync is still
     * there in Settings once the app is open.
     */
    fun continueWithoutSync() = commit(_state.value.step) { vault -> seedIfAsked(vault) }

    /** Guarded so a relay that failed and was retried does not write the samples twice. */
    private var seeded = false

    private suspend fun seedIfAsked(vault: VaultRepository) {
        if (seeded || _state.value.mode != StartMode.Sample) return
        vault.seed()
        seeded = true
    }

    /**
     * Creates the folder, runs [work], and only then records setup as done. A
     * failure leaves the vault in place but setup unfinished, so the next launch
     * comes back here rather than into a shell with no sync anyone asked for.
     */
    private fun commit(retryStep: SetupStep, work: suspend (VaultRepository) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            edit { it.copy(step = SetupStep.Working, busy = true, error = null, committed = true) }
            val name = _state.value.vaultName
            // Off the main thread: fixing the folder writes preferences, and
            // building the repository is what does the mkdirs.
            runCatching {
                val vault = withContext(Dispatchers.IO) {
                    prefs.openVault(name)
                    repository()
                }
                work(vault)
            }
                .onSuccess {
                    prefs.markComplete()
                    edit { it.copy(busy = false, done = true) }
                }
                .onFailure { failure ->
                    edit {
                        it.copy(
                            step = retryStep,
                            busy = false,
                            error = failure.message ?: "That did not work",
                        )
                    }
                }
        }
    }
}
