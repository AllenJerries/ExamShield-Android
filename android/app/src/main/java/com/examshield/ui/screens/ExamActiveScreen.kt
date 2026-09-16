package com.examshield.ui.screens

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examshield.data.models.*
import com.examshield.scanner.ScanStatus
import com.examshield.ui.components.DeviceCard
import com.examshield.ui.components.LegacyDeviceCard
import com.examshield.ui.components.SignalInfoDialog
import com.examshield.ui.components.DeviceTypesGuideDialog
import com.examshield.ui.theme.*
import com.examshield.utils.formatDistanceHuman
import com.examshield.viewmodel.DeviceCategoryFilter
import com.examshield.viewmodel.ScanViewModel

private const val TAG = "ExamShield_Scan"

private val RELEVANT_TYPES = setOf(
    DeviceType.PHONE_ANDROID,
    DeviceType.PHONE_IOS,
    DeviceType.SMARTWATCH,
    DeviceType.EARPHONE,
    DeviceType.HIDDEN_EARPIECE,
    DeviceType.MOBILE_HOTSPOT,
    DeviceType.WIFI_DEVICE,
    DeviceType.UNKNOWN,
    DeviceType.OTHER
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamActiveScreen(
    examId: Long,
    onHuntDevice: (String, String, String) -> Unit,
    onEndExam: () -> Unit,
    scanViewModel: ScanViewModel = viewModel()
) {
    val isScanning by scanViewModel.isExamActive.collectAsState()
    val activeExam by scanViewModel.activeExam.collectAsState()
    val detectedDevices by scanViewModel.detectedDevices.collectAsState()
    val lastScanTime by scanViewModel.lastScanTime.collectAsState()
    val sortedUnauthorizedDevices by scanViewModel.sortedUnauthorizedDevices.collectAsState()
    val criticalAlertDevice by scanViewModel.criticalAlertDevice.collectAsState()
    val wifiScanStatus by scanViewModel.wifiScanStatus.collectAsState()
    val wifiDeviceCount by scanViewModel.wifiDeviceCount.collectAsState()
    val scanStats by scanViewModel.scanStats.collectAsState()
    val scanStatus by scanViewModel.scanStatus.collectAsState()
    val bleActive by scanViewModel.bleActive.collectAsState()
    val wifiActive by scanViewModel.wifiActive.collectAsState()
    val categoryFilter by scanViewModel.deviceCategoryFilter.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDeviceGuide by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Auto-start scanning as soon as the exam screen composes so the
        // UI never shows the scanner in a "Stopped" state after the
        // Baseline scan transitions into exam mode.
        scanViewModel.startScanning()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Restart scanning whenever the invigilator returns to this screen
        // (e.g. back from the Proximity Hunter) so discovery keeps running.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d(TAG, "ON_RESUME -> ensuring scanning is active")
                scanViewModel.startScanning()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(examId) {
        Log.d(TAG, "ExamActiveScreen launched for examId=$examId")
        scanViewModel.startExamMonitoring(examId)
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "ExamActiveScreen disposed")
            scanViewModel.stopScanning()
        }
    }

    val elapsedTime = remember(lastScanTime) {
        activeExam?.startedAt?.let {
            val elapsed = System.currentTimeMillis() - it
            val seconds = (elapsed / 1000) % 60
            val minutes = (elapsed / (1000 * 60)) % 60
            val hours = elapsed / (1000 * 60 * 60)
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } ?: "00:00:00"
    }

    val relevantDevices = remember(sortedUnauthorizedDevices) {
        sortedUnauthorizedDevices.filter { it.deviceType in RELEVANT_TYPES }
    }

    val visibleDevices = remember(relevantDevices, categoryFilter) {
        relevantDevices.filter { scanViewModel.matchesCategory(it, categoryFilter) }
    }

    val criticalCount = remember(relevantDevices) {
        relevantDevices.count { it.riskLevel == RiskLevel.CRITICAL }
    }
    val highCount = remember(relevantDevices) {
        relevantDevices.count { it.riskLevel == RiskLevel.HIGH }
    }
    val mediumCount = remember(relevantDevices) {
        relevantDevices.count { it.riskLevel == RiskLevel.MEDIUM }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Exam Active", fontWeight = FontWeight.Bold)
                        ScanStatusSubtitle(scanStatus)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = {
                        isRefreshing = true
                        scanViewModel.manualRefreshWiFi()
                    }) {
                        AnimatedRefreshIcon(isRefreshing = isRefreshing)
                    }
                    IconButton(onClick = { showDeviceGuide = true }) {
                        Icon(
                            Icons.Default.Devices,
                            contentDescription = "Device Types Guide",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Signal Info",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = activeExam?.examName ?: "Exam",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${activeExam?.hallName ?: ""} - Room ${activeExam?.roomNumber ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = elapsedTime,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Elapsed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                LiveScanIndicator(
                    scanStatus = scanStatus,
                    bleActive = bleActive,
                    wifiActive = wifiActive,
                    onRefresh = {
                        isRefreshing = true
                        scanViewModel.manualRefreshWiFi()
                    }
                )
            }

            item {
                StatsCard(
                    total = relevantDevices.size,
                    critical = criticalCount,
                    high = highCount,
                    medium = mediumCount
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Category filter chips ───────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryFilterChip(
                        label = "All",
                        icon = Icons.Default.Devices,
                        selected = categoryFilter == DeviceCategoryFilter.ALL,
                        color = Color(0xFF607D8B),
                        onClick = { scanViewModel.setDeviceCategoryFilter(DeviceCategoryFilter.ALL) },
                        modifier = Modifier.weight(1f)
                    )
                    CategoryFilterChip(
                        label = "Bluetooth",
                        icon = Icons.Default.Bluetooth,
                        selected = categoryFilter == DeviceCategoryFilter.BLUETOOTH,
                        color = Color(0xFF2196F3),
                        onClick = { scanViewModel.setDeviceCategoryFilter(DeviceCategoryFilter.BLUETOOTH) },
                        modifier = Modifier.weight(1f)
                    )
                    CategoryFilterChip(
                        label = "WiFi",
                        icon = Icons.Default.Wifi,
                        selected = categoryFilter == DeviceCategoryFilter.WIFI,
                        color = Color(0xFF9C27B0),
                        onClick = { scanViewModel.setDeviceCategoryFilter(DeviceCategoryFilter.WIFI) },
                        modifier = Modifier.weight(1f)
                    )
                    CategoryFilterChip(
                        label = "Hotspot",
                        icon = Icons.Default.WifiTethering,
                        selected = categoryFilter == DeviceCategoryFilter.HOTSPOT,
                        color = Color(0xFFE53935),
                        onClick = { scanViewModel.setDeviceCategoryFilter(DeviceCategoryFilter.HOTSPOT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            criticalAlertDevice?.let { device ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = AlertRed
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "CRITICAL ALERT",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = device.name.ifEmpty { "Unknown Device" },
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${device.description} \u2022 ${formatDistanceHuman(device.rssi, device.source)}",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scanViewModel.setHuntTarget(device)
                                        onHuntDevice(device.macAddress, device.name, device.deviceType.name)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = AlertRed
                                    )
                                ) {
                                    Text("HUNT DEVICE", fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = { scanViewModel.dismissAlert() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }

            if (visibleDevices.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nearby Threats",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${visibleDevices.size} found",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(
                    visibleDevices,
                    key = { it.macAddress }
                ) { unifiedDevice ->
                    DeviceCard(
                        device = unifiedDevice,
                        allDevices = relevantDevices,
                        onHuntClick = {
                            Log.d(TAG, "HUNT: ${unifiedDevice.macAddress} | ${unifiedDevice.name}")
                            scanViewModel.setHuntTarget(unifiedDevice)
                            onHuntDevice(unifiedDevice.macAddress, unifiedDevice.name, unifiedDevice.deviceType.name)
                        }
                    )
                }
            } else if (categoryFilter == DeviceCategoryFilter.ALL) {
                val legacyDevices = detectedDevices.filter { !it.isWhitelisted }
                if (legacyDevices.isNotEmpty()) {
                    items(
                        legacyDevices.sortedWith(
                            compareByDescending<Device> { it.riskLevel == "CRITICAL" }
                                .thenByDescending { it.riskLevel == "HIGH" }
                                .thenByDescending { it.riskLevel == "MEDIUM" }
                        ),
                        key = { it.macAddress }
                    ) { device ->
                        LegacyDeviceCard(
                            device = device,
                            onHuntClick = {
                                onHuntDevice(device.macAddress, device.deviceName, device.deviceType)
                            }
                        )
                    }
                }
            }

            if (visibleDevices.isEmpty() && detectedDevices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "All Clear",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "No threats detected",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (visibleDevices.isEmpty() && categoryFilter != DeviceCategoryFilter.ALL) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                when (categoryFilter) {
                                    DeviceCategoryFilter.BLUETOOTH -> Icons.Default.Bluetooth
                                    DeviceCategoryFilter.WIFI -> Icons.Default.Wifi
                                    DeviceCategoryFilter.HOTSPOT -> Icons.Default.WifiTethering
                                    DeviceCategoryFilter.ALL -> Icons.Default.Devices
                                },
                                null,
                                tint = Color.Gray,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No ${categoryFilter.name.lowercase()} devices found",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showEndDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("End Exam", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("End Exam?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to end this exam session? All scanning will stop.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEndDialog = false
                        scanViewModel.endExam()
                        onEndExam()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("End Exam")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showInfoDialog) {
        SignalInfoDialog(onDismiss = { showInfoDialog = false })
    }

    if (showDeviceGuide) {
        DeviceTypesGuideDialog(onDismiss = { showDeviceGuide = false })
    }
}



// ============ NEW COMPOSABLES ============

@Composable
private fun CategoryFilterChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = color.copy(alpha = 0.08f),
            labelColor = color.copy(alpha = 0.7f),
            iconColor = color.copy(alpha = 0.7f),
            selectedContainerColor = color.copy(alpha = 0.20f),
            selectedLabelColor = color,
            selectedLeadingIconColor = color
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = color.copy(alpha = 0.25f),
            selectedBorderColor = color,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.5.dp
        ),
        modifier = modifier.height(32.dp)
    )
}

@Composable
private fun ScanStatusSubtitle(scanStatus: ScanStatus) {
    val text = when (scanStatus) {
        is ScanStatus.Idle -> "Stopped"
        is ScanStatus.Starting -> "Starting..."
        is ScanStatus.Active -> "Scanning Live"
        is ScanStatus.Refreshing -> "Refreshing..."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
        fontSize = 11.sp
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

@Composable
fun LivePulsingDot(active: Boolean) {
    if (active) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulseDot")
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
        val infiniteTransition = rememberInfiniteTransition(label = "refreshRotation")
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

// ============ EXISTING COMPOSABLES ============

@Composable
private fun StatsCard(
    total: Int,
    critical: Int,
    high: Int,
    medium: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(count = total, label = "Total", color = MaterialTheme.colorScheme.primary)
            StatItem(count = critical, label = "Critical", color = RiskCritical)
            StatItem(count = high, label = "High", color = RiskHigh)
            StatItem(count = medium, label = "Medium", color = RiskMedium)
        }
    }
}

@Composable
private fun StatItem(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}