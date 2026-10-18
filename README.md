# OpenAirBook

**Pomysł i wykonanie: [redzikowski.com](https://redzikowski.com)**

[Polski](#polski) · [English](#english)

---

## Polski

Aplikacja na Androida dla wirtualnych pilotów (MSFS, X-Plane, VATSIM, IVAO). Pogoda, dane lotnisk, plan lotu i kokpit na telefonie. Korzysta wyłącznie z darmowych, otwartych źródeł danych: nie wymaga kont ani kluczy API i nie ma reklam. Interfejs jest po polsku i angielsku (zgodnie z językiem systemu albo do wyboru w ustawieniach).

Do sterowania samolotem i danych z symulatora służy **OpenAirBook Link**, mały program na Windows, który łączy telefon z MSFS 2024 w sieci lokalnej. Instrukcja: [docs/link-setup.md](docs/link-setup.md).

### Funkcje

**Briefing lotniska**
- pogoda teraz i interaktywny METAR (dotknięcie grupy pokazuje jej znaczenie);
- wcześniejsze depesze z godziną i kategorią lotu w jednej linii;
- TAF w okresach z kategorią lotu, SIGMET i PIREP, wiatr na wysokościach;
- pasy ze składowymi wiatru, częstotliwości i ILS, okolica;
- ATIS czytany na głos z bieżącego METAR-u;
- charty FAA dla lotnisk w USA z przeglądarką PDF.

**Mapa**
- lotniska w kolorach kategorii lotu, obszary SIGMET, aktywna trasa z procedurami;
- **widok 3D**: pochylenie i obrót mapy, a przy śledzeniu kamera patrzy w kierunku lotu;
- **ślad lotu z wysokością**: linia zmienia kolor wraz z wysokością, na śladzie są etykiety wysokości (stopy albo FL), a w 3D pod śladem stoi „kurtyna” do ziemi, która pokazuje profil wznoszenia i zniżania (wysokość kurtyny jest przewyższona 3×, żeby profil był czytelny);
- bieżąca wysokość przy symbolu samolotu, budynki 3D przy dużym przybliżeniu;
- każdy zapisany lot można otworzyć na mapie 3D z historii lotów.

**Plan lotu**
- import z SimBrief, wpisana trasa albo plik;
- edytor z wyborem SID, STAR i podejścia (dane z bazy MSFS przez Link);
- eksport i import tras.

**Kokpit (z OpenAirBook Link)**
- PFD zawsze na widoku, mapa ND, silniki, postęp lotu;
- **autopilot z pokrętłami** jak w awionice: podwójne pokrętło (zewnętrzny pierścień zmienia o duży krok, środek o mały), przyciski −/+ z powtarzaniem i okienko, w które można wpisać wartość. Pod każdą nastawą są jej przyciski trybów, a układ zależy od samolotu: Airbus FCU, Boeing MCP, Garmin (odrzutowiec i turbośmigłowy), prosty autopilot GA. Profil wybiera się automatycznie albo ręcznie;
- radio (COM, NAV, transponder) i systemy (podwozie, klapy, spoilery, trym, światła).

**Sterowanie samolotem**
- **żyroskop** (domyślnie): trzymasz telefon jak wolant; steruje tylko podczas przytrzymania, a puszczenie od razu oddaje stery;
- drążek pod kciukiem albo fizyczny kontroler lub joystick (Bluetooth, USB-C);
- **pływający przycisk sterowania**: widoczny na każdej zakładce, więc można przeglądać briefing czy mapę i mieć stery pod ręką. Przytrzymaj go i przechylaj telefon (żyroskop) albo przesuwaj kciuk (drążek). Da się go przesunąć za uchwyt.

**Historia lotów**
- automatyczny zapis każdego lotu: pozycja, wysokość, prędkość względem ziemi i przyrządowa, prędkość pionowa, kurs;
- częstotliwość zapisu do wyboru w ustawieniach: 4×, 2× lub 1× na sekundę, co 2 s albo co 5 s;
- kompaktowe archiwum: tor jest zapisywany jako różnice między punktami i pakowany gzipem, więc zajmuje ułamek zwykłego JSON-a;
- eksport i import całej historii w jednym pliku `.oab` (kopia zapasowa, przeniesienie na inny telefon) oraz eksport pojedynczego lotu do GPX;
- wykresy wysokości, prędkości i prędkości pionowej oraz ocena lądowania (prędkość pionowa, przeciążenie, położenie względem osi i progu pasa, odbicia).

**Pozostałe**
- narzędzia: kalkulator wiatru, wysokość gęstościowa, zniżanie, paliwo, jednostki, dekoder METAR i TAF, ściąga radiowa;
- motywy AMOLED (czysta czerń), grafitowy i jasny; układy na telefon pionowo i poziomo oraz tablet;
- automatyczne aktualizacje z GitHuba.

### Źródła danych

| Dane | Źródło | Licencja |
|---|---|---|
| METAR, TAF, SIGMET, PIREP | [aviationweather.gov Data API](https://aviationweather.gov/data/api/) | domena publiczna (NOAA) |
| Lotniska, pasy, częstotliwości, pomoce nawigacyjne | [OurAirports](https://ourairports.com/data/) | domena publiczna |
| Wiatr i temperatura na wysokościach | [Open-Meteo](https://open-meteo.com/) | CC BY 4.0 |
| Podkład mapy i budynki | [OpenFreeMap](https://openfreemap.org/) / OpenStreetMap | ODbL |
| Charty (USA) | [FAA d-TPP](https://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/dtpp/) | domena publiczna |
| Plan lotu | [SimBrief](https://www.simbrief.com/) (publiczny eksport planu) | — |
| Procedury, ILS, stan lotu | baza i SimConnect Twojego MSFS przez OpenAirBook Link | — |

Baza OurAirports pobiera się raz przy pierwszym uruchomieniu i odświeża co 30 dni. Historia lotów i trasy są przechowywane tylko na telefonie.

> Tylko do symulacji lotów. Nie używaj do rzeczywistej nawigacji.

### Budowanie

Kotlin, Jetpack Compose i Material 3, minSdk 26. OpenAirBook Link: C# i .NET 8 (folder `bridge/`).

Lokalnie skrypt `tools/build-local.ps1` buduje APK i Link oraz uruchamia testy:

```bash
powershell -ExecutionPolicy Bypass -File tools/build-local.ps1
```

### Wydania

Każdy push na `main` uruchamia [workflow](.github/workflows/release.yml), który buduje APK i `OpenAirBook-Link.exe` i publikuje je w wydaniu `v1.0.<numer przebiegu>`.

Bez sekretów APK jest podpisane kluczem debug. Żeby aktualizacje instalowały się na stałym kluczu, dodaj sekrety repozytorium: `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

---

## English

An Android app for flight-sim pilots (MSFS, X-Plane, VATSIM, IVAO): weather, airport data, flight planning and a cockpit on your phone. It uses free and open data sources only, with no accounts, API keys or ads. The interface is in English and Polish (follows the system language or can be set in Settings).

To fly the aircraft and read live data from the simulator, run **OpenAirBook Link**, a small Windows program that connects the phone to MSFS 2024 over your local network. Setup: [docs/link-setup.md](docs/link-setup.md) (in Polish).

### Features

**Airport briefing**
- current weather and an interactive METAR (tap a group to see what it means);
- earlier reports with time and flight category on one line;
- TAF split into periods with flight category, SIGMETs and PIREPs, winds aloft;
- runways with wind components, frequencies and ILS, nearby airports;
- ATIS read aloud from the latest METAR;
- FAA charts for US airports with a PDF viewer.

**Map**
- airports coloured by flight category, SIGMET areas, the active route with procedures;
- **3D view**: tilt and rotate the map; while following the aircraft the camera looks along the flight path;
- **flight track with altitude**: the line changes colour with altitude and carries altitude labels (feet or FL). In 3D a curtain drops from the track to the ground and shows the climb and descent profile (its height is exaggerated 3× so the profile is readable);
- current altitude next to the aircraft symbol, 3D buildings when zoomed in;
- any saved flight can be opened on the 3D map from the flight history.

**Flight plan**
- import from SimBrief, a typed route or a file;
- editor with SID, STAR and approach selection (from the MSFS database via Link);
- route export and import.

**Cockpit (with OpenAirBook Link)**
- PFD always in view, ND map, engines, flight progress;
- **autopilot with knobs** like real avionics: a dual knob (the outer ring moves in big steps, the centre in small ones), −/+ buttons that repeat when held, and a window you can tap to type a value. Each setting has its mode buttons right below it, and the layout follows the aircraft: Airbus FCU, Boeing MCP, Garmin (jet and turboprop), simple GA autopilot. The profile is picked automatically or by hand;
- radios (COM, NAV, transponder) and systems (gear, flaps, spoilers, trim, lights).

**Flying the aircraft**
- **gyro** (default): hold the phone like a yoke; it only flies while you hold the pad, and letting go hands control back at once;
- an on-screen thumb stick, or a physical controller or joystick (Bluetooth, USB-C);
- **floating control button**: shown on every tab, so you can read the briefing or the map with the controls under your thumb. Hold it and tilt the phone (gyro) or move your thumb (stick). Drag it by the handle to move it.

**Flight history**
- every flight is recorded automatically: position, altitude, ground and indicated speed, vertical speed, heading;
- recording rate is set in Settings: 4, 2 or 1 times per second, every 2 s or every 5 s;
- compact archive: the track is stored as differences between points and gzip-compressed, a fraction of the size of plain JSON;
- export and import of the whole history as a single `.oab` file (backup or moving to another phone), plus GPX export of a single flight;
- altitude, speed and vertical speed charts, and a landing score (vertical speed, g-force, position relative to the runway centreline and threshold, bounces).

**Also**
- tools: wind calculator, density altitude, descent, fuel, unit converter, METAR and TAF decoder, radio phraseology;
- AMOLED (true black), graphite and bright themes; layouts for phones in portrait and landscape and for tablets;
- automatic updates from GitHub.

### Data sources

| Data | Source | Licence |
|---|---|---|
| METAR, TAF, SIGMET, PIREP | [aviationweather.gov Data API](https://aviationweather.gov/data/api/) | public domain (NOAA) |
| Airports, runways, frequencies, navaids | [OurAirports](https://ourairports.com/data/) | public domain |
| Winds and temperatures aloft | [Open-Meteo](https://open-meteo.com/) | CC BY 4.0 |
| Base map and buildings | [OpenFreeMap](https://openfreemap.org/) / OpenStreetMap | ODbL |
| Charts (USA) | [FAA d-TPP](https://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/dtpp/) | public domain |
| Flight plan | [SimBrief](https://www.simbrief.com/) (public plan export) | — |
| Procedures, ILS, live flight data | your MSFS database and SimConnect via OpenAirBook Link | — |

The OurAirports database downloads once on first launch and refreshes every 30 days. Flight history and routes stay on the phone.

> For flight simulation only. Do not use for real-world navigation.

### Building

Kotlin, Jetpack Compose and Material 3, minSdk 26. OpenAirBook Link: C# and .NET 8 (in `bridge/`).

Locally, `tools/build-local.ps1` builds the APK and Link and runs the tests:

```bash
powershell -ExecutionPolicy Bypass -File tools/build-local.ps1
```

### Releases

Every push to `main` runs the [workflow](.github/workflows/release.yml), which builds the APK and `OpenAirBook-Link.exe` and publishes them as release `v1.0.<run number>`.

Without secrets the APK is signed with the debug key. To keep updates on a fixed key, add these repository secrets: `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
