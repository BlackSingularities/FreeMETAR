package com.freemetar.app.ui.charts

import com.freemetar.app.domain.loc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freemetar.app.data.charts.Chart
import com.freemetar.app.data.charts.ChartType
import com.freemetar.app.data.charts.ChartsStatus
import com.freemetar.app.data.charts.FaaCharts
import com.freemetar.app.ui.components.Pill
import com.freemetar.app.ui.components.Rule
import com.freemetar.app.ui.components.Section
import com.freemetar.app.ui.theme.Fm
import kotlinx.coroutines.launch

/**
 * Karty FAA (d-TPP) lotniska w USA do osadzenia w briefingu. Dla lotnisk spoza USA nic nie rysuje.
 * Pierwsze użycie pyta o pobranie katalogu (~16 MB); karty otwierają się w [ChartViewer]
 * (pełny ekran w oknie dialogowym), chyba że podano własne [onOpen].
 */
@Composable
fun ChartsPanel(
    icao: String,
    modifier: Modifier = Modifier,
    onOpen: ((Chart) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repo = remember { FaaCharts.get(context) }
    if (!repo.isUsAirport(icao) && !FaaCharts.isUsIcao(icao)) return

    val scope = rememberCoroutineScope()
    val status by repo.status.collectAsState()
    var charts by remember(icao) { mutableStateOf<List<Chart>?>(null) }
    var error by remember(icao) { mutableStateOf<String?>(null) }
    var hasIndex by remember { mutableStateOf(repo.hasIndex()) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable(icao) { mutableStateOf<ChartType?>(null) }
    var open by remember { mutableStateOf<Chart?>(null) }

    LaunchedEffect(icao, hasIndex, reload) {
        if (!hasIndex) return@LaunchedEffect
        error = null
        runCatching { repo.chartsFor(icao) }
            .onSuccess { charts = it }
            .onFailure { error = it.message ?: loc("Nie udało się wczytać kart", "Could not load charts") }
    }

    fun download(force: Boolean) = scope.launch {
        error = null
        runCatching { repo.update(force) }
            .onSuccess { hasIndex = true; reload++ }
            .onFailure { error = it.message ?: loc("Brak połączenia z FAA", "Cannot reach FAA") }
    }

    val c = Fm.c
    Section(loc("Karty FAA", "FAA charts"), Icons.Outlined.Map, modifier, trailing = {
        (status as? ChartsStatus.Ready)?.let { Text(loc("cykl ", "cycle ") + it.cycle, style = MaterialTheme.typography.labelSmall, color = c.ink2) }
    }) {
        val st = status
        when {
            st is ChartsStatus.Downloading -> {
                Text(loc("Pobieranie katalogu d-TPP ${st.cycle}…", "Downloading d-TPP catalogue ${st.cycle}…"), style = MaterialTheme.typography.bodySmall, color = c.ink2)
                Spacer(Modifier.size(8.dp))
                if (st.total > 0) LinearProgressIndicator(progress = { (st.bytes.toFloat() / st.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            !hasIndex -> {
                Text(
                    loc(
                        "Oficjalne karty FAA: diagram lotniska, podejścia, SID, STAR, minima. Katalog kart zajmuje ok. 2,5 MB, " +
                            "pobranie to ok. 16 MB. Karty PDF pobierają się po otwarciu i działają potem offline.",
                        "Official FAA charts: airport diagram, approaches, SIDs, STARs, minimums. The chart catalogue takes about 2.5 MB " +
                            "(about 16 MB to download). Chart PDFs download when opened and then work offline.",
                    ),
                    style = MaterialTheme.typography.bodySmall, color = c.ink2,
                )
                Spacer(Modifier.size(10.dp))
                Pill(loc("Pobierz katalog kart FAA", "Download FAA chart catalogue"), icon = Icons.Outlined.CloudDownload) { download(force = false) }
            }
            else -> {
                if (repo.isStale() && st !is ChartsStatus.Downloading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(loc("Nowy cykl ${repo.currentCycle()} — karty mogą być nieaktualne", "New cycle ${repo.currentCycle()} — charts may be outdated"), style = MaterialTheme.typography.bodySmall, color = c.caution, modifier = Modifier.weight(1f))
                        Pill(loc("Aktualizuj", "Update"), icon = Icons.Outlined.Refresh) { download(force = false) }
                    }
                    Spacer(Modifier.size(10.dp))
                }
                val list = charts
                when {
                    list == null && error == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    list.isNullOrEmpty() && error == null -> Text(loc("FAA nie publikuje kart dla tego lotniska.", "FAA publishes no charts for this airport."), style = MaterialTheme.typography.bodySmall, color = c.ink2)
                    !list.isNullOrEmpty() -> ChartList(list, filter, onFilter = { filter = it }, isDownloaded = repo::isDownloaded) { chart ->
                        if (onOpen != null) onOpen(chart) else open = chart
                    }
                }
            }
        }
        error?.let {
            Spacer(Modifier.size(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger)
        }
    }

    open?.let { chart -> ChartViewerDialog(chart, onClose = { open = null; reload++ }) }
}

@Composable
private fun ChartList(
    charts: List<Chart>,
    filter: ChartType?,
    onFilter: (ChartType?) -> Unit,
    isDownloaded: (Chart) -> Boolean,
    onOpen: (Chart) -> Unit,
) {
    val c = Fm.c
    val groups = ChartType.entries.mapNotNull { t -> charts.filter { it.type == t }.takeIf { it.isNotEmpty() }?.let { t to it } }
    // Filtr typów jako pigułki; aktywna to biały blok z czarnym tekstem
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TypePill(loc("Wszystkie", "All"), filter == null) { onFilter(null) }
        groups.forEach { (t, l) -> TypePill("${short(t)} ${l.size}", filter == t) { onFilter(if (filter == t) null else t) } }
    }
    Spacer(Modifier.size(8.dp))
    groups.filter { filter == null || it.first == filter }.forEach { (t, l) ->
        Text(t.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.ink2, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        Rule()
        l.forEach { chart ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(chart) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(chart.name, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(
                        chart.pdfs.size.takeIf { it > 1 }?.let { loc("$it strony", "$it pages") },
                        chart.amendment,
                        loc("zmieniona w tym cyklu", "changed this cycle").takeIf { chart.changed },
                    )
                    if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = c.ink2)
                }
                if (isDownloaded(chart)) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.DownloadDone, loc("Dostępna offline", "Available offline"), tint = c.ink2, modifier = Modifier.size(16.dp))
                }
            }
            Rule()
        }
    }
    Text(
        loc("Źródło: FAA d-TPP. Karty rzeczywiste — w symulatorze mogą różnić się od bazy nawigacyjnej MSFS.", "Source: FAA d-TPP. Real-world charts — may differ from the MSFS navigation database."),
        style = MaterialTheme.typography.labelSmall, color = c.ink2, modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun TypePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) scheme.onSecondaryContainer else Fm.c.ink,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) scheme.secondaryContainer else Fm.c.terrain)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

private fun short(t: ChartType) = when (t) {
    ChartType.APD -> "Diagram"
    ChartType.IAP -> "APP"
    ChartType.DP -> "SID"
    ChartType.STAR -> "STAR"
    ChartType.MIN -> "Minima"
    ChartType.HOT -> "Hot spots"
}
