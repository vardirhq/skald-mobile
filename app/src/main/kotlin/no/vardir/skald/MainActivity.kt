package no.vardir.skald

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import no.vardir.skald.core.model.AttachmentRef
import no.vardir.skald.data.AppPrefs
import no.vardir.skald.data.GitHubService
import no.vardir.skald.data.VaultRepository
import no.vardir.skald.ui.SkaldShell
import no.vardir.skald.ui.SkaldViewModel
import no.vardir.skald.ui.onboarding.OnboardingScreen
import no.vardir.skald.ui.onboarding.OnboardingViewModel

class MainActivity : ComponentActivity() {

    /**
     * The pairing link that launched or resumed the app, as state rather than as
     * `intent`, which is not observable — a QR scanned while Skald is already open
     * arrives through [onNewIntent] and has to reach the composition.
     */
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SkaldApp
        incoming = pairingUriOf(intent)

        setContent {
            // Setup owns the first launch: there is no vault until it has made
            // one, so nothing here may touch the repository before it says so.
            var setUp by remember { mutableStateOf(app.prefs.setupComplete) }

            if (!setUp) {
                OnboardingScreen(
                    viewModel = viewModel(factory = setupFactory(app.prefs) { app.repository }),
                    invite = incoming,
                    onDone = {
                        // Consumed by the pairing setup just ran; the shell must
                        // not redeem the same single-use code a second time.
                        incoming = null
                        setUp = true
                    },
                )
            } else {
                val model: SkaldViewModel = viewModel(factory = shellFactory(app.repository, app.github))

                // A pairing QR scanned by the system camera arrives here as a
                // gesh:// link, which is the shortest path from "point the phone
                // at the screen" to a paired vault.
                LaunchedPairing(incoming, model)

                SkaldShell(
                    viewModel = model,
                    onOpenExternal = ::openExternal,
                    onOpenAttachment = ::openAttachment,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = pairingUriOf(intent)
    }

    private fun openExternal(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun openAttachment(ref: AttachmentRef) {
        val path = ref.path ?: return
        val repository = (application as SkaldApp).repository
        val file = repository.vault.assetFile(path) ?: return
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, ref.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    private fun pairingUriOf(intent: Intent?): String? =
        intent?.data?.toString()?.takeIf { it.startsWith("gesh://", ignoreCase = true) }

    private fun shellFactory(repository: VaultRepository, github: GitHubService) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SkaldViewModel(repository, github) as T
    }

    private fun setupFactory(prefs: AppPrefs, repository: () -> VaultRepository) =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OnboardingViewModel(prefs, repository) as T
        }
}

@Composable
private fun LaunchedPairing(pairingUri: String?, model: SkaldViewModel) {
    LaunchedEffect(pairingUri) {
        if (pairingUri != null) model.pairWith(pairingUri)
    }
}
