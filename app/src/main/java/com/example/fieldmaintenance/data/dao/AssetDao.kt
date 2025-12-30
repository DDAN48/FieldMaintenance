package com.example.fieldmaintenance.data.dao

import androidx.room.*
import com.example.fieldmaintenance.data.model.Asset
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets WHERE reportId = :reportId")
    fun getAssetsByReportId(reportId: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE reportId = :reportId")
    suspend fun listAssetsByReportId(reportId: String): List<Asset>

    @Query("SELECT * FROM assets WHERE id = :assetId")
    suspend fun getAssetById(assetId: String): Asset?
    
    @Query("SELECT * FROM assets WHERE reportId = :reportId AND type = 'NODE'")
    suspend fun getNodeByReportId(reportId: String): Asset?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: Asset)
    
    @Update
    suspend fun updateAsset(asset: Asset)
    
    @Delete
    suspend fun deleteAsset(asset: Asset)
    
    @Query("DELETE FROM assets WHERE reportId = :reportId")
    suspend fun deleteAssetsByReportId(reportId: String)
}

