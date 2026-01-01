package com.example.fieldmaintenance.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.planDataStore by preferencesDataStore(name = "plan_cache")

class PlanCacheStore(private val context: Context) {
    private val gson: Gson = GsonBuilder().create()

    private object Keys {
        val PLAN_JSON = stringPreferencesKey("plan_json")
    }

    val cache: Flow<PlanCache> = context.planDataStore.data.map { prefs ->
        val raw = prefs[Keys.PLAN_JSON].orEmpty()
        if (raw.isBlank()) PlanCache()
        else runCatching { gson.fromJson(raw, PlanCache::class.java) }.getOrDefault(PlanCache())
    }

    suspend fun save(cache: PlanCache) {
        val json = gson.toJson(cache)
        context.planDataStore.edit { it[Keys.PLAN_JSON] = json }
    }
}


