package com.examshield.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.examshield.data.local.dao.ExamDao
import com.examshield.data.local.dao.IncidentDao
import com.examshield.data.local.dao.WhitelistDao
import com.examshield.data.local.entities.ExamEntity
import com.examshield.data.local.entities.IncidentEntity
import com.examshield.data.local.entities.WhitelistEntity
import com.examshield.data.models.Device
import com.examshield.data.models.Exam
import com.examshield.data.models.Incident

@Database(
    entities = [
        Device::class,
        Exam::class,
        Incident::class,
        ExamEntity::class,
        IncidentEntity::class,
        WhitelistEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceDao(): com.examshield.data.local.DeviceDao
    abstract fun examDao(): com.examshield.data.local.ExamDao
    abstract fun incidentDao(): com.examshield.data.local.IncidentDao
    abstract fun whitelistDao(): com.examshield.data.local.WhitelistDao

    abstract fun examDaoV2(): ExamDao
    abstract fun incidentDaoV2(): IncidentDao
    abstract fun whitelistDaoV2(): WhitelistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "examshield_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun getDatabase(context: Context): AppDatabase = getInstance(context)
    }
}
