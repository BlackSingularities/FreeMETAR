package com.freemetar.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val UA = "OpenAirBook-Android/1.0"

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            // aviationweather.gov zwraca 204 gdy brak danych
            if (resp.code == 204) return@withContext ""
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} dla $url")
            resp.body?.string().orEmpty()
        }
    }

    /** Pobiera plik do tymczasowego, potem atomowo podmienia docelowy. */
    suspend fun download(url: String, target: File) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} dla $url")
            val tmp = File(target.parentFile, target.name + ".part")
            resp.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
    }
}
