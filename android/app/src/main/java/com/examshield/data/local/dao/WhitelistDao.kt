package com.examshield.data.local.dao

import androidx.room.*
import com.examshield.data.local.entities.WhitelistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToWhitelist(device: WhitelistEntity)

    @Delete
    suspend fun removeFromWhitelist(device: WhitelistEntity)

    @Query("SELECT * FROM whitelist_v2 ORDER BY addedTime DESC")
    fun getAllWhitelisted(): Flow<List<WhitelistEntity>>

    @Query("SELECT * FROM whitelist_v2 WHERE macAddress = :mac")
    suspend fun isWhitelisted(mac: String): WhitelistEntity?

    @Query("SELECT macAddress FROM whitelist_v2")
    suspend fun getAllWhitelistedMacs(): List<String>

    @Query("DELETE FROM whitelist_v2")
    suspend fun clearWhitelist()
}
