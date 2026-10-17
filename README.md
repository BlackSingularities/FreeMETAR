# FreeMETAR

Aplikacja na Androida dla wirtualnych pilotów (MSFS, X-Plane, VATSIM, IVAO). Pokazuje pogodę i dane lotnisk wyłącznie z darmowych, otwartych źródeł — bez kont, kluczy API i reklam.

## Funkcje

- **Briefing lotniska** czytany z góry na dół: pogoda, prognoza, pasy, radio, lotnisko, okolica, z paskiem skoków do rozdziałów
- **Interaktywny METAR**: dotknij dowolnej grupy (np. `BKN025CB`), a pod spodem pojawi się jej znaczenie po polsku
- **ATIS na głos**: komunikat w stylu ATIS złożony z METAR, czytany po angielsku przez syntezator mowy telefonu
- **TAF podzielony na okresy** z kategorią lotu dla każdego (VFR/MVFR/IFR/LIFR)
- Ostrzeżenia: burza i CB, oblodzenie, silne porywy, ryzyko mgły; oznaczenie nieaktualnych depesz
- Pas wg wiatru, składowe czołowe i boczne dla każdego kierunku, schemat lotniska ze strzałką wiatru
- Częstotliwości (dotknij, aby skopiować), radiolatarnie do 40 NM, wschód/zachód słońca i zmierzch cywilny
- Trendy temperatury, wiatru i QNH z 12 godzin, wcześniejsze depesze
- **Paski lotu** na ekranie startowym jak na stanowisku kontrolera; ulubione z układaniem kolejności
- **W pobliżu Ciebie**: najbliższe lotniska z pogodą wg przybliżonej lokalizacji (bez usług Google)
- Planowanie: trasa z pogodą na obu końcach, kalkulator wiatru, wysokość gęstościowa i TAS, zniżanie (TOD), paliwo, jednostki, dekoder METAR/TAF, ściąga radiowa
- Motyw dzienny (kolory map VFR) i nocny (kokpit), układ na telefon i tablet, automatyczne aktualizacje z GitHuba

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
