# OpenAirBook Link — szybki start

OpenAirBook Link to mały program na Windows, który łączy **MSFS 2024** z aplikacją na telefonie
w tej samej sieci Wi-Fi. Telefon pokazuje samolot na mapie, PFD i panele, a także może sterować
autopilotem, radiami, transponderem, podwoziem, klapami, światłami i lotem (przechylaniem telefonu).

## 1. Pobranie i uruchomienie

1. Pobierz `OpenAirBook-Link.exe` z najnowszego wydania na GitHubie (ten sam release co APK).
2. Uruchom plik — nie wymaga instalacji ani .NET. Przy pierwszym uruchomieniu Windows SmartScreen
   może ostrzec o nieznanym wydawcy: **Więcej informacji → Uruchom mimo to**.
3. Kolejność nie ma znaczenia: program sam połączy się z MSFS, gdy symulator wystartuje
   (i ponownie po jego restarcie). Stan widać w sekcji **Symulator** — zielona kropka = połączono.
4. Zamknięcie okna chowa program do zasobnika obok zegara. Całkowicie zamkniesz go z menu ikony
   (**Zakończ**).

Opcje uruchomienia (np. w skrócie na pulpicie): `--minimized` (start w zasobniku),
`--simulate` / `--no-simulate` (wymuszenie trybu symulacji).

## 2. Zapora Windows

Przy pierwszym uruchomieniu Windows zapyta o dostęp do sieci. Zaznacz **Sieci prywatne** i kliknij
**Zezwalaj**. Sieć domowa musi być ustawiona jako prywatna (Ustawienia → Sieć i Internet →
Wi-Fi/Ethernet → Typ profilu sieci: **Prywatna**).

Jeśli okno zapory się nie pojawiło albo telefon nie widzi komputera, dodaj regułę ręcznie
(PowerShell **jako administrator**; broadcast UDP 47811 wychodzi z komputera i nie wymaga reguły):

```powershell
New-NetFirewallRule -DisplayName "OpenAirBook Link (WebSocket)" -Direction Inbound -Protocol TCP -LocalPort 47810 -Action Allow -Profile Private
```

Sprawdzenie z telefonu: otwórz w przeglądarce `http://<IP komputera>:47810/` — powinien pojawić się
napis „OpenAirBook Link dziala”. Adres IP komputera jest w oknie programu (sekcja **Parowanie**).

## 3. Parowanie telefonu

1. Telefon i komputer muszą być w tej samej sieci (ten sam router; nie sieć „gościnna”).
2. W aplikacji wybierz połączenie z MSFS. Komputer pojawi się na liście sam (program rozgłasza się
   co 2 s). Jeśli nie — wpisz ręcznie adres IP z okna programu.
3. Wpisz **6-cyfrowy kod** widoczny w oknie programu. Telefon zapamięta parowanie — kolejne
   połączenia nie wymagają kodu.
4. Połączony telefon pojawi się na liście **Połączone telefony**.

Przycisk **Nowy kod** zmienia kod (już sparowane telefony działają dalej).
**Unieważnij sparowania** odłącza wszystkie telefony — każdy będzie musiał podać nowy kod.
Po 5 błędnych kodach z jednego telefonu program blokuje go na minutę.

## 4. Tryb symulacji

Przełącznik **Tryb symulacji** uruchamia lot testowy bez MSFS: start z EPWA (pas 33), wznoszenie
na FL240, przelot, zniżanie i lądowanie w EPGD (pas 29), po czym lot zaczyna się od nowa.
Symulacja przyjmuje komendy z telefonu (autopilot, radia, transponder, światła, podwozie, klapy,
osie), więc da się sprawdzić aplikację bez uruchamiania symulatora. Hamulec postojowy włączony
z telefonu wstrzymuje start.

## 5. Sterowanie przechylaniem telefonu

Telefon wysyła wychylenia lotek, steru wysokości i kierunku. Program ogranicza je do 80% pełnego
wychylenia, a gdy przez pół sekundy nie dostanie aktualizacji (np. zerwane Wi-Fi), sam ustawia
stery w neutrum i oddaje sterowanie joystickowi. Naraz osiami steruje tylko jeden telefon.

## Problemy

| Objaw | Co zrobić |
|---|---|
| „Czekam na MSFS…” mimo działającego symulatora | Poczekaj do załadowania menu MSFS; SimConnect startuje razem z symulatorem. Uruchom program bez uprawnień administratora (albo oba jako administrator). |
| Telefon nie widzi komputera | Profil sieci „Prywatna”, reguła zapory z punktu 2, wyłączona izolacja klientów Wi-Fi w routerze; ewentualnie wpisz IP ręcznie. |
| „Zły kod parowania” | Kod zmienia się przy każdym uruchomieniu programu — przepisz aktualny z okna. |
| Port 47810 zajęty | Inna kopia programu działa w zasobniku albo inny program używa portu. |

Szczegóły techniczne: [link-protocol.md](link-protocol.md).
