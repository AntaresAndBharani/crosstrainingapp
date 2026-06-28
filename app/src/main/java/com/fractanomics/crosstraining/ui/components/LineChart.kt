package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.util.Locale

/** A single point on a [LineChart]. */
data class ChartPoint(val label: String, val value: Float)

private fun fmt(v: Float): String =
    if (v % 1f == 0f) v.toLong().toString() else String.format(Locale.getDefault(), "%.1f", v)

/**
 * A lightweight line chart drawn with Canvas: a connected series with dots, a
 * baseline, min/max value labels on the Y axis and first/last point labels on
 * the X axis. No third-party charting dependency.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (points.isEmpty()) {
        Text("No data yet.", style = MaterialTheme.typography.bodyMedium)
        return
    }

    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val density = LocalDensity.current.density

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val leftPad = 52f * density
        val rightPad = 12f * density
        val topPad = 14f * density
        val botPad = 26f * density
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - botPad

        val values = points.map { it.value }
        var minV = values.min()
        var maxV = values.max()
        if (minV == maxV) { minV -= 1f; maxV += 1f }
        val range = maxV - minV

        fun xAt(i: Int): Float =
            if (points.size == 1) leftPad + plotW / 2f
            else leftPad + plotW * i / (points.size - 1)

        fun yAt(v: Float): Float = topPad + plotH * (1f - (v - minV) / range)

        // Axes
        drawLine(axisColor, Offset(leftPad, topPad), Offset(leftPad, topPad + plotH), 1.5f * density)
        drawLine(axisColor, Offset(leftPad, topPad + plotH), Offset(leftPad + plotW, topPad + plotH), 1.5f * density)

        // Series line + dots
        val pts = points.mapIndexed { i, p -> Offset(xAt(i), yAt(p.value)) }
        for (i in 1 until pts.size) {
            drawLine(lineColor, pts[i - 1], pts[i], strokeWidth = 3f * density, cap = StrokeCap.Round)
        }
        pts.forEach { drawCircle(lineColor, radius = 4f * density, center = it) }

        // Labels
        val paint = android.graphics.Paint().apply {
            color = labelArgb
            textSize = 11f * density
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText(fmt(maxV), 6f * density, topPad + paint.textSize / 2f, paint)
            drawText(fmt(minV), 6f * density, topPad + plotH, paint)
            val first = points.first().label
            val last = points.last().label
            drawText(first, leftPad, size.height - 6f * density, paint)
            if (points.size > 1) {
                val lastW = paint.measureText(last)
                drawText(last, leftPad + plotW - lastW, size.height - 6f * density, paint)
            }
        }
    }
}
