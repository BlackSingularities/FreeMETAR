package com.freemetar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LocalAirport
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Water
import androidx.compose.ui.graphics.vector.ImageVector
import com.freemetar.app.ui.components.EmptyState
import com.freemetar.app.ui.components.ListHeader
import com.freemetar.app.ui.components.windowColumns
import com.freemetar.app.ui.theme.PanelHi
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.DbState
import com.freemetar.app.MainViewModel
import com.freemetar.app.data.Airport
import com.freemetar.app.data.Metar
import com.freemetar.app.ui.components.CategoryBadge
import com.freemetar.app.ui.components.SectionCard
import com.freemetar.app.ui.theme.Amber
import com.freemetar.app.ui.theme.Line
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.Panel
import com.freemetar.app.ui.theme.RawText
import com.freemetar.app.ui.theme.Sky
import com.freemetar.app.ui.theme.TextHi
import com.freemetar.app.ui.theme.TextLo
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val Full = StaggeredGridItemSpan.FullLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val db by vm.dbState.collectAsStateWithLifecycle()
    val metars by vm.favMetars.collectAsStateWithLifecycle()
    val loading by vm.favLoading.collectAsStateWithLifecycle()
    val results by vm.search.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    PullToRefreshBox(isRefreshing = loading, onRefresh = { vm.refreshFavorites() }, modifier = Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(windowColumns()),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = Full) { Header() }
            item(span = Full) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; vm.onQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ICAO, IATA, nazwa lub miasto") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = ""; vm.onQuery("") }) { Icon(Icons.Outlined.Close, "Wyczyść") } },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.length >= 3) onOpen(results.firstOrNull()?.ident ?: query.uppercase()) }),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel, unfocusedBorderColor = Line),
                )
            }
            item(span = Full) { DbBanner(db) { vm.loadDb(force = true) } }

            if (query.length >= 2) {
                if (results.isEmpty()) {
                    item(span = Full) {
                        EmptyState(
                            Icons.Outlined.SearchOff, "Brak wyników",
                            if (db is DbState.Ready) "Naciśnij Szukaj na klawiaturze, aby spróbować pobrać METAR dla „${query.uppercase()}”." else "Baza lotnisk jeszcze się ładuje…",
                        )
                    }
                }
                items(results, key = { "s" + it.ident }, span = { Full }) { a -> SearchRow(a) { onOpen(a.ident) } }
            } else {
                if (settings.recent.isNotEmpty()) {
                    item(span = Full) {
                        ListHeader("Ostatnio oglądane", Icons.Outlined.History) {
                            Text("Wyczyść", color = Sky, fontSize = 12.sp, modifier = Modifier.clickable { vm.clearRecent() })
                        }
                        Spacer(Modifier.size(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems(settings.recent) { id -> AssistChip(onClick = { onOpen(id) }, label = { Text(id, fontFamily = Mono) }, leadingIcon = { Icon(Icons.Outlined.History, null, modifier = Modifier.size(16.dp)) }) }
                        }
                    }
                }
                item(span = Full) {
                    ListHeader("Ulubione", Icons.Outlined.Star) {
                        IconButton({ vm.refreshFavorites() }) { Icon(Icons.Outlined.Refresh, "Odśwież", tint = TextLo) }
                    }
                }
                if (settings.favorites.isEmpty()) {
                    item(span = Full) { EmptyState(Icons.Outlined.StarBorder, "Brak ulubionych", "Wyszukaj lotnisko i dotknij gwiazdki na jego karcie.") }
                }
                items(settings.favorites, key = { "f$it" }) { id ->
                    FavoriteCard(
                        ident = id,
                        name = vm.airportName(id),
                        metar = metars[id],
                        loading = loading,
                        onClick = { onOpen(id) },
                        onUp = { vm.moveFavorite(id, -1) },
                        onDown = { vm.moveFavorite(id, 1) },
                    )
                }
                item(span = Full) { Attribution() }
            }
        }
    }
}

@Composable
private fun Header() {
    var now by remember { mutableStateOf(ZonedDateTime.now(ZoneOffset.UTC)) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(ZoneOffset.UTC); delay(1000) } }
    val local = now.withZoneSameInstant(ZoneId.systemDefault())
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Sky.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.FlightTakeoff, null, tint = Sky, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(buildLogo(), style = MaterialTheme.typography.headlineLarge)
            Text("Pogoda i dane lotnisk dla wirtualnych pilotów", color = TextLo, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Public, null, tint = Amber, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "Z", fontFamily = Mono, fontSize = 20.sp, color = Amber, fontWeight = FontWeight.Bold)
            }
            Text("LT " + local.format(DateTimeFormatter.ofPattern("HH:mm")), fontFamily = Mono, fontSize = 12.sp, color = TextLo)
        }
    }
}

@Composable
private fun buildLogo() = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(color = TextHi)); append("Free"); pop()
    pushStyle(androidx.compose.ui.text.SpanStyle(color = Sky)); append("METAR"); pop()
}

@Composable
private fun DbBanner(db: DbState, onRetry: () -> Unit) {
    when (db) {
        is DbState.Loading -> SectionCard {
            Text(db.message, color = TextLo, fontSize = 13.sp)
            Spacer(Modifier.size(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.size(6.dp))
            Text("Jednorazowo ok. 15 MB z OurAirports — potem działa z pamięci.", color = TextLo, fontSize = 12.sp)
        }
        is DbState.Error -> SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, null, tint = Amber)
                Spacer(Modifier.width(10.dp))
                Text("Nie udało się pobrać bazy lotnisk: ${db.message}", color = TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.size(8.dp))
            Button(onRetry) { Text("Spróbuj ponownie") }
        }
        else -> {}
    }
}

@Composable
private fun SearchRow(a: Airport, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(PanelHi), contentAlignment = Alignment.Center) {
            Icon(
                when (a.type) { "heliport" -> Icons.Outlined.Hub; "seaplane_base" -> Icons.Outlined.Water; "small_airport" -> Icons.Outlined.Flight; else -> Icons.Outlined.LocalAirport },
                null, tint = Sky, modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(64.dp)) {
            Text(a.ident, fontFamily = Mono, fontWeight = FontWeight.Bold, color = Sky, fontSize = 16.sp)
            if (a.iata.isNotBlank()) Text(a.iata, fontFamily = Mono, color = TextLo, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(a.name, color = TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(a.municipality, a.country).filter { it.isNotBlank() }.joinToString(", ") + " · " + a.typeLabel, color = TextLo, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun FavoriteCard(ident: String, name: String?, metar: Metar?, loading: Boolean, onClick: () -> Unit, onUp: () -> Unit, onDown: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ident, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextHi)
                        Spacer(Modifier.width(10.dp))
                        CategoryBadge(metar?.flightCategory)
                    }
                    Text(name ?: metar?.name ?: "", color = TextLo, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column {
                    Icon(Icons.Outlined.KeyboardArrowUp, "W górę", tint = TextLo, modifier = Modifier.size(22.dp).clickable(onClick = onUp))
                    Icon(Icons.Outlined.KeyboardArrowDown, "W dół", tint = TextLo, modifier = Modifier.size(22.dp).clickable(onClick = onDown))
                }
            }
            Spacer(Modifier.size(10.dp))
            if (metar != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MiniStat("Wiatr", windShort(metar), Icons.Outlined.Air)
                    MiniStat("Temp", metar.tempC?.let { "${it.toInt()}°" } ?: "—", Icons.Outlined.Thermostat)
                    MiniStat("QNH", metar.altimHpa?.let { "${it.toInt()}" } ?: "—", Icons.Outlined.Compress)
                    MiniStat("Wiek", ageMinutes(metar.obsTimeEpoch), Icons.Outlined.Schedule)
                }
                Spacer(Modifier.size(10.dp))
                Text(metar.raw, style = RawText, color = TextHi.copy(alpha = 0.85f))
            } else {
                Text(if (loading) "Pobieranie METAR…" else "Brak aktualnego METAR", color = TextLo, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, icon: ImageVector) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextLo, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLo, fontSize = 10.sp)
        }
        Text(value, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
    }
}

fun windShort(m: Metar): String {
    val spd = m.windKt ?: return "—"
    if (spd == 0) return "CALM"
    val d = if (m.windVariable) "VRB" else "%03d".format(m.windDir ?: 0)
    return "$d/$spd" + (m.gustKt?.let { "G$it" } ?: "")
}

fun ageMinutes(epoch: Long): String {
    if (epoch == 0L) return "—"
    val min = (System.currentTimeMillis() / 1000 - epoch) / 60
    return if (min < 60) "${min}m" else "${min / 60}h${min % 60}m"
}

@Composable
private fun Attribution() {
    Text(
        "Dane pogodowe: NOAA Aviation Weather Center. Dane lotnisk: OurAirports (domena publiczna). " +
            "Tylko do symulacji lotów — nie używaj do rzeczywistej nawigacji.",
        color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
    )
}
