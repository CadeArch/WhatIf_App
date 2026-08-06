# Tier B: two-device real Espresso end-to-end test (see WhatIf_App's README.md/CHANGELOG.md Tier B
# design notes for background).
#
# Orchestrates two `adb shell am instrument` invocations against two real emulators (Test_API35 as
# host, Test_API35_B as guest, both running the actual app UI), launched at the same time - the
# room-code handoff between them goes through a small Firebase location keyed by a per-run
# correlation ID (see MixedUp/src/androidTest/.../E2ERoomCodeSignal.java), not a logcat signal this
# script has to wait on before starting the guest process, so both roles' own app-launch/
# name-entry/navigation steps genuinely run in parallel instead of the guest's being serialized
# after the host's.
#
# This script also boots both emulators itself if they aren't already running - headless
# (-no-window) by default for speed, since routine runs don't need to be watched. Pass -Visible to
# get real windows (e.g. when you want to watch it play, or a run is proving hard to diagnose from
# logs alone and you want to see the screens directly).
#
# Prerequisites (this script checks/fails fast, does not attempt to fix):
#   1. Firebase Emulator Suite running: firebase emulators:start --only database,auth
#   2. App built with the emulator flag: .\gradlew.bat :MixedUp:assembleDebug -PuseFirebaseEmulator=true
#   3. Test APK built: .\gradlew.bat :MixedUp:assembleDebugAndroidTest
#
# Usage: .\scripts\run-tier-b.ps1 [-TestClass com.CadeMixedUpGame.phoneapp.TwoDeviceFullGameLoopTest] [-Rounds 3] [-Visible]
# Defaults to the minimal join-only smoke test, headless. Any test class following the same
# role/correlationId instrumentation-argument convention as TwoDeviceMultiplayerTest works here
# unchanged. -Rounds is only meaningful for TwoDeviceFullGameLoopTest (its own -e rounds <n>
# instrumentation argument, defaults to 2 if omitted here) - harmless no-op for tests that don't
# read it.

param(
    [string]$TestClass = "com.CadeMixedUpGame.phoneapp.TwoDeviceMultiplayerTest",
    [int]$Rounds = 0,
    [switch]$Visible
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$adb = "D:\AndroidDevData\sdk\Sdk\platform-tools\adb.exe"
$emulatorExe = "D:\AndroidDevData\sdk\Sdk\emulator\emulator.exe"
$apk = Join-Path $repoRoot "MixedUp\build\outputs\apk\debug\MixedUp-debug.apk"
$testApk = Join-Path $repoRoot "MixedUp\build\outputs\apk\androidTest\debug\MixedUp-debug-androidTest.apk"
$packageName = "com.CadeMixedUpGame.phoneapp"
$testPackageName = "com.CadeMixedUpGame.phoneapp.test"
$testRunner = "androidx.test.runner.AndroidJUnitRunner"
$testClass = $TestClass
$hostAvd = "Test_API35"
$guestAvd = "Test_API35_B"
$hostSerial = "emulator-5554"
$guestSerial = "emulator-5556"
$correlationId = [guid]::NewGuid().ToString("N").Substring(0, 12)
$roundsArgs = @()
if ($Rounds -gt 0) {
    $roundsArgs = @("-e", "rounds", "$Rounds")
    Write-Host "Rounds override: $Rounds"
}

if (!(Test-Path $adb)) {
    throw "adb.exe was not found at $adb"
}

Write-Host "Running $testClass (correlationId=$correlationId, $(if ($Visible) { 'visible' } else { 'headless' }))"
Write-Host "Checking Firebase Emulator Suite is reachable on 127.0.0.1:9000..."
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect("127.0.0.1", 9000)
    $tcp.Close()
}
catch {
    throw "Firebase Emulator Suite is not reachable on 127.0.0.1:9000. Start it first with: firebase emulators:start --only database,auth"
}

function Test-DeviceConnected([string]$serial) {
    $connected = & $adb devices | Select-String "`tdevice$" | ForEach-Object { ($_ -split "\s+")[0] }
    return $connected -contains $serial
}

function Wait-ForBootCompleted([string]$serial, [int]$timeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $prop = (& $adb -s $serial shell getprop sys.boot_completed 2>$null) -join ""
        if ($prop.Trim() -eq "1") {
            return $true
        }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Ensure-EmulatorRunning([string]$avdName, [string]$expectedSerial) {
    if (Test-DeviceConnected $expectedSerial) {
        return
    }
    if (!(Test-Path $emulatorExe)) {
        throw "emulator.exe was not found at $emulatorExe, and $expectedSerial isn't already connected."
    }
    Write-Host "Booting $avdName (expecting it to come up as $expectedSerial, $(if ($Visible) { 'visible' } else { 'headless' }))..."
    $emulatorArgs = @("-avd", $avdName, "-no-snapshot-save")
    if (-not $Visible) {
        $emulatorArgs += "-no-window"
    }
    Start-Process -FilePath $emulatorExe -ArgumentList $emulatorArgs | Out-Null

    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline -and -not (Test-DeviceConnected $expectedSerial)) {
        Start-Sleep -Seconds 3
    }
    if (-not (Test-DeviceConnected $expectedSerial)) {
        throw "$avdName did not come online as $expectedSerial within 120s. If it landed on a different serial (port already in use by something else), boot it manually and re-run."
    }
    if (-not (Wait-ForBootCompleted $expectedSerial 120)) {
        throw "$avdName connected as $expectedSerial but did not finish booting within 120s."
    }
    Write-Host "$avdName is up."
}

Ensure-EmulatorRunning $hostAvd $hostSerial
Ensure-EmulatorRunning $guestAvd $guestSerial

if (!(Test-Path $apk)) {
    throw "App APK not found at $apk. Build it first with: .\gradlew.bat :MixedUp:assembleDebug -PuseFirebaseEmulator=true"
}
if (!(Test-Path $testApk)) {
    throw "Test APK not found at $testApk. Build it first with: .\gradlew.bat :MixedUp:assembleDebugAndroidTest"
}

foreach ($serial in @($hostSerial, $guestSerial)) {
    Write-Host "Installing app + test APK on $serial..."
    & $adb -s $serial install -r -t $apk
    & $adb -s $serial install -r -t $testApk
    & $adb -s $serial shell am force-stop $packageName
}

Write-Host "Launching host role on $hostSerial..."
$hostJob = Start-Job -ScriptBlock {
    param($adbPath, $serial, $pkg, $runner, $class, $correlationId, $extraArgs)
    & $adbPath -s $serial shell am instrument -w -e class $class -e role host -e correlationId $correlationId @extraArgs "$pkg/$runner"
} -ArgumentList $adb, $hostSerial, $testPackageName, $testRunner, $testClass, $correlationId, $roundsArgs

Write-Host "Launching guest role on $guestSerial (same time as host - no longer waiting on a logcat-captured room code first)..."
$guestJob = Start-Job -ScriptBlock {
    param($adbPath, $serial, $pkg, $runner, $class, $correlationId, $extraArgs)
    & $adbPath -s $serial shell am instrument -w -e class $class -e role guest -e correlationId $correlationId @extraArgs "$pkg/$runner"
} -ArgumentList $adb, $guestSerial, $testPackageName, $testRunner, $testClass, $correlationId, $roundsArgs

Write-Host "Waiting for both roles to finish..."
$hostResult = Receive-Job -Job $hostJob -Wait
$guestResult = Receive-Job -Job $guestJob -Wait
Remove-Job -Job $hostJob
Remove-Job -Job $guestJob

Write-Host "`n--- Host result ($hostSerial) ---"
Write-Host ($hostResult -join "`n")
Write-Host "`n--- Guest result ($guestSerial) ---"
Write-Host ($guestResult -join "`n")

$hostOk = ($hostResult -join "`n") -match "OK \("
$guestOk = ($guestResult -join "`n") -match "OK \("

if ($hostOk -and $guestOk) {
    Write-Host "`nTier B run PASSED."
    exit 0
}
else {
    Write-Host "`nTier B run FAILED (host OK=$hostOk, guest OK=$guestOk). Check errorLogs/ in the Firebase Emulator UI (http://localhost:4000) for the breadcrumb trail before digging through raw logcat. Re-run with -Visible if you want to watch it happen."
    exit 1
}
