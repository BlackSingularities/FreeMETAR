package com.freemetar.app

import com.freemetar.app.domain.loc

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
import com.freemetar.app.data.ProcSelection
import com.freemetar.app.data.RoutePoint
import com.freemetar.app.domain.RouteEdit
import com.freemetar.app.data.nav.AirportNav
import com.freemetar.app.data.nav.NavDataStore
import com.freemetar.app.data.nav.NavRepository
import com.freemetar.app.data.link.FlightLog
import com.freemetar.app.data.link.FlightLogStore
import com.freemetar.app.data.link.FlightRecorder
import com.freemetar.app.data.link.LinkClient
import com.freemetar.app.data.link.LinkStatus
import com.freemetar.app.data.link.TrackPoint
import com.freemetar.app.data.sync.DriveSync
import com.freemetar.app.data.sync.SyncViewModel
import android.net.Uri
import com.freemetar.app.data.AloftCell
import com.freemetar.app.data.RouteImport
import com.freemetar.app.data.RouteStore
import com.freemetar.app.data.SavedRoute
import com.freemetar.app.data.SimBrief
import com.freemetar.app.data.Taf
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
    /** Dane z bazy MSFS (pasy z ILS, częstotliwości, procedury) — z pamięci telefonu lub z Link. */
    val nav: AirportNav? = null,
    val navLoading: Boolean = false,
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
    /** [aircraft] = lista wokół samolotu z symulatora (zamiast lokalizacji telefonu). */
    data class Found(val airports: List<Pair<Airport, Double>>, val metars: Map<String, Metar>, val aircraft: Boolean = false, val loaded: Boolean = true) : NearMe
    data class Failed(val message: String) : NearMe
}

/** Pogoda dla aktywnej trasy: lotniska końcowe i zapasowe, SIGMET przecinające trasę, wiatr na przelotowym FL. */
data class RouteBriefing(
    val routeId: String,
    val metars: Map<String, Metar> = emptyMap(),
    val tafs: Map<String, Taf> = emptyMap(),
    val sigmets: List<Sigmet> = emptyList(),
    /** Wiatr na poziomie przelotowym w punktach kontrolnych: start, środek trasy, cel. */
    val cruiseWinds: List<Pair<String, AloftCell?>> = emptyList(),
    val loading: Boolean = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AirportDb(app)
    private val prefs = Prefs(app)

    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    /** Ustawienia wczytane z dysku — do tego czasu [settings] ma wartości domyślne (np. welcomeShown = false). */
    val settingsLoaded: StateFlow<Boolean> = prefs.settings.map { true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Google Drive sync
    private val driveSync = DriveSync(app)
    private val flightLogStore = FlightLogStore(app)
    val syncViewModel = SyncViewModel(driveSync, flightLogStore)

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

    // Callouts
    private val callouts = com.freemetar.app.data.Callouts(app)
    private val _calloutsEnabled = MutableStateFlow(true)
    val calloutsEnabled = _calloutsEnabled.asStateFlow()
    private val _enabledCallouts = MutableStateFlow(com.freemetar.app.data.CalloutsList.ALL.map { it.first }.toSet())
    val enabledCallouts = _enabledCallouts.asStateFlow()
    private val _calloutsLanguage = MutableStateFlow("en")
    val calloutsLanguage = _calloutsLanguage.asStateFlow()
    private val _calloutsSpeechRate = MutableStateFlow(1.0f)
    val calloutsSpeechRate = _calloutsSpeechRate.asStateFlow()

    // Checklists
    private val checklistsManager = com.freemetar.app.data.ChecklistsManager(app)
    private val _currentChecklists = MutableStateFlow<com.freemetar.app.data.AircraftChecklists?>(null)
    val currentChecklists = _currentChecklists.asStateFlow()

    /** Dialog o nowej wersji pokazujemy raz na uruchomienie. */
    private val _updatePromptDismissed = MutableStateFlow(false)
    val updatePromptDismissed = _updatePromptDismissed.asStateFlow()

    // ---------- trasy i plan lotu ----------

    private val routeStore = RouteStore(app)
    private val _routes = MutableStateFlow<List<SavedRoute>>(emptyList())
    val routes = _routes.asStateFlow()
    private val _routeBriefing = MutableStateFlow<RouteBriefing?>(null)
    val routeBriefing = _routeBriefing.asStateFlow()
    /** Krótki komunikat dla użytkownika po imporcie/eksporcie (pokazywany jako pasek u dołu). */
    private val _flightMessage = MutableStateFlow<String?>(null)
    val flightMessage = _flightMessage.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    fun activeRoute(): SavedRoute? = settings.value.activeRouteId?.let { id -> _routes.value.firstOrNull { it.id == id } }

    fun consumeFlightMessage() { _flightMessage.value = null }

    /** Prośba o wykadrowanie aktywnej trasy na mapie (licznik, żeby każde dotknięcie działało). */
    private val _mapRouteFocus = MutableStateFlow(0)
    val mapRouteFocus = _mapRouteFocus.asStateFlow()
    fun focusRouteOnMap() { _mapRouteFocus.value += 1 }

    private fun addRoutes(new: List<SavedRoute>, activate: Boolean) = viewModelScope.launch {
        // Ten sam plan z SimBrief zastępuje poprzednią wersję zamiast się dublować
        val keep = _routes.value.filterNot { old -> new.any { it.id == old.id } }
        val all = new + keep
        _routes.value = all
        routeStore.save(all)
        if (activate) new.firstOrNull()?.let { activateRoute(it.id) }
    }

    fun importSimbrief(user: String) = viewModelScope.launch {
        if (user.isBlank()) { _flightMessage.value = loc("Wpisz nazwę użytkownika lub Pilot ID z SimBrief.", "Enter your SimBrief username or Pilot ID."); return@launch }
        _importing.value = true
        prefs.setSimbriefUser(user)
        try {
            val r = SimBrief.fetch(user)
            addRoutes(listOf(r), activate = true).join()
            _flightMessage.value = loc("Pobrano plan ${r.name} z SimBrief.", "Downloaded plan ${r.name} from SimBrief.")
        } catch (e: Exception) {
            _flightMessage.value = loc("Nie pobrano planu z SimBrief: ${e.message}. Sprawdź nazwę użytkownika i czy masz wygenerowany plan.", "Could not download the SimBrief plan: ${e.message}. Check the username and that you have generated a plan.")
        } finally {
            _importing.value = false
        }
    }

    // ---------- planowanie trasy na mapie ----------

    private val _planning = MutableStateFlow(false)
    /** Tryb planowania na mapie: dotknięcie dodaje punkt do szkicu trasy. */
    val planning = _planning.asStateFlow()
    private val _draft = MutableStateFlow<List<RoutePoint>>(emptyList())
    /** Szkic trasy budowany na mapie (pierwszy i ostatni punkt to zwykle lotniska). */
    val draft = _draft.asStateFlow()

    fun startMapPlanning() { _planning.value = true }
    fun stopMapPlanning() { _planning.value = false }
    fun undoDraft() { _draft.value = _draft.value.dropLast(1) }
    fun clearDraft() { _draft.value = emptyList() }

    /**
     * Punkt w miejscu dotknięcia: najbliższe lotnisko albo pomoc nawigacyjna (baza OurAirports
     * i — gdy jest połączenie — baza MSFS) w promieniu [radiusNm]; inaczej punkt użytkownika ze współrzędnymi.
     */
    fun addDraftAt(lat: Double, lon: Double, radiusNm: Double) {
        val candidates = buildList {
            // Lotniska z kodem ICAO (4 litery) — lądowiska typu „PL-0124” nie istnieją w symulatorze
            db.nearby(lat, lon, radiusNm, limit = 8).firstOrNull { (a, _) -> a.ident.length == 4 && a.ident.all { it.isLetter() } }
                ?.let { (a, d) -> add(RoutePoint(a.ident, a.lat, a.lon, "airport", a.elevationFt) to d) }
            db.navaidsNear(lat, lon, radiusNm).firstOrNull()?.let { (n, d) -> add(RoutePoint(n.ident, n.lat, n.lon, n.type.lowercase()) to d) }
            _nearbyNav.value.map { it to com.freemetar.app.domain.Geo.distanceNm(lat, lon, it.lat, it.lon) }.filter { it.second <= radiusNm }
                .minByOrNull { it.second }?.let { (f, d) -> add(RoutePoint(f.ident, f.lat, f.lon, if (f.type == "waypoint") "fix" else f.type) to d) }
        }
        val p = candidates.minByOrNull { it.second }?.first
            ?: RoutePoint("WP%02d".format(_draft.value.count { it.ident.startsWith("WP") } + 1), lat, lon, "user")
        // Ten sam punkt dwa razy pod rząd nic nie zmienia
        if (_draft.value.lastOrNull()?.ident == p.ident) return
        _draft.value = _draft.value + p
    }

    /** Zapis szkicu jako trasy (aktywnej). Wymaga co najmniej dwóch punktów. */
    fun saveDraft(cruiseFl: Int?) = viewModelScope.launch {
        val pts = _draft.value
        if (pts.size < 2) { _flightMessage.value = loc("Dodaj co najmniej dwa punkty.", "Add at least two points."); return@launch }
        val route = SavedRoute(
            name = "${pts.first().ident} → ${pts.last().ident}",
            origin = pts.first().ident, destination = pts.last().ident, cruiseFl = cruiseFl,
            routeText = pts.drop(1).dropLast(1).joinToString(" ") { it.ident },
            points = pts,
            source = "map",
        ).let { it.copy(distanceNm = it.measuredDistanceNm()) }
        addRoutes(listOf(route), activate = true).join()
        _draft.value = emptyList()
        _planning.value = false
        _flightMessage.value = loc("Zapisano trasę ${route.name} (${route.distanceNm ?: 0} NM).", "Saved route ${route.name} (${route.distanceNm ?: 0} NM).")
    }

    fun addRouteFromText(text: String, cruiseFl: Int?) = viewModelScope.launch {
        try {
            val r = RouteImport.fromText(text, cruiseFl, db.fixResolver)
            addRoutes(listOf(r), activate = true).join()
            val unknown = r.points.count { !it.hasPosition }
            _flightMessage.value = loc("Zapisano trasę ${r.name}", "Saved route ${r.name}") + if (unknown > 0) loc(". $unknown punktów bez pozycji (brak w bazie) — pominięte na mapie.", ". $unknown waypoints without a position (not in the database) are skipped on the map.") else "."
        } catch (e: Exception) {
            _flightMessage.value = e.message ?: loc("Nie rozpoznano trasy.", "Could not recognise the route.")
        }
    }

    fun importRoutesFile(uri: Uri) = viewModelScope.launch {
        try {
            val text = routeStore.readText(getApplication(), uri)
            val list = RouteImport.parseAny(text, db.fixResolver)
            addRoutes(list, activate = list.size == 1).join()
            _flightMessage.value = loc("Zaimportowano trasy: ${list.size}.", "Routes imported: ${list.size}.")
        } catch (e: Exception) {
            _flightMessage.value = loc("Nie zaimportowano pliku: ${e.message}", "File not imported: ${e.message}")
        }
    }

    fun exportRoutes(uri: Uri) = viewModelScope.launch {
        try {
            routeStore.exportTo(getApplication(), uri, _routes.value)
            _flightMessage.value = loc("Wyeksportowano trasy: ${_routes.value.size}.", "Routes exported: ${_routes.value.size}.")
        } catch (e: Exception) {
            _flightMessage.value = loc("Nie zapisano pliku: ${e.message}", "File not saved: ${e.message}")
        }
    }

    fun deleteRoute(id: String) = viewModelScope.launch {
        _routes.value = _routes.value.filterNot { it.id == id }
        routeStore.save(_routes.value)
        if (settings.value.activeRouteId == id) { prefs.setActiveRoute(null); _routeBriefing.value = null }
    }

    fun activateRoute(id: String) = viewModelScope.launch {
        prefs.setActiveRoute(id)
        _routes.value.firstOrNull { it.id == id }?.let { refreshRouteBriefing(it) }
    }

    fun refreshRouteBriefing(r: SavedRoute? = activeRoute()) {
        r ?: return
        viewModelScope.launch {
            _routeBriefing.value = RouteBriefing(r.id)
            val ids = (listOf(r.origin, r.destination) + r.alternates).distinct()
            val m = async { runCatching { WeatherApi.metars(ids) }.getOrDefault(emptyList()).associateBy { it.station } }
            val t = async { ids.mapNotNull { id -> runCatching { WeatherApi.taf(id) }.getOrNull()?.let { id to it } }.toMap() }
            val sig = async {
                val pts = r.fullPath().filter { it.hasPosition }.map { LatLon(it.lat!!, it.lon!!) }
                runCatching { WeatherApi.sigmets() }.getOrDefault(emptyList())
                    .filter { s -> pts.any { p -> s.distanceNm(p) <= 25 } }
            }
            val winds = async {
                val pts = r.fullPath().filter { it.hasPosition }
                val fl = r.cruiseFl
                if (pts.isEmpty() || fl == null) emptyList() else
                    listOf(pts.first(), pts[pts.size / 2], pts.last()).distinct().map { p ->
                        p.ident to runCatching { OpenMeteo.fetch(p.lat!!, p.lon!!, hours = 3)?.windAt(fl) }.getOrNull()
                    }
            }
            _routeBriefing.value = RouteBriefing(r.id, m.await(), t.await(), sig.await(), winds.await(), loading = false)
        }
    }

    // ---------- OpenTakeoff Link (MSFS) ----------

    val link = LinkClient(app, prefs.linkTokens)
    val linkStatus = link.status
    val linkServers = link.servers
    val flightState = link.state
    val nav = NavRepository(link, NavDataStore(app))
    private val recorder = FlightRecorder(
        nearestAirport = { lat, lon -> db.nearby(lat, lon, radiusNm = 5.0, limit = 1).firstOrNull()?.first?.ident },
        // Progi pasów lotnisk do 5 NM od przyziemienia (OurAirports: współrzędne progów i kursy rzeczywiste)
        runwaysNear = { lat, lon ->
            db.nearby(lat, lon, radiusNm = 5.0, limit = 3).flatMap { (a, _) ->
                db.runways(a.ident).flatMap { rw ->
                    listOf(rw.le, rw.he).mapNotNull { e ->
                        val tLat = e.lat ?: return@mapNotNull null
                        val tLon = e.lon ?: return@mapNotNull null
                        val hdg = com.freemetar.app.domain.Wind.headingOf(e.ident, e.headingTrue) ?: return@mapNotNull null
                        com.freemetar.app.domain.RunwayGeom("${a.ident} ${e.ident}", tLat, tLon, hdg, rw.lengthFt?.toDouble())
                    }
                }
            }
        },
    )
    private var announcedLanding: com.freemetar.app.domain.LandingReport? = null
    private val _flightLogs = MutableStateFlow<List<FlightLog>>(emptyList())
    val flightLogs = _flightLogs.asStateFlow()
    private val _track = MutableStateFlow<List<TrackPoint>>(emptyList())
    /** Tor bieżącego lotu (do rysowania na mapie). */
    val track = _track.asStateFlow()
    private var userDisconnected = false

    /** Odkrywanie działa, gdy ekran Lot lub Mapa jest widoczny; znany komputer łączy się sam. */
    fun startLinkDiscovery() {
        link.startDiscovery()
    }

    fun stopLinkDiscovery() = link.stopDiscovery()

    fun linkConnect(ip: String, port: Int = 47810, code: String? = null) {
        userDisconnected = false
        link.connect(ip, port, code)
    }

    fun linkDisconnect() {
        userDisconnected = true
        link.disconnect()
    }

    /** Tor lotu jako GPX 1.1 (do Google Earth, Garmin, Strava i innych). */
    fun exportFlightGpx(ctx: android.content.Context, f: FlightLog, uri: Uri) = viewModelScope.launch {
        runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { w -> w.write(com.freemetar.app.data.link.FlightExport.gpx(f)) }
            }
        }.onSuccess { _flightMessage.value = loc("Zapisano tor lotu do GPX.", "Flight track saved as GPX.") }
            .onFailure { _flightMessage.value = loc("Nie zapisano pliku: ${it.message}", "File not saved: ${it.message}") }
    }

    /** Wszystkie loty w jednym pliku .oab (gzip) — kopia zapasowa albo przeniesienie na inny telefon. */
    fun exportFlightArchive(ctx: android.content.Context, uri: Uri) = viewModelScope.launch {
        runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(com.freemetar.app.data.link.FlightArchive.encode(_flightLogs.value)) }
            }
        }.onSuccess { _flightMessage.value = loc("Wyeksportowano ${_flightLogs.value.size} lotów.", "Exported ${_flightLogs.value.size} flights.") }
            .onFailure { _flightMessage.value = loc("Nie zapisano pliku: ${it.message}", "File not saved: ${it.message}") }
    }

    /** Import archiwum .oab lub starego flights.json; loty o tym samym id nie są dublowane. */
    fun importFlightArchive(ctx: android.content.Context, uri: Uri) = viewModelScope.launch {
        runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.freemetar.app.data.link.FlightArchive.decode(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            }
        }.onSuccess { incoming ->
            val before = _flightLogs.value.size
            _flightLogs.value = com.freemetar.app.data.link.FlightArchive.merge(_flightLogs.value, incoming)
            flightLogStore.save(_flightLogs.value)
            val added = _flightLogs.value.size - before
            _flightMessage.value = loc("Zaimportowano $added lotów (pominięto ${incoming.size - added} już zapisanych).", "Imported $added flights (skipped ${incoming.size - added} already saved).")
        }.onFailure { _flightMessage.value = loc("To nie jest archiwum lotów OpenTakeoff.", "This isn't an OpenTakeoff flight archive.") }
    }

    /** Stan zapisu lotu do kokpitu: nagrywa (od kiedy) albo wstrzymany po ręcznym zakończeniu. */
    data class RecordingInfo(val recording: Boolean = false, val startedAt: Long? = null, val held: Boolean = false)
    private val _recordingInfo = MutableStateFlow(RecordingInfo())
    val recordingInfo = _recordingInfo.asStateFlow()
    private fun publishRecording() { _recordingInfo.value = RecordingInfo(recorder.recording, recorder.startedAt, recorder.holdUntilGround) }

    /** Ręczne zakończenie zapisu bieżącego lotu — trafia do dziennika (jeśli trwał co najmniej 2 min). */
    fun stopRecording() {
        val log = recorder.stopByUser()
        if (log != null) saveFlight(log)
        else { _flightMessage.value = loc("Zapis zakończony — lot krótszy niż 2 min nie trafia do dziennika.", "Recording stopped — flights under 2 min are not saved."); viewModelScope.launch { flightLogStore.clearDraft() } }
        _track.value = emptyList()
        publishRecording()
    }

    fun resumeRecording() { recorder.resume(); publishRecording() }

    private val _nearbyNav = MutableStateFlow<List<com.freemetar.app.data.nav.FixResult>>(emptyList())
    /** Lotniska, VOR, NDB i punkty trasy z bazy MSFS wokół samolotu — do mapy ND. */
    val nearbyNav = _nearbyNav.asStateFlow()

    private val _mapFlight = MutableStateFlow<String?>(null)
    /** Zapisany lot pokazywany na mapie (zamiast toru bieżącego lotu); null = brak. */
    val mapFlight = _mapFlight.asStateFlow()
    fun showFlightOnMap(id: String?) { _mapFlight.value = id }

    /** Rozmiar archiwum lotów na dysku (do pokazania w ustawieniach). */
    fun flightArchiveBytes(): Long = flightLogStore.sizeBytes()

    fun deleteFlightLog(id: String) = viewModelScope.launch {
        _flightLogs.value = _flightLogs.value.filterNot { it.id == id }
        flightLogStore.save(_flightLogs.value)
    }

    private fun saveFlight(log: FlightLog?) {
        log ?: return
        viewModelScope.launch {
            // Ten sam id co szkic — wpis końcowy zastępuje szkic, nie dubluje go
            _flightLogs.value = listOf(log) + _flightLogs.value.filterNot { it.id == log.id }
            flightLogStore.clearDraft()
            flightLogStore.save(_flightLogs.value)
            val route = "${log.from ?: "?"} → ${log.to ?: "?"}"
            _flightMessage.value = loc("Zapisano lot $route (${log.durationMin} min).", "Flight $route saved (${log.durationMin} min).")
        }
    }

    private fun startLinkWatchers() {
        viewModelScope.launch {
            val logs = flightLogStore.load()
            // Lot przerwany zamknięciem aplikacji: szkic trafia do dziennika
            val draft = flightLogStore.takeDraft()
            _flightLogs.value = if (draft != null && logs.none { it.id == draft.id }) listOf(draft) + logs else logs
            if (draft != null) { flightLogStore.save(_flightLogs.value); flightLogStore.clearDraft() }
        }
        // Szkic trwającego lotu co 30 s; po 60 s bez danych z symulatora lot jest zamykany i zapisywany
        viewModelScope.launch {
            var noDataSince: Long? = null
            while (true) {
                kotlinx.coroutines.delay(30_000)
                if (recorder.recording) {
                    recorder.snapshot()?.let { flightLogStore.saveDraft(it) }
                    if (link.state.value == null) {
                        val since = noDataSince ?: System.currentTimeMillis().also { noDataSince = it }
                        if (System.currentTimeMillis() - since >= 60_000) { saveFlight(recorder.finish()); noDataSince = null }
                    } else noDataSince = null
                } else noDataSince = null
            }
        }
        viewModelScope.launch { settings.collect { recorder.intervalMs = it.recordIntervalMs.toLong().coerceIn(250, 10_000) } }
        // Lotniska i pomoce nawigacyjne z MSFS wokół samolotu (do ND): po ~10 NM lotu albo co 3 min
        viewModelScope.launch {
            var lastLat = Double.NaN; var lastLon = Double.NaN; var lastAt = 0L
            while (true) {
                val st = link.state.value
                val lat = st?.position?.lat; val lon = st?.position?.lon
                if (lat != null && lon != null && nav.connected) {
                    val moved = lastLat.isNaN() || com.freemetar.app.domain.Geo.distanceNm(lastLat, lastLon, lat, lon) > 10
                    if (moved || System.currentTimeMillis() - lastAt > 180_000) {
                        findNearAircraft(lat, lon)
                        val found = nav.nearby(lat, lon, 80)
                        if (found.isNotEmpty() || moved) _nearbyNav.value = found
                        lastLat = lat; lastLon = lon; lastAt = System.currentTimeMillis()
                    }
                } else if (st == null) {
                    lastLat = Double.NaN
                    // Bez symulatora wracamy do lokalizacji telefonu
                    if ((_nearMe.value as? NearMe.Found)?.aircraft == true) _nearMe.value = NearMe.Idle
                } else if (lat != null && lon != null && (_nearMe.value as? NearMe.Found)?.aircraft != true) {
                    // Połączony bez MSFS-owych danych nawigacyjnych (np. symulacja) — lista lotnisk i tak wokół samolotu
                    findNearAircraft(lat, lon); lastLat = lat; lastLon = lon; lastAt = System.currentTimeMillis()
                }
                kotlinx.coroutines.delay(15_000)
            }
        }
        viewModelScope.launch {
            link.state.collect { st ->
                if (st != null) {
                    saveFlight(recorder.onState(st))
                    if (recorder.recording != _recordingInfo.value.recording || recorder.holdUntilGround != _recordingInfo.value.held) publishRecording()
                    // Kopia toru tylko po dojściu nowego punktu, nie przy każdym stanie z Link
                    if (recorder.recording && recorder.pointCount != _track.value.size) _track.value = recorder.currentTrack
                    // Ocena zaraz po przyziemieniu — nie trzeba czekać na koniec lotu
                    recorder.lastLanding?.takeIf { it !== announcedLanding }?.let { l ->
                        announcedLanding = l
                        _flightMessage.value = loc("Lądowanie: ${l.score}/100, ${l.grade.lowercase()} — ${l.vsFpm} ft/min", "Landing: ${l.score}/100, ${l.grade.lowercase()} — ${l.vsFpm} ft/min") + (l.runway?.let { ", $it" } ?: "")
                    }
                }
            }
        }
        viewModelScope.launch {
            link.status.collect { s ->
                if (s !is LinkStatus.Connected && s !is LinkStatus.Lost) saveFlight(recorder.finish())
            }
        }
        // Po połączeniu z MSFS: dane nawigacyjne lotnisk trasy, ulubionych i otwartego briefingu
        viewModelScope.launch {
            link.status.collect { s ->
                if (s is LinkStatus.Connected && s.source == "msfs" || s is LinkStatus.Connected && s.source == "simulation") {
                    val ids = buildList {
                        activeRoute()?.let { r -> add(r.origin); add(r.destination); addAll(r.alternates) }
                        _detail.value?.ident?.let(::add)
                        addAll(settings.value.favorites.take(6))
                    }.distinct()
                    ids.forEach { id -> runCatching { nav.airport(id) } }
                    _detail.value?.ident?.let { id ->
                        val cached = nav.cached(id)
                        _detail.update { d -> if (d?.ident == id) d.copy(nav = cached) else d }
                    }
                }
            }
        }
        // Automatyczne połączenie z komputerem, z którym telefon już się sparował
        viewModelScope.launch {
            link.servers.collect { list ->
                val last = prefs.lastLinkIp() ?: return@collect
                if (!userDisconnected && link.status.value is LinkStatus.Idle) {
                    list.firstOrNull { it.ip == last }?.let { link.connect(it.ip, it.port) }
                }
            }
        }
    }

    override fun onCleared() {
        saveFlight(recorder.finish())
        link.disconnect()
        link.stopDiscovery()
        super.onCleared()
    }

    // ---------- edytor planu lotu ----------

    private val _editing = MutableStateFlow<SavedRoute?>(null)
    /** Kopia trasy w edycji; zapisywana dopiero po [saveEdit]. */
    val editing = _editing.asStateFlow()
    private val _editNav = MutableStateFlow<Map<String, AirportNav>>(emptyMap())
    /** Dane MSFS lotnisk edytowanej trasy (pasy, procedury). */
    val editNav = _editNav.asStateFlow()
    private val _editBusy = MutableStateFlow<String?>(null)
    val editBusy = _editBusy.asStateFlow()

    /** Oznaczenia pasów z bazy OurAirports (gdy brak danych MSFS). */
    fun airportRunwayIdents(icao: String): List<String> =
        db.runways(icao).flatMap { listOf(it.le.ident, it.he.ident) }.filter { it.isNotBlank() }.distinct()

    fun startEdit(id: String) {
        val r = _routes.value.firstOrNull { it.id == id } ?: return
        _editing.value = r
        refreshEditNav(listOf(r.origin, r.destination), refresh = false)
    }

    fun refreshEditNav(icaos: List<String>, refresh: Boolean) = viewModelScope.launch {
        _editBusy.value = if (nav.connected) loc("Pobieranie procedur z MSFS…", "Loading procedures from MSFS…") else null
        val map = _editNav.value.toMutableMap()
        icaos.distinct().forEach { id -> runCatching { nav.airport(id, refresh) }.getOrNull()?.let { map[id] = it } }
        _editNav.value = map
        _editBusy.value = null
    }

    fun editUpdate(change: (SavedRoute) -> SavedRoute) { _editing.update { it?.let(change) } }

    fun cancelEdit() { _editing.value = null }

    fun saveEdit() = viewModelScope.launch {
        val r = _editing.value ?: return@launch
        _routes.value = _routes.value.map { if (it.id == r.id) r else it }
        routeStore.save(_routes.value)
        _editing.value = null
        _flightMessage.value = loc("Zapisano plan ${r.name}.", "Saved plan ${r.name}.")
        if (settings.value.activeRouteId == r.id) refreshRouteBriefing(r)
    }

    /** Wybór SID / STAR / podejścia: odcinki z pamięci telefonu albo z MSFS przez Link. */
    fun chooseProcedure(kind: String, icao: String, name: String?, runway: String?, transition: String?, label: String?) = viewModelScope.launch {
        if (name == null) {
            editUpdate { r ->
                when (kind) {
                    "departure" -> RouteEdit.setDeparture(r, runway, null)
                    "arrival" -> RouteEdit.setArrival(r, null)
                    else -> RouteEdit.setApproach(r, runway, null)
                }
            }
            return@launch
        }
        _editBusy.value = loc("Pobieranie odcinków $name…", "Loading legs of $name…")
        val legs = runCatching { nav.legs(icao, kind, name, runway, transition) }.getOrNull()
        _editBusy.value = null
        val segment = when (kind) { "departure" -> "sid"; "arrival" -> "star"; else -> "approach" }
        val sel = ProcSelection(kind, name, runway, transition, label, legs?.let { RouteEdit.legsToPoints(it.legs, segment) }.orEmpty())
        if (legs == null) _flightMessage.value = loc("Brak odcinków $name w pamięci — połącz się z OpenTakeoff Link, aby pobrać je z MSFS.", "No legs for $name stored — connect to OpenTakeoff Link to load them from MSFS.")
        editUpdate { r ->
            when (kind) {
                "departure" -> RouteEdit.setDeparture(r, runway, sel)
                "arrival" -> RouteEdit.setArrival(r, sel)
                else -> RouteEdit.setApproach(r, runway, sel)
            }
        }
    }

    /** Dodanie punktu po identyfikatorze: najpierw baza OurAirports (lotniska, VOR, NDB), potem punkty MSFS. */
    fun addFix(ident: String, index: Int) = viewModelScope.launch {
        val r = _editing.value ?: return@launch
        val id = ident.trim().uppercase()
        if (id.isEmpty()) return@launch
        val before = (listOf(r.points.firstOrNull()) + r.enroute.take(index)).lastOrNull { it?.hasPosition == true }
        val near = before?.let { LatLon(it.lat!!, it.lon!!) }
        val p = db.fixResolver.resolve(id, near)
            ?: nav.fixes(id, near?.lat, near?.lon).firstOrNull()?.let { RoutePoint(it.ident, it.lat, it.lon, it.type) }
        if (p == null) {
            _flightMessage.value = loc("Nie znaleziono punktu $id", "Waypoint $id not found") + if (!nav.connected) loc(". Punkty RNAV są w bazie MSFS — połącz się z OpenTakeoff Link.", ". RNAV waypoints are in the MSFS database — connect to OpenTakeoff Link.") else "."
            return@launch
        }
        editUpdate { RouteEdit.insert(it, index, p) }
    }

    private val _nearMe = MutableStateFlow<NearMe>(NearMe.Idle)
    val nearMe = _nearMe.asStateFlow()

    init {
        viewModelScope.launch {
            _routes.value = routeStore.load()
            prefs.settings.first().activeRouteId?.let { id -> _routes.value.firstOrNull { it.id == id }?.let { refreshRouteBriefing(it) } }
        }
        loadDb(force = false)
        startLinkWatchers()
        startAutoRefresh()
        checkForUpdate(silent = true)
        // Lista ulubionych zmieniona (dodanie, usunięcie) → pobierz brakujące METAR-y
        viewModelScope.launch {
            prefs.settings.map { it.favorites.toSet() }.distinctUntilChanged().collect { refreshFavorites() }
        }
    }

    /** Najbliższe lotniska z kodem ICAO (lądowiska typu „PL-0250” nie mają pogody ani odpowiednika w symulatorze). */
    private fun nearIcao(lat: Double, lon: Double) =
        db.nearby(lat, lon, radiusNm = 120.0, limit = 30).filter { (a, _) -> a.ident.length == 4 && a.ident.all { it.isLetter() } }.take(8)

    /** Lotniska najbliżej samolotu w symulatorze — zastępują listę „w pobliżu Ciebie”, dopóki jest połączenie. */
    private fun findNearAircraft(lat: Double, lon: Double) = viewModelScope.launch {
        if (!db.isLoaded) return@launch
        val list = nearIcao(lat, lon)
        val old = (_nearMe.value as? NearMe.Found)?.takeIf { it.aircraft }?.metars.orEmpty()
        _nearMe.value = NearMe.Found(list, old, aircraft = true, loaded = old.isNotEmpty())
        _nearMe.value = NearMe.Found(list, metarsFor(list.map { it.first.ident }), aircraft = true)
    }

    /** Lotniska najbliżej użytkownika (wymaga zgody na przybliżoną lokalizację). */
    fun findNearMe() = viewModelScope.launch {
        _nearMe.value = NearMe.Locating
        val pos = runCatching { Locator.locate(getApplication()) }.getOrNull()
        if (pos == null) {
            _nearMe.value = NearMe.Failed(loc("Nie udało się ustalić pozycji. Włącz lokalizację i spróbuj ponownie.", "Could not determine your position. Turn on location and try again."))
            return@launch
        }
        if (!db.isLoaded) {
            _nearMe.value = NearMe.Failed(loc("Baza lotnisk jeszcze się ładuje.", "The airport database is still loading."))
            return@launch
        }
        val list = nearIcao(pos.latitude, pos.longitude)
        _nearMe.value = NearMe.Found(list, emptyMap(), loaded = false)
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
            if (silent) UpdateState.Idle else UpdateState.Error(e.message ?: loc("Nie udało się sprawdzić aktualizacji", "Could not check for updates"))
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
            UpdateState.Error(loc("Pobieranie nie powiodło się: ${e.message}", "Download failed: ${e.message}"))
        }
    }

    fun dismissUpdatePrompt() { _updatePromptDismissed.value = true }

    fun loadDb(force: Boolean) = viewModelScope.launch {
        try {
            _db.value = DbState.Loading(loc("Przygotowanie bazy lotnisk…", "Preparing the airport database…"))
            db.ensureDownloaded(force) { _db.value = DbState.Loading(it) }
            _db.value = DbState.Loading(loc("Indeksowanie…", "Indexing…"))
            db.load()
            _db.value = DbState.Ready(db.airportCount, db.lastUpdated())
            _detail.value?.let { open(it.ident) }
        } catch (e: Exception) {
            _db.value = DbState.Error(e.message ?: loc("Nie udało się pobrać bazy lotnisk", "Could not download the airport database"))
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
        loadNav(key, refresh = false)
    }

    /** Dane nawigacyjne MSFS dla briefingu: najpierw kopia lokalna, potem (po połączeniu) pobranie brakujących. */
    fun loadNav(icao: String, refresh: Boolean) = viewModelScope.launch {
        val cached = nav.cached(icao)
        _detail.update { if (it?.ident == icao) it.copy(nav = cached, navLoading = nav.connected) else it }
        val fresh = runCatching { nav.airport(icao, refresh) }.getOrNull()
        _detail.update { if (it?.ident == icao) it.copy(nav = fresh ?: it.nav, navLoading = false) else it }
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
            _detail.update { if (it?.ident == ident) it.copy(loading = false, error = e.message ?: loc("Błąd sieci", "Network error")) else it }
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
    fun setLanguage(v: String) = viewModelScope.launch { prefs.setLanguage(v) }
    fun setAircraftProfile(p: com.freemetar.app.domain.AircraftProfile?) = viewModelScope.launch { prefs.setAircraftProfile(p?.name) }
    fun setControlMode(m: String) = viewModelScope.launch { prefs.setControlMode(m) }
    fun setFloatingControl(v: Boolean) = viewModelScope.launch { prefs.setFloatingControl(v) }
    fun setPinJumpBar(v: Boolean) = viewModelScope.launch { prefs.setPinJumpBar(v) }
    fun setSimbriefUser(v: String) = viewModelScope.launch { prefs.setSimbriefUser(v) }
    fun setHideStatusBar(v: Boolean) = viewModelScope.launch { prefs.setHideStatusBar(v) }
    fun setLeversLeft(v: Boolean) = viewModelScope.launch { prefs.setLeversLeft(v) }
    fun setFloatingStyle(v: String) = viewModelScope.launch { prefs.setFloatingStyle(v) }
    fun setRecordInterval(ms: Int) = viewModelScope.launch { prefs.setRecordInterval(ms) }
    fun setTheme(m: com.freemetar.app.data.ThemeMode) = viewModelScope.launch { prefs.setTheme(m) }
    fun setAutoRefresh(v: Boolean) = viewModelScope.launch { prefs.setAutoRefresh(v) }
    fun setWelcomeShown() = viewModelScope.launch { prefs.setWelcomeShown(true) }

    // Callouts functions
    fun setCalloutsEnabled(enabled: Boolean) {
        _calloutsEnabled.value = enabled
        callouts.setEnabled(enabled)
    }

    fun toggleCallout(id: String, enabled: Boolean) {
        val current = _enabledCallouts.value.toMutableSet()
        if (enabled) current.add(id) else current.remove(id)
        _enabledCallouts.value = current
        callouts.setCalloutEnabled(id, enabled)
    }

    fun setCalloutsLanguage(lang: String) {
        _calloutsLanguage.value = lang
        val locale = if (lang == "pl") java.util.Locale("pl") else java.util.Locale.US
        callouts.setLanguage(locale)
    }

    fun setCalloutsSpeechRate(rate: Float) {
        _calloutsSpeechRate.value = rate
        callouts.setSpeechRate(rate)
    }

    // Checklists functions
    fun loadChecklistsForAircraft(aircraftTitle: String?) = viewModelScope.launch {
        val checklists = checklistsManager.getChecklists(aircraftTitle)
        _currentChecklists.value = checklists
    }

    // Update callouts with flight state
    fun updateCallouts(state: com.freemetar.app.data.link.FlightState) {
        callouts.update(state)
    }
}
