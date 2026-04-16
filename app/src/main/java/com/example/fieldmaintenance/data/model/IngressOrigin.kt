package com.example.fieldmaintenance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "ingress_origins")
data class IngressOrigin(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val reportId: String,
    val address: String,
    val clientId: String,
    val buildingId: String,
    val ticketGenerated: String,
    val observation: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

