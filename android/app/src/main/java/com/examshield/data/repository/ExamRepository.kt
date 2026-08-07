package com.examshield.data.repository

import com.examshield.data.local.AppDatabase
import com.examshield.data.models.Exam
import kotlinx.coroutines.flow.Flow

class ExamRepository(private val db: AppDatabase) {

    private val examDao = db.examDao()

    suspend fun insertExam(exam: Exam): Long {
        return examDao.insertExam(exam)
    }

    suspend fun updateExam(exam: Exam) {
        examDao.updateExam(exam)
    }

    fun getAllExams(): Flow<List<Exam>> {
        return examDao.getAllExams()
    }

    suspend fun getExamById(examId: Long): Exam? {
        return examDao.getExamById(examId)
    }

    fun getExamByIdFlow(examId: Long): Flow<Exam?> {
        return examDao.getExamByIdFlow(examId)
    }

    suspend fun getActiveExam(): Exam? {
        return examDao.getActiveExam()
    }

    fun getActiveExamFlow(): Flow<Exam?> {
        return examDao.getActiveExamFlow()
    }

    suspend fun startExam(examId: Long) {
        examDao.startExam(examId, System.currentTimeMillis())
    }

    suspend fun endExam(examId: Long, devices: Int, incidents: Int) {
        examDao.endExam(examId, System.currentTimeMillis(), devices, incidents)
    }

    fun getTotalExamsCount(): Flow<Int> {
        return examDao.getTotalExamsCount()
    }

    suspend fun deleteExam(examId: Long) {
        examDao.deleteExam(examId)
    }
}
