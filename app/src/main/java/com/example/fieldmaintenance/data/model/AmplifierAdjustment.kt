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


