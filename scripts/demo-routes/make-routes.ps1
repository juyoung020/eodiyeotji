<#
  시연용 주행 데이터(scripts\demo-routes)를 다시 만든다. replay.ps1이 이 결과를 재생한다.

  예)
    powershell -ExecutionPolicy Bypass -File scripts\demo-routes\make-routes.ps1          # 전부 다시 만들기
    powershell -ExecutionPolicy Bypass -File scripts\demo-routes\make-routes.ps1 -Only seoul-short

  하는 일:
    1. 아래 $Places / $Scenarios 에 적힌 구간마다 OSRM 공개 서버에 길찾기를 요청해 실제 도로 모양(OSM 도로망)을 받는다.
    2. OSRM이 준 구간별 속도를 현실 속도로 바꾼다.
         시내(OSRM 70km/h 미만): 20~50km/h,  고속도로(70km/h 이상): 80~100km/h
         급한 회전(꺾임 35/60/100도 이상)에서는 30/20/12km/h까지 줄인다.
         출발은 0에서 가속(1.2m/s²), 도착은 0까지 감속(1.5m/s²).
    3. 그 속도로 달렸을 때의 위치를 1초 간격으로 뽑아 legs\<출발>__<도착>.csv 로 쓴다.
    4. 시나리오(주행·정차 순서)를 <시나리오>.json 으로 쓴다.
  데이터 출처: © OpenStreetMap contributors (ODbL), 길찾기 엔진 OSRM (router.project-osrm.org). README.md 참고.
#>
param(
    [string[]]$Only,
    [string]$Osrm = 'https://router.project-osrm.org'
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
# OSRM 서버는 TLS 1.3만 받는다 → Windows PowerShell 5.1에서 TLS 1.2/1.3 둘 다 켠다
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]3072 -bor [Net.SecurityProtocolType]12288
$inv = [Globalization.CultureInfo]::InvariantCulture
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$utf8 = New-Object Text.UTF8Encoding($false)

# 장소: 좌표는 OSM(Nominatim)에서 찾은 값. 도착 지점은 그 장소 앞 도로(주차장 입구 근처)로 잡았다.
$Places = @{
    'jamsil'        = @{ name = '집(서울 잠실)';            lat = 37.51330; lon = 127.10010 }
    'hoengseong-rest' = @{ name = '횡성휴게소(강릉방향)';   lat = 37.46308; lon = 128.13487 }
    'gangneung-stn' = @{ name = '강릉역';                   lat = 37.76452; lon = 128.89940 }
    'gyeongpo'      = @{ name = '경포해변';                 lat = 37.80420; lon = 128.90622 }
    'chodang'       = @{ name = '초당순두부마을';           lat = 37.79114; lon = 128.91552 }
    'anmok'         = @{ name = '안목 커피거리';            lat = 37.77357; lon = 128.94539 }
    'seoul-cityhall' = @{ name = '서울시청';                lat = 37.56679; lon = 126.97842 }
    'gwangjang'     = @{ name = '광장시장';                 lat = 37.57019; lon = 126.99950 }
    'gyeongbokgung' = @{ name = '경복궁 주차장';            lat = 37.57650; lon = 126.97700 }
}

# 시나리오: 출발지 → (drive) → 장소 → (stop 분) → ... 순서. 'stop' 숫자는 그 장소에 머무는 시간(분).
$Scenarios = [ordered]@{
    'gangneung-day'  = @{ title = '강릉 하루: 강릉역 → 경포해변 → 초당순두부마을 → 안목 커피거리'
                          plan  = @('gangneung-stn', 'gyeongpo', 60, 'chodang', 50, 'anmok', 40) }
    'gangneung-trip' = @{ title = '가족 강릉 여행(README 이야기): 집 → 횡성휴게소 → 경포해변 → 초당순두부 → 안목 커피거리'
                          plan  = @('jamsil', 'hoengseong-rest', 20, 'gyeongpo', 60, 'chodang', 50, 'anmok', 40) }
    'seoul-short'    = @{ title = '서울 짧은 시내 주행(빠른 확인용): 서울시청 → 광장시장 → 경복궁'
                          plan  = @('seoul-cityhall', 'gwangjang', 12, 'gyeongbokgung', 10) }
}

# ---------- 기하 ----------
# 주의: [Math]::Min/Max에 정수 리터럴을 넣으면 PowerShell이 int 오버로드를 골라 소수가 잘린다 → 항상 1.0처럼 쓴다
function Get-Dist($la1, $lo1, $la2, $lo2) {
    $r = 6371008.8; $d2r = [Math]::PI / 180
    $dla = ($la2 - $la1) * $d2r; $dlo = ($lo2 - $lo1) * $d2r
    $a = [Math]::Sin($dla / 2) * [Math]::Sin($dla / 2) + [Math]::Cos($la1 * $d2r) * [Math]::Cos($la2 * $d2r) * [Math]::Sin($dlo / 2) * [Math]::Sin($dlo / 2)
    2 * $r * [Math]::Asin([Math]::Min(1.0, [Math]::Sqrt($a)))
}
function Get-Bearing($la1, $lo1, $la2, $lo2) {
    $d2r = [Math]::PI / 180
    $y = [Math]::Sin(($lo2 - $lo1) * $d2r) * [Math]::Cos($la2 * $d2r)
    $x = [Math]::Cos($la1 * $d2r) * [Math]::Sin($la2 * $d2r) - [Math]::Sin($la1 * $d2r) * [Math]::Cos($la2 * $d2r) * [Math]::Cos(($lo2 - $lo1) * $d2r)
    [Math]::Atan2($y, $x) * 180 / [Math]::PI
}
function Get-Target([double]$osrmKmh) {
    # OSRM 구간 속도(자유 주행 추정) → 현실 목표 속도(km/h)
    if ($osrmKmh -ge 70) { [Math]::Min(100.0, [Math]::Max(80.0, $osrmKmh * 1.15)) }
    else { [Math]::Min(50.0, [Math]::Max(20.0, $osrmKmh)) }
}

# ---------- 한 구간 만들기 ----------
function New-Leg($fromKey, $toKey) {
    $a = $Places[$fromKey]; $b = $Places[$toKey]
    $coords = '{0},{1};{2},{3}' -f $a.lon.ToString($inv), $a.lat.ToString($inv), $b.lon.ToString($inv), $b.lat.ToString($inv)
    $url = "$Osrm/route/v1/driving/${coords}?overview=full&geometries=geojson&annotations=distance,duration"
    Write-Host "  OSRM $($a.name) → $($b.name)"
    $res = Invoke-RestMethod -Uri $url -TimeoutSec 60
    if ($res.code -ne 'Ok') { throw "OSRM 실패: $($res.code)" }
    $route = $res.routes[0]
    $g = $route.geometry.coordinates          # [lon, lat]
    $ann = $route.legs[0].annotation
    $n = $g.Count

    # 1) 원래 꼭짓점의 목표 속도·회전 제한
    $tgt = New-Object double[] ($n - 1)
    for ($i = 0; $i -lt $n - 1; $i++) {
        $t = [double]$ann.duration[$i]; $d = [double]$ann.distance[$i]
        $kmh = if ($t -gt 0) { $d / $t * 3.6 } elseif ($i -gt 0) { $tgt[$i - 1] } else { 40 }
        $tgt[$i] = Get-Target $kmh
    }
    $nodeCap = New-Object double[] $n
    $nodeCap[0] = 0; $nodeCap[$n - 1] = 0
    for ($j = 1; $j -lt $n - 1; $j++) {
        $b1 = Get-Bearing $g[$j - 1][1] $g[$j - 1][0] $g[$j][1] $g[$j][0]
        $b2 = Get-Bearing $g[$j][1] $g[$j][0] $g[$j + 1][1] $g[$j + 1][0]
        $turn = [Math]::Abs((($b2 - $b1 + 540) % 360) - 180)
        $cap = [Math]::Min($tgt[$j - 1], $tgt[$j])
        if ($turn -gt 100) { $cap = [Math]::Min($cap, 12.0) } elseif ($turn -gt 60) { $cap = [Math]::Min($cap, 20.0) } elseif ($turn -gt 35) { $cap = [Math]::Min($cap, 30.0) }
        $nodeCap[$j] = $cap
    }

    # 2) 20m 이하 조각으로 쪼개기 (가감속을 촘촘하게 계산하려고)
    $lat = New-Object Collections.Generic.List[double]; $lon = New-Object Collections.Generic.List[double]
    $cap = New-Object Collections.Generic.List[double]; $seg = New-Object Collections.Generic.List[double]
    $lat.Add($g[0][1]); $lon.Add($g[0][0]); $cap.Add(0)
    for ($i = 0; $i -lt $n - 1; $i++) {
        $d = Get-Dist $g[$i][1] $g[$i][0] $g[$i + 1][1] $g[$i + 1][0]
        $k = [Math]::Max(1, [int][Math]::Ceiling($d / 20))
        for ($s = 1; $s -le $k; $s++) {
            $f = $s / $k
            $lat.Add($g[$i][1] + ($g[$i + 1][1] - $g[$i][1]) * $f)
            $lon.Add($g[$i][0] + ($g[$i + 1][0] - $g[$i][0]) * $f)
            $seg.Add($d / $k)
            $cap.Add($(if ($s -eq $k) { $nodeCap[$i + 1] } else { $tgt[$i] }))
        }
    }
    $m = $lat.Count
    $v = New-Object double[] $m
    for ($j = 0; $j -lt $m; $j++) { $v[$j] = $cap[$j] / 3.6 }
    # 3) 가속(앞으로) · 감속(뒤로) 한계
    $acc = 1.2; $dec = 1.5
    for ($j = 1; $j -lt $m; $j++) { $v[$j] = [Math]::Min($v[$j], [Math]::Sqrt($v[$j - 1] * $v[$j - 1] + 2 * $acc * $seg[$j - 1])) }
    for ($j = $m - 2; $j -ge 0; $j--) { $v[$j] = [Math]::Min($v[$j], [Math]::Sqrt($v[$j + 1] * $v[$j + 1] + 2 * $dec * $seg[$j])) }

    # 4) 1초 간격으로 위치 뽑기 (조각 안에서는 등가속도)
    $sb = New-Object Text.StringBuilder
    [void]$sb.AppendLine('t_offset_s,lat,lon,speed_kmh')
    $clock = 0.0; $next = 0; $dist = 0.0
    for ($j = 0; $j -lt $m - 1; $j++) {
        $d = $seg[$j]; $v0 = $v[$j]; $v1 = $v[$j + 1]
        $dt = 2 * $d / [Math]::Max(0.2, $v0 + $v1)
        $a = if ($dt -gt 0) { ($v1 - $v0) / $dt } else { 0 }
        while ($next -le $clock + $dt) {
            $tau = $next - $clock
            $s = [Math]::Min($d, $v0 * $tau + 0.5 * $a * $tau * $tau)
            $f = if ($d -gt 0) { $s / $d } else { 0 }
            $pla = $lat[$j] + ($lat[$j + 1] - $lat[$j]) * $f
            $plo = $lon[$j] + ($lon[$j + 1] - $lon[$j]) * $f
            $sp = [Math]::Max(0.0, ($v0 + $a * $tau) * 3.6)
            [void]$sb.AppendLine(('{0},{1:F6},{2:F6},{3:F1}' -f $next, $pla, $plo, $sp))
            $next++
        }
        $clock += $dt; $dist += $d
    }
    # 마지막 줄: 도착 지점, 속도 0
    [void]$sb.AppendLine(('{0},{1:F6},{2:F6},0.0' -f $next, $lat[$m - 1], $lon[$m - 1]))
    $file = "legs/${fromKey}__${toKey}.csv"
    [IO.File]::WriteAllText((Join-Path $here $file), ($sb.ToString() -replace "`r`n", "`n"), $utf8)
    $avg = $dist / $clock * 3.6
    Write-Host ("    {0:N1} km, {1:N0}분 {2:N0}초, 평균 {3:N0} km/h, {4}줄" -f ($dist / 1000), [Math]::Floor($clock / 60), ($clock % 60), $avg, ($next + 1))
    @{ file = $file; distance_m = [Math]::Round($dist); duration_s = $next }
}

# ---------- 실행 ----------
# 소수점은 항상 '.' (한국어 Windows 기본 문화권도 '.'지만 다른 PC를 위해 고정)
[Threading.Thread]::CurrentThread.CurrentCulture = $inv
New-Item -ItemType Directory -Force (Join-Path $here 'legs') | Out-Null
$legCache = @{}
foreach ($name in $Scenarios.Keys) {
    if ($Only -and ($Only -notcontains $name)) { continue }
    $sc = $Scenarios[$name]
    Write-Host "[$name] $($sc.title)"
    $segs = New-Object Collections.Generic.List[string]
    $cur = $sc.plan[0]
    for ($i = 1; $i -lt $sc.plan.Count; $i++) {
        $item = $sc.plan[$i]
        if ($item -is [string]) {
            $key = "${cur}__${item}"
            if (-not $legCache.ContainsKey($key)) { $legCache[$key] = New-Leg $cur $item }
            $leg = $legCache[$key]
            $segs.Add(('    {{ "type": "drive", "from": "{0}", "to": "{1}", "file": "{2}", "distance_m": {3}, "duration_s": {4} }}' -f
                    $Places[$cur].name, $Places[$item].name, $leg.file, $leg.distance_m, $leg.duration_s))
            $cur = $item
        } else {
            $segs.Add(('    {{ "type": "stop", "place": "{0}", "minutes": {1} }}' -f $Places[$cur].name, $item))
        }
    }
    $json = "{`n  `"name`": `"$name`",`n  `"title`": `"$($sc.title)`",`n  `"start`": `"$($Places[$sc.plan[0]].name)`",`n  `"segments`": [`n" + ($segs -join ",`n") + "`n  ]`n}`n"
    [IO.File]::WriteAllText((Join-Path $here "$name.json"), $json, $utf8)
}
Write-Host '완료.'
