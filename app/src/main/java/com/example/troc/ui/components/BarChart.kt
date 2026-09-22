package com.example.troc.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.troc.domain.model.ChartSeries
import kotlin.math.max

/** Animated bar chart (pure Compose Canvas) for data-sandbox visualizations. */
@Composable
fun BarChart(series: ChartSeries, modifier: Modifier = Modifier) {
    var started by remember(series) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(600),
        label = "bars"
    )
    LaunchedEffect(series) { started = true }

    val points = series.points.ifEmpty { return }
    val maxValue = max(points.maxOf { it.value }, 1e-9)
    val barColor = MaterialTheme.colorScheme.primary
    val valueTextColor = MaterialTheme.colorScheme.onSurfaceVariant.hashCode()
    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant.hashCode()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labelPx = with(density) { 9.dp.toPx() }
    val valuePx = with(density) { 10.dp.toPx() }

    Column(modifier) {
        Text(
            series.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val chartHeight = size.height - labelPx * 2.2f
            val slot = size.width / points.size
            val barWidth = slot * 0.62f
            points.forEachIndexed { index, point ->
                val barHeight = (point.value / maxValue).toFloat() * chartHeight * progress
                val left = index * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, chartHeight - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
                drawContext.canvas.nativeCanvas.apply {
                    // value on top of the bar
                    drawText(
                        trimNumber(point.value),
                        left + barWidth / 2,
                        (chartHeight - barHeight - 6f).coerceAtLeast(valuePx),
                        android.graphics.Paint().apply {
                            textSize = valuePx
                            color = valueTextColor
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                    // category label under the axis
                    drawText(
                        point.label.take(10),
                        left + barWidth / 2,
                        chartHeight + labelPx * 1.4f,
                        android.graphics.Paint().apply {
                            textSize = labelPx
                            color = labelTextColor
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                }
            }
            drawLine(
                color = barColor.copy(alpha = 0.35f),
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight)
            )
        }
    }
}

private fun trimNumber(value: Double): String {
    return if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e12) {
        value.toLong().toString()
    } else {
        "%.2f".format(value)
    }
}
