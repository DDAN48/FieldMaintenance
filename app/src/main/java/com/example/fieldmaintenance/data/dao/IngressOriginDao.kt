package com.example.fieldmaintenance.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fieldmaintenance.data.model.IngressOrigin
import kotlinx.coroutines.flow.Flow

@Dao
interface IngressOriginDao {
    @Query("SELECT * FROM ingress_origins WHERE reportId = :reportId ORDER BY createdAt DESC")
    fun getByReportId(reportId: String): Flow<List<IngressOrigin>>

    @Query("SELECT * FROM ingress_origins WHERE reportId = :reportId ORDER BY createdAt DESC")
    suspend fun listByReportId(reportId: String): List<IngressOrigin>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: IngressOrigin)

    @Update
    suspend fun update(item: IngressOrigin)

    @Query("DELETE FROM ingress_origins WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM ingress_origins WHERE reportId = :reportId")
    suspend fun deleteByReportId(reportId: String)
}

