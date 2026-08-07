package com.examshield.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examshield.utils.toFormattedDate
import com.examshield.viewmodel.SetupViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onExamCreated: (Long) -> Unit,
    onBack: () -> Unit,
    setupViewModel: SetupViewModel = viewModel()
) {
    val hallName by setupViewModel.hallName.collectAsState()
    val roomNumber by setupViewModel.roomNumber.collectAsState()
    val examName by setupViewModel.examName.collectAsState()
    val examDate by setupViewModel.examDate.collectAsState()
    val invigilatorName by setupViewModel.invigilatorName.collectAsState()
    val durationMinutes by setupViewModel.durationMinutes.collectAsState()
    val validationError by setupViewModel.validationError.collectAsState()
    val examCreated by setupViewModel.examCreated.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(examCreated) {
        examCreated?.let { examId ->
            setupViewModel.resetCreationState()
            onExamCreated(examId)
        }
    }

    var durationExpanded by remember { mutableStateOf(false) }
    val durationOptions = listOf("1 Hour" to 60, "2 Hours" to 120, "3 Hours" to 180)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Exam Setup") },
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create New Exam",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = hallName,
                onValueChange = { setupViewModel.updateHallName(it) },
                label = { Text("Hall Name") },
                leadingIcon = { Icon(Icons.Default.MeetingRoom, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = roomNumber,
                onValueChange = { setupViewModel.updateRoomNumber(it) },
                label = { Text("Room Number") },
                leadingIcon = { Icon(Icons.Default.Numbers, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = examName,
                onValueChange = { setupViewModel.updateExamName(it) },
                label = { Text("Exam Name") },
                leadingIcon = { Icon(Icons.Default.Grade, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = examDate.toFormattedDate(),
                onValueChange = {},
                label = { Text("Exam Date") },
                leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = examDate }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val selected = Calendar.getInstance().apply {
                                    set(year, month, day)
                                }.timeInMillis
                                setupViewModel.updateExamDate(selected)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Icon(Icons.Default.EditCalendar, contentDescription = "Pick Date")
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = invigilatorName,
                onValueChange = { setupViewModel.updateInvigilatorName(it) },
                label = { Text("Invigilator Name") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = durationExpanded,
                onExpandedChange = { durationExpanded = it }
            ) {
                OutlinedTextField(
                    value = durationOptions.find { it.second == durationMinutes }?.first ?: "2 Hours",
                    onValueChange = {},
                    label = { Text("Duration") },
                    leadingIcon = { Icon(Icons.Default.Timer, null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    readOnly = true
                )

                ExposedDropdownMenu(
                    expanded = durationExpanded,
                    onDismissRequest = { durationExpanded = false }
                ) {
                    durationOptions.forEach { (label, minutes) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                setupViewModel.updateDurationMinutes(minutes)
                                durationExpanded = false
                            }
                        )
                    }
                }
            }

            if (validationError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = validationError!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { setupViewModel.createExam() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create & Scan Baseline", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
