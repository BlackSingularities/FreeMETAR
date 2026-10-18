# Synchronizacja z Google Drive

OpenTakeoff używa Google Drive jako **głównego źródła danych** dla dziennika lotów.

## Jak to działa

### Struktura danych na Google Drive

```
Google Drive/
└── OpenTakeoff/
    ├── flights.oab          ← główny plik (bieżące dane)
    └── flights-backup.oab   ← poprzednia wersja (jeden backup)
```

### Automatyczna synchronizacja

Dane są automatycznie synchronizowane w następujących przypadkach:

1. **Po każdym zapisie lotu** - gdy lot się kończy lub zapisujesz go ręcznie
2. **Po każdym usunięciu lotu** - gdy usuwasz lot z dziennika

### Strategia backupu

- Przed zapisem nowej wersji, obecny plik `flights.oab` staje się `flights-backup.oab`
- Stary backup jest usuwany
- Dzięki temu **zawsze masz 2 wersje**: bieżącą i poprzednią

## Pierwsze uruchomienie

### Logowanie

1. Otwórz **Ustawienia** → sekcja **Google Drive**
2. Kliknij **"Zaloguj do Google"**
3. Wybierz swoje konto Google
4. Nadaj uprawnienia do Google Drive

### Co się dzieje po zalogowaniu?

Aplikacja automatycznie:
- Sprawdza czy masz już dane na Google Drive
- Jeśli **TAK** i lokalnie jest pusto → pobiera dane z Drive
- Jeśli lokalnie są dane, ale na Drive **NIE** → wysyła lokalne dane na Drive
- Od tej pory wszystko jest synchronizowane automatycznie

## Użycie na wielu urządzeniach

### Nowy telefon / reinstalacja

1. Zainstaluj OpenTakeoff
2. Zaloguj się do Google Drive
3. Aplikacja automatycznie pobierze wszystkie Twoje loty

### Dwa telefony jednocześnie

Możesz używać aplikacji na dwóch urządzeniach:

- **Automatyczna synchronizacja** działa osobno na każdym
- Jeśli masz nowe loty na obu, użyj **"Pobierz"** żeby scalić dane
- Aplikacja nie tworzy duplikatów - loty są rozpoznawane po unikalnym ID

## Ręczna synchronizacja

W sekcji **Google Drive** w ustawieniach masz przyciski:

- **Pobierz** - ściągnij najnowsze dane z Drive i scal z lokalnymi
- **Wyślij** - wyślij lokalne dane na Drive (nadpisuje co jest w chmurze)
- **Backup** - przywróć poprzednią wersję z `flights-backup.oab`

## Bezpieczeństwo danych

### Co jest przechowywane?

Tylko dziennik lotów w formacie `.oab`:
- Trasy lotów (pozycje GPS, wysokości, prędkości)
- Czasy lotów
- Oceny lądowań
- Kody lotnisk

### Gdzie?

- W folderze **OpenTakeoff** na Twoim Google Drive
- Aplikacja ma dostęp **tylko do tego folderu**, nie do reszty Drive
- Dane są Twoje - możesz je ściągnąć, usunąć lub wyeksportować w każdej chwili

### Prywatność

- Nie ma serwera pośredniczącego - dane idą bezpośrednio z telefonu do Google Drive
- Aplikacja nie zbiera statystyk ani nie wysyła danych nigdzie indziej
- Kod jest otwarty - możesz sprawdzić co robi

## Rozwiązywanie problemów

### "Błąd synchronizacji"

Sprawdź:
- Połączenie z internetem
- Czy nadal jesteś zalogowany (Settings → Google Drive)
- Czy Google Drive ma miejsce (każdy lot to kilka KB, backup też)

### Przywracanie danych

Jeśli przypadkowo usunąłeś loty:
1. **NIE** rób więcej zmian w aplikacji
2. Kliknij **Backup** w ustawieniach Google Drive
3. Przywróci to poprzednią wersję sprzed ostatniej synchronizacji

### Konflikt danych (dwa telefony)

Jeśli nagrywałeś loty na dwóch telefonach bez synchronizacji:
1. Na pierwszym telefonie: **Wyślij** (to stanie się główna wersja)
2. Na drugim telefonie: **Pobierz** (doda loty z pierwszego bez duplikatów)
3. Od teraz synchronizuj regularnie

## Wylogowanie

**Wyloguj** w ustawieniach Google Drive:
- Przestaje synchronizować
- Dane lokalne **zostają** na telefonie
- Dane na Drive **zostają** w chmurze
- Możesz się zalogować ponownie w każdej chwili

## FAQ

**Q: Co jeśli nie mam internetu?**  
A: Aplikacja działa normalnie offline. Synchronizacja uruchomi się automatycznie gdy będzie internet.

**Q: Ile miejsca to zajmuje?**  
A: ~1-5 KB na minutę lotu (zależy od częstości próbkowania). 100 godzin lotów to ~15-30 MB.

**Q: Czy mogę używać bez Google Drive?**  
A: Tak - po prostu nie loguj się. Dane będą tylko lokalnie na telefonie.

**Q: Co z eksportem do .oab?**  
A: Nadal działa. Export/Import w sekcji "Zapis lotów" to kopia zapasowa niezależna od Google Drive.

**Q: Czy to zużywa transfer danych?**  
A: Minimalnie - tylko przy synchronizacji. Możesz włączyć synchronizację tylko przez WiFi w ustawieniach telefonu (ograniczenie transferu dla aplikacji).
