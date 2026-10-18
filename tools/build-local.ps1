<#
.SYNOPSIS
  Lokalny build OpenTakeoff na przenośnym toolchainie z %LOCALAPPDATA%\OpenTakeoff-toolchain.

.DESCRIPTION
  Ustawia JAVA_HOME, ANDROID_HOME i PATH, buduje APK debug i uruchamia testy JVM.
  Jeśli istnieje folder bridge/, buduje też każdy projekt *.csproj przez dotnet build.
  Kończy się kodem różnym od zera przy pierwszym błędzie.

.PARAMETER SkipTests
  Pomija :app:testDebugUnitTest.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\build-local.ps1
#>
param([switch]$SkipTests)

$ErrorActionPreference = 'Continue'
$repo = Split-Path -Parent $PSScriptRoot
$toolchain = Join-Path $env:LOCALAPPDATA 'OpenTakeoff-toolchain'

$jdk = Join-Path $toolchain 'jdk'
$sdk = Join-Path $toolchain 'android-sdk'
$gradle = Join-Path $toolchain 'gradle\bin\gradle.bat'
$dotnetDir = Join-Path $toolchain 'dotnet'

foreach ($p in @($jdk, $sdk, $gradle)) {
    if (-not (Test-Path $p)) {
        Write-Host "Brak toolchainu: $p" -ForegroundColor Red
        Write-Host "Zainstaluj JDK 17, Android SDK i Gradle 8.11.1 w $toolchain."
        exit 2
    }
}

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:DOTNET_ROOT = $dotnetDir
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$env:DOTNET_NOLOGO = '1'
$env:PATH = "$jdk\bin;$sdk\platform-tools;$dotnetDir;$env:PATH"

$results = [System.Collections.Generic.List[object]]::new()
function Step([string]$name, [scriptblock]$body) {
    Write-Host "`n=== $name ===" -ForegroundColor Cyan
    $sw = [Diagnostics.Stopwatch]::StartNew()
    & $body | Out-Host
    $code = $LASTEXITCODE
    $sw.Stop()
    $results.Add([pscustomobject]@{ Krok = $name; Wynik = $(if ($code -eq 0) { 'OK' } else { "BŁĄD ($code)" }); Czas = '{0:mm\:ss}' -f $sw.Elapsed })
    return $code
}

function Summary {
    Write-Host "`n=== Podsumowanie ===" -ForegroundColor Cyan
    $results | Format-Table -AutoSize | Out-String | Write-Host
    $testDir = Join-Path $repo 'app\build\test-results\testDebugUnitTest'
    if (-not $SkipTests -and (Test-Path $testDir)) {
        $tests = 0; $fail = 0; $skip = 0
        Get-ChildItem $testDir -Filter 'TEST-*.xml' | ForEach-Object {
            $s = ([xml](Get-Content $_.FullName -Raw)).testsuite
            $tests += [int]$s.tests; $fail += [int]$s.failures + [int]$s.errors; $skip += [int]$s.skipped
        }
        Write-Host "Testy: $tests, nieudane: $fail, pominięte: $skip"
    }
    $apk = Get-ChildItem (Join-Path $repo 'app\build\outputs\apk\debug') -Filter '*.apk' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($apk) { Write-Host "APK: $($apk.FullName)" }
}

$tasks = @(':app:assembleDebug')
if (-not $SkipTests) { $tasks += ':app:testDebugUnitTest' }

Push-Location $repo
try {
    $code = Step "gradle $($tasks -join ' ')" { & $gradle @tasks --no-daemon --console=plain }
    if ($code -ne 0) { Summary; exit $code }

    $bridge = Join-Path $repo 'bridge'
    if (Test-Path $bridge) {
        $dotnet = Join-Path $dotnetDir 'dotnet.exe'
        foreach ($proj in Get-ChildItem $bridge -Recurse -Filter '*.csproj' | Where-Object { $_.FullName -notmatch '\\(bin|obj)\\' }) {
            $code = Step "dotnet build $($proj.Name)" { & $dotnet build $proj.FullName -nologo }
            if ($code -ne 0) { Summary; exit $code }
        }
    }
} finally {
    Pop-Location
}

Summary
exit 0
