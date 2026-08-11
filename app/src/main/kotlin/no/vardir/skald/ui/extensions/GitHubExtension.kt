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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.extensions.BuiltInExtensions
import no.vardir.skald.core.text.GitHubRepository
import no.vardir.skald.core.text.Markdown
import no.vardir.skald.ui.components.MarkdownContext
import no.vardir.skald.ui.theme.Skald

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
                repo ?: "Repository not connected",
                style = Skald.type.row.copy(fontWeight = FontWeight.SemiBold),
                color = if (repo == null) colors.err else colors.tx0,
            )
        }
        Text(
            if (url == null) "Add github: owner/repository to this note or name a repository in the callout."
            else "Open on GitHub · live repository details are available on desktop",
            style = Skald.type.meta,
            color = colors.tx3,
        )
    }
}
