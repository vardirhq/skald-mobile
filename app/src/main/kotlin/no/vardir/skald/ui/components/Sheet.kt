package no.vardir.skald.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import no.vardir.skald.ui.theme.Skald

/**
 * A sheet that rises from the bottom edge.
 *
 * The desktop can put a context menu wherever the pointer is; a phone has a
 * thumb that reaches the bottom third of the screen and not much else. So every
 * "what can I do with this" in the app arrives the same way — as a sheet with a
 * handle, a title that says what is being acted on, and rows big enough to hit.
 */
@Composable
fun SkaldSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Skald.colors
    val backdrop = remember { MutableInteractionSource() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The window is the whole screen, so the ground above the sheet is ours
        // to dismiss on — a dialog only closes on a touch outside its own window,
        // and there is no outside left.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = backdrop, indication = null, onClick = onDismiss)
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // The sheet is not the ground: a tap that lands on it stops
                    // here rather than falling through and closing it.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .clip(RoundedCornerShape(topStart = Skald.metrics.sheet, topEnd = Skald.metrics.sheet))
                    .background(colors.bgPop)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(top = 10.dp, bottom = 6.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.line3),
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = Skald.type.heading, color = colors.tx0, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = Skald.type.meta, color = colors.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButtonSlot(onDismiss, contentDescription = "Close") {
                        Text("✕", style = Skald.type.row, color = colors.tx2)
                    }
                }
                Hairline()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content,
                )
            }
        }
    }
}

/** One thing the sheet can do: a glyph, what it does, and why you would. */
@Composable
fun SheetAction(
    glyph: String,
    label: String,
    hint: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = Skald.colors
    val tint = when {
        !enabled -> colors.tx4
        destructive -> colors.err
        else -> colors.tx1
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(glyph, style = Skald.type.row, color = if (enabled) colors.tx3 else colors.tx4, modifier = Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = Skald.type.row, color = tint)
            if (hint != null) Text(hint, style = Skald.type.meta, color = colors.tx3)
        }
    }
}

/**
 * The one text field shape the app uses: sunken, hairlined, with the label
 * standing outside it rather than floating into it.
 */
@Composable
fun SkaldTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    mono: Boolean = false,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Default,
    onSubmit: (() -> Unit)? = null,
) {
    val colors = Skald.colors
    val style = (if (mono) Skald.type.code else Skald.type.row).copy(color = colors.tx0)
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg1)
            .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, style = style, color = colors.tx4, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = style,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { onSubmit?.invoke() },
                onGo = { onSubmit?.invoke() },
                onSend = { onSubmit?.invoke() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        )
    }
}

/** The label above a field, in the chrome voice. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Eyebrow(text, modifier.padding(top = 10.dp, bottom = 6.dp))
}

/** The pair of words a sheet ends on: dismiss on the left, commit on the right. */
@Composable
fun SheetButtons(
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    dismiss: String = "Cancel",
) {
    val colors = Skald.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dismiss,
            style = Skald.type.row,
            color = colors.tx2,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        )
        Text(
            confirm,
            style = Skald.type.row,
            color = if (enabled) colors.accent else colors.tx4,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = enabled, onClick = onConfirm)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}
