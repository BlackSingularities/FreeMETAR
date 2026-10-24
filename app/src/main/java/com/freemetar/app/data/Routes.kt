package com.freemetar.app.data

import kotlinx.serialization.Serializable

/** Punkt na trasie (np. odcinek GPS z symulatora na wyświetlaczu nawigacyjnym). */
@Serializable
data class RoutePoint(
    val ident: String,
    val lat: Double? = null,
    val lon: Double? = null,
    /** airport, vor, ndb, fix, toc, tod… */
    val type: String = "fix",
    val altitudeFt: Int? = null,
    val windDir: Int? = null,
    val windKt: Int? = null,
    val oatC: Int? = null,
    val airway: String? = null,
    /** Ograniczenie wysokości z procedury, np. „5000A”. */
    val constraint: String? = null,
    /** Odcinek trasy: sid / enroute / star / approach (null = enroute dla starszych plików). */
    val segment: String? = null,
) {
    val hasPosition get() = lat != null && lon != null
}
