package com.example.fieldmaintenance.data.repository

import com.example.fieldmaintenance.data.dao.AssetDao
import com.example.fieldmaintenance.data.dao.AmplifierAdjustmentDao
import com.example.fieldmaintenance.data.dao.MaintenanceReportDao
import com.example.fieldmaintenance.data.dao.PhotoDao
import com.example.fieldmaintenance.data.dao.PassiveItemDao
import com.example.fieldmaintenance.data.dao.ReportPhotoDao
import com.example.fieldmaintenance.data.dao.NodeAdjustmentDao
import com.example.fieldmaintenance.data.model.AmplifierAdjustment
import com.example.fieldmaintenance.data.model.Asset
import com.example.fieldmaintenance.data.model.MaintenanceReport
import com.example.fieldmaintenance.data.model.NodeAdjustment
import com.example.fieldmaintenance.data.model.Photo
import com.example.fieldmaintenance.data.model.PhotoType
import com.example.fieldmaintenance.data.model.PassiveItem
import com.example.fieldmaintenance.data.model.ReportPhoto
import com.example.fieldmaintenance.data.model.ReportPhotoType
import kotlinx.coroutines.flow.Flow
import java.io.File

class MaintenanceRepository(
    private val reportDao: MaintenanceReportDao,
    private val assetDao: AssetDao,
    private val photoDao: PhotoDao,
    private val amplifierAdjustmentDao: AmplifierAdjustmentDao,
    private val passiveItemDao: PassiveItemDao,
    private val reportPhotoDao: ReportPhotoDao,
    private val nodeAdjustmentDao: NodeAdjustmentDao
) {
    fun getAllReports(): Flow<List<MaintenanceReport>> = reportDao.getAllReports()

    fun getTrashReports(): Flow<List<MaintenanceReport>> = reportDao.getTrashReports()
    
    fun searchReports(query: String): Flow<List<MaintenanceReport>> = reportDao.searchReports(query)
    
    suspend fun getReportById(id: String): MaintenanceReport? = reportDao.getReportById(id)
    
    suspend fun insertReport(report: MaintenanceReport) = reportDao.insertReport(report)
    
    suspend fun updateReport(report: MaintenanceReport) = reportDao.updateReport(report)
    
    /**
     * Soft delete -> move to trash.
     */
    suspend fun deleteReport(report: MaintenanceReport) {
        reportDao.moveToTrash(report.id, System.currentTimeMillis())
    }

    suspend fun restoreReport(report: MaintenanceReport) {
        reportDao.restoreFromTrash(report.id)
    }

    /**
     * Hard delete -> remove assets/photos/files and delete DB records.
     */
    suspend fun deleteReportPermanently(report: MaintenanceReport) {
        // Delete photos + files for each asset, then assets, then report
        val assets = assetDao.listAssetsByReportId(report.id)
        assets.forEach { asset ->
            deleteAsset(asset)
        }
        assetDao.deleteAssetsByReportId(report.id)
        deleteReportPhotosByReportId(report.id)
        passiveItemDao.deleteByReportId(report.id)
        reportDao.deleteReport(report)
    }
    
    fun getAssetsByReportId(reportId: String): Flow<List<Asset>> = assetDao.getAssetsByReportId(reportId)

    suspend fun listAssetsByReportId(reportId: String): List<Asset> = assetDao.listAssetsByReportId(reportId)
    
    suspend fun getNodeByReportId(reportId: String): Asset? = assetDao.getNodeByReportId(reportId)

    suspend fun getAssetById(assetId: String): Asset? = assetDao.getAssetById(assetId)
    
    suspend fun insertAsset(asset: Asset) = assetDao.insertAsset(asset)
    
    suspend fun updateAsset(asset: Asset) = assetDao.updateAsset(asset)
    
    suspend fun deleteAsset(asset: Asset) {
        // Delete files on disk (best-effort) + DB records
        val photos = photoDao.listPhotosByAssetId(asset.id)
        photos.forEach { photo ->
            runCatching { File(photo.filePath).delete() }
        }
        photoDao.deletePhotosByAssetId(asset.id)
        amplifierAdjustmentDao.deleteByAssetId(asset.id)
        nodeAdjustmentDao.deleteByAssetId(asset.id)
        assetDao.deleteAsset(asset)
    }
    
    fun getPhotosByAssetId(assetId: String): Flow<List<Photo>> = photoDao.getPhotosByAssetId(assetId)

    suspend fun listPhotosByAssetId(assetId: String): List<Photo> = photoDao.listPhotosByAssetId(assetId)
    
    suspend fun getPhotosByAssetIdAndType(assetId: String, photoType: PhotoType): List<Photo> =
        photoDao.getPhotosByAssetIdAndType(assetId, photoType)
    
    suspend fun insertPhoto(photo: Photo) = photoDao.insertPhoto(photo)
    
    suspend fun deletePhoto(photo: Photo) = photoDao.deletePhoto(photo)

    // Amplifier adjustment (per asset)
    fun getAmplifierAdjustment(assetId: String): Flow<AmplifierAdjustment?> =
        amplifierAdjustmentDao.getByAssetId(assetId)

    suspend fun upsertAmplifierAdjustment(adjustment: AmplifierAdjustment) =
        amplifierAdjustmentDao.upsert(adjustment)

    // Node adjustment (per asset)
    fun getNodeAdjustment(assetId: String): Flow<NodeAdjustment?> =
        nodeAdjustmentDao.getByAssetId(assetId)

    suspend fun getNodeAdjustmentOne(assetId: String): NodeAdjustment? =
        nodeAdjustmentDao.getOneByAssetId(assetId)

    suspend fun upsertNodeAdjustment(adjustment: NodeAdjustment) =
        nodeAdjustmentDao.upsert(adjustment)

    // Passives (per report)
    fun getPassivesByReportId(reportId: String): Flow<List<PassiveItem>> =
        passiveItemDao.getByReportId(reportId)

    suspend fun listPassivesByReportId(reportId: String): List<PassiveItem> =
        passiveItemDao.listByReportId(reportId)

    suspend fun insertPassive(item: PassiveItem) = passiveItemDao.insert(item)

    suspend fun updatePassive(item: PassiveItem) = passiveItemDao.update(item)

    suspend fun deletePassiveById(id: String) = passiveItemDao.deleteById(id)

    suspend fun deletePassivesByReportId(reportId: String) = passiveItemDao.deleteByReportId(reportId)

    // Report photos (Monitoria y QR) – per report
    fun getReportPhotosByReportId(reportId: String): Flow<List<ReportPhoto>> =
        reportPhotoDao.getByReportId(reportId)

    suspend fun listReportPhotosByReportId(reportId: String): List<ReportPhoto> =
        reportPhotoDao.listByReportId(reportId)

    suspend fun upsertReportPhoto(photo: ReportPhoto) = reportPhotoDao.insert(photo)

    suspend fun deleteReportPhoto(photo: ReportPhoto) {
        runCatching { File(photo.filePath).delete() }
        reportPhotoDao.deleteById(photo.id)
    }

    suspend fun deleteReportPhotosByReportId(reportId: String) {
        // best-effort: delete files first
        val list = runCatching { reportPhotoDao.listByReportId(reportId) }.getOrDefault(emptyList())
        list.forEach { runCatching { File(it.filePath).delete() } }
        reportPhotoDao.deleteByReportId(reportId)
    }
}

