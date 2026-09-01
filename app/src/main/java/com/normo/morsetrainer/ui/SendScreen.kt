package com.normo.morsetrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normo.morsetrainer.MorseViewModel
import com.normo.morsetrainer.core.MorseCode
import com.normo.morsetrainer.ui.theme.CardColors

/**
 * Type something and send it — on the sidetone, and optionally on the camera flash
 * and the vibrator, which is what makes this useful away from the phone's speaker.
 */
@Composable
fun SendScreen(
    vm: MorseViewModel,
    modifier: Modifier = Modifier,
) {
    val settings = vm.settings
    var text by remember { mutableStateOf("CQ CQ DE M0RSE") }

    val state = vm.playState
    val sanitized = remember(text) { MorseCode.sanitize(text) }

    // Output sinks are read by the player on a background thread, so push changes eagerly.
    LaunchedEffect(settings.torchOutput, settings.vibrationOutput) {
        vm.applyAudioSettings()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        MorseChart(
            activePath = if (state.playing) state.partialCode else "",
            dimUnreached = state.playing,
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        MorseTranscript(sanitized, state.charIndex)

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Lamp(lit = state.keyDown)

            if (state.playing) {
                OutlinedButton(onClick = vm::stop, modifier = Modifier.weight(1f)) {
                    Text("Stop")
                }
            } else {
                Button(
                    onClick = { vm.play(text) },
                    enabled = sanitized.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Send")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutputToggles(vm)

        Spacer(Modifier.height(12.dp))
    }
}

/** The message rendered as Morse, with the character being sent picked out. */
@Composable
private fun MorseTranscript(text: String, activeIndex: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            if (text.isEmpty()) {
                Text(
                    text = "Nothing sendable yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            text.forEachIndexed { index, ch ->
                if (ch == ' ') return@forEachIndexed
                val code = MorseCode.codeFor(ch) ?: return@forEachIndexed
                val active = index == activeIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = ch.toString(),
                        fontWeight = FontWeight.Bold,
                        color = if (active) CardColors.BrassBright else CardColors.Brass,
                    )
                    Text(
                        text = prettyCode(code),
                        fontFamily = FontFamily.Monospace,
                        color = if (active) {
                            CardColors.BrassBright
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Lamp(lit: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (lit) CardColors.BrassBright else Color(0xFF2A2521)),
    )
}

@Composable
private fun OutputToggles(vm: MorseViewModel) {
    val settings = vm.settings

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            ToggleRow(
                label = "Camera flash",
                subtitle = if (vm.outputs.hasTorch) null else "No flash on this device",
                checked = settings.torchOutput && vm.outputs.hasTorch,
                enabled = vm.outputs.hasTorch,
                onCheckedChange = settings::setTorchOutput,
            )
            ToggleRow(
                label = "Vibration",
                subtitle = if (vm.outputs.hasVibrator) null else "No vibrator on this device",
                checked = settings.vibrationOutput && vm.outputs.hasVibrator,
                enabled = vm.outputs.hasVibrator,
                onCheckedChange = settings::setVibrationOutput,
            )
        }
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
