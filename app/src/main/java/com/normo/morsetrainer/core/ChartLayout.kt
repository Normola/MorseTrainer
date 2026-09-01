package com.normo.morsetrainer.core

/**
 * A pad placed on a chart.
 *
 * [x] and [y] are in grid units, not pixels. [label] is empty for a junction — a real
 * position in the tree that no character of the selected set happens to land on.
 */
data class LayoutNode(
    val code: String,
    val label: String,
    val x: Float,
    val y: Float,
) {
    val element: Element = if (code.last() == '.') Element.DIT else Element.DAH
    val parentCode: String = code.dropLast(1)
    val isCharacter: Boolean = label.isNotEmpty()
}

/**
 * Positioned pads plus the aerial they hang from.
 *
 * [compact] marks the hand-transcribed card, whose every edge is axis-aligned and whose
 * labels sit beside their pads. Generated layouts are drawn with elbow traces and labels
 * underneath instead.
 */
class ChartLayout(
    val nodes: List<LayoutNode>,
    val rootX: Float,
    val columns: Float,
    val rows: Float,
    val compact: Boolean,
) {
    val byCode: Map<String, LayoutNode> = nodes.associateBy { it.code }

    fun node(code: String): LayoutNode? = byCode[code]

    /** Grid position of a pad, or of the aerial for the empty code. */
    fun position(code: String): Pair<Float, Float>? = when {
        code.isEmpty() -> rootX to 0f
        else -> byCode[code]?.let { it.x to it.y }
    }
}

/** What the chart is showing. */
enum class ChartMode(val label: String) {
    CARD("Card"),
    LETTERS_DIGITS("+ Digits"),
    EVERYTHING("Everything"),
}

object ChartLayouts {

    /** The physical card, exactly as printed. */
    val card: ChartLayout by lazy {
        ChartLayout(
            nodes = CardChart.nodes.map {
                LayoutNode(
                    code = it.code,
                    label = it.letter.toString(),
                    x = (it.col - CardChart.MIN_COL).toFloat(),
                    y = it.row.toFloat(),
                )
            },
            rootX = (CardChart.ROOT_COL - CardChart.MIN_COL).toFloat(),
            columns = (CardChart.MAX_COL - CardChart.MIN_COL + 1).toFloat(),
            rows = (CardChart.MAX_ROW - CardChart.MIN_ROW + 1).toFloat(),
            compact = true,
        )
    }

    private val lettersAndDigits: ChartLayout by lazy {
        tidyTree(MorseCode.LETTERS + MorseCode.DIGITS)
    }

    private val everything: ChartLayout by lazy {
        tidyTree(MorseCode.ALL)
    }

    fun of(mode: ChartMode): ChartLayout = when (mode) {
        ChartMode.CARD -> card
        ChartMode.LETTERS_DIGITS -> lettersAndDigits
        ChartMode.EVERYTHING -> everything
    }

    /**
     * Lays out the pruned Morse tree for [chars].
     *
     * Dits branch left, dahs branch right, depth sets the row. Leaves take consecutive
     * columns and every parent is centred over its children, so the result is correct by
     * construction — there is nothing to hand-pack and nothing to get wrong when the
     * character set changes.
     *
     * Subtrees containing no selected character are pruned, which is what keeps this to
     * 25 columns rather than the 128 a full depth-7 tree would need.
     */
    fun tidyTree(chars: Map<Char, String>): ChartLayout {
        val labelOf: Map<String, String> = chars.entries.associate { it.value to it.key.toString() }

        // Every prefix of every code is a node you pass through.
        val present = HashSet<String>()
        for (code in chars.values) {
            for (i in 1..code.length) present.add(code.substring(0, i))
        }

        val xs = HashMap<String, Float>(present.size * 2)
        var nextLeaf = 0f

        // Iterative post-order so a deep punctuation branch cannot blow the stack.
        // removeAt rather than removeLast: the latter is ambiguous with the method
        // Java 21 added to List, which does not exist on older Android runtimes.
        val stack = ArrayList<Pair<String, Boolean>>()
        stack.add("" to false)
        while (stack.isNotEmpty()) {
            val (code, expanded) = stack.removeAt(stack.lastIndex)
            val kids = listOf("$code.", "$code-").filter(present::contains)

            if (kids.isEmpty()) {
                xs[code] = nextLeaf
                nextLeaf += 1f
                continue
            }
            if (!expanded) {
                // Revisit this node once its children have positions.
                stack.add(code to true)
                // Pushed in reverse so the dit child is processed first, and therefore
                // ends up to the left.
                for (kid in kids.asReversed()) stack.add(kid to false)
            } else {
                val kidXs = kids.mapNotNull(xs::get)
                xs[code] = (kidXs.min() + kidXs.max()) / 2f
            }
        }

        val nodes = present.map { code ->
            LayoutNode(
                code = code,
                label = labelOf[code].orEmpty(),
                x = xs.getValue(code),
                y = code.length.toFloat(),
            )
        }.sortedWith(compareBy({ it.y }, { it.x }))

        return ChartLayout(
            nodes = nodes,
            rootX = xs.getValue(""),
            columns = nextLeaf,
            rows = (nodes.maxOf { it.y } + 1f),
            compact = false,
        )
    }
}
