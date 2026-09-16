package com.examshield.ui.screens

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.DeviceType
import com.examshield.scanner.Direction
import com.examshield.ui.components.DistanceMeter
import com.examshield.utils.*
import com.examshield.viewmodel.ProximityViewModel
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.platform.LocalContext

private const val TAG = "ExamShield_Hunt"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProximityHunterScreen(
    navController: NavController,
    targetMacAddress: String,
    targetDeviceName: String,
    targetDeviceType: String = "",
    targetSource: String = "BLUETOOTH",
    viewModel: ProximityViewModel = viewModel()
) {
    val currentRssi by viewModel.rssi.collectAsState()
    val smoothedDistance by viewModel.smoothedDistance.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val proximityLevel by viewModel.proximityLevel.collectAsState()
    val isFound by viewModel.isFound.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val direction by viewModel.direction.collectAsState()
    val directionConfidence by viewModel.directionConfidence.collectAsState()
    val huntSource by viewModel.huntSource.collectAsState()

    val deviceTypeEnum = parseDeviceType(targetDeviceType)
    val searchHint = DeviceIcons.getSearchHint(deviceTypeEnum)
    val typeDisplayName = DeviceIcons.getDisplayName(deviceTypeEnum)
    val isHiddenEarpiece = deviceTypeEnum == DeviceType.HIDDEN_EARPIECE

    val sourceEnum = try {
        DeviceSource.valueOf(targetSource)
    } catch (_: Exception) {
        DeviceSource.BLUETOOTH
    }

    LaunchedEffect(targetMacAddress) {
        Log.d(TAG, "HunterScreen launched: mac=$targetMacAddress name=$targetDeviceName type=$targetDeviceType source=$targetSource")
        if (targetMacAddress.isNotEmpty() && targetMacAddress.length >= 17) {
            viewModel.startHunting(targetMacAddress, sourceEnum)
        } else {
            Log.e(TAG, "Invalid MAC address: $targetMacAddress")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopHunting()
        }
    }

    val proxColor by animateColorAsState(
        targetValue = getProximityColor(proximityLevel),
        animationSpec = tween(500),
        label = "proxColor"
    )

    val bgColor by animateColorAsState(
        targetValue = getBackgroundColor(proximityLevel),
        animationSpec = tween(500),
        label = "bgColor"
    )

    val smoothDistanceAnim = remember { Animatable(999.0f) }
    LaunchedEffect(smoothedDistance) {
        smoothDistanceAnim.animateTo(
            targetValue = smoothedDistance.toFloat(),
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
    }

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isFound) "FOUND" else "HUNTING",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isFound) Color(0xFF4CAF50) else proxColor
                        )
                        Text(
                            text = when (sourceEnum) {
                                DeviceSource.BLUETOOTH -> "Bluetooth Tracking"
                                DeviceSource.WIFI_NETWORK -> "WiFi Network Tracking"
                                DeviceSource.WIFI_HOTSPOT -> "Hotspot Tracking"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = proxColor.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopHunting()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = proxColor)
                    }
                },
                actions = {
                    LiveUpdateIndicator(lastUpdate = viewModel.lastUpdate)
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.markAsFound()
                        navController.popBackStack()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("MARK FOUND", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.stopHunting()
                        navController.popBackStack()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("STOP HUNT", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = targetDeviceName.ifEmpty { "Unknown Device" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = targetMacAddress,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            if (typeDisplayName.isNotEmpty() && deviceTypeEnum != DeviceType.UNKNOWN && deviceTypeEnum != DeviceType.OTHER) {
                Text(
                    text = typeDisplayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = proxColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isHiddenEarpiece)
                        Color.Red.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = searchHint,
                    fontSize = 14.sp,
                    color = if (isHiddenEarpiece) Color.Red else Color.Gray,
                    fontWeight = if (isHiddenEarpiece) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Red.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error,
                            color = Color.Red,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                Log.d(TAG, "Retry clicked for: $targetMacAddress")
                                viewModel.startHunting(targetMacAddress, sourceEnum)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            StableDirectionGuide(
                direction = direction,
                confidence = directionConfidence,
                color = proxColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            DistanceMeter(
                distance = smoothedDistance,
                rssi = currentRssi,
                proximityLevel = proximityLevel,
                source = sourceEnum
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = DistanceCalculator.formatDistance(smoothDistanceAnim.value.toDouble()),
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = proxColor
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = getProximityText(proximityLevel),
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = proxColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            InfoChipsRow(
                rssi = currentRssi,
                smoothedDistance = smoothedDistance,
                proximityLevel = proximityLevel,
                direction = direction
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StableDirectionGuide(
    direction: Direction,
    confidence: Float,
    color: Color
) {
    val arrowRotation by animateFloatAsState(
        targetValue = when (direction) {
            Direction.FRONT -> 0f
            Direction.RIGHT -> 90f
            Direction.BACK -> 180f
            Direction.LEFT -> 270f
            else -> 0f
        },
        animationSpec = tween(600),
        label = "arrowRotation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DIRECTION GUIDE",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = color
            )

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        color = color.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (direction) {
                    Direction.STAY -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Move",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(50.dp)
                        )
                    }
                    Direction.SEARCHING, Direction.UNKNOWN -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(50.dp),
                            color = color,
                            strokeWidth = 4.dp
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Direction",
                            tint = color,
                            modifier = Modifier
                                .size(60.dp)
                                .rotate(arrowRotation)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = getDirectionText(direction),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )

            Text(
                text = getDirectionHint(direction),
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (confidence > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (confidence / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = color,
                    trackColor = color.copy(alpha = 0.15f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Confidence: ${confidence.toInt()}%",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun LiveUpdateIndicator(lastUpdate: StateFlow<Long>) {
    var lastTime by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        lastUpdate.collect { lastTime = it }
    }

    val elapsed = with(LocalContext.current) {
        val now = System.currentTimeMillis()
        if (lastTime > 0) now - lastTime else 9999L
    }

    val isStale = elapsed > 3000
    val color = if (isStale) Color.Red else Color(0xFF4CAF50)

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val alpha by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (isStale) 1f else alpha))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (isStale) "Stale" else "Live",
            fontSize = 11.sp,
            color = color
        )
    }
}

@Composable
private fun InfoChipsRow(
    rssi: Int,
    smoothedDistance: Double,
    proximityLevel: ProximityLevel,
    direction: Direction
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoChip(
            icon = Icons.Outlined.Sensors,
            label = "Level",
            value = getProximityText(proximityLevel),
            modifier = Modifier.weight(1f)
        )
        InfoChip(
            icon = Icons.Outlined.SignalCellularAlt,
            label = "Signal",
            value = "${formatDistanceHuman(rssi)} (${getSignalStrengthText(rssi)})",
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoChip(
            icon = Icons.Outlined.Bluetooth,
            label = "Distance",
            value = DistanceCalculator.formatDistance(smoothedDistance),
            modifier = Modifier.weight(1f)
        )
        InfoChip(
            icon = Icons.Filled.NearMe,
            label = "Direction",
            value = getDirectionText(direction),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    label,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun getSignalStrengthText(rssi: Int): String {
    return when {
        rssi > -50 -> "Excellent"
        rssi > -65 -> "Good"
        rssi > -80 -> "Fair"
        rssi > -90 -> "Weak"
        else -> "Very Weak"
    }
}

private fun getDirectionText(direction: Direction): String {
    return when (direction) {
        Direction.FRONT -> "GO FRONT"
        Direction.BACK -> "GO BACK"
        Direction.LEFT -> "GO LEFT"
        Direction.RIGHT -> "GO RIGHT"
        Direction.STAY -> "WALK TO DETECT"
        Direction.SEARCHING -> "SEARCHING..."
        Direction.UNKNOWN -> "MOVE AROUND"
        Direction.FOUND -> "DEVICE FOUND"
    }
}

private fun getDirectionHint(direction: Direction): String {
    return when (direction) {
        Direction.FRONT -> "Device is in front of you"
        Direction.BACK -> "Device is behind you - turn around"
        Direction.LEFT -> "Device is to your left"
        Direction.RIGHT -> "Device is to your right"
        Direction.STAY -> "Walk 2-3 steps in any direction"
        Direction.SEARCHING -> "Keep walking to determine direction"
        Direction.UNKNOWN -> "Move around the room"
        Direction.FOUND -> "You have reached the device"
    }
}
