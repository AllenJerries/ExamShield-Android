package com.examshield.data.local

import androidx.room.*
import com.examshield.data.models.Exam
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam): Long

    @Update
    suspend fun updateExam(exam: Exam)

    @Query("SELECT * FROM exams ORDER BY createdAt DESC")
    fun getAllExams(): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :examId LIMIT 1")
    suspend fun getExamById(examId: Long): Exam?

    @Query("SELECT * FROM exams WHERE id = :examId LIMIT 1")
    fun getExamByIdFlow(examId: Long): Flow<Exam?>

    @Query("SELECT * FROM exams WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveExam(): Exam?

    @Query("SELECT * FROM exams WHERE isActive = 1 LIMIT 1")
    fun getActiveExamFlow(): Flow<Exam?>

    @Query("UPDATE exams SET isActive = 0, isCompleted = 1, endedAt = :endedAt, devicesDetected = :devices, incidentsCount = :incidents WHERE id = :examId")
    suspend fun endExam(examId: Long, endedAt: Long, devices: Int, incidents: Int)

    @Query("UPDATE exams SET isActive = 1, startedAt = :startedAt WHERE id = :examId")
    suspend fun startExam(examId: Long, startedAt: Long)

    @Query("SELECT COUNT(*) FROM exams")
    fun getTotalExamsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM exams")
    suspend fun getTotalExamsCountSync(): Int

    @Query("DELETE FROM exams WHERE id = :examId")
    suspend fun deleteExam(examId: Long)
}
