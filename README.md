# OpenAirBook

Aplikacja na Androida dla wirtualnych pilotów (MSFS, X-Plane, VATSIM, IVAO). Pogoda, dane lotnisk, plan lotu i kokpit na telefonie — wyłącznie z darmowych, otwartych źródeł, bez kont, kluczy API i reklam. Interfejs po polsku i angielsku.

**Pomysł i wykonanie: [redzikowski.com](https://redzikowski.com)**

*English: free aviation weather, airport data, flight planning and a phone cockpit for flight-sim pilots. Free and open data only. Polish and English UI (follows the system language or can be set manually).*

## Funkcje

- **Briefing lotniska**: pogoda, interaktywny METAR (dotknij grupy, by zobaczyć jej znaczenie), TAF w okresach z kategorią lotu, SIGMET/PIREP, wiatry na wysokościach, pasy ze składowymi wiatru, częstotliwości i ILS, okolica
- **ATIS na głos** z bieżącego METAR
- **Mapa** (MapLibre + OpenFreeMap): lotniska z kategorią lotu, trasa, zagrożenia
- **Plan lotu**: import z SimBrief, wpisana trasa lub plik; edytor z wyborem SID/STAR/podejścia (dane z MSFS przez Link), eksport i import tras
- **Charty FAA** (USA) z przeglądarką PDF
- **Kokpit** po połączeniu z MSFS 2024 przez OpenAirBook Link: PFD zawsze na widoku, ND, autopilot zależny od samolotu (Airbus FCU, Boeing MCP, Garmin, GA), radio, systemy, silniki
- **Sterowanie**: drążek na ekranie, przechylanie telefonu albo fizyczny kontroler/joystick Bluetooth lub USB (osie i przyciski przekazywane do symulatora)
- **Historia lotów** z torem, wykresami i oceną lądowania (prędkość pionowa, przeciążenie, oś pasa)
- Narzędzia: kalkulator wiatru, wysokość gęstościowa, zniżanie, paliwo, jednostki, dekoder METAR/TAF, ściąga radiowa
- Motyw AMOLED, grafitowy i jasny; układ na telefon i tablet; automatyczne aktualizacje z GitHuba

## Źródła danych

| Dane | Źródło | Licencja |
|---|---|---|
| METAR, TAF, SIGMET, PIREP | [aviationweather.gov Data API](https://aviationweather.gov/data/api/) | domena publiczna (NOAA) |
| Lotniska, pasy, częstotliwości, navaidy | [OurAirports](https://ourairports.com/data/) | domena publiczna |
| Wiatr i temperatura na wysokościach | [Open-Meteo](https://open-meteo.com/) | CC BY 4.0 |
| Podkład mapy | [OpenFreeMap](https://openfreemap.org/) / OpenStreetMap | ODbL |
| Charty (USA) | [FAA d-TPP](https://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/dtpp/) | domena publiczna |
| Plan lotu | [SimBrief](https://www.simbrief.com/) (publiczny eksport planu) | — |
| Procedury, ILS, stan lotu | baza i SimConnect Twojego MSFS przez OpenAirBook Link | — |

Baza OurAirports pobiera się raz przy pierwszym uruchomieniu i odświeża co 30 dni.

> Tylko do symulacji lotów. Nie używaj do rzeczywistej nawigacji.

## Budowanie

Kotlin + Jetpack Compose + Material 3, minSdk 26. Lokalnie: `tools/build-local.ps1` (APK, testy JVM, OpenAirBook Link).

```bash
gradle :app:assembleRelease
```

## Release

Każdy push na `main` uruchamia [workflow](.github/workflows/release.yml), który buduje APK i publikuje release `v1.0.<numer_przebiegu>`.

Domyślnie APK jest podpisane kluczem debug. Aby aktualizacje instalowały się na stałym kluczu, dodaj sekrety repozytorium: `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
