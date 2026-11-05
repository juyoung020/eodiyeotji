<#
  Virtual Console 차량 상태를 명령으로 바꾼다 (주행/정차 전환을 손 대신 스크립트로 재현).

  예)
    powershell -ExecutionPolicy Bypass -File scripts\vc.ps1 -Gear Drive -Speed 40   # 주행 중 → CarUxRestrictions 제한 켜짐
    powershell -ExecutionPolicy Bypass -File scripts\vc.ps1 -Speed 0 -Gear Park      # 정차
    powershell -ExecutionPolicy Bypass -File scripts\vc.ps1 -State                    # 현재 상태 JSON
    powershell -ExecutionPolicy Bypass -File scripts\vc.ps1 -Geo 37.7905,128.9150     # 위치(위도,경도) 넣기

  원리: Virtual Console 웹 페이지(http://127.0.0.1:8088)가 쓰는 HTTP API를 그대로 부른다.
    POST /browser/session?client=ID   → 세션 활성화 (약 3초 유지, 없으면 {"error":"emulator_inactive"})
    GET  /vehicle/gear?gear=Park|Reverse|Neutral|Drive
    GET  /vehicle/controls?speed=<km/h>&accelerator=<0-100>&brake=<0-100>
    GET  /vehicle/state
  위치는 Virtual Console이 아니라 adb emu geo fix (경도, 위도 순서)로 넣는다 → Pleos FusedLocation에 그대로 들어간다.
  시동(IGNITION) 항목은 Virtual Console에 없다.
#>
param(
    [ValidateSet('Park', 'Reverse', 'Neutral', 'Drive')][string]$Gear,
    [Nullable[double]]$Speed,
    [string]$Geo,
    [switch]$State,
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$adb = "$SdkRoot\platform-tools\adb.exe"
$base = 'http://127.0.0.1:8088'
& $adb forward tcp:8088 tcp:8088 | Out-Null

function Invoke-Vc([string]$path) {
    Invoke-RestMethod -Method Post -Uri "$base/browser/session?client=eodi-script" | Out-Null
    Invoke-RestMethod -Uri "$base$path"
}

if ($Gear) { Invoke-Vc "/vehicle/gear?gear=$Gear" | Out-Null; Write-Host "기어 = $Gear" }
if ($null -ne $Speed) {
    $acc = if ($Speed -gt 0) { 20 } else { 0 }
    $brk = if ($Speed -gt 0) { 0 } else { 30 }
    Invoke-Vc "/vehicle/controls?speed=$Speed&accelerator=$acc&brake=$brk" | Out-Null
    Write-Host "속도 = $Speed km/h"
}
if ($Geo) {
    $lat, $lon = $Geo.Split(',') | ForEach-Object { $_.Trim() }
    & $adb emu geo fix $lon $lat | Out-Null
    Write-Host "위치 = $lat, $lon"
}
if ($State) { Invoke-Vc '/vehicle/state' | ConvertTo-Json -Depth 6 }
if ($Gear -or ($null -ne $Speed)) {
    Start-Sleep -Milliseconds 500
    $ds = (& $adb shell dumpsys car_service --services CarDrivingStateService) -match 'Current Driving State'
    Write-Host "$ds   (0=정차 1=공회전 2=주행)"
}
