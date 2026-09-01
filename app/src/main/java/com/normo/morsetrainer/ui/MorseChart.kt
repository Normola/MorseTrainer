package com.normo.morsetrainer.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.normo.morsetrainer.core.CardChart
import com.normo.morsetrainer.core.CardNode
import com.normo.morsetrainer.core.Element
import com.normo.morsetrainer.ui.theme.CardColors
import kotlin.math.abs

/**
 * Maps the card's integer grid onto pixels.
 *
 * The grid is inset by half a cell on each side, with a title band above and a footer
 * band below, matching the printed layout.
 */
private class ChartMetrics(val cell: Float) {
    val cellH = cell * 0.92f
    val gridLeft = cell * 0.5f
    val gridTop = cell * 1.05f

    fun cx(col: Int): Float = gridLeft + (col - CardChart.MIN_COL + 0.5f) * cell
    fun cy(row: Int): Float = gridTop + (row + 0.5f) * cellH

    val totalHeight: Float = gridTop + (CardChart.MAX_ROW + 1) * cellH + cell * 1.15f

    val ditRadius = cell * 0.165f
    val dahWidth = cell * 0.46f
    val dahHeight = cell * 0.205f
    val traceWidth = (cell * 0.035f).coerceAtLeast(1.5f)
    val hitRadius = cell * 0.42f
}

/**
 * The trainer card, drawn to scale.
 *
 * [activePath] highlights the walk from the antenna down to that code — pass "-." and
 * the root-to-T and T-to-N traces light up along with both pads. [wrongCode] flashes a
 * single pad in the error colour, which the quiz uses for a miss. Passing
 * [enabledLetters] greys out everything outside the current practice set.
 */
@Composable
fun MorseChart(
    modifier: Modifier = Modifier,
    activePath: String = "",
    wrongCode: String? = null,
    dimUnreached: Boolean = false,
    enabledLetters: Set<Char>? = null,
    onNodeTap: (CardNode) -> Unit = {},
) {
    val density = LocalDensity.current

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }
    val titlePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
        }
    }

    val litCodes = remember(activePath) { CardChart.pathTo(activePath).toSet() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Nine columns' worth of width: eight grid columns plus half a cell of margin.
        val cell = with(density) { maxWidth.toPx() } / 9f
        val metrics = remember(cell) { ChartMetrics(cell) }
        val heightDp = with(density) { metrics.totalHeight.toDp() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
                .pointerInput(metrics) {
                    detectTapGestures { tap -> hitTest(metrics, tap)?.let(onNodeTap) }
                },
        ) {
            drawPlate(metrics)
            drawTitle(metrics, titlePaint)
            drawEdges(metrics, litCodes, activePath, dimUnreached, enabledLetters)
            drawAntenna(metrics, activePath.isNotEmpty())
            drawNodes(
                metrics = metrics,
                litCodes = litCodes,
                activePath = activePath,
                wrongCode = wrongCode,
                dimUnreached = dimUnreached,
                enabledLetters = enabledLetters,
                labelPaint = labelPaint,
            )
            drawFooterGlyph(metrics)
        }
    }
}

private fun hitTest(metrics: ChartMetrics, tap: Offset): CardNode? =
    CardChart.nodes.firstOrNull { node ->
        abs(tap.x - metrics.cx(node.col)) <= metrics.hitRadius &&
            abs(tap.y - metrics.cy(node.row)) <= metrics.hitRadius
    }

private fun DrawScope.drawPlate(metrics: ChartMetrics) {
    val inset = metrics.cell * 0.10f
    val corner = metrics.cell * 0.42f

    // Cream edge.
    drawRoundRect(
        color = CardColors.Cream,
        topLeft = Offset.Zero,
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    // Black anodised face.
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(CardColors.PlateTop, CardColors.PlateBottom)),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(corner - inset),
    )
}

private fun DrawScope.drawTitle(metrics: ChartMetrics, paint: Paint) {
    paint.textSize = metrics.cell * 0.42f
    paint.color = CardColors.Cream.toArgb()
    drawContext.canvas.nativeCanvas.drawText(
        "MORSE CODE",
        size.width / 2f,
        metrics.cell * 0.72f,
        paint,
    )
}

/**
 * Every edge on the card is axis-aligned, so a straight line from parent centre to
 * child centre is all that is ever needed.
 */
private fun DrawScope.drawEdges(
    metrics: ChartMetrics,
    litCodes: Set<String>,
    activePath: String,
    dimUnreached: Boolean,
    enabledLetters: Set<Char>?,
) {
    CardChart.nodes.forEach { node ->
        val parentPos = CardChart.position(node.parentCode) ?: return@forEach
        val start = Offset(metrics.cx(parentPos.first), metrics.cy(parentPos.second))
        val end = Offset(metrics.cx(node.col), metrics.cy(node.row))

        val lit = node.code in litCodes
        val enabled = enabledLetters?.contains(node.letter) ?: true
        val color = when {
            lit -> CardColors.BrassBright
            !enabled -> CardColors.Trace.copy(alpha = 0.15f)
            dimUnreached && activePath.isNotEmpty() -> CardColors.Trace.copy(alpha = 0.20f)
            else -> CardColors.Trace.copy(alpha = 0.55f)
        }

        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = if (lit) metrics.traceWidth * 2f else metrics.traceWidth,
        )
    }
}

/** The little aerial at the top of the card, where every character starts. */
private fun DrawScope.drawAntenna(metrics: ChartMetrics, live: Boolean) {
    val cx = metrics.cx(CardChart.ROOT_COL)
    val cy = metrics.cy(CardChart.ROOT_ROW)
    val color = if (live) CardColors.BrassBright else CardColors.Cream
    val stroke = metrics.traceWidth * 1.2f
    val mastTop = cy - metrics.cell * 0.62f

    drawLine(color, Offset(cx, mastTop), Offset(cx, cy), strokeWidth = stroke)

    // Downward-pointing dipole triangle.
    val half = metrics.cell * 0.26f
    val apexY = mastTop + metrics.cell * 0.34f
    drawLine(color, Offset(cx - half, mastTop), Offset(cx + half, mastTop), strokeWidth = stroke)
    drawLine(color, Offset(cx - half, mastTop), Offset(cx, apexY), strokeWidth = stroke)
    drawLine(color, Offset(cx + half, mastTop), Offset(cx, apexY), strokeWidth = stroke)
}

private fun DrawScope.drawNodes(
    metrics: ChartMetrics,
    litCodes: Set<String>,
    activePath: String,
    wrongCode: String?,
    dimUnreached: Boolean,
    enabledLetters: Set<Char>?,
    labelPaint: Paint,
) {
    labelPaint.textSize = metrics.cell * 0.34f

    CardChart.nodes.forEach { node ->
        val cx = metrics.cx(node.col)
        val cy = metrics.cy(node.row)

        val isCurrent = node.code == activePath
        val isLit = node.code in litCodes
        val isWrong = node.code == wrongCode
        val enabled = enabledLetters?.contains(node.letter) ?: true

        val fill = when {
            isWrong -> CardColors.Wrong
            isCurrent -> CardColors.BrassBright
            isLit -> CardColors.Brass
            !enabled -> CardColors.BrassDim.copy(alpha = 0.30f)
            dimUnreached && activePath.isNotEmpty() -> CardColors.BrassDim.copy(alpha = 0.45f)
            else -> CardColors.Brass
        }

        // Halo behind the pad being sent right now.
        if (isCurrent || isWrong) {
            drawCircle(
                color = fill.copy(alpha = 0.25f),
                radius = metrics.cell * 0.40f,
                center = Offset(cx, cy),
            )
        }

        when (node.element) {
            Element.DIT -> drawCircle(
                color = fill,
                radius = metrics.ditRadius,
                center = Offset(cx, cy),
            )

            Element.DAH -> drawRoundRect(
                color = fill,
                topLeft = Offset(cx - metrics.dahWidth / 2f, cy - metrics.dahHeight / 2f),
                size = Size(metrics.dahWidth, metrics.dahHeight),
                cornerRadius = CornerRadius(metrics.dahHeight / 2f),
            )
        }

        drawNodeLabel(node, metrics, cx, cy, labelPaint, isCurrent, isLit, enabled)
    }
}

/**
 * Labels sit above a pad reached along a horizontal trace and to the right of one
 * reached from above, so they never land on top of the line that feeds them.
 */
private fun DrawScope.drawNodeLabel(
    node: CardNode,
    metrics: ChartMetrics,
    cx: Float,
    cy: Float,
    paint: Paint,
    isCurrent: Boolean,
    isLit: Boolean,
    enabled: Boolean,
) {
    val parentPos = CardChart.position(node.parentCode)
    val fedFromAbove = parentPos != null && parentPos.first == node.col

    paint.color = when {
        isCurrent -> CardColors.BrassBright.toArgb()
        isLit -> CardColors.Cream.toArgb()
        !enabled -> CardColors.CreamDim.copy(alpha = 0.35f).toArgb()
        else -> CardColors.Cream.copy(alpha = 0.85f).toArgb()
    }

    val labelX: Float
    val labelY: Float
    if (fedFromAbove) {
        labelX = cx + metrics.cell * 0.42f
        labelY = cy + paint.textSize * 0.36f
    } else {
        labelX = cx
        labelY = cy - metrics.cell * 0.30f
    }

    drawContext.canvas.nativeCanvas.drawText(node.letter.toString(), labelX, labelY, paint)
}

/** The broadcast mark printed at the foot of the card. */
private fun DrawScope.drawFooterGlyph(metrics: ChartMetrics) {
    val cx = size.width / 2f
    val cy = size.height - metrics.cell * 0.55f
    val color = CardColors.Cream.copy(alpha = 0.75f)
    val stroke = metrics.traceWidth

    drawCircle(color = color, radius = metrics.cell * 0.10f, center = Offset(cx, cy))

    listOf(0.22f, 0.34f).forEach { factor ->
        val radius = metrics.cell * factor
        listOf(210f, 330f).forEach { startAngle ->
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke),
            )
        }
    }
}
