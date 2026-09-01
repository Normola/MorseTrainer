package com.normo.morsetrainer.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import com.normo.morsetrainer.core.ChartLayout
import com.normo.morsetrainer.core.ChartLayouts
import com.normo.morsetrainer.core.Element
import com.normo.morsetrainer.core.LayoutNode
import com.normo.morsetrainer.ui.theme.CardColors
import kotlin.math.abs

/** Column width used by the generated trees, which scroll rather than shrink to fit. */
private val WIDE_CELL = 46.dp

/** Maps a layout's grid units onto pixels. */
private class ChartMetrics(val cell: Float, val layout: ChartLayout) {
    val cellH = cell * if (layout.compact) 0.92f else 1.05f
    val gridLeft = cell * 0.5f
    val gridTop = if (layout.compact) cell * 1.05f else cell * 0.75f

    fun px(x: Float): Float = gridLeft + (x + 0.5f) * cell
    fun py(y: Float): Float = gridTop + (y + 0.5f) * cellH

    val contentWidth: Float = gridLeft * 2 + layout.columns * cell
    val totalHeight: Float =
        gridTop + layout.rows * cellH + cell * (if (layout.compact) 1.15f else 0.5f)

    val ditRadius = cell * 0.165f
    val dahWidth = cell * 0.46f
    val dahHeight = cell * 0.205f
    val traceWidth = (cell * 0.035f).coerceAtLeast(1.5f)
    val hitRadius = cell * 0.42f
}

/**
 * A Morse chart.
 *
 * Defaults to the physical trainer card. Pass a generated layout from [ChartLayouts] to
 * show digits and punctuation, which the card has no room for — those are wider than a
 * phone screen and scroll horizontally.
 *
 * [activePath] lights the walk from the aerial down to that code; pass "-." and the
 * root-to-T and T-to-N traces light up along with both pads.
 */
@Composable
fun MorseChart(
    modifier: Modifier = Modifier,
    layout: ChartLayout = ChartLayouts.card,
    activePath: String = "",
    wrongCode: String? = null,
    dimUnreached: Boolean = false,
    enabledLetters: Set<Char>? = null,
    onNodeTap: (LayoutNode) -> Unit = {},
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

    val litCodes = remember(activePath) {
        (1..activePath.length).map { activePath.substring(0, it) }.toSet()
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availablePx = with(density) { maxWidth.toPx() }
        // The card is sized to fit the screen; generated trees keep a readable column
        // width and scroll instead.
        val cell = if (layout.compact) {
            availablePx / (layout.columns + 1f)
        } else {
            with(density) { WIDE_CELL.toPx() }
        }

        val metrics = remember(cell, layout) { ChartMetrics(cell, layout) }
        val widthDp = with(density) { metrics.contentWidth.toDp() }
        val heightDp = with(density) { metrics.totalHeight.toDp() }

        val scrollable = metrics.contentWidth > availablePx
        val outer = if (scrollable) {
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxWidth()
        }

        Box(modifier = outer) {
            Canvas(
                modifier = Modifier
                    .width(widthDp)
                    .height(heightDp)
                    .pointerInput(metrics) {
                        detectTapGestures { tap -> hitTest(metrics, tap)?.let(onNodeTap) }
                    },
            ) {
                drawPlate(metrics)
                if (layout.compact) drawTitle(metrics, titlePaint)
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
                if (layout.compact) drawFooterGlyph(metrics)
            }
        }
    }
}

private fun hitTest(metrics: ChartMetrics, tap: Offset): LayoutNode? =
    metrics.layout.nodes.firstOrNull { node ->
        node.isCharacter &&
            abs(tap.x - metrics.px(node.x)) <= metrics.hitRadius &&
            abs(tap.y - metrics.py(node.y)) <= metrics.hitRadius
    }

private fun DrawScope.drawPlate(metrics: ChartMetrics) {
    val inset = metrics.cell * 0.10f
    val corner = metrics.cell * 0.42f

    drawRoundRect(
        color = CardColors.Cream,
        topLeft = Offset.Zero,
        size = size,
        cornerRadius = CornerRadius(corner),
    )
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
 * Traces from each pad to its parent.
 *
 * The card's edges are always straight because parent and child share a row or a column.
 * A generated tree centres parents over their children, so those need an elbow — down,
 * across, down — which keeps every segment orthogonal like the printed card.
 */
private fun DrawScope.drawEdges(
    metrics: ChartMetrics,
    litCodes: Set<String>,
    activePath: String,
    dimUnreached: Boolean,
    enabledLetters: Set<Char>?,
) {
    metrics.layout.nodes.forEach { node ->
        val parent = metrics.layout.position(node.parentCode) ?: return@forEach
        val sx = metrics.px(parent.first)
        val sy = metrics.py(parent.second)
        val ex = metrics.px(node.x)
        val ey = metrics.py(node.y)

        val lit = node.code in litCodes
        val enabled = enabledLetters?.contains(node.label.firstOrNull()) ?: true
        val color = when {
            lit -> CardColors.BrassBright
            !enabled -> CardColors.Trace.copy(alpha = 0.15f)
            dimUnreached && activePath.isNotEmpty() -> CardColors.Trace.copy(alpha = 0.20f)
            else -> CardColors.Trace.copy(alpha = 0.55f)
        }
        val width = if (lit) metrics.traceWidth * 2f else metrics.traceWidth

        when {
            sx == ex || sy == ey -> drawLine(color, Offset(sx, sy), Offset(ex, ey), width)
            else -> {
                val midY = sy + (ey - sy) * 0.5f
                drawLine(color, Offset(sx, sy), Offset(sx, midY), width)
                drawLine(color, Offset(sx, midY), Offset(ex, midY), width)
                drawLine(color, Offset(ex, midY), Offset(ex, ey), width)
            }
        }
    }
}

/** The aerial at the top of the chart, where every character starts. */
private fun DrawScope.drawAntenna(metrics: ChartMetrics, live: Boolean) {
    val cx = metrics.px(metrics.layout.rootX)
    val cy = metrics.py(0f)
    val color = if (live) CardColors.BrassBright else CardColors.Cream
    val stroke = metrics.traceWidth * 1.2f
    val mastTop = cy - metrics.cell * 0.62f

    drawLine(color, Offset(cx, mastTop), Offset(cx, cy), strokeWidth = stroke)

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

    metrics.layout.nodes.forEach { node ->
        val cx = metrics.px(node.x)
        val cy = metrics.py(node.y)

        val isCurrent = node.code == activePath
        val isLit = node.code in litCodes
        val isWrong = node.code == wrongCode
        val enabled = enabledLetters?.contains(node.label.firstOrNull()) ?: true

        val fill = when {
            isWrong -> CardColors.Wrong
            isCurrent -> CardColors.BrassBright
            isLit -> CardColors.Brass
            !enabled -> CardColors.BrassDim.copy(alpha = 0.30f)
            // A junction is a real position you pass through but not a character.
            !node.isCharacter -> CardColors.BrassDim.copy(alpha = 0.55f)
            dimUnreached && activePath.isNotEmpty() -> CardColors.BrassDim.copy(alpha = 0.45f)
            else -> CardColors.Brass
        }

        if (isCurrent || isWrong) {
            drawCircle(
                color = fill.copy(alpha = 0.25f),
                radius = metrics.cell * 0.40f,
                center = Offset(cx, cy),
            )
        }

        val scale = if (node.isCharacter) 1f else 0.62f
        when (node.element) {
            Element.DIT -> drawCircle(
                color = fill,
                radius = metrics.ditRadius * scale,
                center = Offset(cx, cy),
            )

            Element.DAH -> drawRoundRect(
                color = fill,
                topLeft = Offset(
                    cx - metrics.dahWidth * scale / 2f,
                    cy - metrics.dahHeight * scale / 2f,
                ),
                size = Size(metrics.dahWidth * scale, metrics.dahHeight * scale),
                cornerRadius = CornerRadius(metrics.dahHeight * scale / 2f),
            )
        }

        if (node.isCharacter) {
            drawNodeLabel(node, metrics, cx, cy, labelPaint, isCurrent, isLit, enabled)
        }
    }
}

/**
 * On the card, labels sit above a pad reached along a horizontal trace and to the right
 * of one reached from above, so they never land on the line that feeds them. Generated
 * trees always feed from above, so their labels go underneath.
 */
private fun DrawScope.drawNodeLabel(
    node: LayoutNode,
    metrics: ChartMetrics,
    cx: Float,
    cy: Float,
    paint: Paint,
    isCurrent: Boolean,
    isLit: Boolean,
    enabled: Boolean,
) {
    paint.color = when {
        isCurrent -> CardColors.BrassBright.toArgb()
        isLit -> CardColors.Cream.toArgb()
        !enabled -> CardColors.CreamDim.copy(alpha = 0.35f).toArgb()
        else -> CardColors.Cream.copy(alpha = 0.85f).toArgb()
    }

    val labelX: Float
    val labelY: Float
    if (!metrics.layout.compact) {
        labelX = cx
        labelY = cy + metrics.cell * 0.42f
    } else {
        val parent = metrics.layout.position(node.parentCode)
        val fedFromAbove = parent != null && parent.first == node.x
        if (fedFromAbove) {
            labelX = cx + metrics.cell * 0.42f
            labelY = cy + paint.textSize * 0.36f
        } else {
            labelX = cx
            labelY = cy - metrics.cell * 0.30f
        }
    }

    drawContext.canvas.nativeCanvas.drawText(node.label, labelX, labelY, paint)
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
