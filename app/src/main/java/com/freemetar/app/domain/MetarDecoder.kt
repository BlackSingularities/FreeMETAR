package com.freemetar.app.domain

/** Jedna rozkodowana grupa METAR/TAF: oryginalny token i opis po polsku. */
data class Decoded(val token: String, val category: Category, val text: String)

enum class Category { STATION, TIME, WIND, VISIBILITY, RVR, WEATHER, CLOUDS, TEMP, PRESSURE, TREND, REMARK, OTHER }

object MetarDecoder {

    private val intensity = mapOf("-" to "słaby", "+" to "silny", "VC" to "w pobliżu")
    private val descriptors = mapOf(
        "MI" to "płytki", "PR" to "częściowy", "BC" to "płaty", "DR" to "zamieć niska",
        "BL" to "zamieć wysoka", "SH" to "przelotny", "TS" to "burza", "FZ" to "marznący",
    )
    private val phenomena = mapOf(
        "DZ" to "mżawka", "RA" to "deszcz", "SN" to "śnieg", "SG" to "śnieg ziarnisty",
        "IC" to "słupki lodowe", "PL" to "deszcz lodowy", "GR" to "grad", "GS" to "krupa",
        "UP" to "nieznany opad", "BR" to "zamglenie", "FG" to "mgła", "FU" to "dym",
        "VA" to "pył wulkaniczny", "DU" to "pył", "SA" to "piasek", "HZ" to "zmętnienie",
        "PY" to "pył wodny", "PO" to "wiry pyłowe", "SQ" to "szkwał", "FC" to "lej/trąba",
        "SS" to "burza piaskowa", "DS" to "burza pyłowa",
    )
    private val cover = mapOf(
        "FEW" to "nieliczne (1–2/8)", "SCT" to "rozproszone (3–4/8)",
        "BKN" to "poprzerywane (5–7/8)", "OVC" to "całkowite (8/8)", "VV" to "widzialność pionowa",
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
                    Decoded(t, Category.OTHER, when (t) { "SPECI" -> "Depesza specjalna (zmiana warunków)"; "TAF" -> "Prognoza lotniskowa"; else -> "Rutynowa depesza METAR" })
                t == "AMD" -> Decoded(t, Category.OTHER, "Prognoza poprawiona")
                (i <= 1 || (i == 2 && (tokens[1] == "AMD" || tokens[1] == "COR"))) && t.length == 4 && t.all { it.isLetterOrDigit() } && t.first().isLetter() && out.none { it.category == Category.STATION } ->
                    Decoded(t, Category.STATION, "Lotnisko $t")
                reTime.matches(t) -> reTime.find(t)!!.destructured.let { (d, h, m) -> Decoded(t, Category.TIME, "Dzień $d, godz. $h:$m UTC") }
                t == "AUTO" -> Decoded(t, Category.OTHER, "Depesza automatyczna (bez obserwatora)")
                t == "COR" -> Decoded(t, Category.OTHER, "Depesza skorygowana")
                t == "NIL" -> Decoded(t, Category.OTHER, "Brak depeszy")
                reWind.matches(t) -> wind(t)
                t == "00000KT" -> Decoded(t, Category.WIND, "Cisza")
                reWindVar.matches(t) -> reWindVar.find(t)!!.destructured.let { (a, b) -> Decoded(t, Category.WIND, "Kierunek zmienny od $a° do $b°") }
                t == "CAVOK" -> Decoded(t, Category.VISIBILITY, "CAVOK — widzialność ≥10 km, brak chmur poniżej 5000 ft i CB, brak zjawisk")
                t == "9999" -> Decoded(t, Category.VISIBILITY, "Widzialność 10 km lub więcej")
                // Po temperaturze 4 cyfry to widzialność tylko w trendzie (TEMPO 4000)
                reVis.matches(t) && out.lastOrNull { it.category == Category.TEMP || it.category == Category.TREND }?.category != Category.TEMP ->
                    reVis.find(t)!!.destructured.let { (v, dir) ->
                        val m = v.toInt()
                        val txt = if (m >= 9999) "10 km lub więcej" else if (m >= 5000) "${m / 1000} km" else "$m m"
                        Decoded(t, Category.VISIBILITY, when (dir) {
                            "" -> "Widzialność $txt"
                            "NDV" -> "Widzialność $txt (brak zmienności kierunkowej)"
                            else -> "Minimalna widzialność $txt w kierunku $dir"
                        })
                    }
                reVisSm.matches(t) -> Decoded(
                    t, Category.VISIBILITY,
                    when (t.first()) { 'P' -> "Widzialność powyżej "; else -> "Widzialność " } +
                        "${t.removeSuffix("SM").trimStart('M', 'P')} mil lądowych" + if (t.startsWith("M")) " (mniej niż)" else "",
                )
                reRvr.matches(t) -> rvr(t)
                reRwyState.matches(t) && t.contains("/") && t.length >= 8 -> Decoded(t, Category.OTHER, "Stan nawierzchni pasa ${reRwyState.find(t)!!.groupValues[1]}")
                t == "NSC" -> Decoded(t, Category.CLOUDS, "Brak znaczących chmur")
                t == "NCD" -> Decoded(t, Category.CLOUDS, "Nie wykryto chmur (stacja automatyczna)")
                t == "SKC" || t == "CLR" -> Decoded(t, Category.CLOUDS, "Niebo bezchmurne")
                t == "NSW" -> Decoded(t, Category.WEATHER, "Ustąpienie zjawisk")
                reCloud.matches(t) -> cloud(t)
                reTemp.matches(t) -> reTemp.find(t)!!.destructured.let { (a, b) ->
                    val tt = a.replace("M", "-").toInt()
                    val dd = b.takeIf { it.isNotEmpty() }?.replace("M", "-")?.toInt()
                    Decoded(t, Category.TEMP, "Temperatura $tt °C" + (dd?.let { ", punkt rosy $it °C (różnica ${tt - it}°)" } ?: ""))
                }
                reQ.matches(t) -> Decoded(t, Category.PRESSURE, "QNH ${t.drop(1).toInt()} hPa (${Atmo.hpaToInHg(t.drop(1).toDouble()).fmt(2)} inHg)")
                reA.matches(t) -> t.drop(1).toInt().let { v -> Decoded(t, Category.PRESSURE, "Altymetr ${v / 100}.${"%02d".format(v % 100)} inHg (${Atmo.inHgToHpa(v / 100.0).fmt()} hPa)") }
                reRecent.matches(t) -> Decoded(t, Category.WEATHER, "Niedawno: " + weatherText(t.drop(2)))
                reWs.matches(t) -> Decoded(t, Category.WIND, "Uskok wiatru")
                t == "NOSIG" -> Decoded(t, Category.TREND, "Bez istotnych zmian w ciągu 2 h")
                t == "BECMG" -> Decoded(t, Category.TREND, "Stopniowo zmieni się na:")
                t == "TEMPO" -> Decoded(t, Category.TREND, "Okresowo:")
                t == "INTER" -> Decoded(t, Category.TREND, "Krótkotrwale:")
                reProb.matches(t) -> Decoded(t, Category.TREND, "Prawdopodobieństwo ${t.drop(4)}%")
                reFm.matches(t) -> reFm.find(t)!!.destructured.let { (d, h, m) -> Decoded(t, Category.TREND, "Od dnia $d, $h:$m UTC:") }
                reTafValid.matches(t) -> reTafValid.find(t)!!.destructured.let { (d1, h1, d2, h2) -> Decoded(t, Category.TIME, "Ważna od dnia $d1 $h1:00 do dnia $d2 $h2:00 UTC") }
                t.startsWith("TX") || t.startsWith("TN") -> Decoded(t, Category.TEMP, (if (t.startsWith("TX")) "Maks. " else "Min. ") + "temperatura " + t.drop(2).substringBefore("/").replace("M", "-") + " °C")
                t == "RMK" -> { inRemarks = true; Decoded(t, Category.REMARK, "Uwagi:") }
                t.startsWith("AT") || t.startsWith("TL") -> Decoded(t, Category.TREND, "Czas: ${t.drop(2)} UTC")
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
        if (speed == 0) return Decoded(t, Category.WIND, "Cisza")
        val d = if (dir == "VRB") "zmienny" else "z $dir°"
        val g = if (gust.isNotEmpty()) ", w porywach ${gust.toInt()} $u" else ""
        return Decoded(t, Category.WIND, "Wiatr $d, $speed $u$g")
    }

    private fun rvr(t: String): Decoded {
        val m = reRvr.find(t)!!.groupValues
        val trend = when (m[8]) { "U" -> ", rośnie"; "D" -> ", maleje"; "N" -> ", bez zmian"; else -> "" }
        val pre = when (m[2]) { "P" -> "powyżej "; "M" -> "poniżej "; else -> "" }
        val unit = if (m[7] == "FT") "ft" else "m"
        val range = if (m[4].isNotEmpty()) " – ${m[6].toInt()}" else ""
        return Decoded(t, Category.RVR, "RVR pas ${m[1]}: $pre${m[3].toInt()}$range $unit$trend")
    }

    private fun cloud(t: String): Decoded {
        val (c, h, type) = reCloud.find(t)!!.destructured
        val height = h.toIntOrNull()?.let { "${it * 100} ft" } ?: "wysokość nieznana"
        val ty = when (type) { "CB" -> " — cumulonimbus!"; "TCU" -> " — cumulus wypiętrzony"; else -> "" }
        return Decoded(t, Category.CLOUDS, "${cover[c]?.replaceFirstChar { it.uppercase() }} na $height$ty")
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
        return parts.joinToString(" ").replaceFirstChar { it.uppercase() }.ifBlank { t }
    }

    private fun remark(t: String) = when {
        t == "AO2" -> "Stacja automatyczna z czujnikiem opadów"
        t == "AO1" -> "Stacja automatyczna bez czujnika opadów"
        t.startsWith("SLP") -> "Ciśnienie zredukowane: " + t.drop(3).toIntOrNull()?.let { (if (it < 500) 1000 + it / 10.0 else 900 + it / 10.0).fmt(1) + " hPa" }.orEmpty()
        t.matches(Regex("""T[01]\d{3}[01]\d{3}""")) -> {
            fun v(s: String) = (if (s[0] == '1') -1 else 1) * s.drop(1).toInt() / 10.0
            "Dokładnie: temp. ${v(t.substring(1, 5))} °C, rosa ${v(t.substring(5, 9))} °C"
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
