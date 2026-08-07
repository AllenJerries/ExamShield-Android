package com.examshield.ui.screens

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examshield.utils.AlarmManager
import com.examshield.utils.AlertUrgency
import com.examshield.utils.DistanceCalculator
import com.examshield.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val alertDistance by viewModel.alertDistanceMeters.collectAsState()
    val scanInterval by viewModel.scanIntervalSeconds.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val beepVolume by viewModel.beepVolume.collectAsState()
    val voiceAlerts by viewModel.voiceAlerts.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val backendUrl by viewModel.backendUrl.collectAsState()

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickPresetsSection(
                currentDistance = alertDistance,
                onPresetSelected = { viewModel.updateAlertDistance(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            DistanceAlertSection(
                distance = alertDistance,
                onDistanceChange = { viewModel.updateAlertDistance(it) }
            )

            SpeedSection(
                selectedInterval = scanInterval,
                onIntervalSelected = { viewModel.updateScanInterval(it) }
            )

            SoundSection(
                soundEnabled = soundEnabled,
                onSoundToggle = { viewModel.updateSoundEnabled(it) },
                beepVolume = beepVolume,
                onVolumeChange = { viewModel.updateBeepVolume(it) },
                voiceAlerts = voiceAlerts,
                onVoiceAlertsToggle = { viewModel.updateVoiceAlerts(it) }
            )

            SoundTestSection()

            ToggleSetting(
                icon = Icons.Outlined.Vibration,
                title = "Vibration",
                subtitle = "Vibrate on device detection",
                checked = vibrationEnabled,
                onCheckedChange = { viewModel.updateVibrationEnabled(it) }
            )

            ToggleSetting(
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                subtitle = "Show notification alerts",
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.updateNotificationsEnabled(it) }
            )

            ServerSection(
                url = backendUrl,
                onUrlChange = { viewModel.updateBackendUrl(it) }
            )

            InfoSection(
                alertRssi = viewModel.alertRssiThreshold,
                alertDistance = alertDistance
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickPresetsSection(
    currentDistance: Float,
    onPresetSelected: (Float) -> Unit
) {
    val presets = listOf(
        1.5f to "Close",
        3.0f to "Medium",
        5.0f to "Far",
        10.0f to "Very Far"
    )

    Column {
        SectionHeader("Quick Presets")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (distance, label) ->
                val isSelected = currentDistance == distance
                FilterChip(
                    selected = isSelected,
                    onClick = { onPresetSelected(distance) },
                    label = {
                        Text(
                            "$label ${distance.toInt()}m",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DistanceAlertSection(
    distance: Float,
    onDistanceChange: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            SectionHeader("Alert Distance")

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Outlined.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${distance.roundToInt()} meters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "~${DistanceCalculator.metersToApproxDbm(distance.toDouble())} dBm signal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    "${distance.roundToInt()}m",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = distance,
                onValueChange = onDistanceChange,
                valueRange = 1f..15f,
                steps = 27,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("15m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SpeedSection(
    selectedInterval: Float,
    onIntervalSelected: (Float) -> Unit
) {
    val speedOptions = listOf(
        0.5f to "Extreme" to "250ms (Battery heavy)",
        1.0f to "Fast" to "1s (Recommended)",
        2.0f to "Normal" to "2s (Balanced)",
        5.0f to "Eco" to "5s (Battery saver)"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            SectionHeader("Scan Speed")

            Spacer(modifier = Modifier.height(8.dp))

            speedOptions.forEach { (pair, description) ->
                val (interval, label) = pair
                val isSelected = selectedInterval == interval
                SpeedOption(
                    label = label,
                    description = description,
                    isSelected = isSelected,
                    onClick = { onIntervalSelected(interval) }
                )
                if (interval != speedOptions.last().first.first) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SpeedOption(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isSelected) BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SoundSection(
    soundEnabled: Boolean,
    onSoundToggle: (Boolean) -> Unit,
    beepVolume: Float,
    onVolumeChange: (Float) -> Unit,
    voiceAlerts: Boolean,
    onVoiceAlertsToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToggleSetting(
                    icon = Icons.Outlined.VolumeUp,
                    title = "Sound",
                    subtitle = if (soundEnabled) "On" else "Off",
                    checked = soundEnabled,
                    onCheckedChange = {
                        onSoundToggle(it)
                        expanded = it
                        // Test sound when enabled / stop sound when disabled
                        if (it) {
                            AlarmManager.testSound(context)
                        } else {
                            AlarmManager.stopAll()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                if (soundEnabled) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess
                            else Icons.Filled.ExpandMore,
                            contentDescription = "Toggle volume"
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = soundEnabled && expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.VolumeDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Slider(
                            value = beepVolume,
                            onValueChange = onVolumeChange,
                            valueRange = 0f..100f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Icon(
                            Icons.Outlined.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        "${beepVolume.roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ToggleSetting(
                        icon = Icons.Outlined.RecordVoiceOver,
                        title = "Voice Alerts",
                        subtitle = "Spoken distance announcements",
                        checked = voiceAlerts,
                        onCheckedChange = onVoiceAlertsToggle
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundTestSection() {
    val context = LocalContext.current
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val maxVol = am?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 0
    val currentVol = am?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 0
    val volPercent = if (maxVol > 0) (currentVol * 100 / maxVol) else 0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            SectionHeader("Sound Test")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Test if alarm sound is working properly",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        AlarmManager.testSound(context)
                        Toast.makeText(
                            context,
                            "Testing alarm sound...",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Alert")
                }

                OutlinedButton(
                    onClick = { AlarmManager.stopAll() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    AlarmManager.testDifferentTones(context)
                    Toast.makeText(
                        context,
                        "Testing all tones...",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🎵 Test All Tones")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = if (volPercent > 50) Color(0xFF4CAF50) else Color(0xFFE53935)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Alarm Volume: $volPercent%",
                    color = if (volPercent > 50) Color(0xFF4CAF50) else Color(0xFFE53935),
                    fontSize = 12.sp
                )
            }

            if (volPercent < 50) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Increase alarm volume in phone settings!",
                    fontSize = 11.sp,
                    color = Color(0xFFE53935)
                )
            }
        }
    }
}

@Composable
private fun ServerSection(
    url: String,
    onUrlChange: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var editUrl by remember(url) { mutableStateOf(url) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            SectionHeader("Server")
            Spacer(modifier = Modifier.height(8.dp))

            if (editing) {
                OutlinedTextField(
                    value = editUrl,
                    onValueChange = { editUrl = it },
                    label = { Text("Backend URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = {
                        editUrl = url
                        editing = false
                    }) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onUrlChange(editUrl)
                        editing = false
                    }) { Text("Save") }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            url.ifBlank { "Not set" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (url.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Backend API endpoint",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        editUrl = url
                        editing = true
                    }) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoSection(alertRssi: Int, alertDistance: Float) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            SectionHeader("Conversion Info")
            Spacer(modifier = Modifier.height(4.dp))
            InfoRow("Alert distance", "${alertDistance.roundToInt()} meters")
            InfoRow("Alert threshold", "$alertRssi dBm")
            InfoRow("Model", "Log-distance path loss")
            InfoRow("Smoothing", "Kalman filter")
            InfoRow("Updates", "Every ${250}ms")
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ToggleSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
