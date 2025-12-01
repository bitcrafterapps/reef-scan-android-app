package com.example.reefscan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GalleryImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(image: GalleryImageEntity)

    @Query("SELECT * FROM gallery_images WHERE path = :path")
    suspend fun getImage(path: String): GalleryImageEntity?

    @Query("DELETE FROM gallery_images WHERE path = :path")
    suspend fun deleteImage(path: String)

    @Query("DELETE FROM gallery_images WHERE tankId = :tankId")
    suspend fun deleteImagesForTank(tankId: Long)

    @Query("SELECT * FROM gallery_images WHERE tankId = :tankId")
    suspend fun getImagesForTank(tankId: Long): List<GalleryImageEntity>
}

