package com.tej.protojunc.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tej.protojunc.models.NearbyPeer
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarDiscoveryUI(
    nearbyPeers: List<NearbyPeer>,
    onPeerClick: (NearbyPeer) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val radiusAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        // Radar Background Circles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = center
            val maxRadius = size.minDimension / 2

            // Pulsing circle
            drawCircle(
                color = PrimaryTeal.copy(alpha = 1f - radiusAnim),
                radius = maxRadius * radiusAnim,
                style = Stroke(width = 2.dp.toPx())
            )

            // Static rings
            drawCircle(color = Color.Gray.copy(alpha = 0.2f), radius = maxRadius * 0.33f, style = Stroke(width = 1.dp.toPx()))
            drawCircle(color = Color.Gray.copy(alpha = 0.2f), radius = maxRadius * 0.66f, style = Stroke(width = 1.dp.toPx()))
            drawCircle(color = Color.Gray.copy(alpha = 0.2f), radius = maxRadius, style = Stroke(width = 1.dp.toPx()))
        }

        // Central "Me" Icon
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = PrimaryTeal,
            tonalElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("ME", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Discovered Peers
        nearbyPeers.forEachIndexed { index, peer ->
            // Distribute peers based on index and RSSI
            val angle = (index * (360f / maxOf(nearbyPeers.size, 1))) * (Math.PI / 180)
            // Map RSSI (-100 to -30) to distance (maxRadius to minRadius)
            val normalizedRssi = ((peer.rssi + 100).coerceIn(0, 70) / 70f)
            val distanceMultiplier = 1f - normalizedRssi 
            
            val xOffset = (120.dp.value * distanceMultiplier * cos(angle)).dp
            val yOffset = (120.dp.value * distanceMultiplier * sin(angle)).dp

            PeerNode(
                peer = peer,
                modifier = Modifier.offset(x = xOffset, y = yOffset),
                onClick = { onPeerClick(peer) }
            )
        }
    }
}

@Composable
private fun PeerNode(
    peer: NearbyPeer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = if (peer.isPaired) CyberGreen else SecondaryAmber,
            tonalElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (peer.isPaired) {
                    Text("★", color = Color.Black, fontWeight = FontWeight.Bold)
                } else {
                    Text(peer.displayName.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            peer.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(horizontal = 4.dp)
        )
    }
}
