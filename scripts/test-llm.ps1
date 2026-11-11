# 验证 LLM 端点：普通对话 + tool calling
# 用法：powershell -ExecutionPolicy Bypass -File .\scripts\test-llm.ps1
$root = Split-Path -Parent $PSScriptRoot
$localPath = Join-Path $root "src\config\local.js"
if (-not (Test-Path $localPath)) { Write-Output "local.js 不存在"; exit 1 }

$t = [System.IO.File]::ReadAllText($localPath, [System.Text.Encoding]::UTF8)
$base = [regex]::Match($t, "baseUrl:\s*'([^']*)'").Groups[1].Value.TrimEnd('/')
$key = [regex]::Match($t, "apiKey:\s*'([^']*)'").Groups[1].Value
$model = [regex]::Match($t, "model:\s*'([^']*)'").Groups[1].Value
Write-Output ("endpoint = " + $base + "  model = " + $model)

$headers = @{ Authorization = "Bearer $key" }
$utf8 = [System.Text.Encoding]::UTF8

# ---------- 1. 普通对话 ----------
$body1 = @{
  model    = $model
  messages = @(@{ role = "user"; content = "只回复两个字：收到" })
  max_tokens = 32
  temperature = 0.2
} | ConvertTo-Json -Depth 6

$t0 = Get-Date
try {
  $r1 = Invoke-RestMethod -Uri "$base/chat/completions" -Method Post -Headers $headers `
    -ContentType "application/json; charset=utf-8" -Body $utf8.GetBytes($body1) -TimeoutSec 60
  $ms = [int]((Get-Date) - $t0).TotalMilliseconds
  Write-Output ("[chat] ok  " + $ms + "ms  -> " + $r1.choices[0].message.content)
  Write-Output ("[chat] usage " + ($r1.usage | ConvertTo-Json -Compress))
} catch {
  Write-Output ("[chat] FAIL " + $_.Exception.Message)
  if ($_.ErrorDetails) { Write-Output $_.ErrorDetails.Message }
}

# ---------- 2. tool calling ----------
$tools = @(@{
  type = "function"
  function = @{
    name = "gui_tap"
    description = "按节点编号点击屏幕上的控件"
    parameters = @{
      type = "object"
      properties = @{
        i = @{ type = "integer"; description = "节点编号" }
      }
      required = @("i")
    }
  }
})
$body2 = @{
  model = $model
  messages = @(
    @{ role = "system"; content = "你是手机操作助手，只能通过工具操作。" },
    @{ role = "user"; content = "当前界面节点 [4] 是「设置」，请点击它。" }
  )
  tools = $tools
  tool_choice = "auto"
  max_tokens = 128
  temperature = 0.2
} | ConvertTo-Json -Depth 10

$t0 = Get-Date
try {
  $r2 = Invoke-RestMethod -Uri "$base/chat/completions" -Method Post -Headers $headers `
    -ContentType "application/json; charset=utf-8" -Body $utf8.GetBytes($body2) -TimeoutSec 60
  $ms = [int]((Get-Date) - $t0).TotalMilliseconds
  $msg = $r2.choices[0].message
  Write-Output ("[tools] ok  " + $ms + "ms")
  if ($msg.tool_calls) {
    Write-Output ("[tools] tool_calls = " + ($msg.tool_calls | ConvertTo-Json -Depth 8 -Compress))
  } else {
    Write-Output ("[tools] 未返回 tool_calls，content = " + $msg.content)
  }
  Write-Output ("[tools] usage " + ($r2.usage | ConvertTo-Json -Compress))
} catch {
  Write-Output ("[tools] FAIL " + $_.Exception.Message)
  if ($_.ErrorDetails) { Write-Output $_.ErrorDetails.Message }
}
