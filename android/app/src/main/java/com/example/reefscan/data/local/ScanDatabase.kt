package com.bitcraftapps.reefscan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for ReefScan app
 */
@Database(
    entities = [ScanEntity::class, TankEntity::class, GalleryImageEntity::class],
    version = 4,
    exportSchema = false
)
abstract class ScanDatabase : RoomDatabase() {
    
    /**
     * Get the ScanDao instance
     */
    abstract fun scanDao(): ScanDao

    /**
     * Get the TankDao instance
     */
    abstract fun tankDao(): TankDao

    /**
     * Get the GalleryImageDao instance
     */
    abstract fun galleryImageDao(): GalleryImageDao
    
    companion object {
        private const val DATABASE_NAME = "reefscan_database"
        
        @Volatile
        private var INSTANCE: ScanDatabase? = null
        
        /**
         * Get the singleton database instance
         */
        fun getInstance(context: Context): ScanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
