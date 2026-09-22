package com.troc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.troc.ui.theme.PrimaryPurple
import kotlin.math.sin

@Composable
fun WaveformVisualizer(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = PrimaryPurple
) {
    Canvas(modifier = modifier.fillMaxWidth().height(40.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        if (amplitudes.isEmpty()) return@Canvas

        val barWidth = width / amplitudes.size.coerceAtLeast(1)
        amplitudes.forEachIndexed { index, amp ->
            val x = index * barWidth
            val barHeight = (amp * height * 0.8f).coerceAtLeast(4.dp.toPx())
            drawLine(
                color = color,
                start = Offset(x + barWidth / 2, centerY - barHeight / 2),
                end = Offset(x + barWidth / 2, centerY + barHeight / 2),
                strokeWidth = (barWidth * 0.6f).coerceAtLeast(2.dp.toPx())
            )
        }
    }
}

@Composable
fun LiveWaveform(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth().height(60.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val points = 50
        val step = width / points

        for (i in 0 until points) {
            val x = i * step
            val progress = i / points.toFloat()
            val wave = sin(progress * 2 * Math.PI + System.currentTimeMillis() * 0.005).toFloat()
            val amp = amplitude * (0.5f + wave * 0.5f)
            val barHeight = amp * height

            drawLine(
                color = PrimaryPurple.copy(alpha = 0.6f + amp * 0.4f),
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = 3.dp.toPx()
            )
        }
    }
}
