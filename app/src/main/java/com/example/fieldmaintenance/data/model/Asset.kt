package com.example.fieldmaintenance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "assets")
data class Asset(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val reportId: String,
    val type: AssetType,
    val frequencyMHz: Int,
    val amplifierMode: AmplifierMode? = null,
    val port: Port? = null,
    /**
     * Amplifier port index (01..99). Only applies to AMPLIFIER assets.
     */
    val portIndex: Int? = null,
    /**
     * Technology type for NODE assets:
     * - "Legacy"
     * - "RPHY"
     * - "VCCAP_Hibrido" (no RX measurements, only module)
     * - "VCCAP_Completo" (same behavior as RPHY)
     *
     * Can come from plan or be manually selected.
     */
    val technology: String? = null
)

