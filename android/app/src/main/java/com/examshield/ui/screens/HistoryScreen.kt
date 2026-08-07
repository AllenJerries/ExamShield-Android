package com.examshield.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examshield.data.local.dao.TypeCount
import com.examshield.data.local.entities.ExamEntity
import com.examshield.data.local.entities.IncidentEntity
import com.examshield.data.repository.HistoryStats
import com.examshield.viewmodel.HistoryFilter
import com.examshield.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val incidents by viewModel.filteredIncidents.collectAsState(initial = emptyList())
    val exams by viewModel.allExams.collectAsState(initial = emptyList())
    val stats by viewModel.stats.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedExam by viewModel.selectedExam.collectAsState()
    val examIncidents by viewModel.examIncidents.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showExamDetail by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val bgDark = Color(0xFF0A0E1A)
    val cardBg = Color(0xFF141B2D)

    if (selectedExam != null && showExamDetail) {
        ExamDetailScreen(
            exam = selectedExam!!,
            incidents = examIncidents,
            onBack = {
                showExamDetail = false
                viewModel.clearExamSelection()
            },
            onExport = {
                val csv = viewModel.exportIncidentsForExam(selectedExam!!.id)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TEXT, csv)
                    putExtra(Intent.EXTRA_SUBJECT, "Exam Report - ${selectedExam!!.name}")
                }
                context.startActivity(Intent.createChooser(intent, "Share Report"))
            },
            cardBg = cardBg,
            bgDark = bgDark
        )
        return
    }

    Scaffold(
        containerColor = bgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("History", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "${incidents.size} incidents",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgDark),
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, "Search", tint = Color.White)
                    }
                    IconButton(onClick = {
                        val csv = viewModel.exportToCsv()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_TEXT, csv)
                            putExtra(Intent.EXTRA_SUBJECT, "ExamShield History Report")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share History"))
                    }) {
                        Icon(Icons.Default.Share, "Export", tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            if (showSearch) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search by name or MAC...", color = Color.Gray) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, null, tint = Color.Gray)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            stats?.let { stats ->
                item {
                    StatisticsCard(stats = stats, cardBg = cardBg)
                    Spacer(Modifier.height(4.dp))
                }
                item {
                    CellularInfoCard(cardBg = cardBg)
                    Spacer(Modifier.height(4.dp))
                }
            }

            item {
                FilterChipsRow(
                    currentFilter = filter,
                    onFilterChange = { viewModel.setFilter(it) }
                )
            }

            item {
                if (exams.isNotEmpty()) {
                    Text(
                        "Exam Sessions",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            exams.forEach { exam ->
                item {
                    ExamSessionCard(
                        exam = exam,
                        cardBg = cardBg,
                        onClick = {
                            viewModel.selectExam(exam)
                            showExamDetail = true
                        }
                    )
                }
            }

            item {
                if (exams.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "All Incidents",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            if (incidents.isEmpty()) {
                item {
                    EmptyHistoryCard(cardBg = cardBg)
                }
            } else {
                items(incidents, key = { it.id }) { incident ->
                    IncidentCard(incident = incident, cardBg = cardBg)
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = cardBg,
            title = { Text("Clear All History?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This cannot be undone. All incidents and exam records will be permanently deleted.", color = Color.Gray) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun StatisticsCard(stats: HistoryStats, cardBg: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Statistics",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(Icons.Default.Assignment, "Exams", "${stats.totalExams}", Color(0xFF00E5FF))
                StatItem(Icons.Default.Warning, "Incidents", "${stats.totalIncidents}", Color(0xFFFFA726))
                StatItem(Icons.Default.PriorityHigh, "Critical", "${stats.criticalIncidents}", Color(0xFFFF1744))
                StatItem(Icons.Default.DevicesOther, "Devices", "${stats.uniqueDevices}", Color(0xFF4ADE80))
            }
            if (stats.deviceDistribution.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Most Detected Devices",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                stats.deviceDistribution.take(3).forEach { type ->
                    DeviceTypeBar(
                        label = getTypeName(type.deviceType),
                        count = type.count,
                        maxCount = stats.deviceDistribution.first().count
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
private fun DeviceTypeBar(label: String, count: Int, maxCount: Int) {
    val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.LightGray, fontSize = 12.sp)
            Text("$count", color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.Gray.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF00E5FF), Color(0xFF4ADE80))
                        )
                    )
            )
        }
    }
}

@Composable
fun CellularInfoCard(cardBg: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SignalCellular4Bar,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Cellular Detection",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Cellular signal scanning is available during live exams. " +
                        "It detects nearby cellular signals, estimates phone density, " +
                        "and monitors mobile data status on this device.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFFFA726),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Cannot identify specific devices or data usage of others",
                    color = Color(0xFFFFA726),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun FilterChipsRow(currentFilter: HistoryFilter, onFilterChange: (HistoryFilter) -> Unit) {
    val filters = HistoryFilter.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        filters.forEach { filter ->
            val isSelected = currentFilter == filter
            val chipColor = when (filter) {
                HistoryFilter.ALL -> Color(0xFF00E5FF)
                HistoryFilter.CRITICAL -> Color(0xFFFF1744)
                HistoryFilter.HIGH -> Color(0xFFFF6B6B)
                HistoryFilter.MEDIUM -> Color(0xFFFFA726)
                HistoryFilter.PHONES -> Color(0xFF7C4DFF)
                HistoryFilter.HOTSPOTS -> Color(0xFFFF6D00)
                HistoryFilter.EARPHONES -> Color(0xFF00BCD4)
                HistoryFilter.WATCHES -> Color(0xFF4ADE80)
            }
            FilterChip(
                selected = isSelected,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        filter.label,
                        fontSize = 12.sp,
                        color = if (isSelected) Color.White else Color.LightGray
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipColor.copy(alpha = 0.25f),
                    containerColor = Color(0xFF1E2A3A)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = chipColor.copy(alpha = 0.5f),
                    selectedBorderColor = chipColor
                )
            )
        }
    }
}

@Composable
private fun ExamSessionCard(exam: ExamEntity, cardBg: Color, onClick: () -> Unit) {
    val statusColor = when (exam.status) {
        "ACTIVE" -> Color(0xFF4ADE80)
        "COMPLETED" -> Color(0xFF00E5FF)
        "CANCELLED" -> Color(0xFFFF1744)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Assignment,
                    null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(exam.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${exam.hallName} - Room ${exam.roomNumber}",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        exam.status,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(Icons.Default.DevicesOther, "${exam.totalDevicesDetected} devices")
                InfoItem(Icons.Default.Warning, "${exam.criticalIncidents} critical")
                InfoItem(Icons.Default.Schedule, formatTimestamp(exam.startTime))
            }
        }
    }
}

@Composable
fun IncidentCard(incident: IncidentEntity, cardBg: Color) {
    val riskColor = when (incident.riskLevel) {
        "CRITICAL" -> Color(0xFFFF1744)
        "HIGH" -> Color(0xFFFF6B6B)
        "MEDIUM" -> Color(0xFFFFA726)
        else -> Color(0xFF4ADE80)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, riskColor.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = getIconForType(incident.deviceType),
                    contentDescription = null,
                    tint = riskColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        incident.deviceName.ifEmpty { "Unknown Device" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatTimestamp(incident.timestamp),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = riskColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        incident.riskLevel,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoItem(Icons.Default.Fingerprint, incident.macAddress.take(8) + "...")
                InfoItem(Icons.Default.SignalCellular4Bar, "${incident.rssi} dBm")
                InfoItem(Icons.Default.Straighten, "${incident.estimatedDistance.toInt()}m")
                InfoItem(Icons.Default.Wifi, incident.source.take(4))
            }
            if (incident.notes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(incident.notes, color = Color.Gray, fontSize = 11.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun InfoItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Color.LightGray, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyHistoryCard(cardBg: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle, null,
                tint = Color(0xFF4ADE80),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("No Incidents Yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Detected devices will appear here", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ExamDetailScreen(
    exam: ExamEntity,
    incidents: List<IncidentEntity>,
    onBack: () -> Unit,
    onExport: () -> Unit,
    cardBg: Color,
    bgDark: Color
) {
    val statusColor = when (exam.status) {
        "ACTIVE" -> Color(0xFF4ADE80)
        "COMPLETED" -> Color(0xFF00E5FF)
        else -> Color.Gray
    }

    Scaffold(
        containerColor = bgDark,
        topBar = {
            TopAppBar(
                title = { Text(exam.name, color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgDark),
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Share, "Export", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Exam Report", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Surface(
                                color = statusColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    exam.status,
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        DetailRow("Invigilator", exam.invigilatorName)
                        DetailRow("Hall", exam.hallName)
                        DetailRow("Room", exam.roomNumber)
                        DetailRow("Duration", exam.duration)
                        DetailRow("Started", formatTimestamp(exam.startTime))
                        exam.endTime?.let { DetailRow("Ended", formatTimestamp(it)) }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(Icons.Default.DevicesOther, "Devices", "${exam.totalDevicesDetected}", Color(0xFF00E5FF))
                            StatItem(Icons.Default.Warning, "Critical", "${exam.criticalIncidents}", Color(0xFFFF1744))
                            StatItem(Icons.Default.Assignment, "High", "${exam.highRiskIncidents}", Color(0xFFFF6B6B))
                            StatItem(Icons.Default.Info, "Medium", "${exam.mediumRiskIncidents}", Color(0xFFFFA726))
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Incidents (${incidents.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            if (incidents.isEmpty()) {
                item { EmptyHistoryCard(cardBg = cardBg) }
            } else {
                items(incidents, key = { it.id }) { incident ->
                    IncidentCard(incident = incident, cardBg = cardBg)
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun getIconForType(deviceType: String): ImageVector {
    return when {
        deviceType.contains("PHONE") -> Icons.Default.PhoneAndroid
        deviceType.contains("WATCH") || deviceType == "SMARTWATCH" -> Icons.Default.Watch
        deviceType.contains("EAR") || deviceType.contains("EARPHONE") -> Icons.Default.Headphones
        deviceType.contains("HOTSPOT") || deviceType.contains("WIFI") -> Icons.Default.Wifi
        deviceType == "HIDDEN_EARPIECE" -> Icons.Default.Hearing
        else -> Icons.Default.DevicesOther
    }
}

private fun getTypeName(deviceType: String): String {
    return when (deviceType) {
        "PHONE_ANDROID" -> "Android Phones"
        "PHONE_IOS" -> "iOS Phones"
        "SMARTWATCH" -> "Smartwatches"
        "EARPHONE" -> "Earphones"
        "HIDDEN_EARPIECE" -> "Hidden Earpieces"
        "MOBILE_HOTSPOT" -> "Mobile Hotspots"
        "WIFI_DEVICE" -> "WiFi Devices"
        else -> deviceType.replace("_", " ")
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
