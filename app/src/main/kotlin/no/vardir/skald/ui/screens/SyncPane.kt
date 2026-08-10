package no.vardir.skald.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.vardir.skald.core.sync.PairingTicket
import no.vardir.skald.core.sync.SyncDeviceInfo
import no.vardir.skald.core.sync.SyncStatus
import no.vardir.skald.ui.components.EmptyState
import no.vardir.skald.ui.components.Eyebrow
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.QrCode
import no.vardir.skald.ui.components.QrScanner
import no.vardir.skald.ui.components.SectionHeader
import no.vardir.skald.ui.theme.Skald

private enum class PaneMode { Overview, Scan, ShowCode }

/**
 * Sync. Two halves of one secret: the relay holds the root and its devices, and
 * the content key never leaves this phone except inside the fragment of a
 * pairing QR — the part no server ever receives.
 */
@Composable
fun SyncPane(
    status: SyncStatus,
    devices: List<SyncDeviceInfo>,
    ticket: PairingTicket?,
    onPair: (String) -> Unit,
    onConnect: (String, String, String) -> Unit,
    onSyncNow: () -> Unit,
    onRepublish: () -> Unit,
    onMintPairing: () -> Unit,
    onClearTicket: () -> Unit,
    onRevoke: (String) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    var mode by remember { mutableStateOf(PaneMode.Overview) }

    if (ticket != null && mode != PaneMode.ShowCode) mode = PaneMode.ShowCode

    when (mode) {
        PaneMode.Scan -> Column(modifier.fillMaxSize()) {
            Eyebrow("Point the camera at the pairing code", Modifier.padding(18.dp))
            Hairline()
            Box(Modifier.weight(1f)) {
                QrScanner(onScanned = { uri ->
                    mode = PaneMode.Overview
                    onPair(uri)
                })
            }
            TextAction("Cancel", Modifier.padding(18.dp)) { mode = PaneMode.Overview }
        }

        PaneMode.ShowCode -> Column(
            modifier.fillMaxSize().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ticket == null) {
                EmptyState("Minting a code…", "This also republishes the vault, so the new device has something to receive.")
            } else {
                Eyebrow("Scan this from the other device", Modifier.padding(bottom = 16.dp))
                QrCode(ticket.uri)
                Text(
                    ticket.displayCode,
                    style = Skald.type.stat.copy(letterSpacing = 3.sp),
                    color = colors.tx0,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    "Single use. Type it if there is no camera.",
                    style = Skald.type.small,
                    color = colors.tx3,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (ticket.uriIsLocal) {
                    Text(
                        "This relay has no public URL set, so Skald built the link itself — " +
                            "the other device must be able to reach the same address.",
                        style = Skald.type.small,
                        color = colors.warn,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            Box(Modifier.weight(1f))
            TextAction("Done") {
                onClearTicket()
                mode = PaneMode.Overview
            }
        }

        PaneMode.Overview -> LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            item {
                Column(Modifier.padding(top = 18.dp)) {
                    SectionHeader("Status")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Skald.metrics.card))
                            .background(colors.bg1)
                            .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(Skald.metrics.card))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(phaseColor(status, colors))
                        )
                        Column(Modifier.weight(1f)) {
                            Text(syncSubtitle(status), style = Skald.type.row, color = colors.tx0)
                            if (status.configured) {
                                Text(
                                    "${status.serverUrl} · ${status.deviceId}",
                                    style = Skald.type.meta,
                                    color = colors.tx3,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            if (!status.configured) {
                item { ConnectForm(onPair = { mode = PaneMode.Scan }, onConnect = onConnect) }
            } else {
                item {
                    Column(Modifier.padding(top = 26.dp)) {
                        SectionHeader("Actions")
                        ActionRow("Sync now", "Pull, apply, acknowledge, push", onSyncNow)
                        ActionRow(
                            if (status.enabled) "Pause automatic sync" else "Resume automatic sync",
                            "Background passes run about twice an hour",
                        ) { onSetEnabled(!status.enabled) }
                        if (status.isRoot) {
                            ActionRow("Pair another device", "Mints a single-use code and republishes the vault", onMintPairing)
                        }
                        ActionRow(
                            "Republish everything",
                            "For a device that has been away past the relay's retention",
                            onRepublish,
                        )
                        ActionRow("Disconnect", "Forgets the root. Your notes are untouched.", onDisconnect, danger = true)
                    }
                }

                if (status.oversize.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(top = 26.dp)) {
                            SectionHeader("Too large to send", status.oversize.size.toString())
                            for (path in status.oversize) {
                                Text(path, style = Skald.type.meta, color = colors.warn, modifier = Modifier.padding(vertical = 4.dp))
                            }
                            Text(
                                "Everything else keeps syncing. One attachment travels per event, and chunking is not built yet.",
                                style = Skald.type.small,
                                color = colors.tx3,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }

                if (status.isRoot) {
                    item {
                        Column(Modifier.padding(top = 26.dp, bottom = 40.dp)) {
                            SectionHeader("Devices", devices.size.toString())
                            for (device in devices) {
                                Column {
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                device.deviceId + if (device.isThisDevice) " · this phone" else "",
                                                style = Skald.type.row,
                                                color = colors.tx1,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                device.lastSeenMs?.let { "last seen ${relativeTime(it)} ago" }
                                                    ?: "never synced",
                                                style = Skald.type.meta,
                                                color = colors.tx3,
                                            )
                                        }
                                        if (!device.isThisDevice) {
                                            Text(
                                                "Revoke",
                                                style = Skald.type.meta,
                                                color = colors.err,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onRevoke(device.deviceId) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                    Hairline()
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(top = 26.dp, bottom = 48.dp)) {
                    Text(
                        if (status.secretsProtected) {
                            "Credentials are held in the Android keystore, never in the vault folder."
                        } else {
                            "This device has no working keystore, so Skald will not provision a root it cannot keep."
                        },
                        style = Skald.type.small,
                        color = if (status.secretsProtected) colors.tx3 else colors.err,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectForm(onPair: () -> Unit, onConnect: (String, String, String) -> Unit) {
    val colors = Skald.colors
    var server by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    Column(Modifier.padding(top = 26.dp, bottom = 40.dp)) {
        SectionHeader("Join an existing vault")
        Text(
            "Scan the pairing code from the device that owns the root. The code carries the " +
                "content key in its fragment, which is the half the relay never sees.",
            style = Skald.type.small,
            color = colors.tx3,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        ActionRow("Scan a pairing code", "Uses the camera once, and only for this", onPair)

        Column(Modifier.padding(top = 30.dp)) {
            SectionHeader("Or start a new root here")
            Text(
                "This phone becomes the authority for the vault, and the only thing that can pair " +
                    "or revoke another device.",
                style = Skald.type.small,
                color = colors.tx3,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Field("Relay address", "https://gesh.example.com", server) { server = it }
            Field("Handle (optional)", "a name people can type", handle) { handle = it }
            Field("Provisioning secret (optional)", "if the relay requires one", secret) { secret = it }
            ActionRow("Create a sync root", "Publishes this vault so another device can receive it") {
                if (server.isNotBlank()) onConnect(server.trim(), handle.trim(), secret.trim())
            }
        }
    }
}

@Composable
private fun Field(label: String, placeholder: String, value: String, onChange: (String) -> Unit) {
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

@Composable
private fun ActionRow(title: String, hint: String, onClick: () -> Unit, danger: Boolean = false) {
    val colors = Skald.colors
    Column {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 13.dp),
        ) {
            Text(title, style = Skald.type.row, color = if (danger) colors.err else colors.accent)
            Text(hint, style = Skald.type.meta, color = colors.tx3, modifier = Modifier.padding(top = 2.dp))
        }
        Hairline()
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

private fun phaseColor(status: SyncStatus, colors: no.vardir.skald.ui.theme.SkaldColors): Color = when {
    !status.configured -> colors.tx4
    status.phase == no.vardir.skald.core.sync.SyncPhase.Error -> colors.err
    status.phase == no.vardir.skald.core.sync.SyncPhase.Syncing -> colors.accent
    !status.enabled -> colors.warn
    else -> colors.ok
}
