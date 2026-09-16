package com.examshield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.examshield.scanner.SuspiciousDeviceDetector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examshield.data.models.Device
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.DeviceType
import com.examshield.data.models.RiskLevel
import com.examshield.data.models.UnifiedDevice
import com.examshield.ui.theme.*
import com.examshield.utils.*

@Composable
fun DeviceCard(
    device: UnifiedDevice,
    allDevices: List<UnifiedDevice> = emptyList(),
    onHuntClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val riskColor = getRiskColor(device.riskLevel)
    val riskColorAnimated by animateColorAsState(targetValue = riskColor, label = "riskColor")

    val typeDisplayName = DeviceIcons.getDisplayName(device.deviceType)

    val cardBgColor = when (device.riskLevel) {
        RiskLevel.HIGH -> RiskHigh.copy(alpha = 0.08f)
        RiskLevel.CRITICAL -> RiskCritical.copy(alpha = 0.12f)
        RiskLevel.MEDIUM -> RiskMedium.copy(alpha = 0.06f)
        RiskLevel.LOW -> Color.Transparent
    }

    val huntButtonColor = when (device.riskLevel) {
        RiskLevel.CRITICAL -> Color(0xFFB71C1C)
        RiskLevel.HIGH -> Color(0xFFE53935)
        RiskLevel.MEDIUM -> Color(0xFFFF6F00)
        RiskLevel.LOW -> Color(0xFF1976D2)
    }

    val distance = device.estimatedDistance
    val distanceText = formatDistanceShort(distance)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = riskColorAnimated.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = DeviceIcons.getIcon(device.deviceType),
                    contentDescription = null,
                    tint = riskColorAnimated,
                    modifier = Modifier.size(26.dp)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(16.dp)
                        .background(
                            color = Color.White,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = getSourceColor(device.source),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name.ifEmpty { "Unknown Device" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = typeDisplayName,
                        fontSize = 12.sp,
                        color = riskColorAnimated,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (device.isHotspot) {
                        Text("\u2022 Hotspot", fontSize = 11.sp, color = Color.Red)
                    }
                }

                Text(
                    text = device.macAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = riskColorAnimated.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = device.riskLevel.name,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = riskColorAnimated,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SignalCellular4Bar,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = formatDistanceHuman(device.rssi),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    Text("\u2022", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = riskColorAnimated
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = distanceText,
                            style = MaterialTheme.typography.bodySmall,
                            color = riskColorAnimated,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                if (allDevices.isNotEmpty()) {
                    val analysis = remember(device, allDevices) {
                        SuspiciousDeviceDetector.analyzeDevice(device, allDevices)
                    }

                    if (analysis.suspicionScore >= 40) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = null,
                                tint = when {
                                    analysis.suspicionScore >= 80 -> Color.Red
                                    analysis.suspicionScore >= 60 -> Color(0xFFFF6B6B)
                                    else -> Color(0xFFFFA726)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Data Usage Risk: ${analysis.suspicionScore}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                analysis.reasons.take(2).forEach { reason ->
                                    Text(
                                        "\u2022 $reason",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (onHuntClick != null) {
                Button(
                    onClick = onHuntClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = huntButtonColor
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.GpsFixed,
                        contentDescription = "Hunt device",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("HUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LegacyDeviceCard(
    device: Device,
    onHuntClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val riskColor = getRiskColorFromString(device.riskLevel)
    val riskColorAnimated by animateColorAsState(targetValue = riskColor, label = "riskColor")

    val deviceTypeEnum = parseDeviceType(device.deviceType)
    val typeDisplayName = DeviceIcons.getDisplayName(deviceTypeEnum)

    val cardBgColor = when (device.riskLevel) {
        "HIGH" -> RiskHigh.copy(alpha = 0.08f)
        "CRITICAL" -> RiskCritical.copy(alpha = 0.12f)
        "MEDIUM" -> RiskMedium.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    val huntButtonColor = when (device.riskLevel) {
        "CRITICAL" -> Color(0xFFB71C1C)
        "HIGH" -> Color(0xFFE53935)
        "MEDIUM" -> Color(0xFFFF6F00)
        else -> Color(0xFF1976D2)
    }

    val distance = calculateDistanceFromRssi(device.rssi)
    val distanceText = formatDistanceShort(distance)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = riskColorAnimated.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getDeviceIconFromString(device.deviceType),
                        contentDescription = null,
                        tint = riskColorAnimated,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName.ifEmpty { "Unknown Device" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = device.macAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = riskColorAnimated.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = device.riskLevel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = riskColorAnimated,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = typeDisplayName,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SignalCellular4Bar,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = formatDistanceHuman(device.rssi),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text("\u2022", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = riskColorAnimated
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = distanceText,
                            style = MaterialTheme.typography.bodySmall,
                            color = riskColorAnimated,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!device.isWhitelisted && onHuntClick != null) {
                Button(
                    onClick = onHuntClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = huntButtonColor
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.GpsFixed,
                        contentDescription = "Hunt device",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("HUNT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (device.isWhitelisted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Whitelisted device",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

fun getSourceIcon(source: DeviceSource): androidx.compose.ui.graphics.vector.ImageVector {
    return when (source) {
        DeviceSource.BLUETOOTH -> Icons.Default.Bluetooth
        DeviceSource.WIFI_NETWORK -> Icons.Default.Wifi
        DeviceSource.WIFI_HOTSPOT -> Icons.Default.WifiTethering
    }
}

fun getSourceColor(source: DeviceSource): Color {
    return when (source) {
        DeviceSource.BLUETOOTH -> Color(0xFF2196F3)
        DeviceSource.WIFI_NETWORK -> Color(0xFF9C27B0)
        DeviceSource.WIFI_HOTSPOT -> Color(0xFFE53935)
    }
}
