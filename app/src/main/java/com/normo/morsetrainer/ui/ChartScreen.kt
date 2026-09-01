package com.normo.morsetrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normo.morsetrainer.MorseViewModel
import com.normo.morsetrainer.core.CardNode
import com.normo.morsetrainer.ui.theme.CardColors

/**
 * The reference card.
 *
 * Tapping a pad sends that character and lights the path the card would have you
 * trace to reach it; while anything is playing the highlight follows the sender, so
 * the chart doubles as a live decoder for the app's own output.
 */
@Composable
fun ChartScreen(
    vm: MorseViewModel,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<CardNode?>(null) }

    val playing = vm.playState
    // While sending, the partial code is the source of truth so the trace animates
    // element by element; otherwise hold the last tapped node.
    val activePath = when {
        playing.playing && playing.partialCode.isNotEmpty() -> playing.partialCode
        else -> selected?.code.orEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        MorseChart(
            activePath = activePath,
            dimUnreached = activePath.isNotEmpty(),
            onNodeTap = { node ->
                selected = node
                vm.playChar(node.letter)
            },
        )

        Spacer(Modifier.height(12.dp))

        SelectionDetail(selected)

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Start at the aerial. A circle is a dit, a bar is a dah. " +
                "Follow the trace and read off the letter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SelectionDetail(node: CardNode?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = node?.letter?.toString() ?: "–",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = CardColors.Brass,
            )
            Column {
                Text(
                    text = node?.code?.let(::prettyCode) ?: "tap a pad",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (node != null) {
                    Text(
                        text = "${node.depth} element${if (node.depth == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Renders a code with the spacing operators actually read it with. */
fun prettyCode(code: String): String = code.map { if (it == '.') '·' else '–' }.joinToString(" ")
