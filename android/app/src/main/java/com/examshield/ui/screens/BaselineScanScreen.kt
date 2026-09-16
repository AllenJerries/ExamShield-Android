package com.examshield.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.DeviceType
import com.examshield.data.models.ScanResult
import com.examshield.scanner.DeviceClassifier
import com.examshield.ui.components.getSourceColor
import com.examshield.utils.DeviceIcons
import com.examshield.utils.DistanceCalculator
import com.examshield.utils.formatDistanceHuman
import com.examshield.viewmodel.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaselineScanScreen(
    examId: Long,
    onStartExam: (Long) -> Unit,
    onBack: () -> Unit,
    scanViewModel: ScanViewModel = viewModel()
) {
    val isScanning by scanViewModel.isBaselineScanning.collectAsState()
    val progress by scanViewModel.baselineProgress.collectAsState()
    val timeRemaining by scanViewModel.baselineTimeRemaining.collectAsState()
    val scannedDevices by scanViewModel.scannedDevices.collectAsState()
    val whitelistedCount by scanViewModel.whitelistedCount.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "scanRotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LaunchedEffect(examId) {
        scanViewModel.startBaselineScan(examId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Baseline Scan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            if (isScanning) {
                Icon(
                    Icons.Default.BluetoothSearching,
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Scanning Room...",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${timeRemaining} seconds remaining",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${scannedDevices.size} devices found so far",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (whitelistedCount > 0) {
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Baseline Complete!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$whitelistedCount devices whitelisted",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Detected Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (scannedDevices.isEmpty()) {
                        Text(
                            text = "Waiting for scan results...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        scannedDevices.take(15).forEach { device ->
                            BaselineDeviceCard(device)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (scannedDevices.size > 15) {
                            Text(
                                text = "...and ${scannedDevices.size - 15} more devices",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!isScanning && whitelistedCount > 0) {
                Button(
                    onClick = { onStartExam(examId) },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Exam Mode", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private class BaselineDeviceInfo(
    val deviceType: DeviceType,
    val source: DeviceSource,
    val isHotspot: Boolean
)

private fun classifyBaselineDevice(result: ScanResult): BaselineDeviceInfo {
    val isHotspot = result.isWifi &&
        isLikelyHotspot(result.deviceName, result.macAddress)

    val deviceType = when {
        isHotspot -> DeviceType.MOBILE_HOTSPOT
        result.isWifi -> DeviceType.WIFI_DEVICE
        else -> DeviceClassifier.classifyDeviceStrict(
            result.deviceName,
            result.macAddress,
            result.rssi,
            result.scanRecord,
            isFromWifi = false
        ).deviceType
    }

    val source = when {
        isHotspot -> DeviceSource.WIFI_HOTSPOT
        result.isWifi -> DeviceSource.WIFI_NETWORK
        else -> DeviceSource.BLUETOOTH
    }

    return BaselineDeviceInfo(deviceType, source, isHotspot)
}

private fun isLikelyHotspot(ssid: String, bssid: String): Boolean {
    val ssidLower = ssid.lowercase()

    val hotspotKeywords = listOf(
        "androidap", "android_hotspot", "mywifi",
        "iphone", "galaxy", "redmi",
        "vivo", "oppo", "realme", "oneplus",
        "poco", "mi ", "samsung", "moto",
        "pixel", "nothing", "iqoo", "infinix",
        "tecno", "hotspot", "phone", "mobile",
        "personal hotspot", "portable", "tethering"
    )

    if (hotspotKeywords.any { ssidLower.contains(it) }) return true

    if (bssid.length >= 8) {
        val oui = bssid.substring(0, 8).replace(":", "").uppercase()
        val mobileOUIs = listOf(
            "F0C77F", "00265C", "D0176A", "8425DB", "34145F", "3413E8", "5C0A5B",
            "00259C", "D89B3B", "F0DBE2", "68967B", "F41BA1", "5CF7E6",
            "A0999B", "8C1D96", "0C1105", "50EC50", "68DFDD", "742344",
            "94E979", "38A28C", "68D247", "A81B18", "8CBFA6"
        )
        if (mobileOUIs.any { oui.startsWith(it) }) return true
    }

    return false
}

@Composable
private fun BaselineDeviceCard(device: ScanResult) {
    val info = classifyBaselineDevice(device)
    val sourceColor = getSourceColor(info.source)
    val typeName = DeviceIcons.getDisplayName(info.deviceType)
    val distance = DistanceCalculator.calculateDistance(
        device.rssi,
        isWifi = device.isWifi
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
                    .size(44.dp)
                    .background(
                        color = sourceColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = DeviceIcons.getIcon(info.deviceType),
                    contentDescription = null,
                    tint = sourceColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName.ifEmpty { "Unknown Device" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.macAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = getSourceIcon(info.source),
                        contentDescription = null,
                        tint = sourceColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = typeName,
                        color = sourceColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "${formatDistanceHuman(device.rssi)} \u2022 ${
                        DistanceCalculator.formatDistance(distance)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Whitelisted",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun getSourceIcon(source: DeviceSource): androidx.compose.ui.graphics.vector.ImageVector {
    return when (source) {
        DeviceSource.BLUETOOTH -> Icons.Default.Bluetooth
        DeviceSource.WIFI_NETWORK -> Icons.Default.Wifi
        DeviceSource.WIFI_HOTSPOT -> Icons.Default.WifiTethering
    }
}
