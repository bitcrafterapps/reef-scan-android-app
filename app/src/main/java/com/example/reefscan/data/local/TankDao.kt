package com.example.reefscan.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for tank operations
 */
@Dao
interface TankDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTank(tank: TankEntity): Long

    @Update
    suspend fun updateTank(tank: TankEntity)

    @Delete
    suspend fun deleteTank(tank: TankEntity)

    @Query("SELECT * FROM tanks ORDER BY timestamp DESC")
    fun getAllTanks(): Flow<List<TankEntity>>

    @Query("SELECT * FROM tanks WHERE id = :tankId")
    suspend fun getTankById(tankId: Long): TankEntity?

    @Query("SELECT COUNT(*) FROM tanks")
    suspend fun getTankCount(): Int
}

