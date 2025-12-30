package com.example.fieldmaintenance.data.dao

import androidx.room.*
import com.example.fieldmaintenance.data.model.MaintenanceReport
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceReportDao {
    @Query("SELECT * FROM maintenance_reports WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun getAllReports(): Flow<List<MaintenanceReport>>

    @Query("SELECT * FROM maintenance_reports WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashReports(): Flow<List<MaintenanceReport>>
    
    @Query("SELECT * FROM maintenance_reports WHERE id = :id")
    suspend fun getReportById(id: String): MaintenanceReport?
    
    // Search only by nodeName (approx match)
    @Query("SELECT * FROM maintenance_reports WHERE deletedAt IS NULL AND nodeName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchReports(query: String): Flow<List<MaintenanceReport>>

    @Query("UPDATE maintenance_reports SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun moveToTrash(id: String, deletedAt: Long)

    @Query("UPDATE maintenance_reports SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: String)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: MaintenanceReport)
    
    @Update
    suspend fun updateReport(report: MaintenanceReport)
    
    @Delete
    suspend fun deleteReport(report: MaintenanceReport)
}

