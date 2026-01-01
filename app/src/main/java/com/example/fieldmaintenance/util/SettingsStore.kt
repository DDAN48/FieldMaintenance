package com.example.fieldmaintenance.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val SHEET_URL = stringPreferencesKey("sheet_url")
        val DEFAULT_RESPONSIBLE = stringPreferencesKey("default_responsible")
        val DEFAULT_CONTRACTOR = stringPreferencesKey("default_contractor")
        val DEFAULT_METER_NUMBER = stringPreferencesKey("default_meter_number")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            sheetUrl = prefs[Keys.SHEET_URL] ?: "",
            defaultResponsible = prefs[Keys.DEFAULT_RESPONSIBLE] ?: "",
            defaultContractor = prefs[Keys.DEFAULT_CONTRACTOR] ?: "",
            defaultMeterNumber = prefs[Keys.DEFAULT_METER_NUMBER] ?: ""
        )
    }

    suspend fun setSheetUrl(url: String) {
        context.settingsDataStore.edit { it[Keys.SHEET_URL] = url.trim() }
    }

    suspend fun setDefaultResponsible(value: String) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_RESPONSIBLE] = value.trim() }
    }

    suspend fun setDefaultContractor(value: String) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_CONTRACTOR] = value.trim() }
    }

    suspend fun setDefaultMeterNumber(value: String) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_METER_NUMBER] = value.trim() }
    }
}


