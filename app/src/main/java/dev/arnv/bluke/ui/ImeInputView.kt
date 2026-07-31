package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/**
 * Result of reconciling the IME buffer against what the host has already received.
 *
 * Held separately from the composable so the diffing logic stays testable and so the
 * "what could not be typed" reporting has somewhere to live.
 */
data class ImeDiff(
    val backspaces: Int,
    val strokes: List<HidCharMap.Stroke>,
    /** Characters that had no US-layout equivalent and were dropped, for user feedback. */
    val droppedChars: String
)

/**
 * Works out the keystrokes that turn [sent] (what the host currently believes was typed) into
 * [target] (what the IME buffer now holds).
 *
 * The IME is free to rewrite text it already gave us - that is exactly what autocorrect and word
 * suggestions do - so a plain append is not enough. We keep the common prefix, backspace away the
 * rest, and retype the tail.
 */
fun computeImeDiff(sent: String, target: String): ImeDiff {
    var prefix = 0
    while (prefix < sent.length && prefix < target.length && sent[prefix] == target[prefix]) {
        prefix++
    }
    val backspaces = sent.length - prefix
    val strokes = mutableListOf<HidCharMap.Stroke>()
    val dropped = StringBuilder()
    for (c in target.substring(prefix)) {
        val expanded = HidCharMap.strokesFor(c)
        if (expanded.isEmpty()) {
            dropped.append(c)
        } else {
            strokes.addAll(expanded)
        }
    }
    return ImeDiff(backspaces, strokes, dropped.toString())
}

/**
 * Full-screen surface that hands typing over to whichever IME the user has installed.
 *
 * Bluke's own keycaps send scancodes directly, which means no autocorrect, no word suggestions and
 * no access to the IME's clipboard or translate panels. Here we instead host a text field, let the
 * system keyboard do its work in it, and forward the committed text out over HID. Composing text
 * (the underlined in-progress word) is deliberately not forwarded until the IME commits it,
 * so suggestions and autocorrect settle locally instead of spraying corrections at the host.
 */
@Composable
fun ImeInputView(
    palette: KeyboardPalette,
    isConnected: Boolean,
    onStroke: (keyCode: Int, isPress: Boolean) -> Unit,
    onExitImeMode: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    // What we have already transmitted to the host, mirrored locally so edits can be reconciled.
    var sentText by remember { mutableStateOf("") }
    var droppedNotice by remember { mutableStateOf<String?>(null) }

    // A single unbounded queue drained by one long-lived consumer. Autocorrect can rewrite a whole
    // word at once, so bursts of dozens of strokes are normal; they must go out in order, and
    // nothing may be dropped if another burst arrives mid-drain.
    val strokeQueue = remember { Channel<Pair<Int, Boolean>>(Channel.UNLIMITED) }

    // Bring the system keyboard up as soon as this view appears - it is the whole point of the mode.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // A short delay lets the field finish attaching before we ask for the IME, otherwise
        // some OEM keyboards ignore the request.
        delay(150)
        keyboardController?.show()
    }

    // Pace the stream so hosts do not coalesce fast repeats of the same key.
    LaunchedEffect(strokeQueue) {
        for ((code, press) in strokeQueue) {
            onStroke(code, press)
            delay(4)
        }
    }

    fun queue(strokes: List<Pair<Int, Boolean>>) {
        strokes.forEach { strokeQueue.trySend(it) }
    }

    // Leaving the mode mid-drain could abandon a held Shift, which would leave the host stuck in
    // uppercase. Releasing it unconditionally on teardown is harmless if it was already up.
    DisposableEffect(Unit) {
        onDispose {
            strokeQueue.close()
            onStroke(KeyboardLayouts.MOD_LSHIFT, false)
        }
    }

    fun transmit(diff: ImeDiff) {
        val out = mutableListOf<Pair<Int, Boolean>>()
        repeat(diff.backspaces) {
            out.add(KeyboardLayouts.KEY_BACKSPACE to true)
            out.add(KeyboardLayouts.KEY_BACKSPACE to false)
        }
        var shiftHeld = false
        for (stroke in diff.strokes) {
            if (stroke.shift && !shiftHeld) {
                out.add(KeyboardLayouts.MOD_LSHIFT to true)
                shiftHeld = true
            } else if (!stroke.shift && shiftHeld) {
                out.add(KeyboardLayouts.MOD_LSHIFT to false)
                shiftHeld = false
            }
            out.add(stroke.keyCode to true)
            out.add(stroke.keyCode to false)
        }
        if (shiftHeld) {
            out.add(KeyboardLayouts.MOD_LSHIFT to false)
        }
        queue(out)
        droppedNotice = diff.droppedChars.takeIf { it.isNotEmpty() }
    }

    // Sends a bare key (Enter, arrows, ...) that has no place in the text buffer.
    fun tapKey(keyCode: Int) {
        queue(listOf(keyCode to true, keyCode to false))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgCode)
            .imePadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "System Keyboard",
                    color = palette.alphaLegend,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isConnected) {
                        "Typing here is sent to the host as it is committed"
                    } else {
                        "Not connected - nothing is being sent"
                    },
                    color = palette.alphaLegend.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable {
                        keyboardController?.hide()
                        onExitImeMode()
                    }
                    .padding(horizontal = 10.dp)
                    .testTag("exit_ime_mode_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Bluke Keys",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // The staging buffer. Text lands here first so the IME can correct it in place; every
        // change is diffed against what the host already has.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            if (fieldValue.text.isEmpty()) {
                Text(
                    text = "Start typing with your own keyboard...",
                    color = palette.alphaLegend.copy(alpha = 0.35f),
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    val diff = computeImeDiff(sentText, newValue.text)
                    if (diff.backspaces > 0 || diff.strokes.isNotEmpty() || diff.droppedChars.isNotEmpty()) {
                        transmit(diff)
                        // Dropped characters never reached the host, so they must not be recorded
                        // as sent - otherwise every later edit would mis-count its backspaces.
                        sentText = newValue.text.filter { HidCharMap.strokesFor(it).isNotEmpty() }
                    }
                    fieldValue = newValue
                },
                textStyle = TextStyle(
                    color = palette.alphaLegend,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                cursorBrush = SolidColor(palette.accentBg),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        // Enter is a key, not text: send it through and clear the staging buffer
                        // so the next line starts from a known-empty state on both ends.
                        tapKey(KeyboardLayouts.KEY_ENTER)
                        fieldValue = TextFieldValue("")
                        sentText = ""
                    }
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .focusRequester(focusRequester)
                    .testTag("ime_input_field")
            )
        }

        droppedNotice?.let { dropped ->
            Text(
                text = "Skipped \"$dropped\" - no equivalent key on a US layout",
                color = Color(0xFFFFB74D),
                fontSize = 10.sp,
                modifier = Modifier.testTag("ime_dropped_notice")
            )
        }

        // Keys the IME cannot express as text but that a physical keyboard user still needs.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ImeAuxKey("Esc", Modifier.weight(1f)) { tapKey(KeyboardLayouts.KEY_ESC) }
            ImeAuxKey("Tab", Modifier.weight(1f)) { tapKey(KeyboardLayouts.KEY_TAB) }
            ImeAuxKey("Enter", Modifier.weight(1f)) {
                tapKey(KeyboardLayouts.KEY_ENTER)
                fieldValue = TextFieldValue("")
                sentText = ""
            }
            ImeAuxKey("←", Modifier.weight(1f)) { tapKey(KeyboardLayouts.KEY_LEFT) }
            ImeAuxKey("↑", Modifier.weight(1f)) { tapKey(KeyboardLayouts.KEY_UP) }
            ImeAuxKey("↓", Modifier.weight(1f)) { tapKey(KeyboardLayouts.KEY_DOWN) }
            ImeAuxKey("→", Modifier.weight(1f)) { tapKey(KeyboardLayouts.KEY_RIGHT) }
        }

        // Cursor keys move the host's caret away from our staging buffer's tail, so the mirrored
        // "already sent" text is no longer a safe basis for diffing. Clearing keeps the two in step.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ImeAuxKey("Clear staged text", Modifier.weight(1f)) {
                fieldValue = TextFieldValue("")
                sentText = ""
                droppedNotice = null
            }
        }
    }
}

@Composable
private fun ImeAuxKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .testTag("ime_aux_$label"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
