package com.freemetar.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class PressureUnit { HPA, INHG }
enum class AltUnit { FT, M }

data class Settings(
    val favorites: List<String> = listOf("EPWA", "EPKK", "EPGD"),
    val recent: List<String> = emptyList(),
    val pressureUnit: PressureUnit = PressureUnit.HPA,
    val altUnit: AltUnit = AltUnit.FT,
    val showRawFirst: Boolean = false,
    val autoRefresh: Boolean = true,
)

class Prefs(private val context: Context) {
    private val FAV = stringPreferencesKey("favorites")
    private val RECENT = stringPreferencesKey("recent")
    private val PRESS = stringPreferencesKey("pressure")
    private val ALT = stringPreferencesKey("alt")
    private val RAW = booleanPreferencesKey("raw_first")
    private val AUTO = booleanPreferencesKey("auto_refresh")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            favorites = p[FAV]?.split(",")?.filter { it.isNotBlank() } ?: Settings().favorites,
            recent = p[RECENT]?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
            pressureUnit = p[PRESS]?.let { runCatching { PressureUnit.valueOf(it) }.getOrNull() } ?: PressureUnit.HPA,
            altUnit = p[ALT]?.let { runCatching { AltUnit.valueOf(it) }.getOrNull() } ?: AltUnit.FT,
            showRawFirst = p[RAW] ?: false,
            autoRefresh = p[AUTO] ?: true,
        )
    }

    suspend fun toggleFavorite(ident: String) = context.dataStore.edit { p ->
        val cur = p[FAV]?.split(",")?.filter { it.isNotBlank() } ?: Settings().favorites
        p[FAV] = (if (ident in cur) cur - ident else cur + ident).joinToString(",")
    }

    suspend fun moveFavorite(ident: String, delta: Int) = context.dataStore.edit { p ->
        val cur = (p[FAV]?.split(",")?.filter { it.isNotBlank() } ?: Settings().favorites).toMutableList()
        val i = cur.indexOf(ident)
        val j = i + delta
        if (i >= 0 && j in cur.indices) {
            cur.removeAt(i); cur.add(j, ident)
            p[FAV] = cur.joinToString(",")
        }
    }

    suspend fun addRecent(ident: String) = context.dataStore.edit { p ->
        val cur = p[RECENT]?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        p[RECENT] = (listOf(ident) + (cur - ident)).take(10).joinToString(",")
    }

    suspend fun clearRecent() = context.dataStore.edit { it.remove(RECENT) }
    suspend fun setPressure(u: PressureUnit) = context.dataStore.edit { it[PRESS] = u.name }
    suspend fun setAlt(u: AltUnit) = context.dataStore.edit { it[ALT] = u.name }
    suspend fun setRawFirst(v: Boolean) = context.dataStore.edit { it[RAW] = v }
    suspend fun setAutoRefresh(v: Boolean) = context.dataStore.edit { it[AUTO] = v }
}
