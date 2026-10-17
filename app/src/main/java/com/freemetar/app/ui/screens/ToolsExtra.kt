package com.freemetar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.MainViewModel
import com.freemetar.app.data.Airport
import com.freemetar.app.data.Metar
import com.freemetar.app.domain.Geo
import com.freemetar.app.ui.components.CategoryBadge
import com.freemetar.app.ui.components.SectionCard
import com.freemetar.app.ui.components.StatTile
import com.freemetar.app.ui.theme.Amber
import com.freemetar.app.ui.theme.CatIfr
import com.freemetar.app.ui.theme.CatVfr
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.PanelHi
import com.freemetar.app.ui.theme.RawText
import com.freemetar.app.ui.theme.Sky
import com.freemetar.app.ui.theme.TextHi
import com.freemetar.app.ui.theme.TextLo
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Odcinek ortodromy: odległość, kurs początkowy, czas lotu i pogoda na obu końcach. */
@Composable
fun RouteTool(vm: MainViewModel) {
    var dep by rememberSaveable { mutableStateOf("EPWA") }
    var arr by rememberSaveable { mutableStateOf("EPGD") }
    var gs by rememberSaveable { mutableStateOf("120") }
    var metars by remember { mutableStateOf<Map<String, Metar>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val a = vm.airport(dep)
    val b = vm.airport(arr)

    SectionCard("Planowanie trasy", icon = Icons.Outlined.Route) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IcaoField("Odlot", dep, Icons.Outlined.FlightTakeoff, Modifier.weight(1f)) { dep = it }
            IcaoField("Przylot", arr, Icons.Outlined.FlightLand, Modifier.weight(1f)) { arr = it }
        }
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("GS kt", gs, Modifier.weight(1f)) { gs = it }
            Button(
                onClick = {
                    loading = true
                    scope.launch {
                        metars = vm.metarsFor(listOfNotNull(a?.ident, b?.ident))
                        loading = false
                    }
                },
                enabled = a != null && b != null && !loading,
            ) { Text(if (loading) "Pobieranie…" else "Pogoda") }
        }

        if (a == null || b == null) {
            Spacer(Modifier.size(10.dp))
            Text(
                when {
                    a == null && dep.length >= 3 -> "Nie znaleziono lotniska $dep"
                    b == null && arr.length >= 3 -> "Nie znaleziono lotniska $arr"
                    else -> "Wpisz kody ICAO obu lotnisk."
                },
                color = TextLo, fontSize = 13.sp,
            )
        } else {
            val dist = Geo.distanceNm(a.lat, a.lon, b.lat, b.lon)
            val crs = Geo.bearing(a.lat, a.lon, b.lat, b.lon)
            val back = Geo.bearing(b.lat, b.lon, a.lat, a.lon)
            val speed = gs.num()?.takeIf { it > 0 }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Dystans", "${dist.roundToInt()} NM", "${(dist * 1.852).roundToInt()} km", Modifier.weight(1f), accent = Sky, icon = Icons.Outlined.Straighten)
                StatTile("Kurs", "%03d°".format(crs.roundToInt()), "powrót %03d°".format(back.roundToInt()), Modifier.weight(1f), icon = Icons.Outlined.Explore)
                StatTile("Czas", speed?.let { val h = dist / it; "%d:%02d".format(h.toInt(), ((h - h.toInt()) * 60).roundToInt()) } ?: "—", "ETE", Modifier.weight(1f), icon = Icons.Outlined.Schedule)
            }
            Text("Kurs rzeczywisty po ortodromie (bez deklinacji i wiatru).", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            if (metars.isNotEmpty()) {
                Spacer(Modifier.size(12.dp))
                listOf(a, b).forEach { ap -> RouteMetar(ap, metars[ap.ident]) }
            }
        }
    }
}

@Composable
private fun IcaoField(label: String, value: String, icon: ImageVector, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.uppercase().filter { c -> c.isLetterOrDigit() }.take(4)) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        singleLine = true,
        textStyle = RawText,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    )
}

@Composable
private fun RouteMetar(a: Airport, m: Metar?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(PanelHi).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.ident, fontFamily = Mono, fontWeight = FontWeight.Bold, color = TextHi)
            Spacer(Modifier.width(8.dp))
            CategoryBadge(m?.flightCategory)
            Spacer(Modifier.width(8.dp))
            Text(a.name, color = TextLo, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.size(6.dp))
        Text(m?.raw ?: "Brak METAR", style = RawText.copy(fontSize = 12.sp), color = TextHi.copy(alpha = 0.85f))
    }
}

private val phonetic = listOf(
    "A" to "Alfa", "B" to "Bravo", "C" to "Charlie", "D" to "Delta", "E" to "Echo", "F" to "Foxtrot",
    "G" to "Golf", "H" to "Hotel", "I" to "India", "J" to "Juliett", "K" to "Kilo", "L" to "Lima",
    "M" to "Mike", "N" to "November", "O" to "Oscar", "P" to "Papa", "Q" to "Quebec", "R" to "Romeo",
    "S" to "Sierra", "T" to "Tango", "U" to "Uniform", "V" to "Victor", "W" to "Whiskey", "X" to "X-ray",
    "Y" to "Yankee", "Z" to "Zulu",
    "3" to "Tree", "5" to "Fife", "9" to "Niner", "." to "Decimal",
)

/** Ściąga radiowa: alfabet ICAO, kody transpondera, sygnały świetlne. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheatSheet() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Alfabet fonetyczny ICAO", icon = Icons.Outlined.RecordVoiceOver) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                phonetic.forEach { (l, w) ->
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(PanelHi).padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text(l, fontFamily = Mono, color = Sky, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text(w, color = TextHi, fontSize = 13.sp)
                    }
                }
            }
        }
        SectionCard("Kody transpondera", icon = Icons.Outlined.Pin) {
            listOf(
                Triple("7700", "Niebezpieczeństwo (MAYDAY)", CatIfr),
                Triple("7600", "Utrata łączności radiowej", Amber),
                Triple("7500", "Bezprawna ingerencja", CatIfr),
                Triple("7000", "VFR — Europa", TextHi),
                Triple("1200", "VFR — USA/Kanada", TextHi),
                Triple("2000", "IFR bez przydziału kodu", TextHi),
            ).forEach { (c, t, col) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c, fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = col, modifier = Modifier.width(70.dp))
                    Text(t, color = TextHi, fontSize = 14.sp)
                }
            }
        }
        SectionCard("Sygnały świetlne z wieży", icon = Icons.Outlined.Lightbulb) {
            Row(Modifier.padding(bottom = 6.dp)) {
                Text("Sygnał", color = TextLo, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("W locie", color = TextLo, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Na ziemi", color = TextLo, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
            listOf(
                Triple(CatVfr to "Zielone ciągłe", "Zezwolenie na lądowanie", "Zezwolenie na start"),
                Triple(CatVfr to "Zielone przeryw.", "Wróć do lądowania", "Zezwolenie na kołowanie"),
                Triple(CatIfr to "Czerwone ciągłe", "Ustąp, krąż dalej", "Stop"),
                Triple(CatIfr to "Czerwone przeryw.", "Nie ląduj, lotnisko niebezp.", "Opuść drogę startową"),
                Triple(Color.White to "Białe przeryw.", "Ląduj tutaj, potem płyta", "Wróć na miejsce startu"),
            ).forEach { (sig, air, ground) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(sig.first))
                        Spacer(Modifier.width(6.dp))
                        Text(sig.second, color = TextHi, fontSize = 12.sp)
                    }
                    Text(air, color = TextHi, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(ground, color = TextHi, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
