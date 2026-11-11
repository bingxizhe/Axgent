# Push ./src to the phone. ASCII only (PS 5.1 reads UTF-8 scripts as ANSI).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

$devices = (& $adb devices | Out-String)
Write-Output $devices
if ($devices -notmatch "\tdevice") { throw "no device in 'device' state" }

& $adb shell "mkdir -p /sdcard/phone-agent"
& $adb push (Join-Path $root "src") "/sdcard/phone-agent/" 2>&1 | Select-Object -Last 1
& $adb shell "ls /sdcard/phone-agent/src"
Write-Output "pushed."
