package com.example.fieldmaintenance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fieldmaintenance.data.model.MaintenanceReport
import com.example.fieldmaintenance.data.repository.MaintenanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MaintenanceViewModel(private val repository: MaintenanceRepository) : ViewModel() {
    private val _reports = MutableStateFlow<List<MaintenanceReport>>(emptyList())
    val reports: StateFlow<List<MaintenanceReport>> = _reports.asStateFlow()

    private val _trashReports = MutableStateFlow<List<MaintenanceReport>>(emptyList())
    val trashReports: StateFlow<List<MaintenanceReport>> = _trashReports.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    init {
        loadReports()
    }
    
    fun loadReports() {
        viewModelScope.launch {
            if (_searchQuery.value.isEmpty()) {
                repository.getAllReports().collect { _reports.value = it }
            } else {
                repository.searchReports(_searchQuery.value).collect { _reports.value = it }
            }
        }
    }
    
    fun search(query: String) {
        _searchQuery.value = query
        loadReports()
    }
    
    fun createNewReport(): String {
        val report = MaintenanceReport()
        viewModelScope.launch {
            repository.insertReport(report)
        }
        return report.id
    }
    
    fun deleteReport(report: MaintenanceReport) {
        viewModelScope.launch {
            repository.deleteReport(report)
        }
    }

    fun loadTrash() {
        viewModelScope.launch {
            repository.getTrashReports().collect { _trashReports.value = it }
        }
    }

    fun restoreReport(report: MaintenanceReport) {
        viewModelScope.launch {
            repository.restoreReport(report)
        }
    }

    fun deleteReportPermanently(report: MaintenanceReport) {
        viewModelScope.launch {
            repository.deleteReportPermanently(report)
        }
    }
}

class MaintenanceViewModelFactory(private val repository: MaintenanceRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MaintenanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MaintenanceViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

