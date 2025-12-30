package com.example.fieldmaintenance.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fieldmaintenance.data.model.PassiveItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PassiveItemDao {
    @Query("SELECT * FROM passive_items WHERE reportId = :reportId ORDER BY createdAt DESC")
    fun getByReportId(reportId: String): Flow<List<PassiveItem>>

    @Query("SELECT * FROM passive_items WHERE reportId = :reportId ORDER BY createdAt DESC")
    suspend fun listByReportId(reportId: String): List<PassiveItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PassiveItem)

    @Update
    suspend fun update(item: PassiveItem)

    @Query("DELETE FROM passive_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM passive_items WHERE reportId = :reportId")
    suspend fun deleteByReportId(reportId: String)
}


