package com.example.fieldmaintenance.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pendingMeasurementDataStore by preferencesDataStore(name = "pending_measurement")

data class PendingMeasurementState(
    val reportId: String? = null,
    val assetId: String? = null,
    val assetType: String? = null
)

class PendingMeasurementStore(private val context: Context) {
    private object Keys {
        val REPORT_ID = stringPreferencesKey("pending_report_id")
        val ASSET_ID = stringPreferencesKey("pending_asset_id")
        val ASSET_TYPE = stringPreferencesKey("pending_asset_type")
    }

    val pending: Flow<PendingMeasurementState> = context.pendingMeasurementDataStore.data.map { prefs ->
        PendingMeasurementState(
            reportId = prefs[Keys.REPORT_ID],
            assetId = prefs[Keys.ASSET_ID],
            assetType = prefs[Keys.ASSET_TYPE]
        )
    }

    suspend fun save(reportId: String, assetId: String, assetType: String) {
        context.pendingMeasurementDataStore.edit { prefs ->
            prefs[Keys.REPORT_ID] = reportId
            prefs[Keys.ASSET_ID] = assetId
            prefs[Keys.ASSET_TYPE] = assetType
        }
    }

    suspend fun clear() {
        context.pendingMeasurementDataStore.edit { prefs ->
            prefs.remove(Keys.REPORT_ID)
            prefs.remove(Keys.ASSET_ID)
            prefs.remove(Keys.ASSET_TYPE)
        }
    }
}
