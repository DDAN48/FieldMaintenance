package com.example.fieldmaintenance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted amplifier adjustment values per Asset (only for AssetType.AMPLIFIER).
 *
 * We store only the user inputs; calculated values are derived on the fly.
 */
@Entity(tableName = "amplifier_adjustments")
data class AmplifierAdjustment(
    @PrimaryKey val assetId: String,

    // Input levels (measured at amp input)
    /**
     * New model (2026): two selectable points (among CH3/CH50/CH116/CH136/CH158) for the "Medido" recta.
     * The UI lets the user choose which 2 channels are editable.
     */
    val inMedidoP1FreqMHz: Int? = null,
    val inMedidoP1Dbmv: Double? = null,
    val inMedidoP2FreqMHz: Int? = null,
    val inMedidoP2Dbmv: Double? = null,

    /**
     * New model (2026): two selectable points (among CH3/CH50/CH116/CH136/CH158) for the "Plano" recta.
     * This column is only used to calculate the recta (and derived values).
     */
    val inPlanoP1FreqMHz: Int? = null,
    val inPlanoP1Dbmv: Double? = null,
    val inPlanoP2FreqMHz: Int? = null,
    val inPlanoP2Dbmv: Double? = null,

    // Legacy (kept for backward compatibility / migration)
    val inputCh50Dbmv: Double? = null,
    val inputCh116Dbmv: Double? = null,
    /**
     * Frequency of the "high" measured input point:
     * - 750 MHz (CH116) or 870 MHz (CH136)
     *
     * We keep the amplitude value in [inputCh116Dbmv] for backward compatibility.
     */
    val inputHighFreqMHz: Int? = null,

    // Plan output line points (frequencies can vary)
    val planLowFreqMHz: Int? = null,   // 54 or 102
    val planLowDbmv: Double? = null,
    val planHighFreqMHz: Int? = null,  // 750 or 870 or 1000
    val planHighDbmv: Double? = null,

    // Output measured (at amp output) for comparison + AGC PAD
    val outCh50Dbmv: Double? = null,
    val outCh70Dbmv: Double? = null,
    val outCh110Dbmv: Double? = null,
    val outCh116Dbmv: Double? = null,
    val outCh136Dbmv: Double? = null,

    val updatedAt: Long = System.currentTimeMillis()
)


