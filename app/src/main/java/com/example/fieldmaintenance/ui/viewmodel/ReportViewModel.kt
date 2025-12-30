package com.example.fieldmaintenance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fieldmaintenance.data.model.Asset
import com.example.fieldmaintenance.data.model.MaintenanceReport
import com.example.fieldmaintenance.data.repository.MaintenanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReportViewModel(
    private val repository: MaintenanceRepository,
    private val reportId: String
) : ViewModel() {
    private val _report = MutableStateFlow<MaintenanceReport?>(null)
    val report: StateFlow<MaintenanceReport?> = _report.asStateFlow()
    
    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets.asStateFlow()
    
    init {
        loadReport()
        loadAssets()
    }
    
    private fun loadReport() {
        viewModelScope.launch {
            _report.value = repository.getReportById(reportId)
        }
    }
    
    private fun loadAssets() {
        viewModelScope.launch {
            repository.getAssetsByReportId(reportId).collect { _assets.value = it }
        }
    }
    
    fun updateReport(report: MaintenanceReport) {
        viewModelScope.launch {
            repository.updateReport(report)
            _report.value = report
        }
    }
    
    fun saveGeneralInfo(
        eventName: String,
        nodeName: String,
        responsible: String,
        contractor: String,
        meterNumber: String
    ) {
        viewModelScope.launch {
            val currentReport = _report.value ?: MaintenanceReport(id = reportId)
            val updatedReport = currentReport.copy(
                eventName = eventName,
                nodeName = nodeName,
                responsible = responsible,
                contractor = contractor,
                meterNumber = meterNumber,
                executionDate = System.currentTimeMillis(),
                status = com.example.fieldmaintenance.data.model.ReportStatus.SAVED
            )
            repository.updateReport(updatedReport)
            _report.value = updatedReport
        }
    }
    
    suspend fun hasNode(): Boolean {
        return repository.getNodeByReportId(reportId) != null
    }
    
    fun addAsset(asset: Asset) {
        viewModelScope.launch {
            repository.insertAsset(asset)
        }
    }
    
    fun updateAsset(asset: Asset) {
        viewModelScope.launch {
            repository.updateAsset(asset)
        }
    }
    
    fun deleteAsset(asset: Asset) {
        viewModelScope.launch {
            repository.deleteAsset(asset)
        }
    }
}

class ReportViewModelFactory(
    private val repository: MaintenanceRepository,
    private val reportId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReportViewModel(repository, reportId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

