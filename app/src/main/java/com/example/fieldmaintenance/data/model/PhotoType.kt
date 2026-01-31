package com.example.fieldmaintenance.data.model

enum class PhotoType {
    MODULE,
    OPTICS,
    SPECTRUM,
    MONITORING,
    // Legacy DSAM measurement photo types (kept for backward compatibility)
    MEASUREMENT_RX,
    MEASUREMENT_MODULE,

    // DSAM measurement checks (new workflow)
    MEASUREMENT_RX_CHANNEL_CHECK,
    MEASUREMENT_MODULE_CHANNEL_CHECK,
    MEASUREMENT_MODULE_DOCSIS_CHECK
}

