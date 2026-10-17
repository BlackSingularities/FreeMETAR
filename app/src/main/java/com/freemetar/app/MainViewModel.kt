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
import com.freemetar.app.data.UpdateInfo
import com.freemetar.app.data.Updater
import java.io.File
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.freemetar.app.data.Locator
import com.freemetar.app.data.LatLon
import com.freemetar.app.data.OpenMeteo
import com.freemetar.app.data.Pirep
import com.freemetar.app.data.Sigmet
import com.freemetar.app.data.WindsAloft
import kotlinx.coroutines.coroutineScope
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
    /** SIGMET obejmujące lotnisko lub do 50 NM od niego, z odległością (0 = nad lotniskiem). */
    val sigmets: List<Pair<Sigmet, Double>> = emptyList(),
    val pireps: List<Pirep> = emptyList(),
    val aloft: WindsAloft? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

sealed interface NearMe {
    data object Idle : NearMe
    data object Locating : NearMe
    data class Found(val airports: List<Pair<Airport, Double>>, val metars: Map<String, Metar>) : NearMe
    data class Failed(val message: String) : NearMe
}

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

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update = _update.asStateFlow()

    /** Dialog o nowej wersji pokazujemy raz na uruchomienie. */
    private val _updatePromptDismissed = MutableStateFlow(false)
    val updatePromptDismissed = _updatePromptDismissed.asStateFlow()

    private val _nearMe = MutableStateFlow<NearMe>(NearMe.Idle)
    val nearMe = _nearMe.asStateFlow()

    init {
        loadDb(force = false)
        startAutoRefresh()
        checkForUpdate(silent = true)
        // Lista ulubionych zmieniona (dodanie, usunięcie) → pobierz brakujące METAR-y
        viewModelScope.launch {
            prefs.settings.map { it.favorites.toSet() }.distinctUntilChanged().collect { refreshFavorites() }
        }
    }

    /** Lotniska najbliżej użytkownika (wymaga zgody na przybliżoną lokalizację). */
    fun findNearMe() = viewModelScope.launch {
        _nearMe.value = NearMe.Locating
        val loc = runCatching { Locator.locate(getApplication()) }.getOrNull()
        if (loc == null) {
            _nearMe.value = NearMe.Failed("Nie udało się ustalić pozycji. Włącz lokalizację i spróbuj ponownie.")
            return@launch
        }
        if (!db.isLoaded) {
            _nearMe.value = NearMe.Failed("Baza lotnisk jeszcze się ładuje.")
            return@launch
        }
        val list = db.nearby(loc.latitude, loc.longitude, radiusNm = 120.0, limit = 8)
        _nearMe.value = NearMe.Found(list, emptyMap())
        val metars = metarsFor(list.map { it.first.ident })
        _nearMe.value = NearMe.Found(list, metars)
    }

    fun checkForUpdate(silent: Boolean = false) = viewModelScope.launch {
        if (_update.value is UpdateState.Downloading) return@launch
        _update.value = UpdateState.Checking
        _update.value = try {
            Updater.check()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
        } catch (e: Exception) {
            // Przy starcie brak sieci nie jest powodem do komunikatu
            if (silent) UpdateState.Idle else UpdateState.Error(e.message ?: "Nie udało się sprawdzić aktualizacji")
        }
    }

    fun downloadUpdate(info: UpdateInfo) = viewModelScope.launch {
        _update.value = UpdateState.Downloading(info, 0f)
        _update.value = try {
            val file = Updater.download(getApplication(), info) { p ->
                _update.value = UpdateState.Downloading(info, p)
            }
            UpdateState.Ready(info, file)
        } catch (e: Exception) {
            UpdateState.Error("Pobieranie nie powiodło się: ${e.message}")
        }
    }

    fun dismissUpdatePrompt() { _updatePromptDismissed.value = true }

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
    fun airport(ident: String): Airport? = db.airport(ident)

    suspend fun metarsFor(ids: List<String>): Map<String, Metar> =
        runCatching { WeatherApi.metars(ids) }.getOrDefault(emptyList()).associateBy { it.station }

    fun refreshFavorites() = viewModelScope.launch {
        _favLoading.value = true
        try {
            val favs = prefs.settings.first().favorites
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
            loadHazardsAndAloft(ident)
        } catch (e: Exception) {
            _detail.update { if (it?.ident == ident) it.copy(loading = false, error = e.message ?: "Błąd sieci") else it }
        }
    }

    /** Zagrożenia (SIGMET, PIREP) i wiatr na wysokościach — dodatki do briefingu, ich brak nie jest błędem. */
    private suspend fun loadHazardsAndAloft(ident: String) = coroutineScope {
        val a = _detail.value?.airport
        val p = a?.let { LatLon(it.lat, it.lon) }
            ?: _detail.value?.weather?.metar?.let { m -> m.lat?.let { la -> m.lon?.let { lo -> LatLon(la, lo) } } }
            ?: return@coroutineScope
        val sig = async { runCatching { WeatherApi.sigmets() }.getOrDefault(emptyList()) }
        val pir = async { runCatching { WeatherApi.pireps(ident) }.getOrDefault(emptyList()) }
        val alo = async { runCatching { OpenMeteo.fetch(p.lat, p.lon) }.getOrNull() }
        val near = sig.await().map { it to it.distanceNm(p) }.filter { it.second <= 50 }.sortedBy { it.second }
        val reports = pir.await().sortedByDescending { it.obsTime }.take(15)
        val aloft = alo.await()
        _detail.update { if (it?.ident == ident) it.copy(sigmets = near, pireps = reports, aloft = aloft) else it }
    }

    // ---------- mapa ----------

    private val _mapMetars = MutableStateFlow<List<Metar>>(emptyList())
    val mapMetars = _mapMetars.asStateFlow()
    private val _mapSigmets = MutableStateFlow<List<Sigmet>>(emptyList())
    val mapSigmets = _mapSigmets.asStateFlow()
    private var mapJob: Job? = null

    /** Pogoda dla widocznego obszaru mapy; wywoływane po zatrzymaniu kamery. Pomija zbyt duże obszary (limit 400 stacji). */
    fun loadMapArea(south: Double, west: Double, north: Double, east: Double) {
        mapJob?.cancel()
        mapJob = viewModelScope.launch {
            delay(350) // odbicie: szybkie przesuwanie mapy nie robi serii zapytań
            if (north - south > 25 || east - west > 40) return@launch
            runCatching { WeatherApi.metarsInBox(south, west, north, east) }.onSuccess { _mapMetars.value = it }
            if (_mapSigmets.value.isEmpty()) runCatching { WeatherApi.sigmets() }.onSuccess { _mapSigmets.value = it }
        }
    }

    /** Punkt startowy mapy: ostatnio otwarte lotnisko albo pierwsze ulubione. */
    fun mapHome(): LatLon? {
        val id = _detail.value?.ident ?: settings.value.favorites.firstOrNull() ?: return null
        return db.airport(id)?.let { LatLon(it.lat, it.lon) }
    }

    fun toggleFavorite(ident: String) = viewModelScope.launch {
        prefs.toggleFavorite(ident)
        refreshFavorites()
    }

    fun moveFavorite(ident: String, delta: Int) = viewModelScope.launch { prefs.moveFavorite(ident, delta) }
    fun clearRecent() = viewModelScope.launch { prefs.clearRecent() }
    fun setPressure(u: com.freemetar.app.data.PressureUnit) = viewModelScope.launch { prefs.setPressure(u) }
    fun setAlt(u: com.freemetar.app.data.AltUnit) = viewModelScope.launch { prefs.setAlt(u) }
    fun setTheme(m: com.freemetar.app.data.ThemeMode) = viewModelScope.launch { prefs.setTheme(m) }
    fun setAutoRefresh(v: Boolean) = viewModelScope.launch { prefs.setAutoRefresh(v) }
}
