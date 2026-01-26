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
    val moduleCount = photos.count { it.photoType == PhotoType.MODULE }
    val opticsCount = photos.count { it.photoType == PhotoType.OPTICS }
    val techNormalized = asset.technology?.trim()?.lowercase() ?: ""
    val moduleOk = if (asset.type == AssetType.NODE && techNormalized == "rphy") true else moduleCount == 2
    val opticsOk = if (asset.type == AssetType.NODE && (techNormalized == "rphy" || techNormalized == "vccap")) true
    else asset.type != AssetType.NODE || (opticsCount in 1..2)

    val nodeAdjOk = if (asset.type != AssetType.NODE) true else {
        val adj = repository.getNodeAdjustmentOne(asset.id)
            ?: NodeAdjustment(assetId = asset.id, reportId = reportId)
        when {
            techNormalized == "rphy" -> {
                adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed
            }
            techNormalized == "vccap" -> {
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

    val measurementCount = MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
        .listFiles()
        ?.count { it.isFile } ?: 0
    val measurementsOk = measurementCount > 0

    return !(moduleOk && opticsOk && nodeAdjOk && ampAdjOk && measurementsOk)
}
