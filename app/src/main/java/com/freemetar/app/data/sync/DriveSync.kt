package com.freemetar.app.data.sync

import android.content.Context
import com.freemetar.app.data.link.FlightArchive
import com.freemetar.app.data.link.FlightLog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Synchronizacja dziennika lotów z Google Drive jako głównym źródłem danych.
 *
 * Struktura na Drive:
 * /OpenTakeoff/
 *   ├── flights.oab          ← główny plik z danymi
 *   └── flights-backup.oab   ← poprzednia wersja (jeden backup)
 *
 * Logika:
 * - Po każdym zapisie/usunięciu lotu: lokalnie → Drive (główny plik)
 * - Przed zapisem nowy plik staje się backupem, nowy głównym
 * - Przy pierwszym uruchomieniu: sprawdź czy jest plik na Drive, jeśli tak - pobierz
 */
class DriveSync(private val context: Context) {

    companion object {
        private const val FOLDER_NAME = "OpenTakeoff"
        private const val FILE_NAME = "flights.oab"
        private const val BACKUP_NAME = "flights-backup.oab"
    }

    private var driveService: Drive? = null
    private var folderId: String? = null

    /** Auto-sync po każdym zapisie może odpalić kilka wysyłek naraz — wykonujemy je po kolei. */
    private val uploadLock = Mutex()

    /** Status synchronizacji */
    sealed class SyncStatus {
        data object NotSignedIn : SyncStatus()
        data object Syncing : SyncStatus()
        data class Success(val timestamp: Long) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    var lastSyncStatus: SyncStatus = SyncStatus.NotSignedIn
        private set

    /** Klient Google Sign-In */
    fun getSignInClient(): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    /** Inicjalizacja serwisu Drive po zalogowaniu */
    suspend fun initialize(account: GoogleSignInAccount): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_FILE)
            ).apply {
                selectedAccount = account.account
            }

            driveService = Drive.Builder(
                NetHttpTransport(),
                com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("OpenTakeoff")
                .build()

            // Znajdź lub utwórz folder OpenTakeoff
            folderId = findOrCreateFolder()
            lastSyncStatus = SyncStatus.Success(System.currentTimeMillis())
        }
    }

    /** Czy użytkownik jest zalogowany */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && driveService != null
    }

    /** Pobierz aktualnie zalogowane konto */
    fun getSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    /** Wylogowanie */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        getSignInClient().signOut()
        driveService = null
        folderId = null
        lastSyncStatus = SyncStatus.NotSignedIn
    }

    /** Znajdź lub utwórz folder OpenTakeoff */
    private suspend fun findOrCreateFolder(): String = withContext(Dispatchers.IO) {
        val service = driveService ?: throw IllegalStateException("Drive service not initialized")

        // Szukaj istniejącego folderu
        val query = "name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val result = service.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        if (result.files.isNotEmpty()) {
            return@withContext result.files.first().id
        }

        // Utwórz nowy folder
        val folder = File().apply {
            name = FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }

        val created = service.files().create(folder)
            .setFields("id")
            .execute()

        created.id
    }

    /** Sprawdź czy użytkownik ma już dane na Drive (przy pierwszym uruchomieniu) */
    suspend fun hasExistingData(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val service = driveService ?: return@withContext false
            val folder = folderId ?: return@withContext false

            val query = "name='$FILE_NAME' and '$folder' in parents and trashed=false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id)")
                .execute()

            result.files.isNotEmpty()
        }.getOrDefault(false)
    }

    /** Pobierz dane z Drive (przy pierwszym uruchomieniu lub przywracaniu) */
    suspend fun downloadFlights(): Result<List<FlightLog>> = withContext(Dispatchers.IO) {
        runCatching {
            lastSyncStatus = SyncStatus.Syncing

            val service = driveService ?: throw IllegalStateException("Not signed in")
            val folder = folderId ?: throw IllegalStateException("Folder not found")

            // Znajdź główny plik
            val query = "name='$FILE_NAME' and '$folder' in parents and trashed=false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            if (result.files.isEmpty()) {
                lastSyncStatus = SyncStatus.Success(System.currentTimeMillis())
                return@runCatching emptyList()
            }

            val fileId = result.files.first().id
            val outputStream = ByteArrayOutputStream()
            service.files().get(fileId).executeMediaAndDownloadTo(outputStream)

            val flights = FlightArchive.decode(outputStream.toByteArray())
            lastSyncStatus = SyncStatus.Success(System.currentTimeMillis())
            flights
        }.onFailure { e ->
            lastSyncStatus = SyncStatus.Error(e.message ?: "Unknown error")
        }
    }

    /** Wyślij dane na Drive (po każdym zapisie/usunięciu) */
    suspend fun uploadFlights(flights: List<FlightLog>): Result<Unit> = withContext(Dispatchers.IO) {
        uploadLock.withLock {
            runCatching {
                lastSyncStatus = SyncStatus.Syncing

                val service = driveService ?: throw IllegalStateException("Not signed in")
                val folder = folderId ?: throw IllegalStateException("Folder not found")

                val existing = findFiles(service, FILE_NAME, folder)
                val oldBackups = findFiles(service, BACKUP_NAME, folder)

                // Najpierw nowy plik główny — jeśli wysyłka padnie, poprzednie dane zostają nietknięte
                val fileMetadata = File().apply {
                    name = FILE_NAME
                    parents = listOf(folder)
                }
                service.files().create(fileMetadata, ByteArrayContent("application/octet-stream", FlightArchive.encode(flights)))
                    .setFields("id")
                    .execute()

                // Dopiero teraz poprzedni plik główny staje się backupem, a starszy backup znika
                existing.firstOrNull()?.let { prev ->
                    oldBackups.forEach { service.files().delete(it).execute() }
                    service.files().update(prev, File().apply { name = BACKUP_NAME }).execute()
                    existing.drop(1).forEach { service.files().delete(it).execute() }
                }

                lastSyncStatus = SyncStatus.Success(System.currentTimeMillis())
            }.onFailure { e ->
                lastSyncStatus = SyncStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun findFiles(service: Drive, name: String, folder: String): List<String> =
        service.files().list()
            .setQ("name='$name' and '$folder' in parents and trashed=false")
            .setSpaces("drive")
            .setFields("files(id)")
            .setOrderBy("modifiedTime desc")
            .execute()
            .files.orEmpty().map { it.id }

    /** Przywróć z backupu (jeśli użytkownik przypadkowo coś usunął) */
    suspend fun restoreFromBackup(): Result<List<FlightLog>> = withContext(Dispatchers.IO) {
        runCatching {
            lastSyncStatus = SyncStatus.Syncing

            val service = driveService ?: throw IllegalStateException("Not signed in")
            val folder = folderId ?: throw IllegalStateException("Folder not found")

            // Znajdź backup
            val query = "name='$BACKUP_NAME' and '$folder' in parents and trashed=false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            if (result.files.isEmpty()) {
                throw IllegalStateException("No backup found")
            }

            val fileId = result.files.first().id
            val outputStream = ByteArrayOutputStream()
            service.files().get(fileId).executeMediaAndDownloadTo(outputStream)

            val flights = FlightArchive.decode(outputStream.toByteArray())
            lastSyncStatus = SyncStatus.Success(System.currentTimeMillis())
            flights
        }.onFailure { e ->
            lastSyncStatus = SyncStatus.Error(e.message ?: "Unknown error")
        }
    }
}
