package com.examshield.data.local.dao

import androidx.room.*
import com.examshield.data.local.entities.ExamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: ExamEntity): Long

    @Update
    suspend fun updateExam(exam: ExamEntity)

    @Delete
    suspend fun deleteExam(exam: ExamEntity)

    @Query("SELECT * FROM exams_v2 ORDER BY startTime DESC")
    fun getAllExams(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams_v2 WHERE id = :examId")
    suspend fun getExamById(examId: Long): ExamEntity?

    @Query("SELECT * FROM exams_v2 WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveExam(): ExamEntity?

    @Query("""
        SELECT * FROM exams_v2 
        WHERE startTime BETWEEN :startDate AND :endDate
        ORDER BY startTime DESC
    """)
    fun getExamsBetweenDates(startDate: Long, endDate: Long): Flow<List<ExamEntity>>

    @Query("SELECT COUNT(*) FROM exams_v2")
    suspend fun getTotalExamsCount(): Int

    @Query("SELECT COUNT(*) FROM exams_v2 WHERE status = 'COMPLETED'")
    suspend fun getCompletedExamsCount(): Int

    @Query("DELETE FROM exams_v2 WHERE startTime < :beforeDate")
    suspend fun deleteOldExams(beforeDate: Long)

    @Query("DELETE FROM exams_v2")
    suspend fun deleteAllExams()
}
