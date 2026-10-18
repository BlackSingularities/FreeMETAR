<#
.SYNOPSIS
  Automatyczne zrzuty ekranów OpenAirBook na emulatorze (telefony i tablet, pionowo i poziomo).

.DESCRIPTION
  Używa przenośnego toolchainu z %LOCALAPPDATA%\OpenAirBook-toolchain (emulator, platform-tools,
  obraz system-images;android-35;google_apis;x86_64). AVD trzyma w toolchainie (ANDROID_AVD_HOME)
  i tworzy je sam, jeśli ich brak.

  Dla każdego AVD: start bez okna, instalacja APK, przejście po ekranach i zrzuty do
  build\screens\<avd>\<ekran>.png oraz <ekran>-land.png (obrót przez settings user_rotation).

  Aplikacja nie ma deep linków, więc nawigacja idzie po tekście: skrypt czyta drzewo
  `uiautomator dump`, szuka elementu po text/content-desc i stuka w środek jego granic.
  Dzięki temu działa na każdej rozdzielczości bez tablicy współrzędnych.

.PARAMETER Avd
  Nazwy AVD (domyślnie wszystkie trzy): oab_phone_360 (360×800 dp), oab_phone_412 (412×915 dp),
  oab_tablet_1280 (1280×800 dp).

.PARAMETER Apk
  Ścieżka APK (domyślnie app\build\outputs\apk\debug\app-debug.apk).

.PARAMETER Screens
  Które ekrany: home, search, briefing, map, flight, plan, settings (domyślnie wszystkie).

.PARAMETER Orientation
  portrait, landscape albo oba (domyślnie oba).

.PARAMETER KeepRunning
  Nie wyłączaj emulatora na końcu.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\screenshots.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\screenshots.ps1 -Avd oab_phone_360 -Screens briefing,plan -Orientation portrait
#>
param(
    [string[]]$Avd = @('oab_phone_360', 'oab_phone_412', 'oab_tablet_1280'),
    [string]$Apk,
    [ValidateSet('home', 'search', 'briefing', 'map', 'flight', 'plan', 'settings')]
    [string[]]$Screens = @('home', 'search', 'briefing', 'map', 'flight', 'plan', 'settings'),
    [ValidateSet('portrait', 'landscape')]
    [string[]]$Orientation = @('portrait', 'landscape'),
    [switch]$KeepRunning
)

$ErrorActionPreference = 'Continue'
$repo = Split-Path -Parent $PSScriptRoot
$toolchain = Join-Path $env:LOCALAPPDATA 'OpenAirBook-toolchain'
$sdk = Join-Path $toolchain 'android-sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$avdmanager = Join-Path $sdk 'cmdline-tools\latest\bin\avdmanager.bat'
$image = 'system-images;android-35;google_apis;x86_64'
$pkg = 'com.freemetar.app'
if (-not $Apk) { $Apk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk' }

$env:JAVA_HOME = Join-Path $toolchain 'jdk'
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_AVD_HOME = Join-Path $toolchain 'avd'
New-Item -ItemType Directory -Force $env:ANDROID_AVD_HOME | Out-Null

foreach ($p in @($adb, $emulator, $avdmanager, $Apk)) {
    if (-not (Test-Path $p)) { Write-Host "Brak: $p" -ForegroundColor Red; exit 2 }
}

# Definicje AVD: rozmiar w dp × gęstość = piksele
$defs = @{
    'oab_phone_360'   = @{ W = 945;  H = 2100; Dpi = 420; Port = 5560 }
    'oab_phone_412'   = @{ W = 1080; H = 2400; Dpi = 420; Port = 5562 }
    'oab_tablet_1280' = @{ W = 2560; H = 1600; Dpi = 320; Port = 5564 }
}

$tmp = Join-Path $env:TEMP 'oab-screens'
New-Item -ItemType Directory -Force $tmp | Out-Null
$script:serial = $null

function Log([string]$m) { Write-Host ("[{0:HH:mm:ss}] {1}" -f (Get-Date), $m) }
function A { & $adb -s $script:serial @args 2>$null }
function Sh([string]$cmd) { & $adb -s $script:serial shell $cmd 2>$null }

function Ensure-Avd([string]$name) {
    $d = $defs[$name]
    if (-not $d) { throw "Nieznany AVD $name (znane: $($defs.Keys -join ', '))" }
    $dir = Join-Path $env:ANDROID_AVD_HOME "$name.avd"
    if (-not (Test-Path $dir)) {
        Log "Tworzę AVD $name"
        # Przez cmd: średniki w nazwie obrazu rozbijałyby argument wywołania .bat
        cmd /c "echo no| `"$avdmanager`" create avd -n $name -k `"$image`" -f" 2>&1 | Out-Null
        if (-not (Test-Path $dir)) { throw "avdmanager nie utworzył $dir" }
    }
    $cfg = Join-Path $dir 'config.ini'
    $lines = Get-Content $cfg | Where-Object { $_ -notmatch '^(hw\.lcd\.(width|height|density)|hw\.ramSize|hw\.keyboard|hw\.gpu\.(enabled|mode)|showDeviceFrame|disk\.dataPartition\.size|hw\.initialOrientation)\s*=' }
    $lines += "hw.lcd.width=$($d.W)", "hw.lcd.height=$($d.H)", "hw.lcd.density=$($d.Dpi)", 'hw.ramSize=3072',
        'hw.keyboard=no', 'hw.gpu.enabled=yes', 'hw.gpu.mode=swiftshader_indirect', 'showDeviceFrame=no',
        'disk.dataPartition.size=4G', "hw.initialOrientation=$(if ($d.W -gt $d.H) { 'landscape' } else { 'portrait' })"
    Set-Content $cfg $lines -Encoding ASCII
}

function Start-Avd([string]$name) {
    $d = $defs[$name]
    $script:serial = "emulator-$($d.Port)"
    if ((& $adb devices) -match "$($script:serial)\s+device") { Log "$name już działa"; return }
    Log "Start $name ($($d.W)x$($d.H) @$($d.Dpi))"
    $log = Join-Path $tmp "$name-emulator.log"
    Start-Process $emulator -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError "$log.err" -ArgumentList @(
        '-avd', $name, '-port', $d.Port, '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot',
        '-gpu', 'swiftshader_indirect', '-netdelay', 'none', '-netspeed', 'full')
    $sw = [Diagnostics.Stopwatch]::StartNew()
    & $adb -s $script:serial wait-for-device 2>$null
    while ((Sh 'getprop sys.boot_completed') -notmatch '1') {
        if ($sw.Elapsed.TotalMinutes -gt 10) { throw "Emulator $name nie wystartował w 10 min (log: $log)" }
        Start-Sleep 3
    }
    Log ("Uruchomiony po {0:mm\:ss}" -f $sw.Elapsed)
    foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') { Sh "settings put global $k 0" | Out-Null }
    Sh 'settings put system accelerometer_rotation 0' | Out-Null
    Sh 'settings put secure show_ime_with_hard_keyboard 1' | Out-Null
    Sh 'svc power stayon true' | Out-Null
    Sh 'input keyevent 82' | Out-Null   # odblokuj ekran
    Start-Sleep 20                       # niech launcher i usługi Google się uspokoją (inaczej ANR)
    Dismiss-Dialogs
    # Ukryj powiadomienia (np. Google), by nie zasłaniały paska stanu
    Sh 'cmd notification set_dnd priority' | Out-Null
}

function Stop-Avd { A emu kill | Out-Null; Start-Sleep 3 }

# --- UI: drzewo uiautomator i stuknięcia po tekście ---

function Get-Nodes {
    for ($i = 0; $i -lt 4; $i++) {
        Sh 'uiautomator dump /sdcard/oab-ui.xml' | Out-Null
        $f = Join-Path $tmp 'ui.xml'
        Remove-Item $f -ErrorAction SilentlyContinue
        A pull /sdcard/oab-ui.xml $f | Out-Null
        if (Test-Path $f) {
            try {
                $x = [xml](Get-Content $f -Raw -Encoding UTF8)
                return @($x.SelectNodes('//node') | ForEach-Object {
                    if ($_.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                        [pscustomobject]@{
                            Text = $_.text; Desc = $_.'content-desc'; Class = $_.class
                            X1 = [int]$Matches[1]; Y1 = [int]$Matches[2]; X2 = [int]$Matches[3]; Y2 = [int]$Matches[4]
                        }
                    }
                })
            } catch { }
        }
        Start-Sleep 1
    }
    return @()
}

function Find-Node([string]$label, [switch]$Contains, $nodes) {
    if (-not $nodes) { $nodes = Get-Nodes }
    $hit = $nodes | Where-Object {
        if ($Contains) { ($_.Text -and $_.Text.Contains($label)) -or ($_.Desc -and $_.Desc.Contains($label)) }
        else { $_.Text -eq $label -or $_.Desc -eq $label }
    } | Where-Object { $_.X2 -gt $_.X1 -and $_.Y2 -gt $_.Y1 } | Select-Object -First 1
    return $hit
}

function Wait-Node([string]$label, [int]$seconds = 30, [switch]$Contains) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $seconds) {
        $n = Find-Node $label -Contains:$Contains
        if ($n) { return $n }
        Start-Sleep 1
    }
    return $null
}

function Tap-Node($n) { Sh "input tap $([int](($n.X1 + $n.X2) / 2)) $([int](($n.Y1 + $n.Y2) / 2))" | Out-Null }

function Tap([string]$label, [switch]$Contains, [int]$wait = 10) {
    $n = Wait-Node $label $wait -Contains:$Contains
    if (-not $n) { Log "  ! nie znaleziono '$label'"; return $false }
    Tap-Node $n
    Start-Sleep -Milliseconds 900
    return $true
}

function Get-ScreenSize {
    # Granice korzenia drzewa UI = bieżący ekran z uwzględnieniem obrotu
    $root = Get-Nodes | Sort-Object { ($_.X2 - $_.X1) * ($_.Y2 - $_.Y1) } -Descending | Select-Object -First 1
    $dens = [int](([regex]::Match(((Sh 'wm density') -join ' '), '(\d+)\s*$')).Groups[1].Value)
    [pscustomobject]@{ W = $root.X2; H = $root.Y2; Dens = $dens; WideDp = ($root.X2 * 160 / $dens) }
}

function Swipe-Up([double]$xFrac = 0.5) {
    $s = $script:size
    $x = [int]($s.W * $xFrac)
    Sh "input swipe $x $([int]($s.H * 0.78)) $x $([int]($s.H * 0.30)) 450" | Out-Null
    Start-Sleep -Milliseconds 900
}

function Shot([string]$name) {
    $suffix = if ($script:land) { '-land' } else { '' }
    $file = Join-Path $script:outDir "$name$suffix.png"
    Sh 'screencap -p /sdcard/oab-shot.png' | Out-Null
    A pull /sdcard/oab-shot.png $file | Out-Null
    if (Test-Path $file) { Log "  zrzut $name$suffix" } else { Log "  ! brak zrzutu $name$suffix" }
}

function Back { Sh 'input keyevent 4' | Out-Null; Start-Sleep -Milliseconds 900 }
function Hide-Keyboard {
    if ((Sh 'dumpsys input_method') -match 'mInputShown=true') { Back }
}

function Dismiss-Dialogs {
    # Systemowe „isn't responding” po zimnym starcie i dialog aktualizacji z GitHuba
    for ($i = 0; $i -lt 3; $i++) {
        $nodes = Get-Nodes
        $n = $nodes | Where-Object { $_.Text -in 'Wait', 'Później' } | Select-Object -First 1
        if (-not $n) { return }
        Log "  zamykam dialog: $($n.Text)"
        Tap-Node $n
        Start-Sleep 1
    }
}

function Launch-App {
    Sh "am force-stop $pkg" | Out-Null
    Sh "am start -W -n $pkg/.MainActivity" | Out-Null
    # Pierwsze uruchomienie pobiera bazę lotnisk (~15 MB)
    if (-not (Wait-Node 'Lotnisko: ICAO' 60 -Contains)) { Log '  ! brak pola wyszukiwania po starcie' }
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ((Find-Node 'Pierwsze uruchomienie' -Contains) -and $sw.Elapsed.TotalSeconds -lt 180) { Start-Sleep 3 }
    Start-Sleep 2
    Dismiss-Dialogs
}

function Go-Tab([string]$label) {
    # Dolny pasek (telefon) albo NavigationRail (szeroko) — te same etykiety
    $nodes = Get-Nodes
    $n = $nodes | Where-Object { $_.Text -eq $label -or $_.Desc -eq $label } | Sort-Object { if ($script:size.WideDp -ge 600) { $_.X1 } else { - $_.Y1 } } | Select-Object -First 1
    if (-not $n) { Log "  ! brak zakładki '$label'"; return }
    Tap-Node $n
    Start-Sleep 2
}

# --- Przejście po ekranach ---

function Run-Pass {
    $want = { param($s) $Screens -contains $s }
    Launch-App
    $script:size = Get-ScreenSize
    $wide = $script:size.WideDp -ge 600
    Log ("  ekran {0}x{1} px, {2:N0} dp szerokości, {3}" -f $script:size.W, $script:size.H, $script:size.WideDp, $(if ($wide) { 'układ szeroki' } else { 'układ wąski' }))

    if (& $want 'home') { Shot 'home'; Swipe-Up; Shot 'home-2' }

    if ((& $want 'search') -or (& $want 'briefing')) {
        Launch-App
        if (Tap 'Lotnisko: ICAO' -Contains) {
            Sh 'input text EPWA' | Out-Null
            Start-Sleep 2
            if (& $want 'search') { Shot 'search' }
            if (& $want 'briefing') {
                Sh 'input keyevent 66' | Out-Null  # IME Szukaj → pierwszy wynik
                Start-Sleep 2
                if (-not (Wait-Node 'EPWA' 15)) { Log '  ! briefing nie otworzył się' }
                Wait-Node 'Pogoda teraz' 40 | Out-Null
                Start-Sleep 4  # TAF, wiatr na wysokościach, NOTAM-y dochodzą osobno
                Shot 'briefing-pogoda'
                if ($wide) {
                    # Dwie kolumny: lewa (pogoda…wysokości), prawa (pasy…okolica)
                    for ($i = 1; $i -le 3; $i++) { Swipe-Up 0.35; Shot "briefing-lewa-$i" }
                    for ($i = 1; $i -le 3; $i++) { Swipe-Up 0.8; Shot "briefing-prawa-$i" }
                } else {
                    $parts = [ordered]@{ 'Zagrożenia' = 'zagrozenia'; 'Prognoza' = 'taf'; 'Wysokości' = 'wysokosci'; 'Pasy' = 'pasy'; 'Radio' = 'radio'; 'Lotnisko' = 'lotnisko'; 'Okolica' = 'okolica' }
                    foreach ($k in $parts.Keys) {
                        $nodes = Get-Nodes
                        # Pasek skoków jest tuż pod nagłówkiem — bierz najwyższe dopasowanie
                        $n = $nodes | Where-Object { $_.Text -eq $k } | Sort-Object Y1 | Select-Object -First 1
                        if (-not $n) {
                            # Pasek przewijany poziomo: przesuń go w lewo i spróbuj ponownie
                            $bar = $nodes | Where-Object { $_.Text -eq 'Pogoda' -or $_.Text -eq 'Radio' } | Sort-Object Y1 | Select-Object -First 1
                            if ($bar) { $y = [int](($bar.Y1 + $bar.Y2) / 2); Sh "input swipe $([int]($script:size.W * 0.8)) $y $([int]($script:size.W * 0.2)) $y 300" | Out-Null; Start-Sleep 1 }
                            $n = Get-Nodes | Where-Object { $_.Text -eq $k } | Sort-Object Y1 | Select-Object -First 1
                        }
                        if ($n) { Tap-Node $n; Start-Sleep 2 } else { Log "  ! brak rozdziału '$k' — przewijam"; Swipe-Up }
                        Shot "briefing-$($parts[$k])"
                    }
                    Swipe-Up; Shot 'briefing-koniec'
                }
                Back
            }
            Hide-Keyboard
        }
    }

    if (& $want 'map') {
        Launch-App; Hide-Keyboard
        Go-Tab 'Mapa'; Start-Sleep 6; Shot 'map'
    }

    if (& $want 'flight') {
        Launch-App
        Go-Tab 'Lot'; Start-Sleep 3; Shot 'flight'
        Swipe-Up; Shot 'flight-2'; Swipe-Up; Shot 'flight-3'
    }

    if (& $want 'plan') {
        Launch-App
        Go-Tab 'Plan'; Start-Sleep 2
        $tools = [ordered]@{ 'Trasa' = 'trasa'; 'Wiatr' = 'wiatr'; 'Wys. gęst.' = 'gestosc'; 'Zniżanie' = 'znizanie'; 'Paliwo' = 'paliwo'; 'Jednostki' = 'jednostki'; 'Dekoder' = 'dekoder'; 'Ściąga' = 'sciaga' }
        $chipY = $null
        foreach ($k in $tools.Keys) {
            $nodes = Get-Nodes
            $n = $nodes | Where-Object { $_.Text -eq $k } | Sort-Object Y1 | Select-Object -First 1
            if ($n -and -not $chipY) { $chipY = [int](($n.Y1 + $n.Y2) / 2) }
            if (-not $n -and $chipY) {
                Sh "input swipe $([int]($script:size.W * 0.8)) $chipY $([int]($script:size.W * 0.3)) $chipY 300" | Out-Null
                Start-Sleep 1
                $n = Get-Nodes | Where-Object { $_.Text -eq $k } | Sort-Object Y1 | Select-Object -First 1
            }
            if (-not $n) { Log "  ! brak narzędzia '$k'"; continue }
            Tap-Node $n; Start-Sleep 2
            Shot "plan-$($tools[$k])"
            if ($k -in 'Trasa', 'Ściąga', 'Dekoder') {
                Swipe-Up; Shot "plan-$($tools[$k])-2"
                # Wróć na górę, żeby chipy były widoczne
                for ($i = 0; $i -lt 3; $i++) { Sh "input swipe $([int]($script:size.W / 2)) $([int]($script:size.H * 0.3)) $([int]($script:size.W / 2)) $([int]($script:size.H * 0.85)) 250" | Out-Null }
                Start-Sleep 1
            }
        }
    }

    if (& $want 'settings') {
        Launch-App
        Go-Tab 'Więcej'; Start-Sleep 2; Shot 'settings'
        Swipe-Up; Shot 'settings-2'; Swipe-Up; Shot 'settings-3'
    }
}

$total = [Diagnostics.Stopwatch]::StartNew()
& $adb start-server 2>$null | Out-Null
foreach ($name in $Avd) {
    try {
        Ensure-Avd $name
        Start-Avd $name
        $script:natLandscape = $defs[$name].W -gt $defs[$name].H
        $script:outDir = Join-Path $repo "build\screens\$name"
        New-Item -ItemType Directory -Force $script:outDir | Out-Null

        Log "Instaluję $Apk"
        A install -r -g $Apk | Out-Host
        Sh "pm grant $pkg android.permission.ACCESS_COARSE_LOCATION" | Out-Null

        foreach ($o in $Orientation) {
            $script:land = $o -eq 'landscape'
            # user_rotation: 0 = naturalna orientacja urządzenia, 1 = obrót o 90°
            $nat = $script:natLandscape
            $rot = if ($script:land -xor $nat) { 1 } else { 0 }
            Sh "settings put system user_rotation $rot" | Out-Null
            Start-Sleep 2
            Log "$name — $o"
            Run-Pass
        }
        Sh 'settings put system user_rotation 0' | Out-Null
    } catch {
        Log "BŁĄD na $name`: $_"
    } finally {
        if (-not $KeepRunning) { Stop-Avd }
    }
}
Log ("Gotowe w {0:mm\:ss}. Zrzuty: {1}" -f $total.Elapsed, (Join-Path $repo 'build\screens'))
