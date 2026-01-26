package com.example.fieldmaintenance.util

import com.example.fieldmaintenance.data.model.AmplifierMode
import com.example.fieldmaintenance.data.model.AmplifierAdjustment
import com.example.fieldmaintenance.data.model.Frequency
import kotlin.math.abs
import kotlin.math.min

object CiscoHfcAmpCalculator {
    data class Recta(val f1: Double, val a1: Double, val f2: Double, val a2: Double) {
        fun valueAt(f: Double): Double {
            require(f2 != f1) { "No se puede calcular recta: f2 == f1" }
            return a1 + (a2 - a1) * (f - f1) / (f2 - f1)
        }
    }

    fun buildEntradaRecta(adj: AmplifierAdjustment): Recta? {
        val a1 = adj.inputCh50Dbmv ?: return null
        val a2 = adj.inputCh116Dbmv ?: return null
        val f1 = (adj.inputLowFreqMHz ?: 379).toDouble()
        val f2 = (adj.inputHighFreqMHz ?: 870).toDouble()
        return Recta(f1, a1, f2, a2)
    }

    fun buildSalidaRecta(adj: AmplifierAdjustment): Recta? {
        val f1 = adj.planLowFreqMHz?.toDouble() ?: return null
        val a1 = adj.planLowDbmv ?: return null
        val f2 = adj.planHighFreqMHz?.toDouble() ?: return null
        val a2 = adj.planHighDbmv ?: return null
        return Recta(f1, a1, f2, a2)
    }

    /**
     * Niveles calculados (Entrada) – frecuencias fijas.
     */
    fun nivelesEntradaCalculados(adj: AmplifierAdjustment): Map<String, Double>? {
        val r = buildEntradaRecta(adj) ?: return null
        return linkedMapOf(
            "L 54" to r.valueAt(54.0),
            "L102" to r.valueAt(102.0),
            "CH3" to r.valueAt(61.0),
            "CH50" to r.valueAt(379.0),
            "CH70" to r.valueAt(495.0),
            "CH116" to r.valueAt(750.0),
            "CH136" to r.valueAt(870.0),
            "CH158" to r.valueAt(1000.0),
        )
    }

    fun inputChannelKeyForFreq(freqMHz: Int?): String? {
        return when (freqMHz) {
            61 -> "CH3"
            379 -> "CH50"
            750 -> "CH116"
            870 -> "CH136"
            1000 -> "CH158"
            else -> null
        }
    }

    fun inputChannelLabelForFreq(freqMHz: Int?): String {
        return when (freqMHz) {
            61 -> "CH3"
            379 -> "CH50"
            750 -> "CH116"
            870 -> "CH136"
            1000 -> "CH158"
            else -> "—"
        }
    }

    fun entradaCalcValueForFreq(adj: AmplifierAdjustment, freqMHz: Int?): Double? {
        val key = inputChannelKeyForFreq(freqMHz) ?: return null
        val entrada = nivelesEntradaCalculados(adj) ?: return null
        return entrada[key]
    }

    /**
     * Niveles calculados (Salida) – con regla especial:
     * CH110 = Recta(711) - 5 dB.
     */
    fun nivelesSalidaCalculados(adj: AmplifierAdjustment): Map<String, Double>? {
        val r = buildSalidaRecta(adj) ?: return null
        return linkedMapOf(
            "L54" to r.valueAt(54.0),
            "L102" to r.valueAt(102.0),
            "CH3" to r.valueAt(61.0),
            "CH50" to r.valueAt(379.0),
            "CH70" to r.valueAt(495.0),
            "CH110" to (r.valueAt(711.0) - 5.0),
            "CH116" to r.valueAt(750.0),
            "CH136" to r.valueAt(870.0),
            "CH158" to r.valueAt(1000.0),
        )
    }

    /**
     * FWD IN EQ (TILT) = in1000 - lowRef
     * lowRef = in54 si BW==42, sino in102.
     */
    fun fwdInEqTilt(adj: AmplifierAdjustment, bw: Frequency?): Double? {
        val entrada = nivelesEntradaCalculados(adj) ?: return null
        val in1000 = entrada["CH158"] ?: return null
        val lowRef = if (bw == Frequency.MHz_42) entrada["L 54"] else entrada["L102"]
        return if (lowRef != null) in1000 - lowRef else null
    }

    /**
     * FWD IN PAD (según tu código ejemplo):
     * PAD = min(lowRef, in1000) - target
     * target = 20 si LE, sino 16.
     */
    fun fwdInPad(adj: AmplifierAdjustment, bw: Frequency?, tipo: AmplifierMode?): Double? {
        val entrada = nivelesEntradaCalculados(adj) ?: return null
        val in1000 = entrada["CH158"] ?: return null
        val lowRef = if (bw == Frequency.MHz_42) entrada["L 54"] else entrada["L102"]
        if (lowRef == null) return null
        val target = if (tipo == AmplifierMode.LE) 20.0 else 16.0
        return min(lowRef, in1000) - target
    }

    /**
     * AGC PAD:
     * - si BW==42 usa CH70 medido, si BW==85 usa CH110 medido
     * - offset 29 si LE, 34 si no (HGD/HGDT)
     */
    fun agcPad(adj: AmplifierAdjustment, bw: Frequency?, tipo: AmplifierMode?): Double? {
        val ref = if (bw == Frequency.MHz_42) adj.outCh70Dbmv else adj.outCh110Dbmv
        val offset = if (tipo == AmplifierMode.LE) 29.0 else 34.0
        return ref?.minus(offset)
    }

    fun recomendacionEqInvEq(tilt: Double?): String? {
        tilt ?: return null
        return when {
            tilt > 0 -> "invEQ ${format1(tilt)} dB"
            tilt < 0 -> "EQ ${format1(abs(tilt))} dB"
            else -> "Sin EQ"
        }
    }

    fun format1(v: Double): String = String.format("%.1f", v)
}

