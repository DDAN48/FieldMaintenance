package com.example.fieldmaintenance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "passive_items")
data class PassiveItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val reportId: String,
    val address: String,
    val type: PassiveType,
    val observation: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)


