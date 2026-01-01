package com.example.fieldmaintenance.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldmaintenance.data.model.NodeAdjustment
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeAdjustmentDao {
    @Query("SELECT * FROM node_adjustments WHERE assetId = :assetId LIMIT 1")
    fun getByAssetId(assetId: String): Flow<NodeAdjustment?>

    @Query("SELECT * FROM node_adjustments WHERE assetId = :assetId LIMIT 1")
    suspend fun getOneByAssetId(assetId: String): NodeAdjustment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(adjustment: NodeAdjustment)

    @Query("DELETE FROM node_adjustments WHERE assetId = :assetId")
    suspend fun deleteByAssetId(assetId: String)

    @Query("DELETE FROM node_adjustments WHERE reportId = :reportId")
    suspend fun deleteByReportId(reportId: String)
}


