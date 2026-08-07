package com.examshield.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examshield.data.local.entities.ExamEntity
import com.examshield.data.local.entities.IncidentEntity
import com.examshield.data.repository.HistoryRepository
import com.examshield.data.repository.HistoryStats
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)

    val allExams: Flow<List<ExamEntity>> = repository.getAllExams()
    val allIncidents: Flow<List<IncidentEntity>> = repository.getAllIncidents()

    private val _filter = MutableStateFlow(HistoryFilter.ALL)
    val filter: StateFlow<HistoryFilter> = _filter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _stats = MutableStateFlow<HistoryStats?>(null)
    val stats: StateFlow<HistoryStats?> = _stats.asStateFlow()

    private val _selectedExam = MutableStateFlow<ExamEntity?>(null)
    val selectedExam: StateFlow<ExamEntity?> = _selectedExam.asStateFlow()

    private val _examIncidents = MutableStateFlow<List<IncidentEntity>>(emptyList())
    val examIncidents: StateFlow<List<IncidentEntity>> = _examIncidents.asStateFlow()

    val filteredIncidents: StateFlow<List<IncidentEntity>> = combine(
        allIncidents,
        _filter,
        _searchQuery
    ) { incidents, filter, query ->
        var result = incidents

        result = when (filter) {
            HistoryFilter.ALL -> result
            HistoryFilter.CRITICAL -> result.filter { it.riskLevel == "CRITICAL" }
            HistoryFilter.HIGH -> result.filter { it.riskLevel == "HIGH" }
            HistoryFilter.MEDIUM -> result.filter { it.riskLevel == "MEDIUM" }
            HistoryFilter.PHONES -> result.filter {
                it.deviceType in listOf("PHONE_ANDROID", "PHONE_IOS")
            }
            HistoryFilter.HOTSPOTS -> result.filter { it.deviceType == "MOBILE_HOTSPOT" }
            HistoryFilter.EARPHONES -> result.filter { it.deviceType == "EARPHONE" }
            HistoryFilter.WATCHES -> result.filter { it.deviceType == "SMARTWATCH" }
        }

        if (query.isNotEmpty()) {
            result = result.filter {
                it.deviceName.contains(query, ignoreCase = true) ||
                it.macAddress.contains(query, ignoreCase = true)
            }
        }

        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadStats()
    }

    fun setFilter(filter: HistoryFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadStats() {
        viewModelScope.launch {
            _stats.value = repository.getStatistics()
        }
    }

    fun selectExam(exam: ExamEntity) {
        _selectedExam.value = exam
        viewModelScope.launch {
            repository.getIncidentsForExam(exam.id).collect {
                _examIncidents.value = it
            }
        }
    }

    fun clearExamSelection() {
        _selectedExam.value = null
        _examIncidents.value = emptyList()
    }

    fun deleteIncident(incident: IncidentEntity) {
        viewModelScope.launch {
            repository.deleteIncident(incident)
            loadStats()
        }
    }

    fun deleteExam(exam: ExamEntity) {
        viewModelScope.launch {
            repository.deleteExam(exam)
            loadStats()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _selectedExam.value = null
            _examIncidents.value = emptyList()
            loadStats()
        }
    }

    fun exportToCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("Time,Device Name,MAC,Type,Risk,RSSI,Distance,Source")

        filteredIncidents.value.forEach { incident ->
            sb.appendLine(
                "${formatTime(incident.timestamp)}," +
                "${incident.deviceName}," +
                "${incident.macAddress}," +
                "${incident.deviceType}," +
                "${incident.riskLevel}," +
                "${incident.rssi}," +
                "${"%.1f".format(incident.estimatedDistance)}," +
                "${incident.source}"
            )
        }

        return sb.toString()
    }

    fun exportIncidentsForExam(examId: Long): String {
        val sb = StringBuilder()
        sb.appendLine("Time,Device Name,MAC,Type,Risk,RSSI,Distance,Source,Action")

        _examIncidents.value.forEach { incident ->
            sb.appendLine(
                "${formatTime(incident.timestamp)}," +
                "${incident.deviceName}," +
                "${incident.macAddress}," +
                "${incident.deviceType}," +
                "${incident.riskLevel}," +
                "${incident.rssi}," +
                "${"%.1f".format(incident.estimatedDistance)}," +
                "${incident.source}," +
                "${incident.actionTaken}"
            )
        }

        return sb.toString()
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

enum class HistoryFilter(val label: String) {
    ALL("All"),
    CRITICAL("Critical"),
    HIGH("High"),
    MEDIUM("Medium"),
    PHONES("Phones"),
    HOTSPOTS("Hotspots"),
    EARPHONES("Earphones"),
    WATCHES("Watches")
}
