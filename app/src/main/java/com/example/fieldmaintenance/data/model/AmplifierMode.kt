package com.example.fieldmaintenance.data.model

enum class AmplifierMode {
    HGD,  // Dual
    HGDT, // Triple (UI label: HGBT)
    LE
}

val AmplifierMode.label: String
    get() = when (this) {
        AmplifierMode.HGD -> "HGD"
        // Corrección solicitada: en UI debe decir HGBT (no HGDT)
        AmplifierMode.HGDT -> "HGBT"
        AmplifierMode.LE -> "LE"
    }

