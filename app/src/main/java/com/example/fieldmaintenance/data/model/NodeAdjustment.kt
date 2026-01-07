package com.example.fieldmaintenance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Confirmaciones del módulo "Ajuste de Nodo" (solo para AssetType.NODE).
 *
 * Nota: persistimos un snapshot de los valores del Plan (para poder exportar a PDF
 * incluso sin conexión) + el estado de confirmaciones.
 */
@Entity(tableName = "node_adjustments")
data class NodeAdjustment(
    @PrimaryKey val assetId: String,
    val reportId: String,

    // Snapshot del Plan (opcional)
    val planNode: String? = null,
    val planContractor: String? = null,
    val planTechnology: String? = null,
    val planPoDirecta: String? = null,
    val planPoRetorno: String? = null,
    val planDistanciaSfp: String? = null,

    // Legacy: Directa
    val tx1310Confirmed: Boolean = false,
    val tx1550Confirmed: Boolean = false,
    val poConfirmed: Boolean = false,

    // Legacy: Retorno
    /**
     * Selección única:
     * - "TP_BLACK"
     * - "TP_NO_BLACK"
     */
    val rxPadSelection: String? = null,
    val measurementConfirmed: Boolean = false,
    val spectrumConfirmed: Boolean = false,

    // Non-legacy: confirmación simple
    val nonLegacyConfirmed: Boolean = false,

    // RPHY/VCCAP: SFP distance (20, 40, or 80 km)
    val sfpDistance: Int? = null,
    // RPHY/VCCAP: PO confirmations
    val poDirectaConfirmed: Boolean = false,
    val poRetornoConfirmed: Boolean = false,
    // VCCAP: DOCSIS confirmation
    val docsisConfirmed: Boolean = false,

    val updatedAt: Long = System.currentTimeMillis()
)


