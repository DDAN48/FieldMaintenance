package com.example.fieldmaintenance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "photos")
data class Photo(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val assetId: String,
    val photoType: PhotoType,
    val filePath: String,
    val fileName: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)
