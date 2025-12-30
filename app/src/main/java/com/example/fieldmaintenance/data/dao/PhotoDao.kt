package com.example.fieldmaintenance.data.dao

import androidx.room.*
import com.example.fieldmaintenance.data.model.Photo
import com.example.fieldmaintenance.data.model.PhotoType
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE assetId = :assetId")
    fun getPhotosByAssetId(assetId: String): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE assetId = :assetId")
    suspend fun listPhotosByAssetId(assetId: String): List<Photo>
    
    @Query("SELECT * FROM photos WHERE assetId = :assetId AND photoType = :photoType")
    suspend fun getPhotosByAssetIdAndType(assetId: String, photoType: PhotoType): List<Photo>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: Photo)
    
    @Delete
    suspend fun deletePhoto(photo: Photo)
    
    @Query("DELETE FROM photos WHERE assetId = :assetId")
    suspend fun deletePhotosByAssetId(assetId: String)
}

