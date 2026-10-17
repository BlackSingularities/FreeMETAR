package com.freemetar.app.ui.components

import com.freemetar.app.ui.theme.Fm
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.BuildConfig
import com.freemetar.app.MainViewModel
import com.freemetar.app.UpdateState
import com.freemetar.app.data.UpdateInfo
import com.freemetar.app.data.Updater
import java.io.File

private fun sizeMb(b: Long) = if (b > 0) " (%.1f MB)".format(b / 1_048_576.0) else ""

/** Instalacja wymaga zgody „Instaluj nieznane aplikacje” — jeśli jej brak, prowadzimy do ustawień. */
@Composable
private fun rememberInstaller(): (File) -> Unit {
    val ctx = LocalContext.current
    return { file -> if (Updater.canInstall(ctx)) Updater.install(ctx, file) else Updater.openInstallPermission(ctx) }
}

/** Dialog przy starcie, gdy na GitHubie jest nowsza wersja. */
@Composable
fun UpdateDialog(vm: MainViewModel) {
    val state by vm.update.collectAsStateWithLifecycle()
    val dismissed by vm.updatePromptDismissed.collectAsStateWithLifecycle()
    val install = rememberInstaller()
    val ctx = LocalContext.current
    if (dismissed) return
    val info: UpdateInfo = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Ready -> s.info
        else -> return
    }
    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) vm.dismissUpdatePrompt() },
        icon = { Icon(Icons.Outlined.SystemUpdate, null) },
        title = { Text("Nowa wersja ${info.versionName}") },
        text = {
            Column {
                Text("Masz ${BuildConfig.VERSION_NAME}. Aktualizacja pobierze się z GitHuba${sizeMb(info.sizeBytes)}.", color = Fm.c.ink2, fontSize = 13.sp)
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        Text(info.notes, color = Fm.c.ink, fontSize = 13.sp)
                    }
                }
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        Spacer(Modifier.size(12.dp))
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        Text("Pobieranie… ${(s.progress * 100).toInt()}%", color = Fm.c.ink2, fontSize = 12.sp)
                    }
                    is UpdateState.Ready -> if (!Updater.canInstall(ctx)) {
                        Spacer(Modifier.size(10.dp))
                        Text("Zezwól FreeMETAR na instalowanie aplikacji, wróć i naciśnij „Zainstaluj” jeszcze raz.", color = Fm.c.caution, fontSize = 12.sp)
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateState.Available -> Button({ vm.downloadUpdate(s.info) }) { Text("Pobierz") }
                is UpdateState.Ready -> Button({ install(s.file) }) { Text("Zainstaluj") }
                else -> {}
            }
        },
        dismissButton = {
            if (state !is UpdateState.Downloading) TextButton({ vm.dismissUpdatePrompt() }) { Text("Później") }
        },
    )
}

/** Sekcja w ustawieniach: wersja, ręczne sprawdzenie, pobranie, instalacja. */
@Composable
fun UpdateSection(vm: MainViewModel) {
    val state by vm.update.collectAsStateWithLifecycle()
    val install = rememberInstaller()
    SectionCard("Aktualizacje", icon = Icons.Outlined.SystemUpdate) {
        Text("Zainstalowana wersja: ${BuildConfig.VERSION_NAME}", color = Fm.c.ink)
        Spacer(Modifier.size(6.dp))
        when (val s = state) {
            UpdateState.Idle -> Text("Sprawdzane automatycznie przy starcie.", color = Fm.c.ink2, fontSize = 13.sp)
            UpdateState.Checking -> Text("Sprawdzanie…", color = Fm.c.ink2, fontSize = 13.sp)
            UpdateState.UpToDate -> Text("Masz najnowszą wersję.", color = Fm.c.vfr, fontSize = 13.sp)
            is UpdateState.Error -> Text(s.message, color = Fm.c.ifr, fontSize = 13.sp)
            is UpdateState.Available -> Text("Dostępna wersja ${s.info.versionName}${sizeMb(s.info.sizeBytes)}", color = Fm.c.caution, fontSize = 13.sp)
            is UpdateState.Downloading -> {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Text("Pobieranie ${s.info.versionName}… ${(s.progress * 100).toInt()}%", color = Fm.c.ink2, fontSize = 12.sp)
            }
            is UpdateState.Ready -> Text("Wersja ${s.info.versionName} pobrana — gotowa do instalacji.", color = Fm.c.vfr, fontSize = 13.sp)
        }
        Spacer(Modifier.size(10.dp))
        Row {
            when (val s = state) {
                is UpdateState.Available -> Button({ vm.downloadUpdate(s.info) }) { Text("Pobierz i zainstaluj") }
                is UpdateState.Ready -> Button({ install(s.file) }) { Text("Zainstaluj") }
                is UpdateState.Downloading, UpdateState.Checking -> {}
                else -> OutlinedButton({ vm.checkForUpdate() }) { Text("Sprawdź teraz") }
            }
        }
    }
}
