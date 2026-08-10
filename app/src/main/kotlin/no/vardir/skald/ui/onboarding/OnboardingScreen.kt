package no.vardir.skald.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.Density
import no.vardir.skald.core.model.LogoVariant
import no.vardir.skald.core.model.ThemeName
import no.vardir.skald.ui.components.Eyebrow
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.QrScanner
import no.vardir.skald.ui.components.SectionHeader
import no.vardir.skald.ui.components.SkaldLogo
import no.vardir.skald.ui.theme.Skald
import no.vardir.skald.ui.theme.SkaldTheme

/**
 * First run: name a vault, decide what is in it, and — because the answer is so
 * often "what is already on my desktop" — set sync up here rather than leaving
 * it to be found in Settings later.
 *
 * There is no vault yet, so this runs on the default surface rather than the
 * one the vault's settings will eventually choose.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    invite: String?,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(invite) { if (invite != null) viewModel.offerInvite(invite) }
    LaunchedEffect(state.done) { if (state.done) onDone() }

    SkaldTheme(ThemeName.Midnight, Density.Regular) {
        val colors = Skald.colors

        Box(Modifier.fillMaxSize().background(colors.bg2)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .imePadding(),
            ) {
                if (state.step != SetupStep.Welcome) {
                    SetupBar(
                        showBack = !state.committed && !state.busy && state.step != SetupStep.Working,
                        onBack = viewModel::back,
                    )
                    Hairline()
                }

                Box(Modifier.weight(1f)) {
                    when (state.step) {
                        SetupStep.Welcome -> WelcomeStep(hasInvite = state.invite != null, onBegin = viewModel::begin)
                        SetupStep.Name -> NameStep(state, viewModel)
                        SetupStep.Start -> StartStep(viewModel)
                        SetupStep.Join -> JoinStep(state, viewModel)
                        SetupStep.Scan -> ScanStep(viewModel)
                        SetupStep.Sync -> SyncStep(state, viewModel)
                        SetupStep.Working -> WorkingStep(state)
                    }
                }
            }

            state.error?.let { message ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(18.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bgPop)
                        .clickable { viewModel.dismissError() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(message, style = Skald.type.small, color = colors.err)
                }
            }
        }
    }
}

@Composable
private fun SetupBar(showBack: Boolean, onBack: () -> Unit) {
    val colors = Skald.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showBack) {
            Text(
                "‹",
                style = Skald.type.title,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Back", onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            )
        } else {
            Box(Modifier.padding(start = 6.dp)) { SkaldLogo(LogoVariant.Sigil, 20.dp) }
        }
        Eyebrow("Set up Skald", Modifier.weight(1f))
    }
}

@Composable
private fun WelcomeStep(hasInvite: Boolean, onBegin: () -> Unit) {
    val colors = Skald.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SkaldLogo(LogoVariant.Sigil, 64.dp, withWordmark = true)
        Text(
            "A vault of Markdown files that lives on this phone, and syncs only where you send it.",
            style = Skald.type.body,
            color = colors.tx2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 22.dp),
        )
        if (hasInvite) {
            Text(
                "A pairing code brought you here. Name the vault and this phone will join it.",
                style = Skald.type.small,
                color = colors.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
        PrimaryButton("Begin", Modifier.padding(top = 36.dp), onClick = onBegin)
    }
}

@Composable
private fun NameStep(state: SetupState, viewModel: OnboardingViewModel) {
    val colors = Skald.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(top = 22.dp)) {
            SectionHeader("Name this vault")
            Text(
                "Only you see this. It labels the vault in the chrome and names the folder its " +
                    "Markdown files sit in.",
                style = Skald.type.small,
                color = colors.tx3,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            SetupField(
                label = "Vault name",
                placeholder = "My Vault",
                value = state.vaultName,
                onChange = viewModel::setVaultName,
            )
            Text(
                "Files go in vaults/${state.folderName}/",
                style = Skald.type.meta,
                color = colors.tx4,
            )
            PrimaryButton(
                label = if (state.invite != null) "Name it and join" else "Continue",
                modifier = Modifier.padding(top = 28.dp),
                enabled = state.nameValid,
                onClick = viewModel::confirmName,
            )
        }
    }
}

@Composable
private fun StartStep(viewModel: OnboardingViewModel) {
    val colors = Skald.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(top = 22.dp, bottom = 40.dp)) {
            SectionHeader("How should it start?")
            Text(
                "You can change any of this later — none of it is a mode you get locked into.",
                style = Skald.type.small,
                color = colors.tx3,
                modifier = Modifier.padding(bottom = 18.dp),
            )
            ChoiceCard(
                title = "Empty",
                body = "One vault, no notes. The first thing in it will be yours.",
                onClick = viewModel::chooseEmpty,
            )
            ChoiceCard(
                title = "Join a vault you already have",
                body = "Scan the pairing code from Skald on your desktop or another phone. " +
                    "Your notes arrive over sync — nothing is written here before they do.",
                onClick = viewModel::chooseJoin,
            )
            ChoiceCard(
                title = "The sample vault",
                body = "A dozen example notes — schemas, threads, wikilinks, a constellation — " +
                    "to look around in. Delete them whenever you like.",
                onClick = viewModel::chooseSample,
            )
        }
    }
}

@Composable
private fun JoinStep(state: SetupState, viewModel: OnboardingViewModel) {
    val colors = Skald.colors
    var link by remember { mutableStateOf(state.invite.orEmpty()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(top = 22.dp, bottom = 40.dp)) {
            SectionHeader("Join an existing vault")
            Text(
                "On the other device: Settings → Sync → Pair a device. The code carries the " +
                    "content key in its fragment, which is the half no relay ever sees.",
                style = Skald.type.small,
                color = colors.tx3,
                modifier = Modifier.padding(bottom = 18.dp),
            )
            ChoiceCard(
                title = "Scan the pairing code",
                body = "Uses the camera once, and only for this.",
                onClick = viewModel::openScanner,
            )

            Column(Modifier.padding(top = 26.dp)) {
                SectionHeader("Or paste the link")
                SetupField(
                    label = "Pairing link",
                    placeholder = "gesh://pair?s=…&c=…#k=…",
                    value = link,
                    onChange = { link = it },
                )
                PrimaryButton(
                    label = "Pair this phone",
                    enabled = link.isNotBlank() && !state.busy,
                ) { viewModel.join(link) }
            }

            TextAction(
                "Skip — set sync up later",
                Modifier.padding(top = 26.dp),
                onClick = viewModel::continueWithoutSync,
            )
        }
    }
}

@Composable
private fun ScanStep(viewModel: OnboardingViewModel) {
    Column(Modifier.fillMaxSize()) {
        Eyebrow("Point the camera at the pairing code", Modifier.padding(18.dp))
        Hairline()
        Box(Modifier.weight(1f)) {
            QrScanner(onScanned = viewModel::join)
        }
        TextAction("Cancel", Modifier.padding(18.dp), onClick = viewModel::closeScanner)
    }
}

@Composable
private fun SyncStep(state: SetupState, viewModel: OnboardingViewModel) {
    val colors = Skald.colors
    var server by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(top = 22.dp, bottom = 40.dp)) {
            SectionHeader("Sync")
            Text(
                "This phone can own the vault and hand pairing codes out to your other devices. " +
                    "It becomes the only thing that can pair or revoke one.",
                style = Skald.type.small,
                color = colors.tx3,
                modifier = Modifier.padding(bottom = 18.dp),
            )
            SetupField("Relay address", "https://gesh.example.com", server) { server = it }
            SetupField("Handle (optional)", "a name people can type", handle) { handle = it }
            SetupField("Provisioning secret (optional)", "if the relay requires one", secret) { secret = it }
            PrimaryButton(
                label = "Create a sync root",
                enabled = server.isNotBlank() && !state.busy,
            ) { viewModel.createRoot(server, handle, secret) }

            Column(Modifier.padding(top = 30.dp)) {
                Hairline()
                TextAction(
                    "Not now — keep it on this phone",
                    Modifier.padding(top = 18.dp),
                    onClick = viewModel::continueWithoutSync,
                )
                Text(
                    "Sync is in Settings whenever you want it, and turning it on later syncs " +
                        "everything you wrote in the meantime.",
                    style = Skald.type.small,
                    color = colors.tx3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkingStep(state: SetupState) {
    val colors = Skald.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            when (state.mode) {
                StartMode.Join -> "Pairing…"
                StartMode.Sample -> "Writing the sample vault…"
                else -> "Making the vault…"
            },
            style = Skald.type.row,
            color = colors.tx1,
        )
        Text(
            state.vaultName,
            style = Skald.type.meta,
            color = colors.tx3,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---------- pieces ----------

@Composable
private fun ChoiceCard(title: String, body: String, onClick: () -> Unit) {
    val colors = Skald.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(Skald.metrics.card))
            .background(colors.bg1)
            .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(Skald.metrics.card))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(title, style = Skald.type.heading, color = colors.tx0)
        Text(body, style = Skald.type.small, color = colors.tx3, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = Skald.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) colors.accent else colors.bg3)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Skald.type.row, color = if (enabled) colors.onAccent else colors.tx4)
    }
}

@Composable
private fun TextAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        style = Skald.type.row,
        color = Skald.colors.accent,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    )
}

@Composable
private fun SetupField(
    label: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
) {
    val colors = Skald.colors
    Column(Modifier.padding(bottom = 12.dp)) {
        Eyebrow(label, Modifier.padding(bottom = 6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.bg1)
                .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (value.isEmpty()) Text(placeholder, style = Skald.type.small, color = colors.tx4)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Skald.type.small.copy(color = colors.tx0),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
