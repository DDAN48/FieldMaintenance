package com.example.fieldmaintenance.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldmaintenance.data.model.AmplifierAdjustment
import kotlinx.coroutines.flow.Flow

@Dao
interface AmplifierAdjustmentDao {
    @Query("SELECT * FROM amplifier_adjustments WHERE assetId = :assetId LIMIT 1")
    fun getByAssetId(assetId: String): Flow<AmplifierAdjustment?>

    @Query("SELECT * FROM amplifier_adjustments WHERE assetId = :assetId LIMIT 1")
    suspend fun getOneByAssetId(assetId: String): AmplifierAdjustment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(adjustment: AmplifierAdjustment)

    @Query("DELETE FROM amplifier_adjustments WHERE assetId = :assetId")
    suspend fun deleteByAssetId(assetId: String)
}


