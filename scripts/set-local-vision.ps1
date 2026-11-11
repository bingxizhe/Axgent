# Write local.js with the dedicated vision model. Key comes from a parameter, never stored here.
param([string]$GlmKey)

$p = Join-Path (Split-Path -Parent $PSScriptRoot) "src\config\local.js"
$t = [IO.File]::ReadAllText($p, [Text.Encoding]::UTF8)
$dk = [regex]::Match($t, "apiKey\s*:\s*'([^']*)'").Groups[1].Value
if (-not $dk) { throw "deepseek apiKey not found in local.js" }

$content = @"
/**
 * Personal config (gitignored). Never commit.
 */
module.exports = {
  llm: {
    baseUrl: 'https://api.deepseek.com',
    apiKey: '$dk',
    model: 'deepseek-flash',

    // Dedicated vision model: Zhipu GLM-4.6V-Flash
    visionModel: 'glm-4.6v-flash',
    visionBaseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    visionApiKey: '$GlmKey'
  },
  vision: {
    // Pretend the primary model cannot see; use the dedicated one.
    simulateNoVision: true,
    // Zhipu expects raw base64, not a data URI.
    imageAsDataUri: false,
    maxTokens: 800
  }
};
"@

[IO.File]::WriteAllText($p, $content, (New-Object Text.UTF8Encoding($false)))
Write-Output "local.js updated (vision=glm-4.6v-flash)"
