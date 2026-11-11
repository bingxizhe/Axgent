# Run a script on the phone via AutoX RunIntentActivity. ASCII only.
# 注意：仅适用于仍保留 RunIntentActivity 的 AutoX release/debug 包；
# Axgent 产品包（com.axgent.agent）已移除该组件，脚本由 App 内引擎直接运行，
# 正常调试请直接在 App 输入框发任务，只有调试旧版 AutoX 时才用本脚本。
param(
  [string]$Script = "src/probe.js",
  [int]$WaitSeconds = 10
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

$local = Join-Path $root $Script
if (-not (Test-Path $local)) { throw "local script not found: $local" }

$rel = $Script -replace "\\", "/"
& $adb shell "mkdir -p /sdcard/phone-agent/$(Split-Path $rel -Parent)"
& $adb push $local "/sdcard/phone-agent/$rel" 2>&1 | Select-Object -Last 1

$uri = "file:///sdcard/phone-agent/$rel"
& $adb shell "am start -n org.autojs.autoxjs.v7/org.autojs.autojs.external.open.RunIntentActivity -d '$uri'"

Start-Sleep -Seconds $WaitSeconds
Write-Output "---- latest outputs ----"
& $adb shell "ls -lt /sdcard/Download/phone-agent/ | head -8"
