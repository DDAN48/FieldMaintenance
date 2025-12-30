package com.example.fieldmaintenance.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldmaintenance.data.model.ReportPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportPhotoDao {
    @Query("SELECT * FROM report_photos WHERE reportId = :reportId ORDER BY createdAt DESC")
    fun getByReportId(reportId: String): Flow<List<ReportPhoto>>

    @Query("SELECT * FROM report_photos WHERE reportId = :reportId ORDER BY createdAt DESC")
    suspend fun listByReportId(reportId: String): List<ReportPhoto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: ReportPhoto)

    @Query("DELETE FROM report_photos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM report_photos WHERE reportId = :reportId")
    suspend fun deleteByReportId(reportId: String)
}


