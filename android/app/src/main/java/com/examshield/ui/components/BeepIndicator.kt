package com.examshield.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.examshield.ui.theme.PulsingGreen

@Composable
fun BeepIndicator(
    isActive: Boolean,
    isAlarm: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "beepPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beepScale"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            contentDescription = null,
            tint = if (isAlarm) Color.Red else if (isActive) PulsingGreen else Color.Gray,
            modifier = if (isActive) Modifier.scale(scale) else Modifier
        )
        if (isActive) {
            Text(
                text = if (isAlarm) "ALARM ACTIVE" else "BEEPING",
                color = if (isAlarm) Color.Red else PulsingGreen,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
