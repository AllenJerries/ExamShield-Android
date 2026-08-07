package com.examshield.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examshield.data.models.Incident
import com.examshield.ui.theme.*
import com.examshield.utils.calculateDistanceFromRssi
import com.examshield.utils.formatDistanceShort
import com.examshield.utils.toFormattedDateTime
import com.examshield.viewmodel.IncidentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentHistoryScreen(
    onBack: () -> Unit,
    incidentViewModel: IncidentViewModel = viewModel()
) {
    val filteredIncidents by incidentViewModel.filteredIncidents.collectAsState()
    val allIncidents by incidentViewModel.allIncidents.collectAsState()
    val filterRiskLevel by incidentViewModel.filterRiskLevel.collectAsState()
    val filterDeviceType by incidentViewModel.filterDeviceType.collectAsState()

    val incidents = if (filteredIncidents.isNotEmpty() || filterRiskLevel != null || filterDeviceType != null)
        filteredIncidents else allIncidents

    val context = LocalContext.current
    var showFilterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Incident History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                    IconButton(onClick = {
                        val text = incidentViewModel.getExportText()
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, text)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Export Incidents"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${incidents.size} incidents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (filterRiskLevel != null) {
                        FilterChip(
                            selected = true,
                            onClick = { incidentViewModel.filterByRiskLevel(null) },
                            label = { Text(filterRiskLevel!!) },
                            leadingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    if (filterDeviceType != null) {
                        FilterChip(
                            selected = true,
                            onClick = { incidentViewModel.filterByDeviceType(null) },
                            label = { Text(filterDeviceType!!) },
                            leadingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            if (incidents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Incidents",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "No unauthorized devices detected yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(incidents) { incident ->
                        IncidentCard(incident = incident)
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        FilterDialog(
            onDismiss = { showFilterDialog = false },
            onFilterRiskLevel = {
                incidentViewModel.filterByRiskLevel(it)
                showFilterDialog = false
            },
            onFilterDeviceType = {
                incidentViewModel.filterByDeviceType(it)
                showFilterDialog = false
            }
        )
    }
}

@Composable
private fun IncidentCard(incident: Incident) {
    val riskColor = when (incident.riskLevel) {
        "HIGH" -> RiskHigh
        "MEDIUM" -> RiskMedium
        "LOW" -> RiskLow
        "CRITICAL" -> RiskCritical
        else -> RiskLow
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = incident.deviceName.ifEmpty { "Unknown" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = riskColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = incident.riskLevel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = riskColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${incident.macAddress} | ${incident.deviceType}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = incident.timestamp.toFormattedDateTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${incident.rssi} dBm (${formatDistanceShort(calculateDistanceFromRssi(incident.rssi))} away)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (incident.roomName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${incident.roomName} - ${incident.examName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FilterDialog(
    onDismiss: () -> Unit,
    onFilterRiskLevel: (String) -> Unit,
    onFilterDeviceType: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Incidents", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Risk Level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                listOf("HIGH", "MEDIUM", "LOW", "CRITICAL").forEach { level ->
                    TextButton(onClick = { onFilterRiskLevel(level) }) {
                        Text(level)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Device Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                listOf("PHONE_ANDROID", "PHONE_IOS", "SMARTWATCH", "EARPHONE", "HIDDEN_EARPIECE", "MOBILE_HOTSPOT", "WIFI_DEVICE", "UNKNOWN", "OTHER").forEach { type ->
                    TextButton(onClick = { onFilterDeviceType(type) }) {
                        Text(type.replace("_", " "))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
