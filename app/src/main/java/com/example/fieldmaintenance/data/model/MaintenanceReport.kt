package com.example.fieldmaintenance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "maintenance_reports")
data class MaintenanceReport @JvmOverloads constructor(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val eventName: String = "",
    val nodeName: String = "",
    val responsible: String = "",
    val contractor: String = "",
    val meterNumber: String = "",
    val executionDate: Long = System.currentTimeMillis(),
    val status: ReportStatus = ReportStatus.DRAFT,
    val createdAt: Long = System.currentTimeMillis(),
    val ticketId: String = "",
    /**
     * Soft-delete timestamp. If not null -> report is in Trash.
     */
    val deletedAt: Long? = null
)

