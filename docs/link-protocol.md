# OpenAirBook Link — protokół v1

Protokół między programem **OpenAirBook Link** (Windows, most do MSFS 2024 przez SimConnect)
a aplikacją **OpenAirBook** (Android) w sieci lokalnej.

| Kanał | Port | Kierunek | Format |
|---|---|---|---|
| Odkrywanie | UDP **47811**, broadcast | PC → sieć, co 2 s | JSON (jeden datagram UTF-8) |
| Sesja | TCP **47810**, WebSocket (`ws://<ip>:47810/`) | dwustronnie | ramki tekstowe, jeden obiekt JSON na ramkę |

Wersja protokołu: **`v: 1`**. Każda wiadomość (w obu kierunkach i w broadcaście) ma pola:

```json
{ "v": 1, "type": "<typ>", ... }
```

Zasady ogólne:

- Nazwy pól w `camelCase`. Nieznane pola należy ignorować (dodawanie pól nie zmienia wersji).
  Zmiana znaczenia lub usunięcie pola = nowa wersja `v`.
- Serwer odrzuca wiadomości z innym `v` błędem `unsupported_version` (sesja zostaje otwarta).
- Maksymalny rozmiar wiadomości od klienta: 16 KiB. Większe zamykają połączenie (kod 1009).
- Jednostki są w nazwach pól (`Ft`, `Kt`, `Fpm`, `Deg`, `Kg`, `C`, `Hz`, `Mhz`, `Pct`).
  Wartości procentowe są w zakresie 0–100 (trym −100…100).
- Kąty: kurs/namiar 0–360, `pitchDeg` dodatni = nos w górę, `bankDeg` dodatni = przechylenie w prawo.
- Brak danych (np. brak silnika) = pole pominięte lub `null`, nigdy wartość zmyślona.

## 1. Odkrywanie (UDP 47811)

Program co 2 s wysyła broadcast (`255.255.255.255` oraz adresy rozgłoszeniowe każdej karty):

```json
{
  "v": 1,
  "type": "announce",
  "name": "OpenAirBook Link",
  "version": "1.0.12",
  "host": "PC-ADAMA",
  "port": 47810,
  "ips": ["192.168.1.20"],
  "source": "msfs"
}
```

`source`: `msfs` (połączony z MSFS), `simulation` (tryb symulacji), `none` (czeka na MSFS).
Telefon nasłuchuje na UDP 47811, a adres serwera bierze z adresu nadawcy datagramu
(lub z `ips`, gdy nadawca jest nieosiągalny). Ręczne wpisanie IP też jest dozwolone.

## 2. Parowanie i sesja

1. Telefon otwiera `ws://<ip>:47810/`.
2. W ciągu 10 s wysyła `hello`. Bez tego serwer zamyka połączenie.
3. Pierwsze parowanie — z 6-cyfrowym kodem widocznym w oknie programu:

```json
{ "v": 1, "type": "hello", "id": "1", "code": "482913",
  "client": { "name": "Pixel 8", "app": "OpenAirBook", "version": "2.0.0" },
  "rateHz": 10 }
```

   Kolejne połączenia — z tokenem z poprzedniej sesji (bez kodu):

```json
{ "v": 1, "type": "hello", "id": "1", "token": "Qm9n...", "client": { "name": "Pixel 8" } }
```

4. Odpowiedź po sukcesie:

```json
{ "v": 1, "type": "hello_ok", "id": "1", "token": "Qm9n...", "server": "OpenAirBook Link",
  "version": "1.0.12", "source": "msfs", "rateHz": 10,
  "limits": { "maxDeflection": 0.8, "axisTimeoutMs": 500 } }
```

   Telefon zapisuje `token` i używa go przy kolejnych połączeniach. Token jest ważny,
   dopóki użytkownik nie kliknie w programie „Unieważnij sparowania”.

5. Błąd: `error` z kodem `pairing_failed` (zły kod / nieznany token). Po 5 nieudanych próbach
   z jednego adresu IP serwer blokuje go na 60 s (`rate_limited`).

`rateHz` — żądana częstotliwość `state`, 1–20 (domyślnie 10). Serwer przycina do zakresu.
Kod parowania można odświeżyć w programie; zmiana kodu nie unieważnia wydanych tokenów.

Przed `hello_ok` serwer odpowiada na każdą inną wiadomość błędem `not_paired`
(wyjątek: `ping`).

## 3. Wiadomości serwer → telefon

### `state` (10–20 Hz)

Wysyłany tylko, gdy jest źródło danych (MSFS lub symulacja). `seq` rośnie o 1, `t` to czas
serwera w ms od epoki Unix.

```json
{
  "v": 1, "type": "state", "seq": 1234, "t": 1792231234567,
  "data": {
    "source": "msfs",
    "aircraft": { "title": "Airbus A320neo", "onGround": false, "paused": false },
    "simTimeUtc": "2026-10-17T12:34:56Z",
    "position": { "lat": 52.1657, "lon": 20.9671, "altMslFt": 12000, "altAglFt": 11630, "altIndFt": 12010 },
    "speed": { "iasKt": 250, "tasKt": 298, "gsKt": 310, "mach": 0.46, "vsFpm": 1800 },
    "attitude": { "pitchDeg": 5.2, "bankDeg": -3.0, "hdgTrueDeg": 331.0, "hdgMagDeg": 325.6, "trackTrueDeg": 333.1 },
    "autopilot": {
      "master": true, "fd": true, "athr": true,
      "hdgDeg": 330, "altFt": 24000, "vsFpm": 1800, "spdKt": 250,
      "modes": { "hdg": true, "nav": false, "apr": false, "loc": false, "alt": false, "vs": false, "flc": true }
    },
    "radios": {
      "com1": { "activeMhz": 121.5, "standbyMhz": 118.3 },
      "com2": { "activeMhz": 121.5, "standbyMhz": 122.8 },
      "nav1": { "activeMhz": 110.3, "standbyMhz": 113.2 },
      "nav2": { "activeMhz": 110.3, "standbyMhz": 113.2 },
      "transponder": { "code": "7000", "mode": "alt", "ident": false }
    },
    "controls": {
      "gearDown": true, "gearPct": 100, "flapsIndex": 1, "flapsPositions": 5, "flapsPct": 25,
      "spoilersPct": 0, "spoilersArmed": true, "parkingBrake": false, "elevatorTrimPct": 2.5
    },
    "lights": { "beacon": true, "strobe": true, "nav": true, "landing": true, "taxi": false,
                "logo": true, "wing": false, "panel": true, "cabin": true },
    "fuel": { "totalKg": 5400, "tanks": { "leftKg": 2500, "centerKg": 400, "rightKg": 2500 } },
    "engines": [ { "n1Pct": 85.1, "n2Pct": 93.0, "rpm": 0, "egtC": 620, "fuelFlowKgH": 1250 } ],
    "environment": { "windDirDeg": 270, "windSpdKt": 25, "oatC": -9.5, "qnhHpa": 1013.2 },
    "axes": { "active": false }
  }
}
```

- `transponder.mode`: `off`, `standby`, `test`, `on`, `alt`, `ground`.
- `transponder.code`: zawsze 4 cyfry ósemkowe jako tekst.
- `engines`: tyle elementów, ile silników ma samolot (maks. 4). `rpm` dla tłokowych/śmigłowych,
  `n1Pct`/`n2Pct` dla turbinowych (u innych 0).
- `axes.active`: `true`, gdy serwer właśnie steruje osiami z telefonu.

### `status`

Wysyłany po `hello_ok` i przy każdej zmianie źródła danych:

```json
{ "v": 1, "type": "status", "source": "none", "simConnected": false, "message": "Czekam na MSFS" }
```

### `ack` / `error`

```json
{ "v": 1, "type": "ack", "id": "c42", "name": "AP_MASTER" }
{ "v": 1, "type": "error", "id": "c43", "code": "not_allowed", "message": "Komenda poza listą dozwolonych" }
```

`id` przepisywany z wiadomości klienta (może być `null`, gdy nie dało się go odczytać).

| `code` | Znaczenie |
|---|---|
| `bad_json` | Nie da się sparsować JSON lub brak `type` |
| `unsupported_version` | `v` różne od 1 |
| `unknown_type` | Nieznany `type` |
| `not_paired` | Wiadomość przed `hello_ok` |
| `pairing_failed` | Zły kod lub token |
| `rate_limited` | Za dużo prób parowania / komend (limit 50 komend/s) |
| `not_allowed` | Komenda spoza listy dozwolonych |
| `invalid_value` | Brak wartości lub wartość poza zakresem |
| `sim_unavailable` | Brak MSFS i wyłączony tryb symulacji |
| `sim_error` | SimConnect odrzucił wywołanie |

### `pong`

```json
{ "v": 1, "type": "pong", "id": "p7", "t": 1792231234000, "serverT": 1792231234012 }
```

## 4. Wiadomości telefon → serwer

### `ping`

`{ "v": 1, "type": "ping", "id": "p7", "t": 1792231234000 }` — odpowiedź `pong` z tym samym
`id` i `t`. Telefon powinien pingować co 5 s; serwer zamyka sesję po 30 s bez żadnej wiadomości.

### `command`

```json
{ "v": 1, "type": "command", "id": "c42", "name": "HEADING_BUG_SET", "value": 270 }
```

Serwer przyjmuje **wyłącznie** nazwy z listy poniżej (dokładna pisownia, wielkie litery).
Nazwy to zdarzenia SimConnect (Event IDs) — z wyjątkiem `XPNDR_MODE`, który zapisuje SimVar.
`value` jest wymagane tylko tam, gdzie podano zakres; w pozostałych jest ignorowane.
Wartość musi być liczbą całkowitą (lub liczbą z zerową częścią ułamkową) w podanym zakresie.

**Autopilot**

| name | value |
|---|---|
| `AP_MASTER`, `AUTOPILOT_ON`, `AUTOPILOT_OFF`, `TOGGLE_FLIGHT_DIRECTOR`, `AUTO_THROTTLE_ARM`, `YAW_DAMPER_TOGGLE` | — |
| `AP_HDG_HOLD`, `AP_NAV1_HOLD`, `AP_APR_HOLD`, `AP_LOC_HOLD`, `AP_BC_HOLD`, `AP_ALT_HOLD`, `AP_VS_HOLD`, `FLIGHT_LEVEL_CHANGE`, `AP_PANEL_SPEED_HOLD` | — |
| `HEADING_BUG_SET` | 0–359 (°) |
| `HEADING_BUG_INC`, `HEADING_BUG_DEC`, `AP_ALT_VAR_INC`, `AP_ALT_VAR_DEC`, `AP_VS_VAR_INC`, `AP_VS_VAR_DEC`, `AP_SPD_VAR_INC`, `AP_SPD_VAR_DEC` | — |
| `AP_ALT_VAR_SET_ENGLISH` | 0–50000 (ft) |
| `AP_VS_VAR_SET_ENGLISH` | −8000–8000 (ft/min) |
| `AP_SPD_VAR_SET` | 0–500 (kt) |

**Radia** (częstotliwości w Hz)

| name | value |
|---|---|
| `COM_RADIO_SET_HZ`, `COM_STBY_RADIO_SET_HZ`, `COM2_RADIO_SET_HZ`, `COM2_STBY_RADIO_SET_HZ` | 118000000–136990000 |
| `NAV1_RADIO_SET_HZ`, `NAV1_STBY_SET_HZ`, `NAV2_RADIO_SET_HZ`, `NAV2_STBY_SET_HZ` | 108000000–117950000 |
| `COM_STBY_RADIO_SWAP`, `COM2_RADIO_SWAP`, `NAV1_RADIO_SWAP`, `NAV2_RADIO_SWAP` | — |

**Transponder**

| name | value |
|---|---|
| `XPNDR_SET` | kod jako liczba dziesiętna o cyfrach 0–7, np. `7000` (serwer zamienia na BCD) |
| `XPNDR_IDENT_ON` | — |
| `XPNDR_MODE` | 0 off, 1 standby, 2 test, 3 on, 4 alt, 5 ground |

**Podwozie, klapy, spoilery, hamulec**

| name | value |
|---|---|
| `GEAR_UP`, `GEAR_DOWN`, `GEAR_TOGGLE` | — |
| `FLAPS_UP`, `FLAPS_DOWN`, `FLAPS_INCR`, `FLAPS_DECR` | — |
| `FLAPS_SET` | 0–16383 |
| `SPOILERS_ON`, `SPOILERS_OFF`, `SPOILERS_TOGGLE`, `SPOILERS_ARM_TOGGLE` | — |
| `SPOILERS_SET` | 0–16383 |
| `PARKING_BRAKES` (przełącz) | — |
| `PARKING_BRAKE_SET` | 0–1 |

**Światła** (przełączniki)

`TOGGLE_BEACON_LIGHTS`, `STROBES_TOGGLE`, `TOGGLE_NAV_LIGHTS`, `LANDING_LIGHTS_TOGGLE`,
`TOGGLE_TAXI_LIGHTS`, `TOGGLE_LOGO_LIGHTS`, `TOGGLE_WING_LIGHTS`, `PANEL_LIGHTS_TOGGLE`,
`TOGGLE_CABIN_LIGHTS` — bez wartości.

**Trymer**

| name | value |
|---|---|
| `ELEV_TRIM_UP`, `ELEV_TRIM_DN`, `AILERON_TRIM_LEFT`, `AILERON_TRIM_RIGHT`, `RUDDER_TRIM_LEFT`, `RUDDER_TRIM_RIGHT` | — |
| `ELEVATOR_TRIM_SET` | −16383–16383 |

Odpowiedź: `ack` po przekazaniu do symulatora (nie oznacza, że samolot zareagował — skutek
widać w kolejnych `state`), albo `error`.

### `axis` — sterowanie przechylaniem telefonu

```json
{ "v": 1, "type": "axis", "aileron": -0.25, "elevator": 0.10, "rudder": 0.0 }
```

- Zakres każdej osi −1…1: `aileron` + = w prawo, `elevator` + = drążek do siebie (nos w górę),
  `rudder` + = w prawo. Pominięta oś = bez zmian; `NaN`/brak liczby = `invalid_value`.
- Serwer **przycina wychylenie** do ±`maxDeflection` (domyślnie 0,8; podawane w `hello_ok`).
- Telefon musi wysyłać `axis` co najmniej 5 razy na sekundę (zalecane 20 Hz) tak długo, jak
  steruje. **Auto-neutral:** jeśli przez 500 ms nie przyjdzie żadne `axis`, serwer ustawia
  wszystkie trzy osie na 0 i przestaje je nadpisywać (oddaje sterowanie joystickowi).
  To samo dzieje się po rozłączeniu telefonu.
- `{ "v": 1, "type": "axis", "release": true }` — natychmiastowe zwolnienie (neutral i koniec).
- Tylko jeden telefon steruje osiami naraz — pierwszy, który zaczął; inne dostają `error`
  `not_allowed` do czasu zwolnienia.
- `axis` nie dostaje `ack` (za duża częstotliwość) — tylko `error`.

W MSFS osie trafiają do zdarzeń `AXIS_AILERONS_SET`, `AXIS_ELEVATOR_SET`, `AXIS_RUDDER_SET`
(±16383).

## 5. Bezpieczeństwo

- Serwer działa tylko w sieci lokalnej, bez TLS. Kod parowania + token chronią przed
  przypadkowym sterowaniem z innego urządzenia w tej samej sieci, nie przed atakiem w sieci.
- Bez sparowania nie da się ani czytać danych, ani wysyłać komend.
- Serwer nigdy nie wykonuje nazw spoza listy — telefon nie może wysłać dowolnego zdarzenia
  ani zapisać dowolnej zmiennej symulatora.
