<#
  같이 가기 시연: 가짜 차 1~2대를 중계 서버(/convoy)에 참여시켜 목적지 쪽으로 움직인다 (에뮬레이터 한 대로 시연).

  1) 앱에서 "같이 가기" → 새로 만들기 → 화면의 참여 코드 6자리를 본다
  2) powershell -ExecutionPolicy Bypass -File scripts\convoy-fake.ps1 -Code 123456
       -Names 민수,지현   가짜 차 이름 (기본 민수,지현)
       -BehindKm 2.5,-0.8 차마다 목적지 반대쪽으로 얼마나 뒤에서 출발하나 (음수 = 내 앞). 기본 2.5,-0.8
       -StartLat/-StartLon 출발 기준점 (기본: 모임 목적지에서 북쪽 6km)
       -SpeedKmh 45        -IntervalSec 3
       -RangeKm 30,180     차마다 남은 주행 가능 거리 km (기본: 민수 30km → "배터리 부족", 지현 180km). 달린 만큼 줄어든다
       -Gas                가짜 차를 연료차로 (기본 전기차)
  중계 주소 · 토큰은 local.properties의 RELAY_URL · RELAY_TOKEN을 읽는다 (-Relay/-Token으로 덮어쓰기).

  중계 서버 없이(RELAY_URL 없음) 빌드한 디버그 앱은 기기 안 시연 모드다. 그때는 adb 브로드캐스트로 넣는다:
    adb shell am broadcast -a app.eodiyeotji.DEBUG_CONVOY --es name 민수 --ef behind_km 2.5 --ef speed_kmh 50 --ef range_km 30
  내 차 잔량 시연 값: adb shell am broadcast -a app.eodiyeotji.DEBUG_CONVOY --ef my_pct 60 --ef my_range_km 240
  (이 스크립트에 -Local 을 주면 위 브로드캐스트를 -Names/-BehindKm 대로 보낸다)
#>
param(
  [string]$Code,
  [string[]]$Names = @('민수', '지현'),
  [double[]]$BehindKm = @(2.5, -0.8),
  [double]$SpeedKmh = 45,
  [double[]]$RangeKm = @(30, 180),
  [switch]$Gas,
  [int]$IntervalSec = 3,
  [double]$StartLat = [double]::NaN,
  [double]$StartLon = [double]::NaN,
  [string]$Relay,
  [string]$Token,
  [switch]$Local,
  [string]$Serial
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

if ($Local) {
  $adb = if ($Serial) { @('adb', '-s', $Serial) } else { @('adb') }
  for ($i = 0; $i -lt $Names.Count; $i++) {
    $b = if ($i -lt $BehindKm.Count) { $BehindKm[$i] } else { 2 }
    $r = if ($i -lt $RangeKm.Count) { $RangeKm[$i] } else { 150 }
    $evArg = if ($Gas) { 'false' } else { 'true' }
    & $adb[0] $adb[1..($adb.Count)] shell am broadcast -a app.eodiyeotji.DEBUG_CONVOY --es name $Names[$i] --ef behind_km $b --ef speed_kmh $SpeedKmh --ef range_km $r --ez ev $evArg | Out-Null
    Write-Host "기기 안 가짜 차: $($Names[$i]) ($b km)"
  }
  return
}

if (-not $Code) { throw '-Code (앱에 보이는 참여 코드 6자리)가 필요합니다' }
$props = @{}
$lp = Join-Path $PSScriptRoot '..\local.properties'
if (Test-Path $lp) {
  Get-Content $lp -Encoding UTF8 | ForEach-Object { if ($_ -match '^\s*([A-Z_]+)\s*=\s*(.*)$') { $props[$Matches[1]] = $Matches[2].Trim() } }
}
if (-not $Relay) { $Relay = $props['RELAY_URL'] }
if (-not $Token) { $Token = $props['RELAY_TOKEN'] }
if (-not $Relay) { throw 'RELAY_URL이 없습니다 (local.properties 또는 -Relay). 중계 없이 시연하려면 -Local' }
$Relay = $Relay.TrimEnd('/')
$headers = @{ 'X-Eodi-App' = $Token }

function Post($path, $body) {
  $json = $body | ConvertTo-Json -Compress
  Invoke-RestMethod -Method Post -Uri "$Relay/convoy$path" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 5
}

$cars = @()
for ($i = 0; $i -lt $Names.Count; $i++) {
  $j = Post '/join' @{ code = $Code; name = $Names[$i] }
  $cars += [pscustomobject]@{ Name = $Names[$i]; Id = $j.id; Member = $j.member_id; Dest = $j.dest; Behind = $(if ($i -lt $BehindKm.Count) { $BehindKm[$i] } else { 2 }); Range = $(if ($i -lt $RangeKm.Count) { $RangeKm[$i] } else { 150 }) }
  Write-Host "참여: $($Names[$i])"
}
$dest = $cars[0].Dest
if (-not $dest) { Write-Warning '모임에 목적지가 없습니다 → 북쪽에서 남쪽으로 곧게 달립니다' }
$dLat = if ($dest) { [double]$dest.lat } else { 37.5665 }
$dLon = if ($dest) { [double]$dest.lng } else { 126.9780 }
$baseLat = if ([double]::IsNaN($StartLat)) { $dLat + 6 / 111.0 } else { $StartLat }
$baseLon = if ([double]::IsNaN($StartLon)) { $dLon } else { $StartLon }

# 차마다 출발점: 기준점에서 목적지 반대쪽으로 Behind km
$vLat = $baseLat - $dLat; $vLon = $baseLon - $dLon
$norm = [Math]::Sqrt([Math]::Pow($vLat * 111, 2) + [Math]::Pow($vLon * 88, 2)); if ($norm -lt 1e-6) { $norm = 1; $vLat = 1 / 111.0 }
foreach ($c in $cars) {
  $c | Add-Member Lat ($baseLat + $vLat / $norm * $c.Behind)
  $c | Add-Member Lon ($baseLon + $vLon / $norm * $c.Behind)
}
$stepM = $SpeedKmh / 3.6 * $IntervalSec
Write-Host "달리는 중 (Ctrl+C로 멈춤) · ${SpeedKmh}km/h · ${IntervalSec}초 간격"
while ($true) {
  foreach ($c in $cars) {
    $toLat = $dLat - $c.Lat; $toLon = $dLon - $c.Lon
    $distM = [Math]::Sqrt([Math]::Pow($toLat * 111000, 2) + [Math]::Pow($toLon * 88000, 2))
    if ($distM -gt 1) {
      $f = [Math]::Min(1, $stepM / $distM)
      $c.Lat += $toLat * $f; $c.Lon += $toLon * $f
      $c.Range = [Math]::Max(0, $c.Range - [Math]::Min($stepM, $distM) / 1000)
    }
    $full = if ($Gas) { 600 } else { 400 }
    $eta = [int]($distM / ($SpeedKmh / 3.6))
    try {
      Post "/$($c.Id)/pos" @{ member_id = $c.Member; lat = $c.Lat; lng = $c.Lon; heading = 180; speed = $SpeedKmh / 3.6; eta_s = $eta; ev = (-not $Gas); range_km = [Math]::Round($c.Range, 1); energy_pct = [Math]::Round($c.Range / $full * 100, 1) } | Out-Null
    } catch { Write-Warning "$($c.Name): $($_.Exception.Message)" }
    Write-Host ("{0}: 목적지까지 {1:N1}km · 남은 {2:N0}km" -f $c.Name, ($distM / 1000), $c.Range)
  }
  Start-Sleep -Seconds $IntervalSec
}
