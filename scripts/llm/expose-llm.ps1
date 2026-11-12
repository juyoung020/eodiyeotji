<#
.SYNOPSIS
  이 PC의 Qwen(start-llm.ps1, :8181)을 Cloudflare 터널로 밖에 열고, 그 주소를 중계 서버(server/relay)에 알린다.
.DESCRIPTION
  - 앱 · 심사위원은 중계 서버 주소(https://eodi-relay.memo2025.workers.dev/llm/v1)만 쓴다. 터널 주소는 켤 때마다 바뀌지만
    이 스크립트가 중계 서버 비밀값 LLM_ORIGIN을 새 주소로 바꾸므로 앱 쪽은 그대로다.
  - Qwen은 비밀번호(local.properties의 LLM_ORIGIN_KEY)를 걸고 띄운다 → 터널 주소가 새도 비밀번호 없이는 못 쓴다.
  - 먼저: start-llm.ps1을 다른 창에서 띄워 둔다. 이 창은 닫지 않는다 (닫으면 터널이 끊긴다).
  - 필요한 것: cloudflared (winget install Cloudflare.cloudflared), server/relay에서 wrangler 로그인.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\llm\expose-llm.ps1
#>
param([int]$Port = 8181)
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$relay = Join-Path $root 'server\relay'
$cf = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
if (-not $cf) { $cf = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter cloudflared.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName }
if (-not $cf) { throw 'cloudflared가 없습니다: winget install Cloudflare.cloudflared' }

$log = Join-Path $env:TEMP 'eodi-llm-tunnel.log'
Remove-Item $log -ErrorAction SilentlyContinue
$p = Start-Process -FilePath $cf -ArgumentList @('tunnel', '--no-autoupdate', '--url', "http://127.0.0.1:$Port") -RedirectStandardError $log -PassThru -WindowStyle Hidden
$url = $null
for ($i = 0; $i -lt 60 -and -not $url; $i++) {
    Start-Sleep -Seconds 1
    if (Test-Path $log) { $url = (Select-String -Path $log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1).Matches.Value }
}
if (-not $url) { Stop-Process -Id $p.Id -Force; throw "터널 주소를 못 받았습니다 ($log)" }
Write-Host "터널: $url"
Push-Location $relay
try {
    $url | npx wrangler secret put LLM_ORIGIN | Out-Null
} finally { Pop-Location }
Write-Host '중계 서버에 새 주소를 알렸습니다 → 앱은 https://eodi-relay.memo2025.workers.dev/llm/v1 로 Qwen을 씁니다.'
Write-Host '이 창을 닫으면 터널이 끊깁니다.'
Wait-Process -Id $p.Id
