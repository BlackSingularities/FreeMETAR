package com.freemetar.app.data.link

import java.time.Instant

/** Eksport nagranego lotu do formatów wymiany. */
object FlightExport {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** GPX 1.1: jeden ślad z wysokością w metrach i czasem UTC każdego punktu. */
    fun gpx(f: FlightLog): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="OpenTakeoff" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        val name = esc("${f.from ?: "?"} - ${f.to ?: "?"}" + (f.aircraft?.let { " ($it)" } ?: ""))
        append("  <trk><name>").append(name).append("</name><trkseg>\n")
        f.track.forEach { p ->
            append("""    <trkpt lat="%.6f" lon="%.6f"><ele>%.1f</ele><time>%s</time></trkpt>""".format(
                java.util.Locale.US, p.lat, p.lon, p.altFt * 0.3048, Instant.ofEpochMilli(p.t).toString(),
            )).append('\n')
        }
        append("  </trkseg></trk>\n</gpx>\n")
    }
}
