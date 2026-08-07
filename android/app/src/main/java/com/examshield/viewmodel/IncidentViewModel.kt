package com.examshield.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examshield.data.local.AppDatabase
import com.examshield.data.models.Incident
import com.examshield.data.repository.IncidentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IncidentViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val incidentRepository = IncidentRepository(db)

    val allIncidents: StateFlow<List<Incident>> = incidentRepository.getAllIncidents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalIncidents: StateFlow<Int> = incidentRepository.getTotalIncidentsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _filterRiskLevel = MutableStateFlow<String?>(null)
    val filterRiskLevel: StateFlow<String?> = _filterRiskLevel.asStateFlow()

    private val _filterDeviceType = MutableStateFlow<String?>(null)
    val filterDeviceType: StateFlow<String?> = _filterDeviceType.asStateFlow()

    private val _filteredIncidents = MutableStateFlow<List<Incident>>(emptyList())
    val filteredIncidents: StateFlow<List<Incident>> = _filteredIncidents.asStateFlow()

    fun filterByRiskLevel(riskLevel: String?) {
        _filterRiskLevel.value = riskLevel
        applyFilters()
    }

    fun filterByDeviceType(deviceType: String?) {
        _filterDeviceType.value = deviceType
        applyFilters()
    }

    private fun applyFilters() {
        viewModelScope.launch {
            val risk = _filterRiskLevel.value
            val type = _filterDeviceType.value

            val flow = when {
                risk != null && type != null -> {
                    incidentRepository.getAllIncidents().map { list ->
                        list.filter { it.riskLevel == risk && it.deviceType == type }
                    }
                }
                risk != null -> incidentRepository.getIncidentsByRiskLevel(risk)
                type != null -> incidentRepository.getIncidentsByDeviceType(type)
                else -> incidentRepository.getAllIncidents()
            }

            flow.collect { _filteredIncidents.value = it }
        }
    }

    fun updateAction(incidentId: Long, action: String) {
        viewModelScope.launch {
            incidentRepository.updateAction(incidentId, action)
        }
    }

    fun deleteIncident(incident: Incident) {
        viewModelScope.launch {
            incidentRepository.deleteIncident(incident)
        }
    }

    fun getExportText(): String {
        val incidents = _filteredIncidents.value
        val sb = StringBuilder()
        sb.appendLine("ExamShield Incident Report")
        sb.appendLine("=========================")
        sb.appendLine("Generated: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine()

        for (incident in incidents) {
            sb.appendLine("Date: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(incident.timestamp))}")
            sb.appendLine("Device: ${incident.deviceName} (${incident.macAddress})")
            sb.appendLine("Type: ${incident.deviceType} | Risk: ${incident.riskLevel}")
            sb.appendLine("RSSI: ${incident.rssi} dBm | Room: ${incident.roomName}")
            sb.appendLine("Exam: ${incident.examName} | Action: ${incident.actionTaken}")
            sb.appendLine("---")
        }

        sb.appendLine("Total Incidents: ${incidents.size}")
        return sb.toString()
    }
}
