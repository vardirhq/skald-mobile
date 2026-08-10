package no.vardir.skald.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import no.vardir.skald.ui.theme.Skald

/** A hairline. The design uses these instead of shadows almost everywhere. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Skald.colors.line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/**
 * The chrome voice: mono, small, wide-tracked, upper-cased — with an optional
 * quieter count trailing it.
 */
@Composable
fun SectionHeader(label: String, count: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label.uppercase(), style = Skald.type.eyebrow, color = Skald.colors.tx2)
        if (count != null) Text(count, style = Skald.type.meta, color = Skald.colors.tx3)
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Skald.colors.tx3) {
    Text(text.uppercase(), style = Skald.type.eyebrow, color = color, modifier = modifier)
}

/**
 * A raised row: `bg1` on a hairline, rounded, tappable.
 *
 * A long press is the phone's right-click, so every card that stands for
 * something — a note, a thread — can offer what else can be done with it
 * without spending width on a button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SkaldCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = Skald.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Skald.metrics.card))
            .background(colors.bg1)
            .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(Skald.metrics.card))
            .let {
                when {
                    onClick == null -> it
                    onLongClick == null -> it.clickable(onClick = onClick)
                    else -> it.combinedClickable(onLongClick = onLongClick, onClick = onClick)
                }
            }
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A list row separated by a hairline rather than a card edge. */
@Composable
fun SkaldRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        Hairline()
    }
}

/** The task checkbox: a rounded square that fills with the accent when done. */
@Composable
fun SkaldCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 22.dp,
    /** Working and blocked threads read as their status colour rather than as done. */
    tint: Color? = null,
) {
    val colors = Skald.colors
    val fill = tint ?: colors.accent
    val progress by animateFloatAsState(if (checked) 1f else 0f, label = "check")

    androidx.compose.foundation.Canvas(
        modifier
            .size(boxSize)
            .clickable(
                role = Role.Checkbox,
                onClickLabel = if (checked) "Reopen thread" else "Complete thread",
            ) { onCheckedChange(!checked) }
    ) {
        val corner = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx())
        if (progress > 0f) {
            drawRoundRect(fill.copy(alpha = progress), cornerRadius = corner)
        }
        drawRoundRect(
            color = if (progress > 0.5f) fill else colors.line3,
            cornerRadius = corner,
            style = Stroke(1.5.dp.toPx()),
        )
        if (progress > 0f) {
            // The tick, drawn as the CSS draws it: two borders of a rotated box.
            val w = this.size.width
            rotate(43f, pivot = Offset(w * 0.44f, w * 0.36f)) {
                drawLine(
                    colors.onAccent.copy(alpha = progress),
                    Offset(w * 0.44f, w * 0.14f),
                    Offset(w * 0.44f, w * 0.59f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Square,
                )
                drawLine(
                    colors.onAccent.copy(alpha = progress),
                    Offset(w * 0.44f, w * 0.59f),
                    Offset(w * 0.20f, w * 0.59f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Square,
                )
            }
        }
    }
}

/** The segmented control from the design: a sunken track with a raised selection. */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(colors.bg1)
            .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(11.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((value, label) in options) {
            val isSelected = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colors.bg3 else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = Skald.type.small,
                    color = if (isSelected) colors.tx0 else colors.tx2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A rounded filter chip, as the search sheet uses. */
@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Skald.colors
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.accentGhost else colors.bg3)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(label, style = Skald.type.small, color = if (selected) colors.accent else colors.tx2)
    }
}

/** Priority, in the mono chrome voice — high is the only one that raises its voice. */
@Composable
fun PriorityMark(priority: no.vardir.skald.core.model.TaskPriority) {
    val colors = Skald.colors
    Text(
        priority.token.uppercase(),
        style = Skald.type.metaSmall,
        color = if (priority == no.vardir.skald.core.model.TaskPriority.High) colors.warn else colors.tx3,
    )
}

/** A tappable icon target that always clears 44dp, per the mobile spec. */
@Composable
fun IconButtonSlot(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** An empty state that says what to do next rather than only what is missing. */
@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = Skald.type.row, color = Skald.colors.tx2)
        Text(hint, style = Skald.type.small, color = Skald.colors.tx3)
    }
}
