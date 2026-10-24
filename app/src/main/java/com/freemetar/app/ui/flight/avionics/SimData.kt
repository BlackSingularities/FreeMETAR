package com.freemetar.app.ui.flight.avionics

import com.freemetar.app.data.link.FlightState
import kotlin.math.atan

/**
 * Wypełnia [FlightData] z symulatora. Blok `avionics` (nowsze Link) daje nastawę ciśnienia, ILS, GPS,
 * dyrektor lotu i pełne tryby autopilota; bez niego wyświetlacze działają na podstawowych danych.
 */
fun FlightState.toFlightData(speedTrend: Float, terrain: TerrainGrid?): FlightData {
    val av = avionics
    val magVar = (av?.magVarDeg ?: attitude.hdgTrueDeg?.let { t -> attitude.hdgMagDeg?.let { wrap180((t - it).toFloat()).toDouble() } } ?: 0.0).toFloat()
    val hdg = (attitude.hdgMagDeg ?: attitude.hdgTrueDeg?.minus(magVar) ?: 0.0).toFloat()
    val gs = speed.gsKt?.toFloat() ?: Float.NaN
    val trk = attitude.trackTrueDeg?.let { wrap360(it.toFloat() - magVar) } ?: hdg
    val vs = (speed.vsFpm ?: 0.0).toFloat()
    val alt = (position.altIndFt ?: position.altMslFt)?.toFloat()
    val agl = position.altAglFt?.toFloat()
    val ra = av?.radioAltFt?.toFloat() ?: agl
    val lat = position.lat ?: 0.0
    val lon = position.lon ?: 0.0
    val baro = (av?.baroHpa ?: environment.qnhHpa)?.toFloat()?.takeIf { it > 800f } ?: 1013.25f
    val ap = autopilot
    val ex = av?.ap

    // Tryby FMA: najpierw blok avionics (GS, ramiona), potem podstawowe flagi Link v1
    val lateral = when {
        ex?.backCourse == true -> Fma.Lateral.BC
        ap.modes.loc || ap.modes.apr -> Fma.Lateral.LOC
        ap.modes.nav -> if (av?.gps?.drivesNav1 == true) Fma.Lateral.NAV_GPS else Fma.Lateral.NAV_VOR
        ap.modes.hdg -> Fma.Lateral.HDG
        ex?.wingLeveler == true || ap.master -> Fma.Lateral.ROL
        else -> null
    }
    val vertical = when {
        ex?.gsActive == true -> Fma.Vertical.GS
        ap.modes.alt -> Fma.Vertical.ALT
        ap.modes.flc -> Fma.Vertical.FLC
        ap.modes.vs -> Fma.Vertical.VS
        ex?.pitchHold == true || ap.master -> Fma.Vertical.PIT
        else -> null
    }
    val vArmed = buildList {
        if (ex?.altArmed == true && vertical != Fma.Vertical.ALT) add(Fma.Vertical.ALTS)
        if (ex?.gsArmed == true && vertical != Fma.Vertical.GS) add(Fma.Vertical.GS)
    }
    val fma = Fma(
        ap = ap.master, fd = ap.fd || av?.fd?.active == true, yd = ex?.yawDamper == true,
        // Bez bloku avionics nie wiadomo, czy automat ciągu trzyma prędkość — uzbrojony traktujemy jak aktywny
        athrArmed = ap.athr, athrActive = ap.athr && (ex == null || ex.airspeedHold || ex.machHold),
        toga = ex?.toga == true,
        speed = when { ex?.machHold == true -> Fma.SpeedMode.MACH; ex?.airspeedHold == true || ap.athr -> Fma.SpeedMode.SPD; else -> null },
        lateral = lateral,
        lateralArmed = if (ap.modes.apr && lateral != Fma.Lateral.LOC) Fma.Lateral.LOC else null,
        vertical = vertical, verticalArmed = vArmed,
    )

    // Źródło nawigacji: GPS (gdy prowadzi NAV1 albo nie ma sygnału radiowego), inaczej NAV1 (LOC/VOR)
    val n1 = av?.nav1
    val g = av?.gps
    val gpsNav = g != null && g.activeFlightPlan && (g.drivesNav1 || n1?.hasNav != true)
    val nav = when {
        gpsNav -> NavInfo(
            source = NavInfo.Source.GPS, ident = g!!.nextId, course = g.desiredTrackMagDeg?.toFloat() ?: Float.NaN,
            // XTK dodatni = samolot na prawo od kursu → kurs w lewo; pełna skala ±5 NM (en route), ±1 NM w podejściu
            cdi = g.crossTrackNm?.let { (-it / (if (g.approachActive) 1.0 else 5.0) * 2.5).coerceIn(-2.5, 2.5).toFloat() } ?: Float.NaN,
            gs = if (n1?.hasGs == true) n1.gsDots?.toFloat() ?: Float.NaN else Float.NaN,
            dmeNm = g.nextDistNm?.toFloat() ?: Float.NaN, toFrom = 1,
        )
        n1?.hasNav == true -> NavInfo(
            source = if (n1.hasLoc) NavInfo.Source.LOC else NavInfo.Source.VOR, ident = n1.ident?.takeIf { it.isNotBlank() },
            course = (if (n1.hasLoc) n1.locCourseDeg else n1.obsDeg)?.toFloat() ?: Float.NaN,
            cdi = n1.cdiDots?.toFloat() ?: Float.NaN,
            gs = if (n1.hasGs) n1.gsDots?.toFloat() ?: Float.NaN else Float.NaN,
            dmeNm = if (n1.hasDme) n1.dmeNm?.toFloat() ?: Float.NaN else Float.NaN,
            toFrom = when (n1.toFrom) { "to" -> 1; "from" -> 2; else -> 0 }, loc = n1.hasLoc,
        )
        else -> NavInfo()
    }
    val n2 = av?.nav2
    val adf = av?.adf
    val sp = av?.speeds
    fun pos(v: Double?) = v?.takeIf { it > 1 }?.toFloat() ?: Float.NaN

    return FlightData(
        pitch = (attitude.pitchDeg ?: 0.0).toFloat(), roll = (attitude.bankDeg ?: 0.0).toFloat(),
        heading = wrap360(hdg), attValid = attitude.pitchDeg != null, headingMagnetic = true,
        slip = ((av?.slipBall ?: 0.0) * 0.25).toFloat(),
        speedKt = (speed.iasKt ?: 0.0).toFloat(), speedValid = speed.iasKt != null, speedTrend = speedTrend,
        altFt = alt ?: 0f, altValid = alt != null, altFromBaro = true,
        vsFpm = vs, track = trk, hasTrack = !gs.isNaN() && gs > 30f,
        fpa = if (!gs.isNaN() && gs > 30f) Math.toDegrees(atan(vs / (gs * 101.27)).toDouble()).toFloat() else 0f,
        qnh = baro, qnhSource = "SIM", std = av?.baroStd == true, inHg = usesInHg(lat, lon), fpv = true,
        gpsAccuracy = 0f, sats = 0, gpsFix = position.lat != null,
        raFt = ra ?: 0f, raValid = ra != null,
        terrainFt = if (alt != null && agl != null) alt - agl else Float.NaN, terrain = terrain,
        lat = lat, lon = lon, mslFt = (position.altMslFt ?: 0.0).toFloat(),
        windDir = environment.windDirDeg?.let { wrap360(it.toFloat() - magVar) } ?: 0f,
        windKt = (environment.windSpdKt ?: 0.0).toFloat(), windValid = environment.windSpdKt != null && environment.windDirDeg != null,
        tasKt = speed.tasKt?.toFloat() ?: Float.NaN, oatC = environment.oatC?.toFloat() ?: Float.NaN,
        drift = angDiff(trk, hdg), gLoad = (av?.gForce ?: 1.0).toFloat(), magVar = magVar,

        gsKt = gs, mach = speed.mach?.toFloat() ?: Float.NaN, onGround = aircraft.onGround == true,
        spdBug = ap.spdKt?.takeIf { it > 0 }?.toFloat() ?: Float.NaN,
        machBug = ex?.machVar?.takeIf { ex.machHold && it > 0 }?.toFloat() ?: Float.NaN,
        altBug = ap.altFt?.toFloat() ?: Float.NaN,
        hdgBug = ap.hdgDeg?.toFloat() ?: Float.NaN,
        vsBug = ap.vsFpm?.takeIf { ap.modes.vs }?.toFloat() ?: Float.NaN,
        fma = fma,
        fdOn = fma.fd && av?.fd != null,
        fdPitch = av?.fd?.pitchDeg?.toFloat() ?: Float.NaN, fdRoll = av?.fd?.bankDeg?.toFloat() ?: Float.NaN,
        nav = nav,
        gps = if (g == null) GpsInfo() else GpsInfo(
            active = g.activeFlightPlan, nextId = g.nextId?.takeIf { it.isNotBlank() }, prevId = g.prevId?.takeIf { it.isNotBlank() },
            nextDistNm = g.nextDistNm?.toFloat() ?: Float.NaN, nextBrg = g.nextBearingMagDeg?.toFloat() ?: Float.NaN,
            dtk = g.desiredTrackMagDeg?.toFloat() ?: Float.NaN, xtkNm = g.crossTrackNm?.toFloat() ?: Float.NaN,
            nextEteSec = g.nextEteSec?.toFloat() ?: Float.NaN, destEteSec = g.destEteSec?.toFloat() ?: Float.NaN,
            approach = g.approachActive,
            nextLat = g.nextLat.takeIf { valid(g.nextLat, g.nextLon) }, nextLon = g.nextLon.takeIf { valid(g.nextLat, g.nextLon) },
            prevLat = g.prevLat.takeIf { valid(g.prevLat, g.prevLon) }, prevLon = g.prevLon.takeIf { valid(g.prevLat, g.prevLon) },
            wpIndex = g.wpIndex ?: 0, wpCount = g.wpCount ?: 0,
        ),
        // NAV RADIAL to radial od stacji; namiar na stację = radial + 180
        brg2 = if (n2?.hasNav == true && n2.hasLoc.not()) n2.radialDeg?.let { wrap360(it.toFloat() + 180f) } ?: Float.NaN else Float.NaN,
        brgAdf = if (adf?.hasSignal == true) adf.relBearingDeg?.let { wrap360(it.toFloat() + hdg) } ?: Float.NaN else Float.NaN,
        minimumsFt = av?.decisionHeightFt?.takeIf { it > 0 }?.toFloat() ?: Float.NaN,
        marker = when (av?.marker) { "outer" -> 1; "middle" -> 2; "inner" -> 3; else -> 0 },
        vs0 = pos(sp?.vs0Kt), vs1 = pos(sp?.vs1Kt), vc = pos(sp?.vcKt), vmo = pos(sp?.vmoKt),
        stallWarn = av?.stallWarning == true, overspeedWarn = av?.overspeedWarning == true,
        turnRate = av?.turnRateDps?.toFloat() ?: Float.NaN,
        aiPitch = av?.attIndPitchDeg?.toFloat() ?: Float.NaN, aiRoll = av?.attIndBankDeg?.toFloat() ?: Float.NaN,
        hiHeading = av?.hdgIndDeg?.toFloat() ?: Float.NaN, suction = av?.suctionInHg?.toFloat() ?: Float.NaN,
        com1 = radios.com1?.activeMhz?.toFloat() ?: Float.NaN, com1Stby = radios.com1?.standbyMhz?.toFloat() ?: Float.NaN,
        nav1 = radios.nav1?.activeMhz?.toFloat() ?: Float.NaN, nav1Stby = radios.nav1?.standbyMhz?.toFloat() ?: Float.NaN,
        squawk = radios.transponder?.code, xpdrMode = radios.transponder?.mode,
        gearDown = controls.gearDown, flapsIndex = controls.flapsIndex, flapsPositions = controls.flapsPositions,
        spoilersPct = controls.spoilersPct?.toFloat() ?: Float.NaN,
        simTime = simTimeUtc?.let { t -> Regex("""T(\d{2}):(\d{2})""").find(t)?.let { "${it.groupValues[1]}${it.groupValues[2]}Z" } },
    )
}

/** Pozycja punktu GPS: MSFS podaje 0/0, gdy punktu nie ma. */
private fun valid(lat: Double?, lon: Double?) = lat != null && lon != null && (lat != 0.0 || lon != 0.0)

/** Cale słupa rtęci jak w Ameryce Północnej (USA, Kanada), w pozostałych regionach hektopaskale. */
internal fun usesInHg(lat: Double, lon: Double): Boolean = lat in 14.0..72.0 && lon in -170.0..-50.0


/**
 * Bieżący odcinek planu lotu z GPS symulatora (poprzedni → następny punkt) jako punkty trasy dla ND/MFD.
 * Pusta lista, gdy symulator nie ma aktywnego planu albo nie podaje pozycji punktów.
 */
fun FlightState.gpsLeg(): List<com.freemetar.app.data.RoutePoint> {
    val g = avionics?.gps ?: return emptyList()
    if (!g.activeFlightPlan) return emptyList()
    return buildList {
        if (valid(g.prevLat, g.prevLon)) add(com.freemetar.app.data.RoutePoint(g.prevId?.takeIf { it.isNotBlank() } ?: "", g.prevLat, g.prevLon))
        if (valid(g.nextLat, g.nextLon)) add(com.freemetar.app.data.RoutePoint(g.nextId?.takeIf { it.isNotBlank() } ?: "", g.nextLat, g.nextLon))
    }
}
