package com.examshield.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examshield.scanner.ProximityHunter
import com.examshield.ui.theme.*
import com.examshield.utils.formatDistanceHuman

@Composable
fun RSSIMeter(
    rssi: Int,
    modifier: Modifier = Modifier
) {
    val statusColor = Color(ProximityHunter.getStatusColor(rssi))
    val animatedProgress = remember { Animatable(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    LaunchedEffect(rssi) {
        val progress = ((rssi + 100) / 70f).coerceIn(0f, 1f)
        animatedProgress.animateTo(progress, animationSpec = tween(300))
    }

    val signalBars = ProximityHunter.getSignalBars(rssi)
    val statusText = ProximityHunter.getStatusText(rssi)
    val isFound = ProximityHunter.isDeviceFound(rssi)

    Box(
        modifier = modifier.size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

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
                color = statusColor.copy(alpha = if (isFound) pulseAlpha else 1f),
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
            Text(
                text = formatDistanceHuman(rssi),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(10) { index ->
                    val barActive = index < signalBars
                    val barHeight = (12 + index * 3).dp
                    Canvas(
                        modifier = Modifier
                            .width(6.dp)
                            .height(barHeight)
                    ) {
                        drawRoundRect(
                            color = if (barActive) statusColor else Color.LightGray.copy(alpha = 0.3f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = statusText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
    }
}
