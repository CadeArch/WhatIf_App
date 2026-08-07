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
# This script also boots both emulators itself if they aren't already running, with real windows
# so you can watch the run. Pass -Headless (-no-window) for an unattended/faster run.
#
# Prerequisites (this script checks/fails fast, does not attempt to fix):
#   1. Firebase Emulator Suite running: firebase emulators:start --only database,auth
#   2. Nothing else - the script builds both APKs itself with -PuseFirebaseEmulator=true and
#      refuses to run if the app APK turns out to be a production build.
#
# Usage: .\scripts\run-tier-b.ps1 [-TestClass com.CadeMixedUpGame.phoneapp.TwoDeviceFullGameLoopTest] [-Rounds 3] [-Headless]
# Defaults to the minimal join-only smoke test, visible. Any test class following the same
# role/correlationId instrumentation-argument convention as TwoDeviceMultiplayerTest works here
# unchanged. -Rounds is only meaningful for TwoDeviceFullGameLoopTest (its own -e rounds <n>
# instrumentation argument, defaults to 2 if omitted here) - harmless no-op for tests that don't
# read it.

param(
    [string]$TestClass = "com.CadeMixedUpGame.phoneapp.TwoDeviceMultiplayerTest",
    [int]$Rounds = 0,
    [switch]$Headless,
    [switch]$SkipBuild
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

Write-Host "Running $testClass (correlationId=$correlationId, $(if ($Headless) { 'headless' } else { 'visible' }))"
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
    Write-Host "Booting $avdName (expecting it to come up as $expectedSerial, $(if ($Headless) { 'headless' } else { 'visible' }))..."
    $emulatorArgs = @("-avd", $avdName, "-no-snapshot-save")
    if ($Headless) {
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

# Build the APKs here rather than trusting whatever is already on disk. Any ordinary
# `./gradlew :MixedUp:assembleDebug` (a regression run, Android Studio's Run button) rebuilds the
# same file WITHOUT -PuseFirebaseEmulator=true, silently turning it into a PRODUCTION build - and
# the old "does the APK exist?" check happily installed it. That is how a full Tier B run ended up
# writing real rooms into the live mixedupgame project instead of the local emulator, and then
# failing anyway because production's default-deny rules don't allow-list the e2eSignals node the
# room-code handoff uses. Build + verify every time; never infer the variant from the file's
# existence. Use -SkipBuild only if you have just built with the flag yourself.
if (-not $SkipBuild) {
    Write-Host "Building app + test APKs with -PuseFirebaseEmulator=true..."
    & (Join-Path $repoRoot "gradlew.bat") "-p" $repoRoot ":MixedUp:assembleDebug" ":MixedUp:assembleDebugAndroidTest" "-PuseFirebaseEmulator=true" "--console=plain"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed - fix the build before running Tier B."
    }
}

if (!(Test-Path $apk)) {
    throw "App APK not found at $apk. Build it first with: .\gradlew.bat :MixedUp:assembleDebug -PuseFirebaseEmulator=true"
}
if (!(Test-Path $testApk)) {
    throw "Test APK not found at $testApk. Build it first with: .\gradlew.bat :MixedUp:assembleDebugAndroidTest"
}

# Hard gate: the *app* module's BuildConfig is what WhatIfApplication.onCreate() reads to decide
# whether to call FirebaseEmulatorConfig. If this is false the run would silently hit production.
$appBuildConfig = Join-Path $repoRoot "MixedUp\build\generated\source\buildConfig\debug\com\CadeMixedUpGame\phoneapp\BuildConfig.java"
if (!(Test-Path $appBuildConfig)) {
    throw "Could not find the app module's generated BuildConfig at $appBuildConfig - cannot confirm this is an emulator build, refusing to run against a possibly-production APK."
}
if (-not (Select-String -Path $appBuildConfig -Pattern 'USE_FIREBASE_EMULATOR\s*=\s*true' -Quiet)) {
    throw "The app APK is a PRODUCTION build (USE_FIREBASE_EMULATOR = false in $appBuildConfig). Refusing to run - this would write test rooms into the live Firebase project. Rebuild with: .\gradlew.bat :MixedUp:assembleDebug :MixedUp:assembleDebugAndroidTest -PuseFirebaseEmulator=true"
}
Write-Host "Verified the app APK is an emulator build (USE_FIREBASE_EMULATOR = true)."

foreach ($serial in @($hostSerial, $guestSerial)) {
    Write-Host "Installing app + test APK on $serial..."
    & $adb -s $serial install -r -t $apk
    & $adb -s $serial install -r -t $testApk
    # MUST clear app data, not just force-stop. `install -r` deliberately preserves app data, so a
    # persisted Firebase Auth session (shared_prefs/com.google.firebase.auth.api.Store*.xml) left
    # behind by any earlier account-play run survives reinstalls AND app-version changes. The Auth
    # emulator is in-memory, so restarting the Emulator Suite deletes the account that session
    # refers to - the SDK then fails to refresh the token, hands RTDB an unusable credential
    # ("PersistentConnection: Provided authentication credentials are invalid"), and the RTDB
    # websocket is never established at all. Every read/write then fails with "Client is offline"
    # and the app sits on the "Connection lost" banner forever, which reads exactly like a network
    # bug but is purely stale local auth state. Cost this session ~9 failed runs to diagnose; the
    # free-play test is just as vulnerable as the account-play one even though it never signs in.
    & $adb -s $serial shell pm clear $packageName
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
    Write-Host "`nTier B run FAILED (host OK=$hostOk, guest OK=$guestOk). Check errorLogs/ in the Firebase Emulator UI (http://localhost:4000) for the breadcrumb trail before digging through raw logcat. Re-run without -Headless if you want to watch it happen."
    exit 1
}
