package com.freemetar.app.ui.flight

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freemetar.app.domain.AircraftProfile
import com.freemetar.app.domain.AutopilotKind
import com.freemetar.app.ui.theme.Fm

/** Jak klawisz pokazuje, że tryb jest włączony — każdy producent robi to inaczej. */
enum class LampStyle {
    /** Airbus FCU: trzy zielone kreski nad napisem. */
    BARS,
    /** Boeing MCP: podłużna lampka pod napisem na kwadratowym klawiszu. */
    BAR_BELOW,
    /** Garmin: napis zmienia kolor na cyjan, obok kropka. */
    TEXT,
    /** GA (KAP140): napis aktywnego trybu na wyświetlaczu, klawisz z obwódką. */
    OUTLINE,
}

/**
 * Wygląd panelu autopilota: kolory płyty, klawiszy, okienek z wartościami i pokręteł,
 * kształt klawiszy i lampek. Zależy od profilu samolotu i od motywu aplikacji
 * (AMOLED — czarna płyta, grafit — szara jak w kabinie, jasny — jasna płyta do pracy w słońcu).
 */
data class PanelSkin(
    val name: String,
    val plate: Color,          // tło płyty panelu
    val plateEdge: Color,      // obwódka płyty i modułów
    val key: Color,            // lico klawisza
    val keyPressed: Color,
    val keyText: Color,
    val lampOn: Color,
    val lampOff: Color,
    val lamp: LampStyle,
    val keyCorner: Dp,         // zaokrąglenie klawisza
    val keyHeight: Dp,
    val window: Color,         // okienko wartości
    val windowText: Color,     // cyfry w okienku
    val windowLabel: Color,
    val knobOuter: Color,
    val knobInner: Color,
    val knobMark: Color,
    val legend: Color,         // opisy na płycie (SPD, HDG…)
)

@Composable
fun panelSkin(p: AircraftProfile): PanelSkin {
    val c = Fm.c
    val day = !c.isNight
    val amoled = c.paper == Color(0xFF000000)
    // Płyta: AMOLED czarna, grafit szara, jasny motyw — jasnoszara
    fun plate(amoledC: Long, graphite: Long, light: Long) = Color(if (day) light else if (amoled) amoledC else graphite)
    return when (p.autopilot) {
        AutopilotKind.FCU -> PanelSkin(
            "FCU",
            plate = plate(0xFF0B0D10, 0xFF3A4048, 0xFFC9CED4), plateEdge = Color(if (day) 0xFF8E959D else 0xFF4C545E),
            key = Color(if (day) 0xFF5A626B else 0xFF2A3038), keyPressed = Color(0xFF4A535D), keyText = Color(0xFFF2F2F2),
            lampOn = Color(0xFF3FE07A), lampOff = Color(0x22FFFFFF), lamp = LampStyle.BARS, keyCorner = 6.dp, keyHeight = 48.dp,
            window = Color(0xFF14100A), windowText = Color(0xFFFF9F2E), windowLabel = Color(0xFFFFFFFF),
            knobOuter = Color(0xFF5B636D), knobInner = Color(0xFF808993), knobMark = Color.White, legend = Color(if (day) 0xFF1C2228 else 0xFFE8ECF0),
        )
        AutopilotKind.MCP, AutopilotKind.MCP_787 -> PanelSkin(
            "MCP",
            plate = plate(0xFF0D0E10, 0xFF5E6266, 0xFFB7B9BA), plateEdge = Color(if (day) 0xFF7E8285 else 0xFF74797E),
            key = Color(if (day) 0xFFE8E6E0 else 0xFF2C2E31), keyPressed = Color(0xFF9EA2A6), keyText = Color(if (day) 0xFF16181A else 0xFFF4F4F4),
            lampOn = Color(0xFF6CFF6C), lampOff = Color(if (day) 0x33000000 else 0x26FFFFFF), lamp = LampStyle.BAR_BELOW, keyCorner = 3.dp, keyHeight = 52.dp,
            window = Color(0xFF050505), windowText = Color(0xFFF5F5F5), windowLabel = Color(if (day) 0xFF16181A else 0xFFEDEDED),
            knobOuter = Color(0xFF1B1C1E), knobInner = Color(0xFF2E3033), knobMark = Color.White, legend = Color(if (day) 0xFF16181A else 0xFFEDEDED),
        )
        // ATR (FGCP) i Embraer (Primus Epic) — ciemne płyty z cyjanowymi wskazaniami, jak Garmin
        AutopilotKind.GFC700, AutopilotKind.ATR_FGCP, AutopilotKind.PRIMUS -> PanelSkin(
            when (p.autopilot) { AutopilotKind.ATR_FGCP -> "FGCP"; AutopilotKind.PRIMUS -> "GP"; else -> "GFC 700" },
            plate = plate(0xFF000000, 0xFF26292D, 0xFFD4D7DA), plateEdge = Color(if (day) 0xFF9AA0A6 else 0xFF3A3F45),
            key = Color(if (day) 0xFF3A3F45 else 0xFF1E2125), keyPressed = Color(0xFF3B4148), keyText = Color(0xFFE6E9EC),
            lampOn = Color(0xFF38D6F5), lampOff = Color(0x00000000), lamp = LampStyle.TEXT, keyCorner = 12.dp, keyHeight = 46.dp,
            window = Color(0xFF000000), windowText = Color(0xFF38D6F5), windowLabel = Color(0xFFB8C0C8),
            knobOuter = Color(0xFF2B2F34), knobInner = Color(0xFF41464C), knobMark = Color(0xFFDDE2E7), legend = Color(if (day) 0xFF1E2125 else 0xFFB8C0C8),
        )
        AutopilotKind.KAP140, AutopilotKind.NONE -> PanelSkin(
            if (p.autopilot == AutopilotKind.KAP140) "KAP140" else p.short,
            plate = plate(0xFF050505, 0xFF1D1F21, 0xFFCFD2D4), plateEdge = Color(if (day) 0xFF8E9296 else 0xFF35383B),
            key = Color(if (day) 0xFF2A2C2E else 0xFF141516), keyPressed = Color(0xFF34373A), keyText = Color(0xFFEDEDED),
            lampOn = Color(0xFFB8F27A), lampOff = Color(0x33FFFFFF), lamp = LampStyle.OUTLINE, keyCorner = 4.dp, keyHeight = 46.dp,
            window = Color(0xFF1A2414), windowText = Color(0xFFB8F27A), windowLabel = Color(0xFF8FB866),
            knobOuter = Color(0xFF202224), knobInner = Color(0xFF313437), knobMark = Color(0xFFEDEDED), legend = Color(if (day) 0xFF1D1F21 else 0xFFC9CCCF),
        )
    }
}
