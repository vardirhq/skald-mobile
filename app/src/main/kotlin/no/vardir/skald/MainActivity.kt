package no.vardir.skald

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import no.vardir.skald.core.model.AttachmentRef
import no.vardir.skald.data.VaultRepository
import no.vardir.skald.ui.SkaldShell
import no.vardir.skald.ui.SkaldViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as SkaldApp).repository

        setContent {
            val model: SkaldViewModel = viewModel(factory = factoryFor(repository))

            // A pairing QR scanned by the system camera arrives here as a
            // gesh:// link, which is the shortest path from "point the phone at
            // the screen" to a paired vault.
            LaunchedPairing(intent, model)

            SkaldShell(
                viewModel = model,
                onOpenExternal = ::openExternal,
                onOpenAttachment = ::openAttachment,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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

    private fun factoryFor(repository: VaultRepository) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SkaldViewModel(repository) as T
    }
}

@androidx.compose.runtime.Composable
private fun LaunchedPairing(intent: Intent?, model: SkaldViewModel) {
    val data = intent?.data?.toString()
    androidx.compose.runtime.LaunchedEffect(data) {
        if (data != null && data.startsWith("gesh://", ignoreCase = true)) model.pairWith(data)
    }
}
