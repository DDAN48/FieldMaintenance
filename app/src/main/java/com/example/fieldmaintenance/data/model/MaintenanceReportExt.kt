package com.example.fieldmaintenance.data.model

fun MaintenanceReport.isGeneralInfoComplete(): Boolean {
    return eventName.isNotBlank() &&
        nodeName.isNotBlank() &&
        responsible.isNotBlank() &&
        contractor.isNotBlank() &&
        meterNumber.isNotBlank()
}


