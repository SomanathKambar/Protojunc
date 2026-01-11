package com.tej.protojunc.ui.bluetooth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.center

@Composable
fun RadarScanView(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    
    val radiusAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radius"
    )
    
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Canvas(modifier = modifier.size(200.dp)) {
        val canvasCenter = size.center
        val maxRadius = size.minDimension / 2
        
        // Draw static circles
        drawCircle(color = Color.Gray.copy(alpha = 0.2f), radius = maxRadius, style = Stroke(width = 2f))
        drawCircle(color = Color.Gray.copy(alpha = 0.2f), radius = maxRadius * 0.6f, style = Stroke(width = 2f))
        drawCircle(color = Color.Gray.copy(alpha = 0.2f), radius = maxRadius * 0.3f, style = Stroke(width = 2f))
        
        // Draw animated pulse
        drawCircle(
            color = Color.Blue.copy(alpha = alphaAnim * 0.5f),
            radius = maxRadius * radiusAnim,
            style = Stroke(width = 8f)
        )
    }
}