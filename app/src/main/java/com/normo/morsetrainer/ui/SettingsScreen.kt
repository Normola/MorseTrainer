package com.normo.morsetrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normo.morsetrainer.MorseViewModel
import com.normo.morsetrainer.data.KeyMode
import com.normo.morsetrainer.ui.theme.CardColors

@Composable
fun SettingsScreen(
    vm: MorseViewModel,
    modifier: Modifier = Modifier,
) {
    val settings = vm.settings

    // The engine reads these fields directly from its render thread.
    LaunchedEffect(settings.toneHz, settings.volume) {
        vm.applyAudioSettings()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSection("Speed") {
            SliderRow(
                label = "Character speed",
                value = settings.charWpm.toFloat(),
                range = 5f..40f,
                steps = 34,
                display = "${settings.charWpm} wpm",
                onChange = { settings.charWpm = it.toInt() },
            )
            SliderRow(
                label = "Overall speed",
                value = settings.effectiveWpm.toFloat(),
                range = 5f..40f,
                steps = 34,
                display = "${settings.effectiveWpm} wpm",
                onChange = { settings.effectiveWpm = it.toInt() },
            )
            Text(
                text = if (settings.effectiveWpm < settings.charWpm) {
                    "Farnsworth spacing: characters at ${settings.charWpm} wpm with longer gaps " +
                        "between them. Learn the sound of a letter at speed, then close the gaps."
                } else {
                    "Standard spacing."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "dit ${settings.timing.ditMs} ms · " +
                    "letter gap ${settings.timing.charGapMs} ms · " +
                    "word gap ${settings.timing.wordGapMs} ms",
                style = MaterialTheme.typography.labelSmall,
                color = CardColors.Brass,
            )
        }

        SettingsSection("Tone") {
            SliderRow(
                label = "Pitch",
                value = settings.toneHz.toFloat(),
                range = 300f..1200f,
                steps = 0,
                display = "${settings.toneHz} Hz",
                onChange = { settings.toneHz = it.toInt() },
            )
            SliderRow(
                label = "Volume",
                value = settings.volume,
                range = 0f..1f,
                steps = 0,
                display = "${(settings.volume * 100).toInt()}%",
                onChange = { settings.volume = it },
            )
            OutlinedButton(onClick = { vm.play("V") }) {
                Text("Test tone")
            }
        }

        SettingsSection("Keying") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.keyMode == mode,
                        onClick = { settings.keyMode = mode },
                        label = { Text(mode.label) },
                    )
                }
            }
            ToggleRow(
                label = "Follow my speed",
                subtitle = "Judge dit against dah from how you actually key, " +
                    "not from the speed setting",
                checked = settings.adaptiveKeying,
                onCheckedChange = { settings.adaptiveKeying = it },
            )
        }

        SettingsSection("Progress") {
            val snapshot = vm.stats.snapshot()
            val attempts = snapshot.values.sumOf { it.attempts }
            val correct = snapshot.values.sumOf { it.correct }
            Text(
                text = if (attempts == 0) {
                    "No quiz history yet."
                } else {
                    "$correct correct out of $attempts, across ${snapshot.size} letters."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = vm.stats::clear) {
                Text("Reset progress")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = CardColors.Brass,
            )
            content()
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = display,
                color = CardColors.Brass,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}
