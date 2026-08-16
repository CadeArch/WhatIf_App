# Tier B: two-device real Espresso end-to-end test for Account Play (signed-in Firebase Auth
# players, not Free Play guest names) - see run-tier-b.ps1 for the shared design notes
# (headless-by-default boot/install/parallel-launch orchestration, room-code handoff via
# E2ERoomCodeSignal). This script is Account Play's counterpart to that script, with two
# differences: it passes a DIFFERENT -e email/-e password pair to the host vs. the guest role
# (TwoDeviceAccountPlayGameLoopTest requires both), and it reads those credentials from
# local-test-accounts.md at runtime instead of hardcoding them - that file is gitignored so this
# committed script never contains real credentials.
#
# Prerequisites (this script checks/fails fast, does not attempt to fix):
#   1. Firebase Emulator Suite running: firebase emulators:start --only database,auth
#   2. App built with the emulator flag: .\gradlew.bat :MixedUp:assembleDebug -PuseFirebaseEmulator=true
#   3. Test APK built: .\gradlew.bat :MixedUp:assembleDebugAndroidTest
#   4. (automated) Both accounts exist as Firebase Auth users in the LOCAL emulator. This used to be
#      a manual step, but the Auth emulator is in-memory - every suite restart deleted them and the
#      only symptom was two devices sitting on the sign-in screen. The script now seeds them from
#      local-test-accounts.md via the Auth emulator's REST API (idempotent). The credentials are
#      PRODUCTION accounts; nothing is ever copied out of production, they are simply recreated
#      locally with the same email/password/displayName.
#   5. local-test-accounts.md exists at the repo root with the same "| Role | Email | Password |
#      Username |" markdown table format this script parses below.
#
# Usage: .\scripts\run-tier-b-account-play.ps1 [-Rounds 3] [-Headless]
# -TestClass is now exposed (as in run-tier-b.ps1) so account-play *variants* can reuse this whole
# orchestration - credential lookup, account seeding, the two-role launch - instead of duplicating
# it. Defaults to the full game loop. Originally omitted because this script is
# specifically for TwoDeviceAccountPlayGameLoopTest's role/email/password contract - a different
# test class would need different credential-role wiring anyway.

param(
    [string]$TestClass = "com.CadeMixedUpGame.phoneapp.TwoDeviceAccountPlayGameLoopTest",
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
$credentialsFile = Join-Path $repoRoot "local-test-accounts.md"
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

if (!(Test-Path $credentialsFile)) {
    throw "local-test-accounts.md not found at $credentialsFile. This file is gitignored and must exist locally with a Host/Guest credentials table - see CLAUDE.md's Tier B section."
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

$hostCreds = Get-CredentialsByRole "Host"
$guestCreds = Get-CredentialsByRole "Guest"
Write-Host "Host credentials: $($hostCreds.Email) (username $($hostCreds.Username))"
Write-Host "Guest credentials: $($guestCreds.Email) (username $($guestCreds.Username))"

# Seed both accounts into the LOCAL Auth emulator.
#
# This used to be a manual prerequisite, which is a trap: the Auth emulator is IN-MEMORY, so every
# suite restart silently deletes both accounts and the next run fails with two devices parked on
# the sign-in screen and no obvious reason. The credentials in local-test-accounts.md are real
# PRODUCTION accounts - they do not exist here until seeded, and nothing about the failure says so.
#
# Idempotent: create, or sign in if it already exists, then set displayName either way. The app
# reads FirebaseUser.getDisplayName() as the username, so an account without one signs in and then
# behaves as a nameless player.
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


Write-Host "Running $testClass (correlationId=$correlationId, $(if ($Headless) { 'headless' } else { 'visible' }))"
Write-Host "Checking Firebase Emulator Suite is reachable on 127.0.0.1:9000..."
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect("127.0.0.1", 9000)
    $tcp.Close()
}
catch {
    throw "Firebase Emulator Suite is not reachable on 127.0.0.1:9000. Start it first with: firebase emulators:start --config firebase.emulator.json --only database,auth"
}

Write-Host "Seeding test accounts into the local Auth emulator..."
Ensure-EmulatorAccount $hostCreds.Email $hostCreds.Password $hostCreds.Username
Ensure-EmulatorAccount $guestCreds.Email $guestCreds.Password $guestCreds.Username

# See run-tier-b.ps1: no REST probe of e2eSignals/ here on purpose - the emulator answers
# unauthenticated REST as admin and skips rules, so such a probe always passes. The enforced
# check is in E2ERoomCodeSignal.publish().

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

# See run-tier-b.ps1 for why this builds + verifies instead of trusting the APK already on disk:
# a flagless `assembleDebug` silently produces a PRODUCTION build, and account play is the worse
# case - it would sign real accounts in against live Firebase Auth and write real rooms to the
# live project.
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

$appBuildConfig = Join-Path $repoRoot "MixedUp\build\generated\source\buildConfig\debug\com\CadeMixedUpGame\phoneapp\BuildConfig.java"
if (!(Test-Path $appBuildConfig)) {
    throw "Could not find the app module's generated BuildConfig at $appBuildConfig - cannot confirm which Firebase project this APK targets."
}
if (-not (Select-String -Path $appBuildConfig -Pattern 'USE_FIREBASE_EMULATOR\s*=\s*true' -Quiet)) {
    throw "The app APK is a PRODUCTION build (USE_FIREBASE_EMULATOR = false in $appBuildConfig). Refusing to run - this would sign the real test accounts in against live Firebase Auth and write test rooms into the live project. Rebuild with: .\gradlew.bat :MixedUp:assembleDebug :MixedUp:assembleDebugAndroidTest -PuseFirebaseEmulator=true"
}
else {
    Write-Host "Verified the app APK is an emulator build (USE_FIREBASE_EMULATOR = true)."
}

foreach ($serial in @($hostSerial, $guestSerial)) {
    Write-Host "Installing app + test APK on $serial..."
    & $adb -s $serial install -r -t $apk
    & $adb -s $serial install -r -t $testApk
    # A stale Firebase Auth session survives `install -r` (it isn't a full data wipe) and makes
    # the app auto-sign-in, skipping AccountFrag entirely - this test needs to drive the real
    # sign-in flow every run, so force a logged-out state by clearing the app's own data (this
    # does not remove either APK, only MixedUp's user data/prefs).
    & $adb -s $serial shell pm clear $packageName
    & $adb -s $serial shell am force-stop $packageName

    # See run-tier-b.ps1: qemu's 10.0.2.2 NAT drops server-initiated RTDB frames (writes work,
    # pushes never arrive), so the app targets 127.0.0.1 and we tunnel the ports over adb.
    & $adb -s $serial reverse tcp:9000 tcp:9000
    & $adb -s $serial reverse tcp:9099 tcp:9099
}

Write-Host "Launching host role on $hostSerial (account: $($hostCreds.Email))..."
$hostJob = Start-Job -ScriptBlock {
    param($adbPath, $serial, $pkg, $runner, $class, $correlationId, $email, $password, $extraArgs)
    & $adbPath -s $serial shell am instrument -w -e class $class -e role host -e correlationId $correlationId -e email $email -e password $password @extraArgs "$pkg/$runner"
} -ArgumentList $adb, $hostSerial, $testPackageName, $testRunner, $testClass, $correlationId, $hostCreds.Email, $hostCreds.Password, $roundsArgs

Write-Host "Launching guest role on $guestSerial (account: $($guestCreds.Email), same time as host)..."
$guestJob = Start-Job -ScriptBlock {
    param($adbPath, $serial, $pkg, $runner, $class, $correlationId, $email, $password, $extraArgs)
    & $adbPath -s $serial shell am instrument -w -e class $class -e role guest -e correlationId $correlationId -e email $email -e password $password @extraArgs "$pkg/$runner"
} -ArgumentList $adb, $guestSerial, $testPackageName, $testRunner, $testClass, $correlationId, $guestCreds.Email, $guestCreds.Password, $roundsArgs

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
    Write-Host "`nTier B account-play run PASSED."
    exit 0
}
else {
    Write-Host "`nTier B account-play run FAILED (host OK=$hostOk, guest OK=$guestOk). Check errorLogs/ in the Firebase Emulator UI (http://localhost:4000) for the breadcrumb trail before digging through raw logcat. Re-run without -Headless if you want to watch it happen."
    exit 1
}
