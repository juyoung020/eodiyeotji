<#
  시연 지역 오프라인 지도 파일(korea-demo.pmtiles) 만들기

  실행:  powershell -ExecutionPolicy Bypass -File scripts\map\build-map.ps1
         powershell -ExecutionPolicy Bypass -File scripts\map\build-map.ps1 -MaxZoom 14
         powershell -ExecutionPolicy Bypass -File scripts\map\build-map.ps1 -Date 20261105 -BufferKm 2

  하는 일
    1. pmtiles 명령줄 도구(protomaps/go-pmtiles)가 scripts\map\tools\ 에 없으면 내려받고 SHA-256을 확인한다.
    2. 시연 경로(scripts\demo-routes\legs\*.csv)를 따라 폭 2×BufferKm 띠 + 서울·강릉 시내 사각형으로
       잘라낼 영역(scripts\map\out\region.geojson)을 만든다.
    3. Protomaps 일일 빌드(전 세계 OpenStreetMap 지도, 약 140GB)에서 그 영역만 HTTP 범위 요청으로 받아
       scripts\map\out\korea-demo.pmtiles 를 만든다 (전 세계 파일을 내려받지 않는다).
    4. pmtiles verify / show 로 확인하고 크기를 출력한다.
    5. node가 있으면 지도 안 글자가 앱에 넣은 글꼴 범위로 다 그려지는지 확인한다 (style\generate.mjs --check).

  - 결과 파일은 git에 올리지 않는다 (.gitignore: scripts/map/out/, scripts/map/tools/).
  - 다시 돌리면 덮어쓴다. 인터넷이 필요하다 (만들 때만. 앱은 인터넷 없이 이 파일로 지도를 그린다).
  - 에뮬레이터로 보내기: scripts\map\push-map.ps1
#>
param(
    [int]$MaxZoom = 15,                 # Protomaps 원본 최대 줌. 14로 낮추면 파일이 작아지고 앱은 14 타일을 확대해 그린다
    [string]$Date = '',                 # 일일 빌드 날짜(yyyyMMdd). 비우면 가장 최근 빌드. 오래된 빌드는 며칠 뒤 지워진다
    [double]$BufferKm = 4.0,            # 경로 양옆으로 넣을 폭(km)
    [int]$Threads = 8
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$inv = [Globalization.CultureInfo]::InvariantCulture

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Resolve-Path (Join-Path $here '..\..')
$tools = Join-Path $here 'tools'
$out = Join-Path $here 'out'
$legsDir = Join-Path $repo 'scripts\demo-routes\legs'
New-Item -ItemType Directory -Force $tools, $out | Out-Null

# ---- 1. pmtiles 도구 ---------------------------------------------------------
$PmtilesVersion = '1.31.2'
$PmtilesZip = "go-pmtiles_${PmtilesVersion}_Windows_x86_64.zip"
$PmtilesSha256 = 'a658baa4d7e55020aef6ca17bd9ff9faa1582671266b36f58c52db0ac8e785a1'  # GitHub 릴리스 자산 digest
$pmtiles = Join-Path $tools 'pmtiles.exe'

$needCli = -not (Test-Path $pmtiles)
if (-not $needCli) { $needCli = -not ((& $pmtiles version) -match [regex]::Escape($PmtilesVersion)) }
if ($needCli) {
    $zip = Join-Path $tools $PmtilesZip
    Write-Host "pmtiles $PmtilesVersion 내려받는 중..."
    Invoke-WebRequest -UseBasicParsing -Uri "https://github.com/protomaps/go-pmtiles/releases/download/v$PmtilesVersion/$PmtilesZip" -OutFile $zip
    $hash = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLower()
    if ($hash -ne $PmtilesSha256) { Remove-Item $zip; throw "pmtiles zip SHA-256 불일치: $hash" }
    Expand-Archive -Force -Path $zip -DestinationPath $tools
    Remove-Item $zip
}
Write-Host ("도구: " + (& $pmtiles version))

# ---- 2. 잘라낼 영역 -----------------------------------------------------------
# 시내 사각형 [서경, 남위, 동경, 북위]: 경로 띠만으로는 시내 지도를 둘러보기 좁으므로 따로 넣는다
$Boxes = [ordered]@{
    '서울 + 김포공항·과천·성남·하남·구리' = @(126.68, 37.38, 127.25, 37.75)
    '강릉 시내 · 경포 · 안목 · 강릉역'     = @(128.78, 37.68, 129.00, 37.87)
}

function Square([double]$lon, [double]$lat, [double]$km) {
    $dLat = $km / 110.574
    $dLon = $km / (111.320 * [Math]::Cos($lat * [Math]::PI / 180))
    return @(($lon - $dLon), ($lat - $dLat), ($lon + $dLon), ($lat + $dLat))
}
function Ring($b) {
    $pts = @(@($b[0], $b[1]), @($b[2], $b[1]), @($b[2], $b[3]), @($b[0], $b[3]), @($b[0], $b[1]))
    $s = ($pts | ForEach-Object { '[' + $_[0].ToString('0.######', $inv) + ',' + $_[1].ToString('0.######', $inv) + ']' }) -join ','
    return "[[$s]]"
}

$polys = New-Object System.Collections.Generic.List[string]
foreach ($k in $Boxes.Keys) { $polys.Add((Ring $Boxes[$k])) }

$legs = Get-ChildItem $legsDir -Filter *.csv | Sort-Object Name
if (-not $legs) { throw "시연 경로가 없습니다: $legsDir" }
$stepKm = $BufferKm / 2   # 정사각형끼리 겹치게 이 간격마다 하나씩
foreach ($leg in $legs) {
    $prev = $null; $n = 0
    $rows = Import-Csv $leg.FullName
    for ($i = 0; $i -lt $rows.Count; $i++) {
        $lat = [double]::Parse($rows[$i].lat, $inv); $lon = [double]::Parse($rows[$i].lon, $inv)
        $last = ($i -eq $rows.Count - 1)
        if ($prev) {
            $dy = ($lat - $prev[1]) * 110.574
            $dx = ($lon - $prev[0]) * 111.320 * [Math]::Cos($lat * [Math]::PI / 180)
            if ([Math]::Sqrt($dx * $dx + $dy * $dy) -lt $stepKm -and -not $last) { continue }
        }
        $polys.Add((Ring (Square $lon $lat $BufferKm))); $prev = @($lon, $lat); $n++
    }
    Write-Host ("  경로 {0}: 점 {1}개 → 띠 조각 {2}개" -f $leg.BaseName, $rows.Count, $n)
}
$regionPath = Join-Path $out 'region.geojson'
$geojson = '{"type":"Feature","properties":{"name":"eodiyeotji demo region"},"geometry":{"type":"MultiPolygon","coordinates":[' + ($polys -join ',') + ']}}'
[IO.File]::WriteAllText($regionPath, $geojson, (New-Object Text.UTF8Encoding($false)))
Write-Host "영역: $regionPath (다각형 $($polys.Count)개, 경로 양옆 ${BufferKm}km + 시내 사각형 $($Boxes.Count)개)"

# ---- 3. 원본 빌드 고르기 + 잘라내기 ------------------------------------------
if (-not $Date) {
    $builds = Invoke-RestMethod -UseBasicParsing -Uri 'https://build-metadata.protomaps.dev/builds.json'
    $Date = ($builds | ForEach-Object { $_.key -replace '\.pmtiles$', '' } | Where-Object { $_ -match '^\d{8}$' } | Sort-Object | Select-Object -Last 1)
    if (-not $Date) { throw '최근 Protomaps 빌드를 찾지 못했습니다. -Date yyyyMMdd 로 지정하세요.' }
}
$src = "https://build.protomaps.com/$Date.pmtiles"
$dst = Join-Path $out 'korea-demo.pmtiles'
$tmp = Join-Path $out 'korea-demo.pmtiles.part'
if (Test-Path $tmp) { Remove-Item $tmp }
Write-Host "원본: $src  (최대 줌 $MaxZoom)"
& $pmtiles extract $src $tmp "--region=$regionPath" "--maxzoom=$MaxZoom" "--download-threads=$Threads"
if ($LASTEXITCODE -ne 0) { throw "pmtiles extract 실패 (exit $LASTEXITCODE)" }

# ---- 4. 확인 -----------------------------------------------------------------
& $pmtiles verify $tmp
if ($LASTEXITCODE -ne 0) { throw "pmtiles verify 실패 (exit $LASTEXITCODE)" }
Move-Item -Force $tmp $dst
& $pmtiles show $dst
$mb = (Get-Item $dst).Length / 1MB
"source=$src`r`nmaxzoom=$MaxZoom`r`nbuffer_km=$BufferKm`r`nsize_mb=$($mb.ToString('0.0', $inv))`r`nbuilt=$((Get-Date).ToString('s'))" |
    Set-Content -Encoding UTF8 (Join-Path $out 'build-info.txt')
Write-Host ("완료: {0}  {1:N1} MB" -f $dst, $mb)

# ---- 5. 글꼴 범위 확인 -------------------------------------------------------
$node = Get-Command node -ErrorAction SilentlyContinue
$styleDir = Join-Path $here 'style'
if ($node -and (Test-Path (Join-Path $styleDir 'node_modules'))) {
    & node (Join-Path $styleDir 'generate.mjs') --check
    if ($LASTEXITCODE -ne 0) { Write-Warning '지도 글자 중 앱에 없는 글꼴 범위가 있습니다. scripts\map\README.md "스타일 다시 만들기"대로 generate.mjs 를 돌려 주세요.' }
} else {
    Write-Host '(글꼴 범위 확인 건너뜀: node 또는 scripts\map\style\node_modules 없음 → README "스타일 다시 만들기")'
}
Write-Host '에뮬레이터로 보내기: powershell -ExecutionPolicy Bypass -File scripts\map\push-map.ps1'
