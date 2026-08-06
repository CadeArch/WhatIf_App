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
#   4. Both accounts in local-test-accounts.md already exist as Firebase Auth users in the LOCAL
#      emulator's Auth instance (separate from production) - sign up each once manually if a run
#      fails with "no user record" / sign-in stuck at AccountFrag.
#   5. local-test-accounts.md exists at the repo root with the same "| Role | Email | Password |
#      Username |" markdown table format this script parses below.
#
# Usage: .\scripts\run-tier-b-account-play.ps1 [-Rounds 3] [-Visible]
# -TestClass is not exposed as a param here (unlike run-tier-b.ps1) since this script is
# specifically for TwoDeviceAccountPlayGameLoopTest's role/email/password contract - a different
# test class would need different credential-role wiring anyway.

param(
    [int]$Rounds = 0,
    [switch]$Visible
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
$testClass = "com.CadeMixedUpGame.phoneapp.TwoDeviceAccountPlayGameLoopTest"
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
    # A stale Firebase Auth session survives `install -r` (it isn't a full data wipe) and makes
    # the app auto-sign-in, skipping AccountFrag entirely - this test needs to drive the real
    # sign-in flow every run, so force a logged-out state by clearing the app's own data (this
    # does not remove either APK, only MixedUp's user data/prefs).
    & $adb -s $serial shell pm clear $packageName
    & $adb -s $serial shell am force-stop $packageName
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
    Write-Host "`nTier B account-play run FAILED (host OK=$hostOk, guest OK=$guestOk). Check errorLogs/ in the Firebase Emulator UI (http://localhost:4000) for the breadcrumb trail before digging through raw logcat. Re-run with -Visible if you want to watch it happen."
    exit 1
}
