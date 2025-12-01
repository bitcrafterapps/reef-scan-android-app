package com.example.reefscan.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for scan operations
 */
@Dao
interface ScanDao {
    
    /**
     * Insert a new scan
     * Returns the generated ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity): Long
    
    /**
     * Get all scans ordered by timestamp (newest first)
     * Limited to 10 most recent
     */
    @Query("SELECT * FROM scans ORDER BY timestamp DESC LIMIT 10")
    fun getAllScans(): Flow<List<ScanEntity>>

    /**
     * Get all scans for a specific tank ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM scans WHERE tankId = :tankId ORDER BY timestamp DESC")
    fun getScansForTank(tankId: Long): Flow<List<ScanEntity>>

    /**
     * Get all scans for a specific tank as a List (suspend)
     */
    @Query("SELECT * FROM scans WHERE tankId = :tankId")
    suspend fun getScansForTankList(tankId: Long): List<ScanEntity>
    
    /**
     * Get all scans as a one-time list (not Flow)
     */
    @Query("SELECT * FROM scans ORDER BY timestamp DESC LIMIT 10")
    suspend fun getAllScansOnce(): List<ScanEntity>
    
    /**
     * Get a single scan by ID
     */
    @Query("SELECT * FROM scans WHERE id = :scanId")
    suspend fun getScanById(scanId: Long): ScanEntity?
    
    /**
     * Get the count of all scans
     */
    @Query("SELECT COUNT(*) FROM scans")
    suspend fun getScanCount(): Int
    
    /**
     * Delete a specific scan
     */
    @Delete
    suspend fun deleteScan(scan: ScanEntity)
    
    /**
     * Delete scan by ID
     */
    @Query("DELETE FROM scans WHERE id = :scanId")
    suspend fun deleteScanById(scanId: Long)

    /**
     * Delete all scans for a specific tank
     */
    @Query("DELETE FROM scans WHERE tankId = :tankId")
    suspend fun deleteScansByTankId(tankId: Long)
    
    /**
     * Delete oldest scans when count exceeds limit
     * Keeps only the 10 most recent scans
     */
    @Query("""
        DELETE FROM scans WHERE id NOT IN (
            SELECT id FROM scans ORDER BY timestamp DESC LIMIT 10
        )
    """)
    suspend fun deleteOldestScans()
    
    /**
     * Delete all scans
     */
    @Query("DELETE FROM scans")
    suspend fun deleteAllScans()
}

