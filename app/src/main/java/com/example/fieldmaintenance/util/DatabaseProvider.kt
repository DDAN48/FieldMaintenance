package com.example.fieldmaintenance.util

import android.content.Context
import androidx.room.Room
import com.example.fieldmaintenance.data.database.AppDatabase
import com.example.fieldmaintenance.data.repository.MaintenanceRepository

object DatabaseProvider {
    private var database: AppDatabase? = null
    private var repository: MaintenanceRepository? = null
    
    fun init(context: Context) {
        if (database == null) {
            database = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "maintenance_database"
            )
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .addMigrations(AppDatabase.MIGRATION_2_3)
                .addMigrations(AppDatabase.MIGRATION_3_4)
                .addMigrations(AppDatabase.MIGRATION_4_5)
                .addMigrations(AppDatabase.MIGRATION_5_6)
                .addMigrations(AppDatabase.MIGRATION_6_7)
                .addMigrations(AppDatabase.MIGRATION_7_8)
                .build()
            
            repository = MaintenanceRepository(
                database!!.maintenanceReportDao(),
                database!!.assetDao(),
                database!!.photoDao(),
                database!!.amplifierAdjustmentDao(),
                database!!.passiveItemDao(),
                database!!.reportPhotoDao()
            )
        }
    }
    
    fun getRepository(): MaintenanceRepository {
        return repository ?: throw IllegalStateException("Database not initialized")
    }
}

