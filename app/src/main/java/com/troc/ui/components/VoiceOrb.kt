package com.troc.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.troc.domain.model.VoiceStatus
import com.troc.ui.theme.PrimaryPurple
import com.troc.ui.theme.SecondaryCyan
import com.troc.ui.theme.TertiaryEmerald
import kotlin.math.sin

@Composable
fun VoiceOrb(
    status: VoiceStatus,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(200.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val baseRadius = size.minDimension / 3

        // Background glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(PrimaryPurple.copy(alpha = 0.3f), Color.Transparent),
                center = center,
                radius = baseRadius * 2.5f
            ),
            radius = baseRadius * 2.5f,
            center = center
        )

        // Main orb with state-based animation
        val radiusMultiplier = when (status) {
            is VoiceStatus.Idle -> breathe
            is VoiceStatus.Listening -> 1f + amplitude * 0.8f
            is VoiceStatus.Speaking -> 1f + amplitude * 0.6f + sin(rotation * 0.05f) * 0.1f
            is VoiceStatus.Thinking -> breathe * 0.9f
            else -> 1f
        }

        val orbRadius = baseRadius * radiusMultiplier

        // Layered ripples for listening/speaking
        if (status is VoiceStatus.Listening || status is VoiceStatus.Speaking) {
            for (i in 1..3) {
                val rippleRadius = orbRadius + i * 20 * (1 + amplitude)
                val alpha = (0.3f - i * 0.08f).coerceAtLeast(0f) * (0.5f + amplitude * 0.5f)
                drawCircle(
                    color = when (i % 3) {
                        0 -> PrimaryPurple.copy(alpha = alpha)
                        1 -> SecondaryCyan.copy(alpha = alpha)
                        else -> TertiaryEmerald.copy(alpha = alpha)
                    },
                    radius = rippleRadius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Core orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    PrimaryPurple,
                    SecondaryCyan.copy(alpha = 0.8f),
                    PrimaryPurple.copy(alpha = 0.6f)
                ),
                center = center,
                radius = orbRadius
            ),
            radius = orbRadius,
            center = center
        )

        // Inner highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = orbRadius * 0.3f,
            center = Offset(center.x - orbRadius * 0.2f, center.y - orbRadius * 0.2f)
        )
    }
}

@Composable
fun SmallVoiceOrbIcon(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "smallOrb")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Canvas(modifier = modifier.size(24.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(PrimaryPurple, SecondaryCyan),
                center = center,
                radius = size.minDimension / 2 * scale
            ),
            radius = size.minDimension / 2 * scale,
            center = center
        )
    }
}
