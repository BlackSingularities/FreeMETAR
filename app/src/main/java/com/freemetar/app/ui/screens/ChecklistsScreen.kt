package com.freemetar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.data.AircraftChecklists
import com.freemetar.app.data.Checklist
import com.freemetar.app.data.ChecklistItem
import com.freemetar.app.domain.loc
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono

/**
 * Ekran interaktywnych checklistów.
 */
@Composable
fun ChecklistsScreen(
    aircraftChecklists: AircraftChecklists?,
    onBack: () -> Unit
) {
    var selectedChecklistId by remember { mutableStateOf<String?>(null) }
    var checkedItems by remember { mutableStateOf<Set<String>>(emptySet()) }

    val selectedChecklist = selectedChecklistId?.let { aircraftChecklists?.checklists?.get(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedChecklist != null) {
                            loc(selectedChecklist.name_pl, selectedChecklist.name_en)
                        } else {
                            loc("Checklisty", "Checklists")
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedChecklist != null) {
                            selectedChecklistId = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Outlined.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (aircraftChecklists == null) {
            // Brak checklistów dla tego samolotu
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        loc("Brak checklistów", "No checklists"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        loc(
                            "Checklisty nie są dostępne dla tego samolotu",
                            "Checklists are not available for this aircraft"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (selectedChecklist == null) {
            // Lista checklistów
            ChecklistsListView(
                aircraftChecklists = aircraftChecklists,
                onSelectChecklist = { id -> selectedChecklistId = id },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            // Szczegóły checklisty
            ChecklistDetailView(
                checklist = selectedChecklist,
                checkedItems = checkedItems,
                onToggleItem = { itemKey ->
                    checkedItems = if (itemKey in checkedItems) {
                        checkedItems - itemKey
                    } else {
                        checkedItems + itemKey
                    }
                },
                onResetChecklist = { checkedItems = emptySet() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun ChecklistsListView(
    aircraftChecklists: AircraftChecklists,
    onSelectChecklist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Informacja o samolocie
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    aircraftChecklists.aircraft,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    aircraftChecklists.manufacturer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Lista checklistów
        Text(
            loc("Dostępne checklisty", "Available checklists"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )

        aircraftChecklists.checklists.forEach { (id, checklist) ->
            ChecklistCard(
                checklist = checklist,
                onClick = { onSelectChecklist(id) }
            )
        }
    }
}

@Composable
private fun ChecklistCard(
    checklist: Checklist,
    onClick: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    loc(checklist.name_pl, checklist.name_en),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${checklist.items.size} " + loc("pozycji", "items"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChecklistDetailView(
    checklist: Checklist,
    checkedItems: Set<String>,
    onToggleItem: (String) -> Unit,
    onResetChecklist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Fm.c
    val completedCount = checklist.items.count { item ->
        "${checklist.name_en}_${item.item_en}" in checkedItems
    }
    val totalCount = checklist.items.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    Column(modifier) {
        // Progress bar
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$completedCount / $totalCount",
                    fontFamily = Mono,
                    fontSize = 14.sp,
                    color = c.ink
                )
                if (progress == 1f) {
                    Text(
                        loc("Ukończono", "Completed"),
                        fontSize = 14.sp,
                        color = Color(0xFF00FF00),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            if (completedCount > 0) {
                TextButton(onClick = onResetChecklist) {
                    Text(loc("Resetuj", "Reset"))
                }
            }
        }

        // Checklist items
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            checklist.items.forEach { item ->
                ChecklistItemRow(
                    item = item,
                    checked = "${checklist.name_en}_${item.item_en}" in checkedItems,
                    onToggle = { onToggleItem("${checklist.name_en}_${item.item_en}") }
                )
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val c = Fm.c

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) Color(0xFF1B3A1B) else c.surface)
            .border(
                1.dp,
                if (checked) Color(0xFF00FF00) else c.rule,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onToggle)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                loc(item.item_pl, item.item_en),
                fontWeight = FontWeight.Medium,
                color = if (checked) Color(0xFF00FF00) else c.ink
            )
            Text(
                item.expected,
                fontSize = 12.sp,
                color = if (checked) Color(0xFF88FF88) else c.ink2,
                fontFamily = Mono
            )
        }
        Icon(
            if (checked) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (checked) Color(0xFF00FF00) else c.ink2,
            modifier = Modifier.size(32.dp)
        )
    }
}
