<#
  시연용 주행 재생: 실행 중인 에뮬레이터에서 실제 도로를 따라 차를 "달리게" 한다.
  주행 구간마다 기어 D + 속도를 넣고 위치(adb emu geo fix)를 1초 간격 경로대로 흘려 넣은 뒤,
  정차 구간에서는 속도 0 + 기어 P로 세워 두고 기다린다. → 앱은 P↔D 전환으로 주행 세션을 시작/종료하고
  Pleos FusedLocation으로 위치를 받는다.

  예)
    powershell -ExecutionPolicy Bypass -File scripts\replay.ps1 -List                          # 시나리오 목록
    powershell -ExecutionPolicy Bypass -File scripts\replay.ps1 -Scenario seoul-short -DryRun  # 할 일만 출력
    powershell -ExecutionPolicy Bypass -File scripts\replay.ps1 -Scenario gangneung-day        # 실제 시간 그대로
    powershell -ExecutionPolicy Bypass -File scripts\replay.ps1 -Scenario gangneung-day -Speed 10 -StopSpeed 60 -MinStopMinutes 11
        # 주행은 10배속, 정차는 짧게 줄이되 최소 11분은 실제로 세워 둔다 (머문 곳 감지 확인용)
    powershell -ExecutionPolicy Bypass -File scripts\replay.ps1 -Scenario gangneung-trip -StartSegment 3   # 3번째 구간부터

  매개변수
    -Scenario        시나리오 이름(scripts\demo-routes\<이름>.json) 또는 .json 경로. 기본 gangneung-day
    -Speed           주행 배속 (기본 1 = 실제 시간). 10이면 경로 10초를 기기 시간 1초에 흘려 넣는다
    -StopSpeed       정차 배속 (기본 = -Speed). 1이면 정차는 실제 분 그대로 기다린다
    -MinStopMinutes  배속을 적용한 뒤에도 정차를 최소 이 시간(실제 분)은 유지한다. 기본 0
    -StartSegment    몇 번째 구간부터 재생할지 (1부터). 앞 구간은 건너뛰고 차를 그 출발점에 둔다
    -LeadInSec       재생 전 출발점에 P로 세워 두는 시간(초). 기본 5
    -Serial          adb 기기 (기본: 연결된 에뮬레이터 하나)
    -DryRun          에뮬레이터를 건드리지 않고 할 일과 예상 시간만 출력
    -List            시나리오 목록 출력

  배속(-Speed)의 대가 — 기기 시간은 그대로 흐르고 위치만 빨리 바뀐다:
    · 기기가 받는 위치 시각이 1/N로 압축된다 → 앱이 기록하는 주행 시간은 1/N, 위치 차이로 계산한 속도는 N배가 된다.
      (차량 속도 신호(Virtual Console)와 geo fix의 속도 값은 경로의 실제 속도를 그대로 넣는다.)
    · 높은 배속에서는 adb 한 번 호출(약 40ms)보다 점이 빨리 오므로 중간 점을 건너뛰고 최신 점만 보낸다.
    · 정차도 같은 배속이면 머문 시간이 줄어, 기기 시간 10분 이상이어야 잡히는 "머문 곳"이 감지되지 않는다.
      → -StopSpeed 1(정차만 실제 시간) 또는 -MinStopMinutes 11(정차는 최소 11분) 을 같이 쓴다.

  Ctrl+C로 언제 멈춰도 마지막에 속도 0 + 기어 P로 세운다.
  Virtual Console 세션(POST /browser/session)은 약 3초면 끝나므로 재생 중 2초마다 다시 활성화한다. (scripts\vc.ps1과 같은 방식)
#>
param(
    [string]$Scenario = 'gangneung-day',
    [ValidateRange(0.1, 1000)][double]$Speed = 1,
    [double]$StopSpeed = 0,
    [double]$MinStopMinutes = 0,
    [ValidateRange(1, 1000)][int]$StartSegment = 1,
    [int]$LeadInSec = 5,
    [string]$Serial,
    [switch]$DryRun,
    [switch]$List,
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$inv = [Globalization.CultureInfo]::InvariantCulture
[Threading.Thread]::CurrentThread.CurrentCulture = $inv
$routeDir = Join-Path $PSScriptRoot 'demo-routes'
$adb = "$SdkRoot\platform-tools\adb.exe"
$base = 'http://127.0.0.1:8088'
if ($StopSpeed -le 0) { $StopSpeed = $Speed }

function Format-Dur([double]$sec) {
    $sec = [Math]::Round($sec)
    if ($sec -ge 3600) { '{0}시간 {1}분' -f [Math]::Floor($sec / 3600), [Math]::Floor(($sec % 3600) / 60) }
    elseif ($sec -ge 60) { '{0}분 {1}초' -f [Math]::Floor($sec / 60), ($sec % 60) }
    else { '{0}초' -f $sec }
}

# ---------- 목록 ----------
if ($List) {
    Get-ChildItem $routeDir -Filter *.json | ForEach-Object {
        $s = Get-Content $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        $drive = ($s.segments | Where-Object type -eq 'drive' | Measure-Object duration_s -Sum).Sum
        $km = ($s.segments | Where-Object type -eq 'drive' | Measure-Object distance_m -Sum).Sum / 1000
        $stop = ($s.segments | Where-Object type -eq 'stop' | Measure-Object minutes -Sum).Sum
        Write-Host ("{0,-16} {1}" -f $s.name, $s.title)
        Write-Host ("{0,-16} 주행 {1:N1}km {2} · 정차 {3}분 · 구간 {4}개" -f '', $km, (Format-Dur $drive), $stop, $s.segments.Count)
    }
    return
}

# ---------- 시나리오 읽기 ----------
$scPath = if ($Scenario -like '*.json' -and (Test-Path $Scenario)) { (Resolve-Path $Scenario).Path } else { Join-Path $routeDir "$Scenario.json" }
if (-not (Test-Path $scPath)) { throw "시나리오가 없다: $scPath  (-List로 목록 확인)" }
$sc = Get-Content $scPath -Raw -Encoding UTF8 | ConvertFrom-Json
$scDir = Split-Path -Parent $scPath
$segs = @($sc.segments)
if ($StartSegment -gt $segs.Count) { throw "구간은 $($segs.Count)개다 (-StartSegment $StartSegment)" }

function Read-Leg($seg) {
    $rows = Import-Csv (Join-Path $scDir $seg.file)
    $n = $rows.Count
    $leg = @{ n = $n; t = New-Object double[] $n; lat = New-Object double[] $n; lon = New-Object double[] $n; kmh = New-Object double[] $n }
    for ($i = 0; $i -lt $n; $i++) {
        $leg.t[$i] = [double]::Parse($rows[$i].t_offset_s, $inv)
        $leg.lat[$i] = [double]::Parse($rows[$i].lat, $inv)
        $leg.lon[$i] = [double]::Parse($rows[$i].lon, $inv)
        $leg.kmh[$i] = [double]::Parse($rows[$i].speed_kmh, $inv)
    }
    $leg
}
function Get-StopSec($seg) { [Math]::Max([double]$seg.minutes * 60 / $StopSpeed, $MinStopMinutes * 60) }

# 계획 출력
Write-Host "시나리오: $($sc.title)"
Write-Host ("배속: 주행 x{0}, 정차 x{1}{2}" -f $Speed, $StopSpeed, $(if ($MinStopMinutes -gt 0) { ", 정차 최소 ${MinStopMinutes}분" } else { '' }))
$total = $LeadInSec
for ($k = $StartSegment - 1; $k -lt $segs.Count; $k++) {
    $s = $segs[$k]
    if ($s.type -eq 'drive') {
        $real = $s.duration_s / $Speed; $total += $real + 2
        Write-Host ("  {0,2}. 주행 {1} → {2}  {3:N1}km, 경로 {4} → 실제 {5}" -f ($k + 1), $s.from, $s.to, ($s.distance_m / 1000), (Format-Dur $s.duration_s), (Format-Dur $real))
    } else {
        $real = Get-StopSec $s; $total += $real
        Write-Host ("  {0,2}. 정차 {1}  {2}분 → 실제 {3}" -f ($k + 1), $s.place, $s.minutes, (Format-Dur $real))
    }
}
Write-Host "예상 재생 시간: $(Format-Dur $total)"
if ($DryRun) {
    Write-Host '(DryRun: 에뮬레이터는 건드리지 않았다)'
    return
}

# ---------- 에뮬레이터 연결 ----------
if (-not $Serial) {
    $Serial = if ($env:ANDROID_SERIAL) { $env:ANDROID_SERIAL } else {
        $emus = @(& $adb devices | Where-Object { $_ -match '^(emulator-\d+)\s+device' } | ForEach-Object { $Matches[1] })
        if ($emus.Count -ne 1) { throw "에뮬레이터가 $($emus.Count)개 연결돼 있다. -Serial 로 고른다." }
        $emus[0]
    }
}
& $adb -s $Serial forward tcp:8088 tcp:8088 | Out-Null

$script:lastSession = [Diagnostics.Stopwatch]::StartNew()
function Enable-Session {
    Invoke-RestMethod -Method Post -Uri "$base/browser/session?client=eodi-replay" -TimeoutSec 3 | Out-Null
    $script:lastSession.Restart()
}
function Invoke-Vc([string]$path) {
    Enable-Session
    $r = Invoke-RestMethod -Uri "$base$path" -TimeoutSec 3
    if ($r.error) { throw "Virtual Console 오류: $($r.error) ($path)" }
}
function Keep-Alive {
    if ($script:lastSession.Elapsed.TotalSeconds -ge 2) {
        try { Enable-Session } catch { Write-Warning "Virtual Console 세션 유지 실패: $_" }
    }
}
function Set-Gear([string]$g) { Invoke-Vc "/vehicle/gear?gear=$g" }
function Set-CarSpeed([double]$kmh) {
    $kmh = [Math]::Round($kmh)
    $acc = if ($kmh -gt 0) { 20 } else { 0 }
    $brk = if ($kmh -gt 0) { 0 } else { 30 }
    Invoke-Vc "/vehicle/controls?speed=$kmh&accelerator=$acc&brake=$brk"
}
function Send-Fix([double]$lat, [double]$lon, [double]$kmh) {
    # 순서: 경도 위도 고도(m) 위성수 속도(노트)
    & $adb -s $Serial emu geo fix $lon.ToString('F6', $inv) $lat.ToString('F6', $inv) '30' '10' ($kmh / 1.852).ToString('F1', $inv) | Out-Null
}
function Get-DrivingState {
    $line = (& $adb -s $Serial shell dumpsys car_service --services CarDrivingStateService) -match 'Current Driving State'
    ($line -join ' ') -replace '.*Current Driving State:\s*', ''
}

# Ctrl+C를 직접 받아서(콘솔에서 실행할 때) 정리 단계로 확실히 넘긴다. 콘솔이 없으면 finally에만 맡긴다.
$ctrlC = $false
try { [Console]::TreatControlCAsInput = $true; $ctrlC = $true } catch { }
function Test-Abort {
    if ($ctrlC) {
        while ([Console]::KeyAvailable) {
            $key = [Console]::ReadKey($true)
            if ($key.Key -eq 'C' -and ($key.Modifiers -band [ConsoleModifiers]::Control)) { throw [OperationCanceledException]'Ctrl+C' }
        }
    }
}
function Wait-Real([double]$sec, [string]$label) {
    $sw = [Diagnostics.Stopwatch]::StartNew(); $lastPrint = -999
    while ($sw.Elapsed.TotalSeconds -lt $sec) {
        Test-Abort; Keep-Alive
        $left = $sec - $sw.Elapsed.TotalSeconds
        if ($label -and ($sw.Elapsed.TotalSeconds - $lastPrint) -ge 60) {
            Write-Host ("    {0} · 남은 시간 {1}" -f $label, (Format-Dur $left)); $lastPrint = $sw.Elapsed.TotalSeconds
        }
        Start-Sleep -Milliseconds ([int][Math]::Min(500.0, [Math]::Max(10.0, $left * 1000)))
    }
}

function Invoke-Drive($seg, $no) {
    $leg = Read-Leg $seg
    Write-Host ("[{0}] 주행 {1} → {2} ({3:N1}km, 점 {4}개)" -f $no, $seg.from, $seg.to, ($seg.distance_m / 1000), $leg.n)
    Send-Fix $leg.lat[0] $leg.lon[0] 0
    Set-Gear 'Drive'
    $lastKmh = [Math]::Max(5.0, $leg.kmh[1]); Set-CarSpeed $lastKmh
    Write-Host "    기어 D · 주행 상태 $(Get-DrivingState)"
    $sw = [Diagnostics.Stopwatch]::StartNew(); $speedClock = [Diagnostics.Stopwatch]::StartNew(); $lastPrint = 0
    $sent = 0; $i = 0; $last = $leg.n - 1; $skipped = 0
    while ($sent -lt $last) {
        Test-Abort
        $routeT = $sw.Elapsed.TotalSeconds * $Speed
        while ($i -lt $last -and $leg.t[$i + 1] -le $routeT) { $i++ }
        if ($i -gt $sent) {
            $skipped += $i - $sent - 1
            Send-Fix $leg.lat[$i] $leg.lon[$i] $leg.kmh[$i]
            $sent = $i
        }
        if ($speedClock.Elapsed.TotalSeconds -ge 1 -and $sent -lt $last) {
            # 주행 중에는 최소 5km/h로 둬서 Driving State 2(주행)를 유지한다
            $want = [Math]::Max(5.0, [Math]::Round($leg.kmh[$sent]))
            if ([Math]::Abs($want - $lastKmh) -ge 2) {
                try { Set-CarSpeed $want; $lastKmh = $want } catch { Write-Warning "속도 설정 실패: $_" }
            }
            $speedClock.Restart()
        }
        Keep-Alive
        if ($sw.Elapsed.TotalSeconds - $lastPrint -ge 10) {
            Write-Host ("    {0,3:N0}%  경로 {1} / {2}  {3,3:N0} km/h" -f (100 * $sent / $last), (Format-Dur $leg.t[$sent]), (Format-Dur $leg.t[$last]), $leg.kmh[$sent])
            $lastPrint = $sw.Elapsed.TotalSeconds
        }
        if ($sent -lt $last) {
            $due = $leg.t[$sent + 1] / $Speed - $sw.Elapsed.TotalSeconds
            Start-Sleep -Milliseconds ([int][Math]::Min(200.0, [Math]::Max(5.0, $due * 1000)))
        }
    }
    Set-CarSpeed 0
    Start-Sleep -Milliseconds 500
    Set-Gear 'Park'
    Write-Host ("    도착 {0} · 실제 {1}, 건너뛴 점 {2}개 · 기어 P · 주행 상태 {3}" -f $seg.to, (Format-Dur $sw.Elapsed.TotalSeconds), $skipped, (Get-DrivingState))
}

# ---------- 재생 ----------
try {
    # 출발점: 시작 구간이 주행이면 그 첫 점, 정차면 바로 앞 주행의 마지막 점
    $startFix = $null
    for ($k = $StartSegment - 1; $k -ge 0 -and -not $startFix; $k--) {
        if ($segs[$k].type -eq 'drive') {
            $leg = Read-Leg $segs[$k]
            $idx = if ($k -eq $StartSegment - 1) { 0 } else { $leg.n - 1 }
            $startFix = @($leg.lat[$idx], $leg.lon[$idx])
        }
    }
    if (-not $startFix) {
        $k = $StartSegment - 1
        while ($segs[$k].type -ne 'drive') { $k++ }
        $leg = Read-Leg $segs[$k]; $startFix = @($leg.lat[0], $leg.lon[0])
    }
    Set-CarSpeed 0; Set-Gear 'Park'
    Send-Fix $startFix[0] $startFix[1] 0
    Write-Host "출발점 $($startFix[0]), $($startFix[1]) 에 P로 세움 · 주행 상태 $(Get-DrivingState) · ${LeadInSec}초 뒤 시작 (Ctrl+C로 중지)"
    Wait-Real $LeadInSec ''

    for ($k = $StartSegment - 1; $k -lt $segs.Count; $k++) {
        $s = $segs[$k]; $no = '{0}/{1}' -f ($k + 1), $segs.Count
        if ($s.type -eq 'drive') { Invoke-Drive $s $no }
        elseif ($s.type -eq 'stop') {
            $sec = Get-StopSec $s
            Set-CarSpeed 0; Set-Gear 'Park'
            Write-Host ("[{0}] 정차 {1} {2}분 → 실제 {3} 기다림 · 주행 상태 {4}" -f $no, $s.place, $s.minutes, (Format-Dur $sec), (Get-DrivingState))
            Wait-Real $sec "정차 $($s.place)"
        }
        else { throw "모르는 구간 종류: $($s.type)" }
    }
    Write-Host '재생 끝.'
}
catch [OperationCanceledException] {
    Write-Host 'Ctrl+C: 재생을 멈춘다.'
}
finally {
    if ($ctrlC) { try { [Console]::TreatControlCAsInput = $false } catch { } }
    try {
        Set-CarSpeed 0; Set-Gear 'Park'
        Write-Host "정리: 속도 0 · 기어 P · 주행 상태 $(Get-DrivingState)  (0=정차 1=공회전 2=주행)"
    } catch {
        Write-Warning "정리 실패 — 직접 세운다: powershell -ExecutionPolicy Bypass -File scripts\vc.ps1 -Speed 0 -Gear Park  ($_)"
    }
}
