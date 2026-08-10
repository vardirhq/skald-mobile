package no.vardir.skald.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.Density
import no.vardir.skald.core.model.LogoVariant
import no.vardir.skald.core.model.ThemeName
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.sync.SyncPhase
import no.vardir.skald.core.sync.SyncStatus
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.SectionHeader
import no.vardir.skald.ui.components.Segmented
import no.vardir.skald.ui.components.SkaldLogo
import no.vardir.skald.ui.theme.Skald

/**
 * Settings, as the mobile spec asks for it: a simplified list. Appearance,
 * where things live, what the vault actually contains, and the way to another
 * device.
 */
@Composable
fun SettingsScreen(
    snapshot: VaultSnapshot,
    syncStatus: SyncStatus,
    onTheme: (ThemeName) -> Unit,
    onDensity: (Density) -> Unit,
    onLogoVariant: (LogoVariant) -> Unit,
    onEditorFontSize: (Int) -> Unit,
    onOpenSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    val settings = snapshot.settings

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        item {
            Column(Modifier.padding(top = 18.dp)) {
                SectionHeader("Surface")
                Segmented(
                    options = ThemeName.entries.map { it to it.name },
                    selected = settings.theme,
                    onSelect = onTheme,
                )
            }
        }

        item {
            Column(Modifier.padding(top = 26.dp)) {
                SectionHeader("Density")
                Segmented(
                    options = Density.entries.map { it to it.name },
                    selected = settings.density,
                    onSelect = onDensity,
                )
            }
        }

        item {
            Column(Modifier.padding(top = 26.dp)) {
                SectionHeader("Mark")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (variant in LogoVariant.entries) {
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (settings.logoVariant == variant) colors.accentGhost else colors.bg1)
                                .clickable { onLogoVariant(variant) }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SkaldLogo(variant, 22.dp)
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(top = 26.dp)) {
                SectionHeader("Editor")
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Body size", style = Skald.type.row, color = colors.tx1, modifier = Modifier.weight(1f))
                    Stepper(settings.editorFontSize, onEditorFontSize)
                }
            }
        }

        item {
            Column(Modifier.padding(top = 26.dp)) {
                SectionHeader("Vault", snapshot.vaultName)
                StatRow("Notes", snapshot.stats.notes.toString())
                StatRow("Folders", snapshot.stats.folders.toString())
                StatRow("Threads", "${snapshot.stats.tasksOpen} open · ${snapshot.stats.tasksTotal} total")
                StatRow("Overdue", snapshot.stats.overdue.toString())
                StatRow("Wikilinks", "${snapshot.stats.resolved} resolved of ${snapshot.stats.wikilinks}")
                StatRow("Orphans", snapshot.stats.orphans.toString())
                StatRow("Daily folder", settings.dailyFolder)
                StatRow("Attachments", settings.attachmentsFolder)
            }
        }

        item {
            Column(Modifier.padding(top = 26.dp, bottom = 40.dp)) {
                SectionHeader("Sync")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Skald.metrics.card))
                        .background(colors.bg1)
                        .clickable(onClick = onOpenSync)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (syncStatus.configured) "Connected" else "Not connected",
                            style = Skald.type.row,
                            color = colors.tx0,
                        )
                        Text(
                            syncSubtitle(syncStatus),
                            style = Skald.type.meta,
                            color = when (syncStatus.phase) {
                                SyncPhase.Error -> colors.err
                                SyncPhase.Syncing -> colors.accent
                                else -> colors.tx3
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("›", style = Skald.type.meta, color = colors.tx4)
                }
            }
        }
    }
}

internal fun syncSubtitle(status: SyncStatus): String = when {
    !status.configured -> "Pair with a desktop vault through a GESH relay"
    status.lastError != null -> status.lastError!!
    status.phase == SyncPhase.Syncing -> "Syncing…"
    !status.enabled -> "Paused"
    status.pending > 0 -> "${status.pending} waiting to publish · ${status.tracked} tracked"
    else -> "Up to date · ${status.tracked} files tracked"
}

@Composable
private fun StatRow(label: String, value: String) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = Skald.type.row, color = Skald.colors.tx2, modifier = Modifier.weight(1f))
            Text(value, style = Skald.type.meta, color = Skald.colors.tx1)
        }
        Hairline()
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    val colors = Skald.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        StepButton("−") { onChange(value - 1) }
        Box(Modifier.padding(horizontal = 6.dp)) {
            Text(value.toString(), style = Skald.type.meta, color = colors.tx0)
        }
        StepButton("+") { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val colors = Skald.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bg3)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = Skald.type.row, color = colors.tx1)
    }
}
