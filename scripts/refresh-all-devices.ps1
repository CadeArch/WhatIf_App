$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$adb = "D:\AndroidDevData\sdk\Sdk\platform-tools\adb.exe"
$apk = Join-Path $repoRoot "MixedUp\build\outputs\apk\debug\MixedUp-debug.apk"
$packageName = "com.CadeMixedUpGame.phoneapp"
$activityName = ".MainActivity"

if (!(Test-Path $adb)) {
    throw "adb.exe was not found at $adb"
}

if (!(Test-Path $apk)) {
    throw "Debug APK was not found at $apk. Build it first with .\gradlew.bat :MixedUp:assembleDebug"
}

$devices = & $adb devices | Select-String "`tdevice$" | ForEach-Object {
    ($_ -split "\s+")[0]
}

if ($devices.Count -eq 0) {
    throw "No connected Android devices or emulators were found."
}

foreach ($serial in $devices) {
    Write-Host "Refreshing $serial..."
    & $adb -s $serial install -r -t $apk
    & $adb -s $serial shell am force-stop $packageName
    & $adb -s $serial shell am start -n "$packageName/$activityName"
}

Write-Host "Refreshed $($devices.Count) device(s)."
