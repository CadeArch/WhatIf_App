# Voice audition harness - boots ONE emulator, drives the app to the reading screen with every
# unlockable voice unlocked, and leaves it sitting there so the voices can be listened to and
# compared. See VoiceAuditionTest for what it fabricates and why.
#
# This is a dev tool, not a Tier B test: there is nothing to pass or fail, so it never prints a
# PASS/FAIL verdict. It intentionally blocks for the whole hold - Ctrl-C ends it early, and the app
# closes on its own when the hold expires.
#
# Single device on purpose. The other players are written into the room by E2ERoomFixture rather
# than played by a second emulator, so no room-code handoff and no guest role are involved.
#
# Prerequisites (checked, not fixed):
#   1. Firebase Emulator Suite running:
#        firebase emulators:start --config firebase.emulator.json --only database,auth
#   2. local-test-accounts.md at the repo root (gitignored) with the Host credentials row.
#
# Usage: .\scripts\run-voice-audition.ps1 [-HoldMinutes 45] [-Headless] [-SkipBuild]

param(
    [int]$HoldMinutes = 45,
    [switch]$Headless,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$adb = "D:\AndroidDevData\sdk\Sdk\platform-tools\adb.exe"
$emulatorExe = "D:\AndroidDevData\sdk\Sdk\emulator\emulator.exe"
$apk = Join-Path $repoRoot "MixedUp\build\outputs\apk\debug\MixedUp-debug.apk"
$testApk = Join-Path $repoRoot "MixedUp\build\outputs\apk\androidTest\debug\MixedUp-debug-androidTest.apk"
$credentialsFile = Join-Path $repoRoot "local-test-accounts.md"
$packageName = "com.CadeMixedUpGame.phoneapp"
$testPackageName = "com.CadeMixedUpGame.phoneapp.test"
$testRunner = "androidx.test.runner.AndroidJUnitRunner"
$testClass = "com.CadeMixedUpGame.phoneapp.VoiceAuditionTest"
$avd = "Test_API35"
$serial = "emulator-5554"

if (!(Test-Path $adb)) {
    throw "adb.exe was not found at $adb"
}
if (!(Test-Path $credentialsFile)) {
    throw "local-test-accounts.md not found at $credentialsFile. This file is gitignored and must exist locally with a Host credentials row - see CLAUDE.md's Tier B section."
}

function Get-CredentialsByRole([string]$roleName) {
    $lines = Get-Content $credentialsFile
    foreach ($line in $lines) {
        if ($line -match '^\|\s*(Host|Guest)\s*\|\s*([^\|]+?)\s*\|\s*([^\|]+?)\s*\|\s*([^\|]+?)\s*\|\s*$') {
            if ($Matches[1] -eq $roleName) {
                return @{ Email = $Matches[2].Trim(); Password = $Matches[3].Trim(); Username = $Matches[4].Trim() }
            }
        }
    }
    throw "Could not find a '| $roleName | email | password | username |' row in $credentialsFile"
}

$creds = Get-CredentialsByRole "Host"
Write-Host "Account: $($creds.Email) (username $($creds.Username))"

Write-Host "Checking Firebase Emulator Suite is reachable on 127.0.0.1:9000..."
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect("127.0.0.1", 9000)
    $tcp.Close()
}
catch {
    throw "Firebase Emulator Suite is not reachable on 127.0.0.1:9000. Start it first with: firebase emulators:start --config firebase.emulator.json --only database,auth"
}

# The Auth emulator is in-memory, so every suite restart deletes this account and the only symptom
# would be the harness parked on the sign-in screen. Idempotent: create, or sign in if present, then
# set displayName either way - the app reads displayName as the player name, and the unlockables are
# written under it.
function Ensure-EmulatorAccount([string]$email, [string]$password, [string]$displayName) {
    $authBase = "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1"
    $key = "fake-api-key"   # the Auth emulator accepts any API key
    $body = @{ email = $email; password = $password; returnSecureToken = $true } | ConvertTo-Json
    $idToken = $null
    try {
        $created = Invoke-RestMethod -Method Post -Uri "$authBase/accounts:signUp?key=$key" -Body $body -ContentType "application/json" -TimeoutSec 20
        $idToken = $created.idToken
        Write-Host "  created $email in the Auth emulator"
    }
    catch {
        $existing = Invoke-RestMethod -Method Post -Uri "$authBase/accounts:signInWithPassword?key=$key" -Body $body -ContentType "application/json" -TimeoutSec 20
        $idToken = $existing.idToken
        Write-Host "  $email already present in the Auth emulator"
    }
    $update = @{ idToken = $idToken; displayName = $displayName; returnSecureToken = $true } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$authBase/accounts:update?key=$key" -Body $update -ContentType "application/json" -TimeoutSec 20 | Out-Null
}

Write-Host "Seeding the account into the local Auth emulator..."
Ensure-EmulatorAccount $creds.Email $creds.Password $creds.Username

function Test-DeviceConnected([string]$expectedSerial) {
    $connected = & $adb devices | Select-String "`tdevice$" | ForEach-Object { ($_ -split "\s+")[0] }
    return $connected -contains $expectedSerial
}

function Wait-ForBootCompleted([string]$expectedSerial, [int]$timeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $prop = (& $adb -s $expectedSerial shell getprop sys.boot_completed 2>$null) -join ""
        if ($prop.Trim() -eq "1") {
            return $true
        }
        Start-Sleep -Seconds 3
    }
    return $false
}

if (-not (Test-DeviceConnected $serial)) {
    if (!(Test-Path $emulatorExe)) {
        throw "emulator.exe was not found at $emulatorExe, and $serial isn't already connected."
    }
    Write-Host "Booting $avd (expecting $serial, $(if ($Headless) { 'headless' } else { 'visible' }))..."
    $emulatorArgs = @("-avd", $avd, "-no-snapshot-save")
    if ($Headless) {
        $emulatorArgs += "-no-window"
    }
    Start-Process -FilePath $emulatorExe -ArgumentList $emulatorArgs | Out-Null
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline -and -not (Test-DeviceConnected $serial)) {
        Start-Sleep -Seconds 3
    }
    if (-not (Test-DeviceConnected $serial)) {
        throw "$avd did not come online as $serial within 120s."
    }
    if (-not (Wait-ForBootCompleted $serial 120)) {
        throw "$avd connected as $serial but did not finish booting within 120s."
    }
    Write-Host "$avd is up."
}

# Built here rather than trusting whatever APK is on disk: a flagless `assembleDebug` (a regression
# run, Android Studio's Run button) silently produces a PRODUCTION build, which would sign the real
# account in against live Firebase Auth and write these fabricated players into the live project.
if (-not $SkipBuild) {
    Write-Host "Building app + test APKs with -PuseFirebaseEmulator=true..."
    & (Join-Path $repoRoot "gradlew.bat") "-p" $repoRoot ":MixedUp:assembleDebug" ":MixedUp:assembleDebugAndroidTest" "-PuseFirebaseEmulator=true" "--console=plain"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed."
    }
}

if (!(Test-Path $apk)) {
    throw "App APK not found at $apk."
}
if (!(Test-Path $testApk)) {
    throw "Test APK not found at $testApk."
}

$appBuildConfig = Join-Path $repoRoot "MixedUp\build\generated\source\buildConfig\debug\com\CadeMixedUpGame\phoneapp\BuildConfig.java"
if (!(Test-Path $appBuildConfig)) {
    throw "Could not find the app module's generated BuildConfig at $appBuildConfig - cannot confirm which Firebase project this APK targets."
}
if (-not (Select-String -Path $appBuildConfig -Pattern 'USE_FIREBASE_EMULATOR\s*=\s*true' -Quiet)) {
    throw "The app APK is a PRODUCTION build (USE_FIREBASE_EMULATOR = false in $appBuildConfig). Refusing to run - this would sign the real account in against live Firebase Auth and write fabricated players into the live project. Rebuild with: .\gradlew.bat :MixedUp:assembleDebug :MixedUp:assembleDebugAndroidTest -PuseFirebaseEmulator=true"
}
Write-Host "Verified the app APK is an emulator build (USE_FIREBASE_EMULATOR = true)."

Write-Host "Installing on $serial..."
& $adb -s $serial install -r -t $apk
& $adb -s $serial install -r -t $testApk
# A stale Firebase Auth session survives `install -r` and both auto-signs-in (skipping the sign-in
# this drives) and, if the Auth emulator has since restarted, wedges the client permanently offline.
& $adb -s $serial shell pm clear $packageName
& $adb -s $serial shell am force-stop $packageName

# qemu's 10.0.2.2 NAT drops server-initiated RTDB frames (writes land, pushes never arrive), so the
# app targets 127.0.0.1 and the emulator-suite ports are tunnelled over adb instead.
& $adb -s $serial reverse tcp:9000 tcp:9000
& $adb -s $serial reverse tcp:9099 tcp:9099

& $adb -s $serial logcat -c

Write-Host ""
Write-Host "Launching the audition on $serial (holding for $HoldMinutes minutes)..."
Write-Host "  Watch the emulator window: it signs in, creates a room, writes an If/Then, and stops"
Write-Host "  on the reading screen with the sentence revealed."
Write-Host "  Then: pick a voice from the dropdown, tap the pink mic to hear it."
Write-Host "  'pass' moves to the next sentence (four in the read order); after the last"
Write-Host "  one the round ends, so re-run this script for more."
Write-Host "  Ctrl-C ends it early."
Write-Host ""

$job = Start-Job -ScriptBlock {
    param($adbPath, $deviceSerial, $pkg, $runner, $class, $email, $password, $holdMinutes)
    & $adbPath -s $deviceSerial shell am instrument -w -e class $class -e email $email -e password $password -e holdMinutes $holdMinutes "$pkg/$runner"
} -ArgumentList $adb, $serial, $testPackageName, $testRunner, $testClass, $creds.Email, $creds.Password, $HoldMinutes

# Surface the READY line (and any failure before it) instead of leaving a silent terminal while the
# app walks itself through six screens.
$readyDeadline = (Get-Date).AddMinutes(4)
$announced = $false
while ((Get-Date) -lt $readyDeadline -and -not $announced) {
    if ($job.State -ne "Running") {
        break
    }
    $ready = & $adb -s $serial logcat -d -s MU.TTS | Select-String "VOICE AUDITION READY"
    if ($ready) {
        Write-Host ($ready -join "`n")
        Write-Host ""
        Write-Host "Ready - the screen is yours until the hold expires."
        $announced = $true
        break
    }
    Start-Sleep -Seconds 5
}
if (-not $announced) {
    Write-Host "Did not see the READY marker within 4 minutes - check the emulator screen and the result below."
}

$result = Receive-Job -Job $job -Wait
Remove-Job -Job $job
Write-Host "`n--- instrumentation result ---"
Write-Host ($result -join "`n")
