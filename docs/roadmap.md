# OpenTakeoff - Plan rozwoju

## ✅ Zrealizowane (Grudzień 2024 - Styczeń 2025)

### Synchronizacja i backup
- ✅ Google Drive jako główne źródło danych
- ✅ Automatyczny backup po każdym locie i usunięciu
- ✅ Struktura: główny plik + jeden backup
- ✅ Scalanie danych bez duplikatów

### UI i UX
- ✅ Ekran powitalny przy pierwszym uruchomieniu
- ✅ Logbook jako osobna zakładka (nie w ekranie Lot)
- ✅ Rozszerzona analityka w Logbook:
  - Karty statystyk (loty, czas, dystans, oceny)
  - Wykres postępów (ostatnie 10 lotów)
  - Top 5 najczęściej odwiedzanych lotnisk

---

## 🚧 W trakcie implementacji

### Faza 1: Podstawowe usprawnienia

#### 1.1 Nagrywanie lotów ✅
- ✅ Przycisk START/STOP już istnieje w kokpicie
- ✅ Możliwość ręcznego zakończenia i wznowienia

#### 1.2 Ustawienia dźwigni mocy
**Status:** Do przetestowania
- Opcja lewa/prawa strona (kod już istnieje w `leversLeft`)
- Widoczne w Ustawieniach → Sterowanie samolotem

---

## 📋 Do zrobienia (Priorytet)

### Faza 2: Mapa 3D (3-4 tygodnie)

**Wymagania użytkownika:**
> "Znacznie rozbuduj i dopracuj mapę, niech ten widok będzie totalnie przerobiony, bo obecnie nie podoba mi się, ma być zbudowany od zera i bez przełączania trybu 3D, lekko pochylona mapa domyślnie"

#### 2.1 Nowy silnik mapy
- **Usunąć przełącznik 3D** - mapa zawsze w 3D
- **Domyślne pochylenie:** pitch 45-50°
- **Smooth camera following:** płynne śledzenie samolotu
- **Smooth zoom/pan:** animacje przejść kamery
- **LOD (Level of Detail):** adaptacyjne ładowanie szczegółów

#### 2.2 Warstwy mapy
- ✅ METAR (już jest)
- ✅ SIGMET (już jest)
- ✅ Trasa (już jest)
- **NOWE:**
  - ILS approach paths (3D korytarze podejścia)
  - Airspace boundaries (CTR, TMA, FIR)
  - Restricted areas (strefy ograniczone)
  - Real-time weather overlay (opady, chmury)

#### 2.3 Rendering 3D
- **Teren 3D:** użyć elevation data
- **Budynki 3D:** już jest, usprawnić
- **Kurtyna śladu:** już jest, poprawić rendering
- **Shadows:** cienie od budynków i samolotu

#### 2.4 Kontrolki
- **Gesture controls:** pinch, rotate, tilt
- **Compass:** zawsze widoczny
- **Altitude indicator:** wysokość kamery
- **Reset button:** powrót do śledzenia samolotu

**Pliki do modyfikacji:**
- `app/src/main/java/com/freemetar/app/ui/screens/MapScreen.kt`
- `app/src/main/java/com/freemetar/app/ui/screens/MapTrack.kt`

---

### Faza 3: Cockpit rozszerzony (4-5 tygodni)

**Wymagania:**
> "Niech pokazują się belki ILS na PFD przy podejściu, więcej systemów na EICAS/ECAM, przeróbw widok silników który jest obecnie na widok EICAS, dzięki temu można przełączać widoki"

#### 3.1 ILS indicators na PFD
- **Localizer:** pozioma belka odchylenia od osi pasa
- **Glideslope:** pionowa belka odchylenia od ścieżki
- **DME distance:** dystans do progu
- **Course deviation:** odchylenie kursowe
- **Automatyczne wykrycie:** gdy samolot w promieniu 15 NM od lotniska z ILS

#### 3.2 EICAS/ECAM przełączanie widoków
**Obecny stan:** Tylko silniki
**Docelowo:** Przełączanie między:
- **ENG** (silniki) - N1/N2/RPM, EGT, paliwo, przepływ
- **ELEC** (elektryka) - generatory, bateria, voltaż, ampery
- **HYD** (hydraulika) - ciśnienie układów A/B/C
- **FUEL** (paliwo) - zbiorniki, pompy, zawory
- **DOOR** (drzwi i podwozie) - status drzwi, podwozia, klapy cargo
- **STATUS** (podsumowanie) - aktywne systemy, ostrzeżenia

#### 3.3 Adaptacja do samolotu
> "Wszystkie funkcje tej aplikacji powinny być dostępne tylko jak samolot je obsługuje"

- **Detection:** rozpoznawanie typu samolotu z MSFS
- **Profiling:** mapowanie samolot → dostępne systemy
- **Dynamic UI:** pokazuj tylko to co ma samolot
  - Cessna 172: podstawowy EICAS (RPM, paliwo, olej)
  - Boeing 737: pełny EICAS (N1, N2, EGT, hydraulika, elektryka)
  - Airbus A320: ECAM (wszystkie systemy + procedury)

#### 3.4 Przepustnica niezależna
- **Osobny widget:** dźwignie mocy oddzielone od EICAS
- **Pozycja:** ustawienie lewa/prawa w Settings (już istnieje: `leversLeft`)
- **Zawsze widoczna:** nawet gdy EICAS pokazuje inne systemy

**Pliki do modyfikacji:**
- `app/src/main/java/com/freemetar/app/ui/flight/Instruments.kt` (PFD)
- `app/src/main/java/com/freemetar/app/ui/flight/EnginePanel.kt` → przerobić na `EicasPanel.kt`
- `app/src/main/java/com/freemetar/app/ui/flight/Cockpit.kt` (layout)

---

### Faza 4: Radar pogodowy (2 tygodnie)

**Wymagania:**
> "Radar pogodowy na podstawie danych pogodowych darmowych do włączenia w ND"

#### 4.1 Źródło danych
- **API:** Open-Meteo lub NOAA
- **Dane:** opady, intensywność, typ (deszcz/śnieg/burza)
- **Zakres:** 160 NM wokół samolotu (jak prawdziwy radar)
- **Refresh:** co 5-10 minut

#### 4.2 Rendering na ND
- **Overlay:** przeźroczysta warstwa nad mapą ND
- **Kolory:** zielony → żółty → czerwony (intensywność)
- **Przełącznik:** EFIS button "WXR" (włącz/wyłącz)
- **Range scaling:** skaluje się z range ND (10/20/40/80/160 NM)

#### 4.3 Legendy i wskaźniki
- **Legenda:** pasek z kolorami i wartościami
- **Age indicator:** ile minut temu odświeżono dane
- **"TEST" mode:** symulacja dla testów

**Pliki do modyfikacji:**
- `app/src/main/java/com/freemetar/app/ui/flight/Instruments.kt` (ND)
- `app/src/main/java/com/freemetar/app/data/OpenMeteo.kt` (weather API)

---

### Faza 5: Callouts (1-2 tygodnie)

**Wymagania:**
> "Można dodać w ustawieniach włączenie callouts i jest lista callouts w osobnym modalu które można wybrać żeby się pojawiały i wtedy telefon je podaje"

#### 5.1 Lista callouts
**Takeoff:**
- "80 knots"
- "V1"
- "Rotate"
- "Positive rate"
- "Gear up"

**Landing:**
- "1000" (feet AGL)
- "500"
- "Minimums"
- "100", "50", "40", "30", "20", "10"
- "Retard, retard"

**Stall/overspeed:**
- "Stall, stall"
- "Overspeed"

**Autopilot:**
- "Autopilot"
- "Autothrottle"

#### 5.2 Implementacja
- **Settings:** sekcja "Callouts" z checkboxami
- **TTS Engine:** Android Text-to-Speech
- **Voice:** męski głos, profesjonalny ton
- **Warunki:** tylko gdy samolot spełnia warunek (np. "50" gdy AGL=50±5 ft)
- **Cooldown:** nie powtarzaj tego samego w 5 sekund

#### 5.3 Customizacja
- **Włącz/wyłącz całość**
- **Wybór głosu** (męski/damski)
- **Głośność** (slider)
- **Język:** polski/angielski

**Pliki do utworzenia:**
- `app/src/main/java/com/freemetar/app/data/Callouts.kt`
- Integracja z `FlightRecorder.kt` lub `LinkClient.kt`

---

### Faza 6: Checklisty (4-5 tygodni)

**Wymagania:**
> "Osobny widok z interaktywnymi checklistami, ale musisz poszukać prawdziwych i na razie zasilić kilka i sparować je z samolotami w MSFS ale być to powinno jakoś tak, aby dopasowywać nazwy samolotów z MSFS z checklistami + muszą być rzeczywiste i prawdziwe po polsku i angielsku"

#### 6.1 Format checklistów
```json
{
  "aircraft": "Boeing 737-800",
  "msfs_matches": ["Boeing 737-800", "PMDG 737-800", "Zibo 737"],
  "checklists": {
    "before_start": {
      "name_pl": "Przed startem silników",
      "name_en": "Before Start",
      "items": [
        {
          "item_pl": "Parking brake",
          "item_en": "Parking brake",
          "expected": "SET"
        },
        {
          "item_pl": "Fuel pumps",
          "item_en": "Fuel pumps",
          "expected": "ON"
        }
      ]
    }
  }
}
```

#### 6.2 Źródła prawdziwych checklistów
- **Boeing 737:** FCOM (Flight Crew Operations Manual) - domena publiczna
- **Airbus A320:** FCOM - dostępne przez symulatory
- **Cessna 172:** POH (Pilot's Operating Handbook) - publicznie dostępne
- **Alternatywa:** checklisty z SimBrief, VATSIM resources

#### 6.3 UI Checklistów
- **Osobna zakładka** albo **przycisk w kokpicie**
- **Lista checklistów:** Before Start, After Start, Before Takeoff, After Landing, itd.
- **Interaktywne:** kliknięcie zaznacza pozycję ✓
- **Progress bar:** X/Y pozycji wykonanych
- **Auto-reset:** po zakończeniu lotu

#### 6.4 Matching samolotów
- **Fuzzy matching:** "Boeing 737-800" dopasuje "PMDG 737-800"
- **Kategorie:** jeśli nie ma dokładnego, użyj kategorii (airliner, GA single, turboprop)
- **Fallback:** generyczna checklista dla kategorii

**Pliki do utworzenia:**
- `app/src/main/assets/checklists/` (JSON pliki)
- `app/src/main/java/com/freemetar/app/data/Checklists.kt`
- `app/src/main/java/com/freemetar/app/ui/flight/ChecklistScreen.kt`

---

### Faza 7: Integracje (5-6 tygodni)

#### 7.1 X-Plane support
**Wymagania:** Wsparcie dla X-Plane obok MSFS

- **Nowy bridge:** Python + X-Plane SDK
- **Protokół:** ten sam co MSFS (WebSocket + JSON)
- **Datarefy:** mapowanie X-Plane → OpenTakeoff format
- **Discovery:** UDP broadcast jak w MSFS Link

**Struktura:**
```
bridge-xplane/
  └── xplane_link.py
      ├── X-Plane SDK integration
      ├── WebSocket server
      └── Protocol compatible with Android app
```

#### 7.2 Import z SimToolkitPro/Volanta
- **Parsery:** odczyt formatów STK i Volanta
- **Mapowanie:** ich format → FlightLog
- **UI:** przycisk "Import from..." w Logbook
- **Walidacja:** sprawdź czy dane są kompletne

**Formaty:**
- SimToolkitPro: JSON z GPX trackiem
- Volanta: proprietary JSON

---

### Faza 8: Planowanie lotu (3-4 tygodnie)

**Wymagania:**
> "Lepszy moduł planowania lotu i planowania trasy"

#### 8.1 Ulepszenia edytora tras
- **Drag & drop:** przestaw punkty na mapie
- **Airways:** wpisz "DCT ROUTE" albo "J123"
- **STAR/SID:** podgląd na mapie przed wyborem
- **Fuel planning:** oblicz paliwo na trasę
- **Alternate selection:** automatyczne zapasowe

#### 8.2 Briefing pack
- **PDF export:** METAR, TAF, NOTAMs, charty, mapa
- **Print-friendly:** czarno-biały, oszczędny
- **Sections:** Route, Weather, Airports, Procedures

#### 8.3 Performance calculator
- **Weight & Balance:** CG, limits
- **V-speeds:** V1, VR, V2 z wagą
- **Runway analysis:** czy wystarczy?

---

## 🗓️ Harmonogram (Szacunkowy)

| Faza | Czas | Priorytet | Status |
|------|------|-----------|--------|
| Faza 1: Podstawowe | 1 tydzień | ✅ Wysoki | ✅ Gotowe |
| Faza 2: Mapa 3D | 3-4 tygodnie | 🔥 Najwyższy | 📋 TODO |
| Faza 3: Cockpit | 4-5 tygodni | 🔥 Najwyższy | 📋 TODO |
| Faza 4: Radar | 2 tygodnie | ⚠️ Średni | 📋 TODO |
| Faza 5: Callouts | 1-2 tygodnie | ⚠️ Średni | 📋 TODO |
| Faza 6: Checklisty | 4-5 tygodni | 🔥 Najwyższy | 📋 TODO |
| Faza 7: Integracje | 5-6 tygodni | ⚠️ Średni | 📋 TODO |
| Faza 8: Planowanie | 3-4 tygodnie | ⚠️ Średni | 📋 TODO |

**Łącznie:** ~23-31 tygodni (6-8 miesięcy)

---

## 🎯 Następne kroki

1. **Commit obecnych zmian** (Logbook + statystyki) ✅
2. **Zacząć Fazę 2:** Przebudowa mapy 3D
3. Po mapie: **Faza 3** (Cockpit) lub **Faza 6** (Checklisty) - do decyzji użytkownika

---

## 📝 Notatki techniczne

### Problem z kamerą
- Kod kamery wygląda poprawnie
- Może wymagać testów na żywym MSFS 2024
- SimConnect API kamery mogło się zmienić w MSFS 2024
- **Action:** Utworzyć issue na GitHubie z debug logami

### Optymalizacja
- Mapa 3D: użyć GPU acceleration
- Checklisty: lazy loading z assets
- Radar pogodowy: cache danych

---

**Ostatnia aktualizacja:** Styczeń 2025
