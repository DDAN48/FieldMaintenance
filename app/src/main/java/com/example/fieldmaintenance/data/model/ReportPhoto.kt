package com.example.fieldmaintenance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "report_photos")
data class ReportPhoto(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val reportId: String,
    val type: ReportPhotoType,
    val filePath: String,
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)


