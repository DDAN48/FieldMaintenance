package com.example.fieldmaintenance.util

import android.content.Context
import com.example.fieldmaintenance.data.model.Asset
import com.example.fieldmaintenance.data.model.AssetType
import com.example.fieldmaintenance.data.model.MaintenanceReport
import com.example.fieldmaintenance.data.model.NodeAdjustment
import com.example.fieldmaintenance.data.model.PhotoType
import com.example.fieldmaintenance.data.repository.MaintenanceRepository

suspend fun hasIncompleteAssets(
    context: Context,
    reportId: String,
    report: MaintenanceReport?,
    repository: MaintenanceRepository
): Boolean {
    val reportFolder = MaintenanceStorage.reportFolderName(report?.eventName, reportId)
    val assets = repository.listAssetsByReportId(reportId)
    return assets.any { asset ->
        isAssetIncomplete(context, reportFolder, reportId, asset, repository)
    }
}

private suspend fun isAssetIncomplete(
    context: Context,
    reportFolder: String,
    reportId: String,
    asset: Asset,
    repository: MaintenanceRepository
): Boolean {
    val photos = repository.listPhotosByAssetId(asset.id)
    val meterKey = asset.meterType?.trim()?.lowercase() ?: ""
    val isDsam = meterKey == "dsam"
    val moduleCount = photos.count { it.photoType == PhotoType.MODULE }
    val opticsCount = photos.count { it.photoType == PhotoType.OPTICS }
    val techNormalized = asset.technology?.trim()?.lowercase() ?: ""
    val techKey = techNormalized.replace("_", "").replace(" ", "")
    val isVccapHibrido = techKey == "vccap" || techKey == "vccaphibrido"
    val isRphyLike = techKey == "rphy" || techKey == "vccapcompleto"
    val moduleOk = if (asset.type == AssetType.NODE && isRphyLike) true else moduleCount == 2
    val opticsOk = if (asset.type == AssetType.NODE && (isRphyLike || isVccapHibrido)) true
    else asset.type != AssetType.NODE || (opticsCount in 1..2)

    val nodeAdjOk = if (asset.type != AssetType.NODE) true else {
        val adj = repository.getNodeAdjustmentOne(asset.id)
            ?: NodeAdjustment(assetId = asset.id, reportId = reportId)
        when {
            isRphyLike -> {
                adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed
            }
            isVccapHibrido -> {
                adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed &&
                    adj.spectrumConfirmed && adj.docsisConfirmed && asset.frequencyMHz != 0
            }
            techNormalized == "legacy" -> {
                val txOk = adj.tx1310Confirmed || adj.tx1550Confirmed
                val poOk = adj.poConfirmed
                val rxOk = !adj.rxPadSelection.isNullOrBlank()
                val measOk = adj.measurementConfirmed
                val specOk = adj.spectrumConfirmed
                txOk && poOk && rxOk && measOk && specOk && asset.frequencyMHz != 0
            }
            else -> adj.nonLegacyConfirmed
        }
    }

    val ampAdjOk = if (asset.type != AssetType.AMPLIFIER) true else {
        val adj = repository.getAmplifierAdjustmentOne(asset.id)
        adj != null &&
            adj.inputCh50Dbmv != null &&
            adj.inputCh116Dbmv != null &&
            (adj.inputHighFreqMHz == 750 || adj.inputHighFreqMHz == 870 || adj.inputHighFreqMHz == 1000) &&
            (adj.inputLowFreqMHz == 61 || adj.inputLowFreqMHz == 379) &&
            (adj.inputPlanLowFreqMHz == 61 || adj.inputPlanLowFreqMHz == 379) &&
            (adj.inputPlanHighFreqMHz == 750 || adj.inputPlanHighFreqMHz == 870 || adj.inputPlanHighFreqMHz == 1000) &&
            adj.planLowDbmv != null &&
            adj.planHighDbmv != null &&
            adj.outCh50Dbmv != null &&
            adj.outCh70Dbmv != null &&
            adj.outCh110Dbmv != null &&
            adj.outCh116Dbmv != null &&
            adj.outCh136Dbmv != null
    }

    val measurementsOk = if (isDsam) {
        val isNode = asset.type == AssetType.NODE
        // RX measurement photos only exist for NODE assets (and not for VCCAP_Hibrido nodes).
        val hasRxMeasurements = isNode && !(techKey == "vccap" || techKey == "vccaphibrido")
        // Module measurement photos exist for NODE assets (unless VCCAP_Completo hides the whole module),
        // and for AMPLIFIER assets.
        val hasModuleMeasurements = if (isNode) techKey != "vccapcompleto" else true

        val rxChannel = photos.count { it.photoType == PhotoType.MEASUREMENT_RX_CHANNEL_CHECK }
        val moduleChannel = photos.count { it.photoType == PhotoType.MEASUREMENT_MODULE_CHANNEL_CHECK }
        val moduleDocsis = photos.count { it.photoType == PhotoType.MEASUREMENT_MODULE_DOCSIS_CHECK }

        val rxOk = !hasRxMeasurements || rxChannel >= 1
        val moduleOkDsam = !hasModuleMeasurements || (moduleChannel >= 4 && moduleDocsis >= 4)
        rxOk && moduleOkDsam
    } else {
        val measurementCount = MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
            .listFiles()
            ?.count { it.isFile } ?: 0
        measurementCount > 0
    }

    return !(moduleOk && opticsOk && nodeAdjOk && ampAdjOk && measurementsOk)
}
