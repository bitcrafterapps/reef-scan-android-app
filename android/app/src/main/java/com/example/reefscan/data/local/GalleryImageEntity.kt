package com.bitcraftapps.reefscan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gallery_images")
data class GalleryImageEntity(
    @PrimaryKey
    val path: String, // Relative path from files dir
    val tankId: Long,
    val dateTaken: Long,
    val rating: Int = 0 // 0 means no rating
)

