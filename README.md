# FreeMETAR

Aplikacja na Androida dla wirtualnych pilotów (MSFS, X-Plane, VATSIM, IVAO). Pokazuje pogodę i dane lotnisk wyłącznie z darmowych, otwartych źródeł — bez kont, kluczy API i reklam.

## Funkcje

- **Pogoda**: aktualny METAR z kategorią lotu (VFR/MVFR/IFR/LIFR), rozkodowanie depeszy po polsku, TAF podzielony na okresy, historia z 12 h
- **Kafelki**: wiatr, widzialność, temperatura/punkt rosy, QNH (hPa/inHg), QFE, pułap chmur, wilgotność, wysokość ciśnieniowa i gęstościowa, ryzyko oblodzenia gaźnika
- **Pasy**: schemat lotniska ze strzałką wiatru, składowe czołowe/boczne dla każdego kierunku (także w porywach), preferowany pas, długość, szerokość, nawierzchnia, oświetlenie
- **Radio**: częstotliwości ATIS/DEL/GND/TWR/APP/DEP, pomoce nawigacyjne (VOR, NDB, DME) w promieniu 40 NM z kursem i odległością
- **Info**: współrzędne, elewacja, wschód/zachód słońca i zmierzch cywilny, odnośniki do SkyVector, Windy, VATSIM Radar, OSM
- **Okolica**: lotniska w promieniu 60 NM z bieżącą pogodą
- **Narzędzia**: kalkulator wiatru, wysokość gęstościowa i TAS, planowanie zniżania (TOD), paliwo, przelicznik jednostek, dekoder dowolnego METAR/TAF
- Ulubione z podglądem METAR, ostatnio oglądane, zegar UTC, automatyczne odświeżanie

## Źródła danych

| Dane | Źródło | Licencja |
|---|---|---|
| METAR, TAF | [aviationweather.gov Data API](https://aviationweather.gov/data/api/) | domena publiczna (NOAA) |
| Lotniska, pasy, częstotliwości, navaidy | [OurAirports](https://ourairports.com/data/) | domena publiczna |

Baza OurAirports (~15 MB) pobiera się raz przy pierwszym uruchomieniu i odświeża co 30 dni.

> Tylko do symulacji lotów. Nie używaj do rzeczywistej nawigacji.

## Budowanie

Kotlin + Jetpack Compose + Material 3, minSdk 26.

```bash
gradle :app:assembleRelease
```

## Release

Każdy push na `main` uruchamia [workflow](.github/workflows/release.yml), który buduje APK i publikuje release `v1.0.<numer_przebiegu>`.

Domyślnie APK jest podpisane kluczem debug. Aby aktualizacje instalowały się na stałym kluczu, dodaj sekrety repozytorium: `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
