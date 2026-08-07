package com.examshield.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examshield.scanner.ScanStatus
import com.examshield.ui.theme.PulsingGreen
import com.examshield.ui.theme.Primary

@Composable
fun ScanStatusCard(
    isScanning: Boolean,
    lastScanTime: String,
    deviceCount: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (isScanning) PulsingGreen.copy(alpha = dotAlpha)
                        else Color.Gray.copy(alpha = 0.5f)
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isScanning) "Scanning Active" else "Scan Paused",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isScanning) PulsingGreen else Color.Gray
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Last scan: $lastScanTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$deviceCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Text(
                    text = "devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LivePulsingDot(active: Boolean) {
    if (active) {
        val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .scale(scale)
                    .background(
                        Color(0xFF4ADE80).copy(alpha = alpha),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF4ADE80), CircleShape)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color.Gray, CircleShape)
        )
    }
}

@Composable
fun ScannerStatusChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    color: Color
) {
    Surface(
        color = if (active) color.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) color else Color.Gray.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, null,
                tint = if (active) color else Color.Gray,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                color = if (active) color else Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AnimatedRefreshIcon(isRefreshing: Boolean) {
    val rotation = if (isRefreshing) {
        val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing)
            ),
            label = "angle"
        )
        angle
    } else 0f

    Icon(
        Icons.Default.Refresh,
        "Refresh",
        modifier = Modifier.rotate(rotation)
    )
}

@Composable
fun LiveScanIndicator(
    scanStatus: ScanStatus,
    bleActive: Boolean,
    wifiActive: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LivePulsingDot(
                    active = scanStatus is ScanStatus.Active
                )
                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (scanStatus) {
                            is ScanStatus.Starting -> "Starting..."
                            is ScanStatus.Active -> "Scanning Live"
                            is ScanStatus.Refreshing -> "Refreshing..."
                            is ScanStatus.Idle -> "Stopped"
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "Detecting all nearby broadcasts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                FilledTonalButton(
                    onClick = onRefresh,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Scan", fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScannerStatusChip(
                    icon = Icons.Default.Bluetooth,
                    label = "Bluetooth",
                    active = bleActive,
                    color = Color(0xFF2196F3)
                )
                ScannerStatusChip(
                    icon = Icons.Default.Wifi,
                    label = "WiFi",
                    active = wifiActive,
                    color = Color(0xFF9C27B0)
                )
                ScannerStatusChip(
                    icon = Icons.Default.WifiTethering,
                    label = "Hotspot",
                    active = wifiActive,
                    color = Color(0xFFE53935)
                )
            }
        }
    }
}
