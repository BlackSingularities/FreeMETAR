package com.freemetar.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalAirport
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.DbState
import com.freemetar.app.MainViewModel
import com.freemetar.app.NearMe
import com.freemetar.app.data.Airport
import com.freemetar.app.data.Locator
import com.freemetar.app.data.Metar
import com.freemetar.app.domain.Geo
import com.freemetar.app.domain.fmt
import com.freemetar.app.ui.components.CategoryBlock
import com.freemetar.app.ui.components.EmptyState
import com.freemetar.app.ui.components.Panel
import com.freemetar.app.ui.components.Pill
import com.freemetar.app.ui.components.windowColumns
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val Full = StaggeredGridItemSpan.FullLine
private val hhmmZ = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val db by vm.dbState.collectAsStateWithLifecycle()
    val metars by vm.favMetars.collectAsStateWithLifecycle()
    val loading by vm.favLoading.collectAsStateWithLifecycle()
    val results by vm.search.collectAsStateWithLifecycle()
    val nearMe by vm.nearMe.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    val c = Fm.c

    PullToRefreshBox(isRefreshing = loading, onRefresh = { vm.refreshFavorites() }, modifier = Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(windowColumns()),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalItemSpacing = 10.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = Full) { Header() }
            item(span = Full) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; vm.onQuery(it) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    placeholder = { Text("Lotnisko: ICAO, IATA lub miasto") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = ""; vm.onQuery("") }) { Icon(Icons.Outlined.Close, "Wyczyść") } },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.length >= 3) onOpen(results.firstOrNull()?.ident ?: query.uppercase().trim()) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = c.card, focusedContainerColor = c.card,
                        unfocusedBorderColor = c.ink2, focusedBorderColor = c.magenta,
                    ),
                )
            }
            item(span = Full) { DbBanner(db) { vm.loadDb(force = true) } }

            if (query.length >= 2) {
                if (results.isEmpty()) item(span = Full) {
                    EmptyState(
                        Icons.Outlined.SearchOff, "Nie ma takiego lotniska w bazie",
                        if (db is DbState.Ready) "Sprawdź kod albo naciśnij Szukaj na klawiaturze, żeby pobrać METAR dla ${query.uppercase()} bezpośrednio." else "Baza lotnisk jeszcze się wczytuje.",
                    )
                }
                items(results, key = { "s" + it.ident }) { a -> SearchRow(a) { onOpen(a.ident) } }
            } else {
                if (settings.recent.isNotEmpty()) item(span = Full) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        settings.recent.forEach { id -> Pill(id, icon = Icons.Outlined.History, mono = true) { onOpen(id) } }
                        Text("Wyczyść", color = c.ink2, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.clearRecent() }.padding(horizontal = 8.dp, vertical = 7.dp))
                    }
                }

                item(span = Full) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Twoje lotniska", style = MaterialTheme.typography.titleLarge, color = c.ink)
                            val newest = metars.values.maxOfOrNull { it.obsTimeEpoch }
                            Text(
                                if (loading) "Pobieranie pogody…" else newest?.let { "Najnowsza obserwacja ${hhmmZ.format(Instant.ofEpochSecond(it))}Z" } ?: "Pociągnij w dół, aby odświeżyć",
                                style = MaterialTheme.typography.bodySmall, color = c.ink2,
                            )
                        }
                        if (settings.favorites.isNotEmpty()) {
                            TextButton({ editing = !editing }) {
                                Icon(if (editing) Icons.Outlined.Close else Icons.Outlined.Edit, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (editing) "Gotowe" else "Ułóż")
                            }
                        }
                        IconButton({ vm.refreshFavorites() }) { Icon(Icons.Outlined.Refresh, "Odśwież pogodę", tint = c.ink2) }
                    }
                }
                if (settings.favorites.isEmpty()) item(span = Full) {
                    EmptyState(Icons.Outlined.StarBorder, "Nie masz jeszcze ulubionych lotnisk", "Wyszukaj lotnisko i dotknij gwiazdki na jego karcie, a pojawi się tutaj z bieżącą pogodą.")
                }
                items(settings.favorites, key = { "f$it" }) { id ->
                    FlightStrip(
                        ident = id,
                        name = vm.airportName(id) ?: metars[id]?.name,
                        metar = metars[id],
                        loading = loading,
                        editing = editing,
                        onClick = { if (!editing) onOpen(id) },
                        onUp = { vm.moveFavorite(id, -1) },
                        onDown = { vm.moveFavorite(id, 1) },
                        onRemove = { vm.toggleFavorite(id) },
                    )
                }

                item(span = Full) { NearMeBlock(vm, nearMe, onOpen) }
                item(span = Full) {
                    Text(
                        "Pogoda: NOAA Aviation Weather Center. Lotniska: OurAirports. Tylko do symulacji lotów — nie używaj do rzeczywistej nawigacji.",
                        style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    var now by remember { mutableStateOf(ZonedDateTime.now(ZoneOffset.UTC)) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(ZoneOffset.UTC); delay(1000) } }
    val local = now.withZoneSameInstant(ZoneId.systemDefault())
    val c = Fm.c
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            buildAnnotatedString {
                append("Free")
                withStyle(SpanStyle(color = c.magenta)) { append("METAR") }
            },
            style = MaterialTheme.typography.headlineLarge, color = c.ink, modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "Z", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.ink)
            Text("czas lokalny " + local.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.bodySmall, color = c.ink2)
        }
    }
}

@Composable
private fun DbBanner(db: DbState, onRetry: () -> Unit) {
    val c = Fm.c
    when (db) {
        is DbState.Loading -> Panel {
            Column(Modifier.padding(14.dp)) {
                Text(db.message, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                Spacer(Modifier.size(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.magenta, trackColor = c.terrain)
                Spacer(Modifier.size(6.dp))
                Text("Pierwsze uruchomienie pobiera ok. 15 MB danych lotnisk. Potem aplikacja korzysta z kopii w telefonie.", style = MaterialTheme.typography.bodySmall, color = c.ink2)
            }
        }
        is DbState.Error -> Panel {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, null, tint = c.ifr)
                    Spacer(Modifier.width(10.dp))
                    Text("Nie pobrano bazy lotnisk: ${db.message}. Sprawdź połączenie z internetem.", style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.size(10.dp))
                Button(onRetry) { Text("Pobierz bazę ponownie") }
            }
        }
        else -> {}
    }
}

@Composable
private fun SearchRow(a: Airport, onClick: () -> Unit) {
    val c = Fm.c
    Panel(onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (a.type) { "seaplane_base" -> Icons.Outlined.Water; "small_airport" -> Icons.Outlined.Flight; else -> Icons.Outlined.LocalAirport },
                null, tint = c.magenta, modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(68.dp)) {
                Text(a.ident, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.ink)
                if (a.iata.isNotBlank()) Text(a.iata, fontFamily = Mono, fontSize = 12.sp, color = c.ink2)
            }
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(a.municipality, a.country).filter { it.isNotBlank() }.joinToString(", ") + ", " + a.typeLabel.lowercase(),
                    style = MaterialTheme.typography.bodySmall, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Pasek lotu: identyfikator i nazwa, pod spodem stałe pola danych,
 * po prawej blok kategorii lotu w jej kolorze.
 */
@Composable
fun FlightStrip(
    ident: String,
    name: String?,
    metar: Metar?,
    loading: Boolean,
    editing: Boolean = false,
    trailingNote: String? = null,
    onClick: () -> Unit,
    onUp: () -> Unit = {},
    onDown: () -> Unit = {},
    onRemove: () -> Unit = {},
) {
    val c = Fm.c
    Panel(onClick = onClick) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(ident, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 23.sp, color = c.ink)
                    Spacer(Modifier.width(10.dp))
                    Text(name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(bottom = 3.dp))
                    if (trailingNote != null) Text(trailingNote, fontFamily = Mono, fontSize = 12.sp, color = c.magenta, modifier = Modifier.padding(bottom = 3.dp))
                }
                Spacer(Modifier.size(7.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
                Spacer(Modifier.size(6.dp))
                if (metar != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        StripField("Wiatr", windShort(metar))
                        StripField("QNH", metar.altimHpa?.roundToInt()?.toString() ?: "—")
                        StripField("Temp", metar.tempC?.let { "${it.roundToInt()}°" } ?: "—")
                        StripField("Obs.", ageMinutes(metar.obsTimeEpoch), stale = isStale(metar.obsTimeEpoch))
                    }
                } else {
                    Text(if (loading) "Pobieranie METAR…" else "Brak bieżącego METAR", style = MaterialTheme.typography.bodySmall, color = c.ink2,
                        modifier = Modifier.padding(vertical = 6.dp))
                }
            }
            if (editing) {
                Column(Modifier.fillMaxHeight().background(c.terrain), verticalArrangement = Arrangement.Center) {
                    Row {
                        IconButton(onUp) { Icon(Icons.Outlined.ArrowUpward, "Przesuń wyżej", tint = c.ink) }
                        IconButton(onDown) { Icon(Icons.Outlined.ArrowDownward, "Przesuń niżej", tint = c.ink) }
                    }
                    IconButton(onRemove, Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Outlined.Delete, "Usuń z ulubionych", tint = c.ifr) }
                }
            } else {
                CategoryBlock(metar)
            }
        }
    }
}

@Composable
private fun StripField(label: String, value: String, stale: Boolean = false) {
    val c = Fm.c
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.ink2)
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (stale) c.caution else c.ink)
    }
}

@Composable
private fun NearMeBlock(vm: MainViewModel, state: NearMe, onOpen: (String) -> Unit) {
    val c = Fm.c
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.findNearMe()
    }
    fun start() = if (Locator.hasPermission(ctx)) vm.findNearMe() else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)

    Column(Modifier.padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("W pobliżu Ciebie", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f))
            if (state is NearMe.Found) IconButton({ start() }) { Icon(Icons.Outlined.MyLocation, "Ustal pozycję ponownie", tint = c.ink2) }
        }
        Spacer(Modifier.size(8.dp))
        when (state) {
            NearMe.Idle -> Panel {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MyLocation, null, tint = c.magenta, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Najbliższe lotniska z bieżącą pogodą, wg przybliżonej lokalizacji telefonu.", style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                }
                Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    OutlinedButton({ start() }) { Text("Pokaż lotniska w pobliżu") }
                }
            }
            NearMe.Locating -> Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.magenta)
                Spacer(Modifier.width(12.dp))
                Text("Ustalanie pozycji…", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            }
            is NearMe.Failed -> Column {
                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                TextButton({ start() }) { Text("Spróbuj ponownie") }
            }
            is NearMe.Found -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.airports.isEmpty()) Text("Brak lotnisk w promieniu 120 NM.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                state.airports.forEach { (a, dist) ->
                    FlightStrip(
                        ident = a.ident, name = a.name, metar = state.metars[a.ident],
                        loading = state.metars.isEmpty(),
                        trailingNote = "${dist.fmt(0)} NM",
                        onClick = { onOpen(a.ident) },
                    )
                }
            }
        }
    }
}

fun windShort(m: Metar): String {
    val spd = m.windKt ?: return "—"
    if (spd == 0) return "CALM"
    val d = if (m.windVariable) "VRB" else "%03d".format(m.windDir ?: 0)
    return "$d/%02d".format(spd) + (m.gustKt?.let { "G$it" } ?: "")
}

fun ageMinutes(epoch: Long): String {
    if (epoch == 0L) return "—"
    val min = (System.currentTimeMillis() / 1000 - epoch) / 60
    return when {
        min < 1 -> "teraz"
        min < 60 -> "${min} min"
        else -> "${min / 60} h ${min % 60} min"
    }
}

/** METAR starszy niż 90 minut prawdopodobnie jest nieaktualny. */
fun isStale(epoch: Long) = epoch > 0 && System.currentTimeMillis() / 1000 - epoch > 90 * 60
