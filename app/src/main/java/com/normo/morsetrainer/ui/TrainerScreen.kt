package com.normo.morsetrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normo.morsetrainer.MorseViewModel
import com.normo.morsetrainer.core.CardChart
import com.normo.morsetrainer.core.LearningOrder
import com.normo.morsetrainer.ui.theme.CardColors
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class Phase { IDLE, ASKING, FEEDBACK }

/**
 * Listening practice.
 *
 * A character is sent, you name it. Picks are weighted by your own history so the
 * letters you keep missing come round more often, and the chart only reveals the
 * answer's path once you have committed to a guess.
 */
@Composable
fun TrainerScreen(
    vm: MorseViewModel,
    modifier: Modifier = Modifier,
) {
    val settings = vm.settings
    val stats = vm.stats

    val pool = remember(settings.learningOrder, settings.kochLevel) {
        LearningOrder.of(settings.learningOrder).take(settings.kochLevel)
    }
    val poolSet = remember(pool) { pool.toSet() }

    var phase by remember { mutableStateOf(Phase.IDLE) }
    var target by remember { mutableStateOf<Char?>(null) }
    var guess by remember { mutableStateOf<Char?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }
    var answeredCount by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }

    fun ask() {
        guess = null
        phase = Phase.ASKING
        val next = pickWeighted(pool) { stats.weight(it) }
        target = next
        vm.playChar(next)
    }

    // Reset the round if the practice set changes under us.
    LaunchedEffect(poolSet) {
        if (phase != Phase.IDLE) {
            phase = Phase.IDLE
            target = null
            guess = null
        }
    }

    // Brief pause on the answer, then move on.
    LaunchedEffect(phase, target, guess) {
        if (phase == Phase.FEEDBACK) {
            delay(if (guess == target) 700 else 1400)
            ask()
        }
    }

    val playing = vm.playState
    val activePath = when {
        playing.playing && playing.partialCode.isNotEmpty() -> playing.partialCode
        phase == Phase.FEEDBACK -> target?.let { CardChart.node(it)?.code }.orEmpty()
        else -> ""
    }
    val wrongCode = if (phase == Phase.FEEDBACK && guess != target) {
        guess?.let { CardChart.node(it)?.code }
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        MorseChart(
            activePath = activePath,
            wrongCode = wrongCode,
            dimUnreached = true,
            enabledLetters = poolSet,
        )

        Spacer(Modifier.height(10.dp))

        ScoreBar(
            correct = correctCount,
            answered = answeredCount,
            streak = streak,
            best = bestStreak,
        )

        Spacer(Modifier.height(10.dp))

        PracticeSetControls(
            order = settings.learningOrder,
            level = settings.kochLevel,
            pool = pool,
            onOrderChange = { settings.learningOrder = it },
            onLevelChange = { settings.kochLevel = it },
        )

        Spacer(Modifier.height(12.dp))

        when (phase) {
            Phase.IDLE -> {
                Button(
                    onClick = {
                        correctCount = 0
                        answeredCount = 0
                        streak = 0
                        ask()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start")
                }
            }

            Phase.ASKING, Phase.FEEDBACK -> {
                FeedbackLine(phase = phase, target = target, guess = guess)

                Spacer(Modifier.height(10.dp))

                LetterGrid(
                    letters = pool,
                    enabled = phase == Phase.ASKING,
                    target = if (phase == Phase.FEEDBACK) target else null,
                    guess = guess,
                ) { picked ->
                    guess = picked
                    val hit = picked == target
                    target?.let { stats.record(it, hit) }
                    answeredCount += 1
                    if (hit) {
                        correctCount += 1
                        streak += 1
                        if (streak > bestStreak) bestStreak = streak
                    } else {
                        streak = 0
                    }
                    phase = Phase.FEEDBACK
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { target?.let(vm::playChar) },
                        enabled = phase == Phase.ASKING,
                    ) {
                        Text("Replay")
                    }
                    OutlinedButton(onClick = { vm.stop(); phase = Phase.IDLE }) {
                        Text("Stop")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        AccuracyTable(vm, pool)

        Spacer(Modifier.height(12.dp))
    }
}

/** Weighted pick without building a cumulative array — the pool is at most 26 items. */
private fun pickWeighted(pool: List<Char>, weight: (Char) -> Float): Char {
    if (pool.isEmpty()) return 'E'
    val total = pool.sumOf { weight(it).toDouble() }
    if (total <= 0.0) return pool.random()
    var threshold = Random.nextDouble(total)
    for (c in pool) {
        threshold -= weight(c)
        if (threshold <= 0.0) return c
    }
    return pool.last()
}

@Composable
private fun ScoreBar(correct: Int, answered: Int, streak: Int, best: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ScoreCell("Score", if (answered == 0) "—" else "$correct/$answered")
        ScoreCell("Streak", streak.toString())
        ScoreCell("Best", best.toString())
    }
}

@Composable
private fun ScoreCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = CardColors.Brass,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PracticeSetControls(
    order: LearningOrder.Order,
    level: Int,
    pool: List<Char>,
    onOrderChange: (LearningOrder.Order) -> Unit,
    onLevelChange: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LearningOrder.Order.entries.forEach { option ->
                    FilterChip(
                        selected = order == option,
                        onClick = { onOrderChange(option) },
                        label = { Text(option.label) },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "$level letters:  ${pool.joinToString(" ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Slider(
                value = level.toFloat(),
                onValueChange = { onLevelChange(it.toInt()) },
                valueRange = 2f..26f,
                steps = 23,
            )
        }
    }
}

@Composable
private fun FeedbackLine(phase: Phase, target: Char?, guess: Char?) {
    val text = when {
        phase == Phase.ASKING -> "Which letter?"
        guess == target -> "Correct — $target"
        else -> "$target, not $guess   ${target?.let { CardChart.node(it)?.code }?.let(::prettyCode).orEmpty()}"
    }
    val color = when {
        phase == Phase.ASKING -> MaterialTheme.colorScheme.onSurfaceVariant
        guess == target -> CardColors.Live
        else -> CardColors.Wrong
    }
    Text(text = text, color = color, fontSize = 18.sp, fontWeight = FontWeight.Medium)
}

/**
 * Answer buttons.
 *
 * Laid out in fixed rows of six rather than a flow row so the grid does not reshuffle
 * between rounds as the practice set grows.
 */
@Composable
private fun LetterGrid(
    letters: List<Char>,
    enabled: Boolean,
    target: Char?,
    guess: Char?,
    onPick: (Char) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        letters.sorted().chunked(6).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { letter ->
                    LetterButton(
                        letter = letter,
                        enabled = enabled,
                        isAnswer = letter == target,
                        isGuess = letter == guess && guess != target,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(letter) },
                    )
                }
                // Keep the last row's buttons the same width as every other row's.
                repeat(6 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LetterButton(
    letter: Char,
    enabled: Boolean,
    isAnswer: Boolean,
    isGuess: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        isAnswer -> CardColors.Live
        isGuess -> CardColors.Wrong
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        isAnswer || isGuess -> CardColors.Black
        else -> CardColors.Brass
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter.toString(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = foreground,
        )
    }
}

@Composable
private fun AccuracyTable(vm: MorseViewModel, pool: List<Char>) {
    val attempted = pool.filter { vm.stats.stat(it).attempts > 0 }
    if (attempted.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Accuracy",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            attempted.sortedBy { vm.stats.stat(it).accuracy }.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { letter ->
                        val stat = vm.stats.stat(letter)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = letter.toString(),
                                fontWeight = FontWeight.Bold,
                                color = CardColors.Brass,
                            )
                            Text(
                                text = "${(stat.accuracy * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
