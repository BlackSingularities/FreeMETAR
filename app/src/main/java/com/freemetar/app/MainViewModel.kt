package com.freemetar.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freemetar.app.data.Airport
import com.freemetar.app.data.AirportDb
import com.freemetar.app.data.Frequency
import com.freemetar.app.data.Metar
import com.freemetar.app.data.Navaid
import com.freemetar.app.data.Prefs
import com.freemetar.app.data.Runway
import com.freemetar.app.data.Settings
import com.freemetar.app.data.WeatherApi
import com.freemetar.app.data.WeatherBundle
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface DbState {
    data object Idle : DbState
    data class Loading(val message: String) : DbState
    data class Ready(val count: Int, val updated: Long?) : DbState
    data class Error(val message: String) : DbState
}

data class AirportDetail(
    val ident: String,
    val airport: Airport? = null,
    val runways: List<Runway> = emptyList(),
    val frequencies: List<Frequency> = emptyList(),
    val nearby: List<Pair<Airport, Double>> = emptyList(),
    val navaids: List<Pair<Navaid, Double>> = emptyList(),
    val weather: WeatherBundle? = null,
    val nearbyMetars: Map<String, Metar> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AirportDb(app)
    private val prefs = Prefs(app)

    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _db = MutableStateFlow<DbState>(DbState.Idle)
    val dbState = _db.asStateFlow()

    private val _favMetars = MutableStateFlow<Map<String, Metar>>(emptyMap())
    val favMetars = _favMetars.asStateFlow()
    private val _favLoading = MutableStateFlow(false)
    val favLoading = _favLoading.asStateFlow()

    private val _search = MutableStateFlow<List<Airport>>(emptyList())
    val search = _search.asStateFlow()

    private val _detail = MutableStateFlow<AirportDetail?>(null)
    val detail = _detail.asStateFlow()

    private var refreshJob: Job? = null

    init {
        loadDb(force = false)
        refreshFavorites()
        startAutoRefresh()
    }

    fun loadDb(force: Boolean) = viewModelScope.launch {
        try {
            _db.value = DbState.Loading("Przygotowanie bazy lotnisk…")
            db.ensureDownloaded(force) { _db.value = DbState.Loading(it) }
            _db.value = DbState.Loading("Indeksowanie…")
            db.load()
            _db.value = DbState.Ready(db.airportCount, db.lastUpdated())
            _detail.value?.let { open(it.ident) }
        } catch (e: Exception) {
            _db.value = DbState.Error(e.message ?: "Nie udało się pobrać bazy lotnisk")
        }
    }

    fun airportName(ident: String): String? = db.airport(ident)?.name

    fun refreshFavorites() = viewModelScope.launch {
        _favLoading.value = true
        try {
            val favs = settings.first().favorites
            _favMetars.value = WeatherApi.metars(favs).associateBy { it.station }
        } catch (_: Exception) {
        } finally {
            _favLoading.value = false
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L)
                if (settings.value.autoRefresh) {
                    refreshFavorites()
                    _detail.value?.let { refreshWeather(it.ident) }
                }
            }
        }
    }

    fun onQuery(q: String) {
        _search.value = if (db.isLoaded) db.search(q) else emptyList()
    }

    fun open(identRaw: String) {
        val ident = identRaw.uppercase().trim()
        val a = db.airport(ident)
        val key = a?.ident ?: ident
        _detail.value = AirportDetail(
            ident = key,
            airport = a,
            runways = db.runways(key),
            frequencies = db.frequencies(key),
            nearby = a?.let { db.nearby(it.lat, it.lon, exclude = key) }.orEmpty(),
            navaids = a?.let { db.navaidsNear(it.lat, it.lon) }.orEmpty(),
            weather = _detail.value?.takeIf { it.ident == key }?.weather,
        )
        viewModelScope.launch { prefs.addRecent(key) }
        refreshWeather(key)
    }

    fun refreshWeather(ident: String) = viewModelScope.launch {
        _detail.update { it?.copy(loading = true, error = null) }
        try {
            val hist = async { WeatherApi.metars(listOf(ident), hours = 12) }
            val taf = async { runCatching { WeatherApi.taf(ident) }.getOrNull() }
            val history = hist.await().sortedByDescending { it.obsTimeEpoch }
            val bundle = WeatherBundle(history.firstOrNull(), history, taf.await(), System.currentTimeMillis())
            _detail.update { if (it?.ident == ident) it.copy(weather = bundle, loading = false) else it }
            // Pogoda na lotniskach w pobliżu — jedna paczka
            val near = _detail.value?.nearby?.map { it.first.ident }.orEmpty()
            if (near.isNotEmpty()) {
                val m = runCatching { WeatherApi.metars(near) }.getOrDefault(emptyList()).associateBy { it.station }
                _detail.update { if (it?.ident == ident) it.copy(nearbyMetars = m) else it }
            }
        } catch (e: Exception) {
            _detail.update { if (it?.ident == ident) it.copy(loading = false, error = e.message ?: "Błąd sieci") else it }
        }
    }

    fun toggleFavorite(ident: String) = viewModelScope.launch {
        prefs.toggleFavorite(ident)
        refreshFavorites()
    }

    fun moveFavorite(ident: String, delta: Int) = viewModelScope.launch { prefs.moveFavorite(ident, delta) }
    fun clearRecent() = viewModelScope.launch { prefs.clearRecent() }
    fun setPressure(u: com.freemetar.app.data.PressureUnit) = viewModelScope.launch { prefs.setPressure(u) }
    fun setAlt(u: com.freemetar.app.data.AltUnit) = viewModelScope.launch { prefs.setAlt(u) }
    fun setRawFirst(v: Boolean) = viewModelScope.launch { prefs.setRawFirst(v) }
    fun setAutoRefresh(v: Boolean) = viewModelScope.launch { prefs.setAutoRefresh(v) }
}
