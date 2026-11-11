# Probe which GLM vision model IDs exist. Key passed as parameter (not stored).
param([string]$Key)
$ids = @('glm-4.6v-flash', 'glm-4.6v', 'glm-4.5v', 'glm-4v-flash', 'glm-4v-plus')
$uri = 'https://open.bigmodel.cn/api/paas/v4/chat/completions'
foreach ($id in $ids) {
  $body = @{ model = $id; messages = @(@{ role = 'user'; content = 'hi' }); max_tokens = 8 } | ConvertTo-Json -Depth 5
  try {
    $r = Invoke-RestMethod -Uri $uri -Method Post -Headers @{ Authorization = "Bearer $Key" } `
      -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 30
    Write-Output ("{0} OK  reply={1}" -f $id, $r.choices[0].message.content)
  } catch {
    $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { '?' }
    $detail = ''
    if ($_.ErrorDetails) { $detail = $_.ErrorDetails.Message }
    Write-Output ("{0} FAIL {1} {2}" -f $id, $status, $detail.Substring(0, [Math]::Min(160, $detail.Length)))
  }
}
