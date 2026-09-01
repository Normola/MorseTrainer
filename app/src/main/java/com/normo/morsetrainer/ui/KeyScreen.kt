package com.normo.morsetrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normo.morsetrainer.MorseViewModel
import com.normo.morsetrainer.core.Element
import com.normo.morsetrainer.data.KeyMode
import com.normo.morsetrainer.input.KeyDecoder
import com.normo.morsetrainer.ui.theme.CardColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Send practice: key with your thumb and watch the card resolve what you sent.
 *
 * The decoder needs a clock as well as events — a character only ends when enough
 * silence has gone by — so a poll loop runs while the screen is up.
 */
@Composable
fun KeyScreen(
    vm: MorseViewModel,
    modifier: Modifier = Modifier,
) {
    val settings = vm.settings
    val scope = rememberCoroutineScope()

    val decoder = remember { KeyDecoder(settings.timing, settings.adaptiveKeying) }

    var buffer by remember { mutableStateOf("") }
    var decoded by remember { mutableStateOf("") }
    var keyDown by remember { mutableStateOf(false) }
    var gapProgress by remember { mutableFloatStateOf(0f) }
    var estimatedWpm by remember { mutableStateOf(settings.charWpm) }

    LaunchedEffect(settings.charWpm, settings.effectiveWpm, settings.adaptiveKeying) {
        decoder.adaptive = settings.adaptiveKeying
        decoder.timing = settings.timing
    }

    // Drives the gap-based decisions: end of character, end of word.
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            decoder.poll(now)?.let { result ->
                decoded += result.char?.toString() ?: "[${result.code}]"
                if (decoded.length > 400) decoded = decoded.takeLast(400)
            }
            buffer = decoder.buffer
            gapProgress = decoder.charGapProgress(now)
            estimatedWpm = decoder.estimatedWpm()
            delay(16)
        }
    }

    // Never leave the tone stuck on if the screen goes away mid-press.
    DisposableEffect(Unit) {
        onDispose { vm.tone.key(false) }
    }

    fun press() {
        vm.stop()
        keyDown = true
        vm.tone.key(true)
        decoder.press(System.currentTimeMillis())
        buffer = decoder.buffer
    }

    fun release() {
        keyDown = false
        vm.tone.key(false)
        decoder.release(System.currentTimeMillis())
        buffer = decoder.buffer
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        MorseChart(activePath = buffer, dimUnreached = true)

        Spacer(Modifier.height(10.dp))

        DecodeReadout(
            decoded = decoded,
            buffer = buffer,
            gapProgress = gapProgress,
            estimatedWpm = estimatedWpm,
            adaptive = settings.adaptiveKeying,
            onClear = {
                decoded = ""
                decoder.reset()
                buffer = ""
            },
            onBackspace = { decoded = decoded.dropLast(1) },
        )

        Spacer(Modifier.height(12.dp))

        when (settings.keyMode) {
            KeyMode.STRAIGHT -> StraightKey(
                keyDown = keyDown,
                onPress = { press() },
                onRelease = { release() },
            )

            KeyMode.PADDLES -> Paddles(
                keyDown = keyDown,
                onElement = { element ->
                    if (decoder.isDown) return@Paddles
                    scope.launch {
                        vm.stop()
                        keyDown = true
                        vm.tone.key(true)
                        decoder.press(System.currentTimeMillis())
                        delay(settings.timing.durationMs(element))
                        vm.tone.key(false)
                        keyDown = false
                        decoder.release(System.currentTimeMillis())
                        buffer = decoder.buffer
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DecodeReadout(
    decoded: String,
    buffer: String,
    gapProgress: Float,
    estimatedWpm: Int,
    adaptive: Boolean,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = decoded.ifEmpty { "…" },
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (buffer.isEmpty()) "—" else prettyCode(buffer),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    color = CardColors.Brass,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (adaptive) "~$estimatedWpm wpm" else "$estimatedWpm wpm",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // How close the current silence is to ending the character.
            LinearProgressIndicator(
                progress = { gapProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = CardColors.Brass,
                trackColor = MaterialTheme.colorScheme.surface,
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackspace) { Text("Delete") }
                OutlinedButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

/** Tap for a dit, hold for a dah — the decoder decides which from how long you held. */
@Composable
private fun StraightKey(
    keyDown: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    Column {
        Text(
            text = "Tap for a dit, hold for a dah. Pause to end a letter, pause longer for a space.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (keyDown) CardColors.Brass else MaterialTheme.colorScheme.surface)
                .border(
                    width = 2.dp,
                    color = CardColors.BrassDim,
                    shape = RoundedCornerShape(20.dp),
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onPress()
                        waitForUpOrCancellation()
                        onRelease()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "KEY",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (keyDown) CardColors.Black else CardColors.BrassDim,
            )
        }
    }
}

/** Two fixed-length paddles, for anyone who would rather not time their own holds. */
@Composable
private fun Paddles(
    keyDown: Boolean,
    onElement: (Element) -> Unit,
) {
    Column {
        Text(
            text = "Each paddle sends one perfectly timed element.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PaddleButton(
                label = "·",
                active = keyDown,
                modifier = Modifier.weight(1f),
                onClick = { onElement(Element.DIT) },
            )
            PaddleButton(
                label = "–",
                active = keyDown,
                modifier = Modifier.weight(1f),
                onClick = { onElement(Element.DAH) },
            )
        }
    }
}

@Composable
private fun PaddleButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) CardColors.Brass else MaterialTheme.colorScheme.surface)
            .border(2.dp, CardColors.BrassDim, RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onClick()
                    waitForUpOrCancellation()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 44.sp,
            textAlign = TextAlign.Center,
            color = if (active) CardColors.Black else CardColors.Brass,
        )
    }
}
