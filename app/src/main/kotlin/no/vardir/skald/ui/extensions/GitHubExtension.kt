package no.vardir.skald.ui.extensions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.extensions.BuiltInExtensions
import no.vardir.skald.core.extensions.github.GitHubRepositoryCard
import no.vardir.skald.core.text.GitHubRepository
import no.vardir.skald.core.text.Markdown
import no.vardir.skald.data.GitHubService
import no.vardir.skald.ui.components.MarkdownContext
import no.vardir.skald.ui.theme.Skald

val LocalGitHubService = staticCompositionLocalOf<GitHubService?> { null }

val githubRendererExtension = RendererExtension(
    descriptor = BuiltInExtensions.GitHub,
    markdownComponents = listOf(
        MarkdownComponentContribution("github") { block, context ->
            val explicit = GitHubRepository.normalize(Markdown.plainText(block.content))
            val inherited = GitHubRepository.normalize(context.frontmatter["github"])
            GitHubRepositoryCard(explicit ?: inherited, context)
        },
    ),
)

@Composable
private fun GitHubRepositoryCard(repo: String?, ctx: MarkdownContext) {
    val colors = Skald.colors
    val url = repo?.let(GitHubRepository::url)
    val service = LocalGitHubService.current
    var card by remember(repo) { mutableStateOf<GitHubRepositoryCard?>(null) }
    var error by remember(repo) { mutableStateOf<String?>(null) }
    var loading by remember(repo) { mutableStateOf(repo != null && service != null) }
    var refresh by remember(repo) { mutableIntStateOf(0) }

    LaunchedEffect(repo, service, refresh) {
        if (repo == null || service == null) return@LaunchedEffect
        loading = true
        error = null
        runCatching { service.repository(repo, force = refresh > 0) }
            .onSuccess { card = it }
            .onFailure { error = it.message ?: "GitHub repository data is unavailable" }
        loading = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .clip(RoundedCornerShape(Skald.metrics.card - 2.dp))
            .background(colors.bg1)
            .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(Skald.metrics.card - 2.dp))
            .then(
                if (url != null) Modifier.clickable(onClickLabel = "Open $repo on GitHub") {
                    ctx.openExternal(url)
                } else Modifier,
            )
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "GH",
                style = Skald.type.metaSmall,
                color = colors.bg0,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(colors.tx0)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                card?.let { "${it.owner}/${it.name}" } ?: repo ?: "Repository not connected",
                style = Skald.type.row.copy(fontWeight = FontWeight.SemiBold),
                color = if (repo == null) colors.err else colors.tx0,
                modifier = Modifier.weight(1f),
            )
            if (repo != null && service != null) {
                Text(
                    if (loading) "refreshing…" else "refresh",
                    style = Skald.type.metaSmall,
                    color = if (loading) colors.tx4 else colors.accent,
                    modifier = Modifier.clip(RoundedCornerShape(7.dp)).clickable(enabled = !loading) { refresh++ }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        when {
            url == null -> Text(
                "Add github: owner/repository to this note or name a repository in the callout.",
                style = Skald.type.meta,
                color = colors.tx3,
            )
            card != null -> RepositoryDetails(card!!)
            error != null -> {
                Text(error!!, style = Skald.type.meta, color = colors.err)
                Text("Tap refresh to try again · tap the card to open GitHub", style = Skald.type.metaSmall, color = colors.tx4)
            }
            else -> Text("Loading repository…", style = Skald.type.meta, color = colors.tx3)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RepositoryDetails(card: GitHubRepositoryCard) {
    val colors = Skald.colors
    card.description?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = Skald.type.meta, color = colors.tx2)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("★ ${card.stars}", style = Skald.type.metaSmall, color = colors.tx2)
        Text("forks ${card.forks}", style = Skald.type.metaSmall, color = colors.tx2)
        Text("issues ${card.openIssues}", style = Skald.type.metaSmall, color = colors.tx2)
        card.openPullRequests?.let { Text("PRs $it", style = Skald.type.metaSmall, color = colors.tx2) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(card.visibility, style = Skald.type.metaSmall, color = colors.tx3)
        Text(card.defaultBranch, style = Skald.type.metaSmall, color = colors.tx3)
        card.language?.let { Text(it, style = Skald.type.metaSmall, color = colors.tx3) }
        card.license?.let { Text(it, style = Skald.type.metaSmall, color = colors.tx3) }
    }
    card.workflow?.let {
        val result = it.conclusion ?: it.status
        Text("${it.name}: $result", style = Skald.type.metaSmall, color = if (result == "success") colors.ok else colors.tx3)
    }
    card.latestRelease?.let {
        Text("release ${it.tag.ifBlank { it.name }}", style = Skald.type.metaSmall, color = colors.tx3)
    }
    Text(
        if (card.stale) "Offline · showing cached data" else "Live GitHub data",
        style = Skald.type.metaSmall,
        color = if (card.stale) colors.warn else colors.tx4,
    )
}
