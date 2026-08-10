package no.vardir.skald.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.text.Fuzzy
import no.vardir.skald.core.text.Suggest
import no.vardir.skald.ui.theme.Skald

/**
 * What the editor offers while you type, drawn as a row of chips right above
 * the keyboard.
 *
 * The desktop can afford a dropdown anchored to the caret; a phone cannot —
 * the caret is under a thumb and the bottom of the screen is the only place a
 * thumb reliably reaches. So the offers sit where the formatting bar sits, one
 * tap from being taken.
 */
@Composable
fun SuggestionBar(
    candidates: List<Suggest.Candidate>,
    onPick: (Suggest.Candidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    if (candidates.isEmpty()) return

    Column(modifier.fillMaxWidth().background(colors.bg1)) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (candidate in candidates) {
                Row(
                    Modifier
                        .widthIn(max = 230.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bg3)
                        .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(10.dp))
                        .clickable(onClickLabel = "Insert ${candidate.label}") { onPick(candidate) }
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    candidate.schema?.let { Rune(it, 14.dp) }
                    Column {
                        Text(
                            highlight(candidate.label, candidate.hit, colors.accent),
                            style = Skald.type.small,
                            color = colors.tx1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (candidate.detail.isNotEmpty()) {
                            Text(
                                candidate.detail,
                                style = Skald.type.metaSmall,
                                color = colors.tx3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The other half of the same idea: when the caret is on a checkbox, say so, and
 * put everything a thread can carry one tap away. Nobody discovers
 * `@due(2026-06-01)` by accident, and nobody types it correctly on a phone.
 */
@Composable
fun ThreadHintBar(summary: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Skald.colors
    Column(modifier.fillMaxWidth().background(colors.bg1)) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Thread options", onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("☐", style = Skald.type.meta, color = colors.accent)
            Text(
                summary,
                style = Skald.type.small,
                color = colors.tx2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("due · priority · tags ›", style = Skald.type.metaSmall, color = colors.accent)
        }
    }
}

private fun highlight(text: String, indices: List<Int>, accent: androidx.compose.ui.graphics.Color) =
    buildAnnotatedString {
        for (segment in Fuzzy.highlight(text, indices)) {
            if (segment.hit) {
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(segment.text) }
            } else {
                append(segment.text)
            }
        }
    }
