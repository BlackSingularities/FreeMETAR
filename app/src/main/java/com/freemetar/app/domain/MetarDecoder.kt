package com.freemetar.app.domain

/** Jedna rozkodowana grupa METAR/TAF: oryginalny token i opis po polsku. */
data class Decoded(val token: String, val category: Category, val text: String)

enum class Category { STATION, TIME, WIND, VISIBILITY, RVR, WEATHER, CLOUDS, TEMP, PRESSURE, TREND, REMARK, OTHER }

object MetarDecoder {

    // Słowniki jako właściwości z getterem — budowane przy każdym użyciu, więc zawsze w bieżącym języku
    private val intensity get() = mapOf("-" to loc("słaby", "light"), "+" to loc("silny", "heavy"), "VC" to loc("w pobliżu", "in the vicinity"))
    private val descriptors get() = mapOf(
        "MI" to loc("płytki", "shallow"), "PR" to loc("częściowy", "partial"), "BC" to loc("płaty", "patches of"), "DR" to loc("zamieć niska", "low drifting"),
        "BL" to loc("zamieć wysoka", "blowing"), "SH" to loc("przelotny", "showers of"), "TS" to loc("burza", "thunderstorm with"), "FZ" to loc("marznący", "freezing"),
    )
    private val phenomena get() = mapOf(
        "DZ" to loc("mżawka", "drizzle"), "RA" to loc("deszcz", "rain"), "SN" to loc("śnieg", "snow"), "SG" to loc("śnieg ziarnisty", "snow grains"),
        "IC" to loc("słupki lodowe", "ice crystals"), "PL" to loc("deszcz lodowy", "ice pellets"), "GR" to loc("grad", "hail"), "GS" to loc("krupa", "small hail"),
        "UP" to loc("nieznany opad", "unknown precipitation"), "BR" to loc("zamglenie", "mist"), "FG" to loc("mgła", "fog"), "FU" to loc("dym", "smoke"),
        "VA" to loc("pył wulkaniczny", "volcanic ash"), "DU" to loc("pył", "dust"), "SA" to loc("piasek", "sand"), "HZ" to loc("zmętnienie", "haze"),
        "PY" to loc("pył wodny", "spray"), "PO" to loc("wiry pyłowe", "dust whirls"), "SQ" to loc("szkwał", "squalls"), "FC" to loc("lej/trąba", "funnel cloud"),
        "SS" to loc("burza piaskowa", "sandstorm"), "DS" to loc("burza pyłowa", "dust storm"),
    )
    private val cover get() = mapOf(
        "FEW" to loc("nieliczne (1–2/8)", "few (1–2/8)"), "SCT" to loc("rozproszone (3–4/8)", "scattered (3–4/8)"),
        "BKN" to loc("poprzerywane (5–7/8)", "broken (5–7/8)"), "OVC" to loc("całkowite (8/8)", "overcast (8/8)"), "VV" to loc("widzialność pionowa", "vertical visibility"),
    )

    private val reTime = Regex("""^(\d{2})(\d{2})(\d{2})Z$""")
    private val reWind = Regex("""^(\d{3}|VRB)(\d{2,3})(G(\d{2,3}))?(KT|MPS|KMH)$""")
    private val reWindVar = Regex("""^(\d{3})V(\d{3})$""")
    private val reVis = Regex("""^(\d{4})(NDV|N|NE|E|SE|S|SW|W|NW)?$""")
    private val reVisSm = Regex("""^([MP]?)(\d+ )?(\d+)?(/?\d*)SM$""")
    private val reSmFraction = Regex("""^\d/\d{1,2}SM$""")
    private val reRvr = Regex("""^R(\d{2}[LCR]?)/([PM]?)(\d{4})(V([PM]?)(\d{4}))?(FT)?([UDN])?$""")
    private val reWx = Regex("""^(-|\+|VC)?(MI|PR|BC|DR|BL|SH|TS|FZ)?((DZ|RA|SN|SG|IC|PL|GR|GS|UP|BR|FG|FU|VA|DU|SA|HZ|PY|PO|SQ|FC|SS|DS)+)?$""")
    private val reCloud = Regex("""^(FEW|SCT|BKN|OVC|VV)(\d{3}|///)(CB|TCU|///)?$""")
    private val reTemp = Regex("""^(M?\d{2})/(M?\d{2})?$""")
    private val reQ = Regex("""^Q(\d{4})$""")
    private val reA = Regex("""^A(\d{4})$""")
    private val reRecent = Regex("""^RE([A-Z]{2,6})$""")
    private val reWs = Regex("""^WS$""")
    private val reRwyState = Regex("""^R?(\d{2}[LCR]?)/(\d|/)(\d|/)(\d\d|//)(\d\d|//)$""")
    private val reTafValid = Regex("""^(\d{2})(\d{2})/(\d{2})(\d{2})$""")
    private val reFm = Regex("""^FM(\d{2})(\d{2})(\d{2})$""")
    private val reProb = Regex("""^PROB(\d{2})$""")

    /** Dzieli depeszę na grupy; amerykańskie "1 1/2SM" zostaje jedną grupą. */
    fun tokenize(raw: String): List<String> {
        val parts = raw.trim().replace("=", "").split(Regex("\\s+")).filter { it.isNotBlank() }
        val out = ArrayList<String>(parts.size)
        parts.forEachIndexed { i, t ->
            if (i > 0 && reSmFraction.matches(t) && parts[i - 1].length <= 2 && parts[i - 1].all { it.isDigit() } && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + " " + t
            } else out += t
        }
        return out
    }

    fun decode(raw: String): List<Decoded> {
        val tokens = tokenize(raw)
        val out = ArrayList<Decoded>()
        var inRemarks = false
        tokens.forEachIndexed { i, t ->
            if (inRemarks) {
                out += Decoded(t, Category.REMARK, remark(t)); return@forEachIndexed
            }
            out += when {
                i == 0 && (t == "METAR" || t == "SPECI" || t == "TAF") ->
                    Decoded(t, Category.OTHER, when (t) { "SPECI" -> loc("Depesza specjalna (zmiana warunków)", "Special report (conditions changed)"); "TAF" -> loc("Prognoza lotniskowa", "Terminal aerodrome forecast"); else -> loc("Rutynowa depesza METAR", "Routine METAR report") })
                t == "AMD" -> Decoded(t, Category.OTHER, loc("Prognoza poprawiona", "Amended forecast"))
                (i <= 1 || (i == 2 && (tokens[1] == "AMD" || tokens[1] == "COR"))) && t.length == 4 && t.all { it.isLetterOrDigit() } && t.first().isLetter() && out.none { it.category == Category.STATION } ->
                    Decoded(t, Category.STATION, loc("Lotnisko $t", "Airport $t"))
                reTime.matches(t) -> reTime.find(t)!!.destructured.let { (d, h, m) -> Decoded(t, Category.TIME, loc("Dzień $d, godz. $h:$m UTC", "Day $d, $h:$m UTC")) }
                t == "AUTO" -> Decoded(t, Category.OTHER, loc("Depesza automatyczna (bez obserwatora)", "Automated report (no observer)"))
                t == "COR" -> Decoded(t, Category.OTHER, loc("Depesza skorygowana", "Corrected report"))
                t == "NIL" -> Decoded(t, Category.OTHER, loc("Brak depeszy", "No report"))
                reWind.matches(t) -> wind(t)
                t == "00000KT" -> Decoded(t, Category.WIND, loc("Cisza", "Calm"))
                reWindVar.matches(t) -> reWindVar.find(t)!!.destructured.let { (a, b) -> Decoded(t, Category.WIND, loc("Kierunek zmienny od $a° do $b°", "Direction varying between $a° and $b°")) }
                t == "CAVOK" -> Decoded(t, Category.VISIBILITY, loc("CAVOK — widzialność ≥10 km, brak chmur poniżej 5000 ft i CB, brak zjawisk", "CAVOK — visibility ≥10 km, no cloud below 5000 ft and no CB, no significant weather"))
                t == "9999" -> Decoded(t, Category.VISIBILITY, loc("Widzialność 10 km lub więcej", "Visibility 10 km or more"))
                // Po temperaturze 4 cyfry to widzialność tylko w trendzie (TEMPO 4000)
                reVis.matches(t) && out.lastOrNull { it.category == Category.TEMP || it.category == Category.TREND }?.category != Category.TEMP ->
                    reVis.find(t)!!.destructured.let { (v, dir) ->
                        val m = v.toInt()
                        val txt = if (m >= 9999) loc("10 km lub więcej", "10 km or more") else if (m >= 5000) "${m / 1000} km" else "$m m"
                        Decoded(t, Category.VISIBILITY, when (dir) {
                            "" -> loc("Widzialność $txt", "Visibility $txt")
                            "NDV" -> loc("Widzialność $txt (brak zmienności kierunkowej)", "Visibility $txt (no directional variation)")
                            else -> loc("Minimalna widzialność $txt w kierunku $dir", "Minimum visibility $txt towards $dir")
                        })
                    }
                reVisSm.matches(t) -> Decoded(
                    t, Category.VISIBILITY,
                    when (t.first()) { 'P' -> loc("Widzialność powyżej ", "Visibility above "); else -> loc("Widzialność ", "Visibility ") } +
                        t.removeSuffix("SM").trimStart('M', 'P') + loc(" mil lądowych", " statute miles") + if (t.startsWith("M")) loc(" (mniej niż)", " (less than)") else "",
                )
                reRvr.matches(t) -> rvr(t)
                reRwyState.matches(t) && t.contains("/") && t.length >= 8 -> Decoded(t, Category.OTHER, loc("Stan nawierzchni pasa ${reRwyState.find(t)!!.groupValues[1]}", "Runway ${reRwyState.find(t)!!.groupValues[1]} surface condition"))
                t == "NSC" -> Decoded(t, Category.CLOUDS, loc("Brak znaczących chmur", "No significant cloud"))
                t == "NCD" -> Decoded(t, Category.CLOUDS, loc("Nie wykryto chmur (stacja automatyczna)", "No cloud detected (automated station)"))
                t == "SKC" || t == "CLR" -> Decoded(t, Category.CLOUDS, loc("Niebo bezchmurne", "Sky clear"))
                t == "NSW" -> Decoded(t, Category.WEATHER, loc("Ustąpienie zjawisk", "No significant weather"))
                reCloud.matches(t) -> cloud(t)
                reTemp.matches(t) -> reTemp.find(t)!!.destructured.let { (a, b) ->
                    val tt = a.replace("M", "-").toInt()
                    val dd = b.takeIf { it.isNotEmpty() }?.replace("M", "-")?.toInt()
                    Decoded(t, Category.TEMP, loc("Temperatura $tt °C", "Temperature $tt °C") + (dd?.let { loc(", punkt rosy $it °C (różnica ${tt - it}°)", ", dew point $it °C (spread ${tt - it}°)") } ?: ""))
                }
                reQ.matches(t) -> Decoded(t, Category.PRESSURE, "QNH ${t.drop(1).toInt()} hPa (${Atmo.hpaToInHg(t.drop(1).toDouble()).fmt(2)} inHg)")
                reA.matches(t) -> t.drop(1).toInt().let { v -> Decoded(t, Category.PRESSURE, loc("Altymetr", "Altimeter") + " ${v / 100}." + "%02d".format(v % 100) + " inHg (${Atmo.inHgToHpa(v / 100.0).fmt()} hPa)") }
                reRecent.matches(t) -> Decoded(t, Category.WEATHER, loc("Niedawno: ", "Recent: ") + weatherText(t.drop(2)))
                reWs.matches(t) -> Decoded(t, Category.WIND, loc("Uskok wiatru", "Wind shear"))
                t == "NOSIG" -> Decoded(t, Category.TREND, loc("Bez istotnych zmian w ciągu 2 h", "No significant change expected within 2 h"))
                t == "BECMG" -> Decoded(t, Category.TREND, loc("Stopniowo zmieni się na:", "Becoming:"))
                t == "TEMPO" -> Decoded(t, Category.TREND, loc("Okresowo:", "Temporarily:"))
                t == "INTER" -> Decoded(t, Category.TREND, loc("Krótkotrwale:", "Intermittently:"))
                reProb.matches(t) -> Decoded(t, Category.TREND, loc("Prawdopodobieństwo ${t.drop(4)}%", "Probability ${t.drop(4)}%"))
                reFm.matches(t) -> reFm.find(t)!!.destructured.let { (d, h, m) -> Decoded(t, Category.TREND, loc("Od dnia $d, $h:$m UTC:", "From day $d, $h:$m UTC:")) }
                reTafValid.matches(t) -> reTafValid.find(t)!!.destructured.let { (d1, h1, d2, h2) -> Decoded(t, Category.TIME, loc("Ważna od dnia $d1 $h1:00 do dnia $d2 $h2:00 UTC", "Valid from day $d1 $h1:00 to day $d2 $h2:00 UTC")) }
                t.startsWith("TX") || t.startsWith("TN") -> Decoded(t, Category.TEMP, (if (t.startsWith("TX")) loc("Maks. ", "Max ") else loc("Min. ", "Min ")) + loc("temperatura ", "temperature ") + t.drop(2).substringBefore("/").replace("M", "-") + " °C")
                t == "RMK" -> { inRemarks = true; Decoded(t, Category.REMARK, loc("Uwagi:", "Remarks:")) }
                t.startsWith("AT") || t.startsWith("TL") -> Decoded(t, Category.TREND, loc("Czas: ${t.drop(2)} UTC", "Time: ${t.drop(2)} UTC"))
                t != "" && reWx.matches(t) && t.length >= 2 -> Decoded(t, Category.WEATHER, weatherText(t))
                else -> Decoded(t, Category.OTHER, "—")
            }
        }
        return out
    }

    private fun wind(t: String): Decoded {
        val (dir, spd, _, gust, unit) = reWind.find(t)!!.destructured
        val u = when (unit) { "MPS" -> "m/s"; "KMH" -> "km/h"; else -> "kt" }
        val speed = spd.toInt()
        if (speed == 0) return Decoded(t, Category.WIND, loc("Cisza", "Calm"))
        val d = if (dir == "VRB") loc("zmienny", "variable") else loc("z $dir°", "from $dir°")
        val g = if (gust.isNotEmpty()) loc(", w porywach ${gust.toInt()} $u", ", gusting ${gust.toInt()} $u") else ""
        return Decoded(t, Category.WIND, loc("Wiatr $d, $speed $u$g", "Wind $d, $speed $u$g"))
    }

    private fun rvr(t: String): Decoded {
        val m = reRvr.find(t)!!.groupValues
        val trend = when (m[8]) { "U" -> loc(", rośnie", ", increasing"); "D" -> loc(", maleje", ", decreasing"); "N" -> loc(", bez zmian", ", no change"); else -> "" }
        val pre = when (m[2]) { "P" -> loc("powyżej ", "above "); "M" -> loc("poniżej ", "below "); else -> "" }
        val unit = if (m[7] == "FT") "ft" else "m"
        val range = if (m[4].isNotEmpty()) " – ${m[6].toInt()}" else ""
        return Decoded(t, Category.RVR, loc("RVR pas ${m[1]}: $pre${m[3].toInt()}$range $unit$trend", "RVR runway ${m[1]}: $pre${m[3].toInt()}$range $unit$trend"))
    }

    private fun cloud(t: String): Decoded {
        val (c, h, type) = reCloud.find(t)!!.destructured
        val height = h.toIntOrNull()?.let { "${it * 100} ft" } ?: loc("wysokość nieznana", "height unknown")
        val ty = when (type) { "CB" -> " — cumulonimbus!"; "TCU" -> loc(" — cumulus wypiętrzony", " — towering cumulus"); else -> "" }
        return Decoded(t, Category.CLOUDS, loc("${cover[c]?.replaceFirstChar { it.uppercase() }} na $height$ty", "${cover[c]?.replaceFirstChar { it.uppercase() }} at $height$ty"))
    }

    /** Czy token jest grupą zjawisk (np. -SHRA, TSRA, BR). */
    fun isWeather(t: String) = t.length >= 2 && reWx.matches(t) && t != "NSW" &&
        (phenomena.keys.any { t.contains(it) } || t.endsWith("TS"))

    private val phenomenaEn = mapOf(
        "DZ" to "drizzle", "RA" to "rain", "SN" to "snow", "SG" to "snow grains", "IC" to "ice crystals", "PL" to "ice pellets",
        "GR" to "hail", "GS" to "small hail", "BR" to "mist", "FG" to "fog", "FU" to "smoke", "HZ" to "haze", "DU" to "dust",
        "SA" to "sand", "SQ" to "squalls", "FC" to "funnel cloud", "SS" to "sandstorm", "DS" to "duststorm", "VA" to "volcanic ash",
    )
    private val descriptorsEn = mapOf(
        "MI" to "shallow", "PR" to "partial", "BC" to "patches of", "DR" to "low drifting", "BL" to "blowing",
        "SH" to "showers of", "TS" to "thunderstorm with", "FZ" to "freezing",
    )

    /** Zjawiska po angielsku dla komunikatu ATIS, np. "-SHRA BR" → "light showers of rain, mist". */
    fun weatherTextEn(wx: String): String = wx.split(" ").filter { it.isNotBlank() }.joinToString(", ") { t ->
        var rest = t
        val parts = mutableListOf<String>()
        when {
            rest.startsWith("+") -> { parts += "heavy"; rest = rest.drop(1) }
            rest.startsWith("-") -> { parts += "light"; rest = rest.drop(1) }
            rest.startsWith("VC") -> { parts += "in the vicinity"; rest = rest.drop(2) }
        }
        descriptorsEn.keys.firstOrNull { rest.startsWith(it) }?.let { parts += descriptorsEn[it]!!; rest = rest.removePrefix(it) }
        rest.chunked(2).forEach { phenomenaEn[it]?.let(parts::add) }
        parts.joinToString(" ").removeSuffix(" with").removeSuffix(" of").ifBlank { t }
    }.replaceFirstChar { it.uppercase() }

    fun weatherText(t: String): String {
        var rest = t
        val parts = mutableListOf<String>()
        listOf("+", "-", "VC").firstOrNull { rest.startsWith(it) }?.let { parts += intensity[it]!!; rest = rest.removePrefix(it) }
        descriptors.keys.firstOrNull { rest.startsWith(it) }?.let { parts += descriptors[it]!!; rest = rest.removePrefix(it) }
        rest.chunked(2).forEach { phenomena[it]?.let(parts::add) }
        // „showers of” / „thunderstorm with” bez dalszego zjawiska — obcinamy łącznik
        return parts.joinToString(" ").removeSuffix(" with").removeSuffix(" of").replaceFirstChar { it.uppercase() }.ifBlank { t }
    }

    private fun remark(t: String) = when {
        t == "AO2" -> loc("Stacja automatyczna z czujnikiem opadów", "Automated station with precipitation sensor")
        t == "AO1" -> loc("Stacja automatyczna bez czujnika opadów", "Automated station without precipitation sensor")
        t.startsWith("SLP") -> loc("Ciśnienie zredukowane: ", "Sea-level pressure: ") + t.drop(3).toIntOrNull()?.let { (if (it < 500) 1000 + it / 10.0 else 900 + it / 10.0).fmt(1) + " hPa" }.orEmpty()
        t.matches(Regex("""T[01]\d{3}[01]\d{3}""")) -> {
            fun v(s: String) = (if (s[0] == '1') -1 else 1) * s.drop(1).toInt() / 10.0
            loc("Dokładnie: temp. ${v(t.substring(1, 5))} °C, rosa ${v(t.substring(5, 9))} °C", "Precisely: temp ${v(t.substring(1, 5))} °C, dew point ${v(t.substring(5, 9))} °C")
        }
        else -> ""
    }

    /** Dzieli TAF na linie okresów (FM/BECMG/TEMPO/PROB). */
    fun splitTaf(raw: String): List<String> {
        val tokens = raw.trim().split(Regex("\\s+"))
        val lines = mutableListOf(StringBuilder())
        tokens.forEachIndexed { i, t ->
            val starts = reFm.matches(t) || t == "BECMG" || (t == "TEMPO" && i > 0 && !reProb.matches(tokens[i - 1])) || reProb.matches(t)
            if (starts && lines.last().isNotEmpty()) lines += StringBuilder()
            if (lines.last().isNotEmpty()) lines.last().append(' ')
            lines.last().append(t)
        }
        return lines.map { it.toString() }
    }
}
