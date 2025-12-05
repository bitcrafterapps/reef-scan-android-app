package com.bitcraftapps.reefscan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing Tank information
 */
@Entity(tableName = "tanks")
data class TankEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Name of the tank (e.g. "Living Room Reef")
     */
    val name: String,

    /**
     * Description or notes about the tank
     */
    val description: String,

    /**
     * Size of the tank (e.g. "120 Gallons")
     */
    val size: String,

    /**
     * Manufacturer of the tank (e.g. "Red Sea")
     */
    val manufacturer: String,

    /**
     * Path to the tank's main image file in internal storage
     * Can be null if no image is set
     */
    val imagePath: String? = null,

    /**
     * Timestamp when the tank was created
     */
    val timestamp: Long = System.currentTimeMillis()
)

