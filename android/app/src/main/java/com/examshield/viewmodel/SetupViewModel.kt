package com.examshield.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examshield.data.local.AppDatabase
import com.examshield.data.models.Exam
import com.examshield.data.repository.ExamRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val examRepository = ExamRepository(db)

    private val _hallName = MutableStateFlow("")
    val hallName: StateFlow<String> = _hallName.asStateFlow()

    private val _roomNumber = MutableStateFlow("")
    val roomNumber: StateFlow<String> = _roomNumber.asStateFlow()

    private val _examName = MutableStateFlow("")
    val examName: StateFlow<String> = _examName.asStateFlow()

    private val _examDate = MutableStateFlow(System.currentTimeMillis())
    val examDate: StateFlow<Long> = _examDate.asStateFlow()

    private val _invigilatorName = MutableStateFlow("")
    val invigilatorName: StateFlow<String> = _invigilatorName.asStateFlow()

    private val _durationMinutes = MutableStateFlow(120)
    val durationMinutes: StateFlow<Int> = _durationMinutes.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _examCreated = MutableStateFlow<Long?>(null)
    val examCreated: StateFlow<Long?> = _examCreated.asStateFlow()

    fun updateHallName(value: String) { _hallName.value = value }
    fun updateRoomNumber(value: String) { _roomNumber.value = value }
    fun updateExamName(value: String) { _examName.value = value }
    fun updateExamDate(value: Long) { _examDate.value = value }
    fun updateInvigilatorName(value: String) { _invigilatorName.value = value }
    fun updateDurationMinutes(value: Int) { _durationMinutes.value = value }

    fun createExam() {
        if (_hallName.value.isBlank()) {
            _validationError.value = "Hall name is required"
            return
        }
        if (_roomNumber.value.isBlank()) {
            _validationError.value = "Room number is required"
            return
        }
        if (_examName.value.isBlank()) {
            _validationError.value = "Exam name is required"
            return
        }
        if (_invigilatorName.value.isBlank()) {
            _validationError.value = "Invigilator name is required"
            return
        }

        _validationError.value = null

        viewModelScope.launch {
            val exam = Exam(
                hallName = _hallName.value.trim(),
                roomNumber = _roomNumber.value.trim(),
                examName = _examName.value.trim(),
                examDate = _examDate.value,
                invigilatorName = _invigilatorName.value.trim(),
                durationMinutes = _durationMinutes.value
            )
            val id = examRepository.insertExam(exam)
            _examCreated.value = id
        }
    }

    fun clearError() {
        _validationError.value = null
    }

    fun resetCreationState() {
        _examCreated.value = null
    }
}
