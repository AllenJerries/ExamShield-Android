package com.examshield.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.examshield.data.models.DeviceSource
import com.examshield.utils.ProximityLevel
import com.examshield.utils.formatDistanceHuman
import com.examshield.utils.getProximityColor

@Composable
fun DistanceMeter(
    distance: Double,
    rssi: Int,
    proximityLevel: ProximityLevel,
    source: DeviceSource = DeviceSource.BLUETOOTH,
    modifier: Modifier = Modifier
) {
    val color = getProximityColor(proximityLevel)
    val progress = calculateProgress(distance)
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(distance) {
        animatedProgress.animateTo(
            targetValue = progress,
            animationSpec = tween(300)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "meterPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier.size(250.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            drawCircle(
                color = color.copy(alpha = 0.1f),
                radius = size.minDimension / 2
            )

            drawArc(
                color = Color.LightGray.copy(alpha = 0.2f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = color.copy(alpha = if (proximityLevel == ProximityLevel.FOUND) pulseAlpha else 0.8f),
                startAngle = 135f,
                sweepAngle = 270f * animatedProgress.value,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (source) {
                    DeviceSource.BLUETOOTH -> Icons.Default.BluetoothSearching
                    DeviceSource.WIFI_NETWORK -> Icons.Default.Wifi
                    DeviceSource.WIFI_HOTSPOT -> Icons.Default.WifiTethering
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatDistanceHuman(rssi, source),
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
        }
    }
}

fun calculateProgress(distance: Double): Float {
    return when {
        distance < 0.5 -> 1.0f
        distance < 1.0 -> 0.9f
        distance < 2.0 -> 0.75f
        distance < 5.0 -> 0.5f
        distance < 10.0 -> 0.3f
        distance < 20.0 -> 0.15f
        else -> 0.05f
    }
}
