package com.normo.morsetrainer.core

/**
 * A node on the trainer card.
 *
 * [col] and [row] are grid coordinates copied from the physical card: column 0 is the
 * antenna's spine, negative columns run left (dah side of the root), positive run right
 * (dit side). Row 0 is the top row.
 *
 * The last symbol of [code] decides the drawn shape — a circle for a dit, a bar for a
 * dah — exactly as the card does it.
 */
data class CardNode(
    val letter: Char,
    val code: String,
    val col: Int,
    val row: Int,
) {
    val element: Element = if (code.last() == '.') Element.DIT else Element.DAH
    val parentCode: String = code.dropLast(1)
    val depth: Int = code.length
}

/**
 * The card's layout, transcribed node for node.
 *
 * Every edge here is either purely horizontal or purely vertical, which is what makes
 * the printed chart readable — the drawing code relies on that.
 */
object CardChart {

    const val MIN_COL = -3
    const val MAX_COL = 4
    const val MIN_ROW = 0
    const val MAX_ROW = 6

    /** Where the antenna sits. The root is not a character. */
    const val ROOT_COL = 0
    const val ROOT_ROW = 0

    val nodes: List<CardNode> = listOf(
        // Top row: dahs run left from the antenna, dits run right.
        CardNode('T', "-", -1, 0),
        CardNode('M', "--", -2, 0),
        CardNode('O', "---", -3, 0),
        CardNode('E', ".", 1, 0),
        CardNode('I', "..", 2, 0),
        CardNode('S', "...", 3, 0),
        CardNode('H', "....", 4, 0),

        // Under M.
        CardNode('G', "--.", -2, 1),
        CardNode('Q', "--.-", -3, 1),
        CardNode('Z', "--..", -2, 2),

        // Under I and S.
        CardNode('U', "..-", 2, 1),
        CardNode('V', "...-", 3, 1),
        CardNode('F', "..-.", 2, 2),

        // The long spine down from T.
        CardNode('N', "-.", -1, 3),
        CardNode('K', "-.-", -2, 3),
        CardNode('Y', "-.--", -3, 3),
        CardNode('C', "-.-.", -2, 4),
        CardNode('D', "-..", -1, 5),
        CardNode('X', "-..-", -2, 5),
        CardNode('B', "-...", -1, 6),

        // The long spine down from E.
        CardNode('A', ".-", 1, 3),
        CardNode('R', ".-.", 2, 3),
        CardNode('L', ".-..", 3, 3),
        CardNode('W', ".--", 1, 5),
        CardNode('P', ".--.", 2, 5),
        CardNode('J', ".---", 1, 6),
    )

    val byCode: Map<String, CardNode> = nodes.associateBy { it.code }
    val byLetter: Map<Char, CardNode> = nodes.associateBy { it.letter }

    /** Card letters in reading order, left to right then top to bottom. */
    val letters: List<Char> = nodes.sortedWith(compareBy({ it.row }, { it.col })).map { it.letter }

    fun node(code: String): CardNode? = byCode[code]

    fun node(letter: Char): CardNode? = byLetter[letter.uppercaseChar()]

    /** Grid position of a node, or of the antenna for the empty code. */
    fun position(code: String): Pair<Int, Int>? = when {
        code.isEmpty() -> ROOT_COL to ROOT_ROW
        else -> byCode[code]?.let { it.col to it.row }
    }

    /** Every prefix of [code], shortest first, excluding the empty root. */
    fun pathTo(code: String): List<String> =
        (1..code.length).map { code.substring(0, it) }
}

/**
 * Character orderings for the trainer.
 *
 * [KOCH] is the classic Koch sequence with non-letters removed, so it lines up with
 * what the card actually teaches. [CARD] walks the chart as printed, which means you
 * learn short codes before long ones.
 */
object LearningOrder {

    val KOCH: List<Char> =
        "KMRSUAPTLOWINJEFYVGQZHBCDX".toList()

    val CARD: List<Char> = CardChart.nodes.sortedBy { it.depth }.map { it.letter }

    val ALPHABETICAL: List<Char> = ('A'..'Z').toList()

    fun of(order: Order): List<Char> = when (order) {
        Order.KOCH -> KOCH
        Order.CARD -> CARD
        Order.ALPHABETICAL -> ALPHABETICAL
    }

    enum class Order(val label: String) {
        KOCH("Koch"),
        CARD("Card"),
        ALPHABETICAL("A–Z"),
    }
}
