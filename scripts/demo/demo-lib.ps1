<#
  시연 녹화 도우미 (record-demo.ps1이 점으로 불러 쓴다).
  - 화면 글자로 누르기: uiautomator dump → text / content-desc가 맞는 첫 칸의 가운데를 누른다 (좌표를 박아 두지 않는다).
  - 녹화: adb shell screenrecord (기기 한 대당 하나, 최대 180초) → 끝나면 SIGINT로 멈추고 가져온다.
  모든 adb 호출에 -s 기기를 붙인다 (차 에뮬레이터와 폰 에뮬레이터가 같이 떠 있다).
#>
$ErrorActionPreference = 'Stop'
$script:Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

function Invoke-Adb {
    # 인자는 그대로 adb에 넘긴다 (-p 같은 글자를 PowerShell 매개변수로 읽지 않게 $args 사용)
    $dev = $args[0]; $rest = @($args | Select-Object -Skip 1)
    $ErrorActionPreference = 'Continue'
    $o = & $script:Adb -s $dev @rest 2>&1
    return ($o | Out-String).Trim()
}

function Get-UiNodes([string]$Dev) {
    $ErrorActionPreference = 'Continue'
    # 화면이 계속 움직이면(주행 화면) dump가 조용히 실패해 지난 파일이 남는다 → 지우고 다시 받는다
    for ($i = 0; $i -lt 5; $i++) {
        $null = & $script:Adb -s $Dev shell rm -f /sdcard/eodi-ui.xml 2>&1
        $null = & $script:Adb -s $Dev shell uiautomator dump /sdcard/eodi-ui.xml 2>&1
        $raw = (& $script:Adb -s $Dev exec-out cat /sdcard/eodi-ui.xml 2>$null | Out-String)
        if ($raw -match '<hierarchy') {
            try { return ([xml]$raw).SelectNodes('//node') } catch { }
        }
        Start-Sleep -Milliseconds 200
    }
    return @()
}

function Get-NodeCenter($n) {
    if ($n.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        return @([int](([int]$Matches[1] + [int]$Matches[3]) / 2), [int](([int]$Matches[2] + [int]$Matches[4]) / 2))
    }
}

<# 글자(정규식)가 맞는 칸 찾기. 클릭할 수 있는 조상이 있으면 그 칸 가운데. 없으면 $null #>
function Find-Ui([string]$Dev, [string]$Pattern, [int]$TimeoutSec = 8, [int]$Index = 0, [string]$Region) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    do {
        $hits = @(Get-UiNodes $Dev | Where-Object {
                ($_.text -and $_.text -match $Pattern) -or ($_.'content-desc' -and $_.'content-desc' -match $Pattern)
            } | ForEach-Object { , (Get-NodeCenter $_) } | Where-Object {
                if (-not $Region) { return $true }
                $r = $Region.Split(',') | ForEach-Object { [int]$_ }   # x1,y1,x2,y2
                $_[0] -ge $r[0] -and $_[1] -ge $r[1] -and $_[0] -le $r[2] -and $_[1] -le $r[3]
            })
        if ($hits.Count -gt $Index) { return $hits[$Index] }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $null
}

function Tap([string]$Dev, [int]$X, [int]$Y) { $null = Invoke-Adb $Dev shell input tap $X $Y }

<# 글자로 누르기. 못 찾으면 -Optional이 아니면 멈춘다 #>
function Tap-Text([string]$Dev, [string]$Pattern, [int]$TimeoutSec = 8, [int]$Index = 0, [string]$Region, [switch]$Optional) {
    $p = Find-Ui $Dev $Pattern $TimeoutSec $Index $Region
    if (-not $p) {
        if ($Optional) { Write-Host "  (없음) $Pattern" -ForegroundColor DarkYellow; return }
        throw "화면에서 '$Pattern'을 찾지 못했습니다 ($Dev)"
    }
    Write-Host "  누름: $Pattern @ $($p[0]),$($p[1])"
    Tap $Dev $p[0] $p[1]
    return
}

function Swipe([string]$Dev, [int]$X1, [int]$Y1, [int]$X2, [int]$Y2, [int]$Ms = 400) {
    $null = Invoke-Adb $Dev shell input swipe $X1 $Y1 $X2 $Y2 $Ms
}

function Wait-Ui([string]$Dev, [string]$Pattern, [int]$TimeoutSec = 15) {
    $p = Find-Ui $Dev $Pattern $TimeoutSec
    if (-not $p) { Write-Host "  (기다렸지만 안 보임) $Pattern" -ForegroundColor DarkYellow }
    return [bool]$p
}

<# 녹화 시작: 기기 안 /sdcard/eodi-rec-<이름>.mp4. 끝은 Stop-Rec #>
function Start-Rec([string]$Dev, [string]$Name, [int]$BitRate = 12000000, [string]$Size) {
    $null = Invoke-Adb $Dev shell rm -f "/sdcard/eodi-rec-$Name.mp4"
    $a = @('-s', $Dev, 'shell', 'screenrecord', '--bit-rate', "$BitRate", '--time-limit', '180')
    if ($Size) { $a += @('--size', $Size) }
    $a += "/sdcard/eodi-rec-$Name.mp4"
    $p = Start-Process -FilePath $script:Adb -ArgumentList $a -PassThru -WindowStyle Hidden
    Start-Sleep -Milliseconds 1200   # 인코더가 첫 프레임을 잡을 때까지
    return @{ Dev = $Dev; Name = $Name; Proc = $p; Started = Get-Date }
}

function Stop-Rec($Rec, [string]$OutDir) {
    Start-Sleep -Milliseconds 800
    $null = Invoke-Adb $Rec.Dev shell pkill -INT screenrecord
    $Rec.Proc | Wait-Process -Timeout 15 -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 1500       # mp4 마무리(moov) 쓰는 시간
    $dst = Join-Path $OutDir "$($Rec.Name).mp4"
    $null = Invoke-Adb $Rec.Dev pull "/sdcard/eodi-rec-$($Rec.Name).mp4" $dst
    $null = Invoke-Adb $Rec.Dev shell rm -f "/sdcard/eodi-rec-$($Rec.Name).mp4"
    $sec = [Math]::Round(((Get-Date) - $Rec.Started).TotalSeconds)
    Write-Host "  녹화 끝: $dst (~${sec}s)" -ForegroundColor Green
    return $dst
}

function Shot([string]$Dev, [string]$Path) {
    $null = Invoke-Adb $Dev shell screencap -p /sdcard/eodi-shot.png
    $null = Invoke-Adb $Dev pull /sdcard/eodi-shot.png $Path
}

<#
  Pleos 차 화면 한글 키보드(두벌식, 2560×1440)를 눌러 글을 친다. adb input text는 한글을 못 넣는다.
  $Keys: 자모를 친 순서대로 (된소리는 ⇧ 다음 예사소리). ' '는 띄어쓰기. 예) 'ㅂㅏㄷㅏ ㅇㅓㄷㅣㅇㅕ⇧ㅅㅈㅣ' = "바다 어디였지"
#>
$script:CarKeys = @{
    'ㅂ' = 557, 920; 'ㅈ' = 666, 920; 'ㄷ' = 775, 920; 'ㄱ' = 884, 920; 'ㅅ' = 993, 920; 'ㅛ' = 1102, 920; 'ㅕ' = 1211, 920; 'ㅑ' = 1320, 920; 'ㅐ' = 1429, 920; 'ㅔ' = 1538, 920
    'ㅁ' = 666, 1029; 'ㄴ' = 775, 1029; 'ㅇ' = 884, 1029; 'ㄹ' = 993, 1029; 'ㅎ' = 1102, 1029; 'ㅗ' = 1211, 1029; 'ㅓ' = 1320, 1029; 'ㅏ' = 1429, 1029; 'ㅣ' = 1538, 1029
    '⇧' = 584, 1138; 'ㅋ' = 720, 1138; 'ㅌ' = 829, 1138; 'ㅊ' = 938, 1138; 'ㅍ' = 1047, 1138; 'ㅠ' = 1156, 1138; 'ㅜ' = 1265, 1138; 'ㅡ' = 1374, 1138
    ' ' = 1020, 1246
}
function Type-CarHangul([string]$Dev, [string]$Keys, [int]$GapMs = 170) {
    foreach ($ch in $Keys.ToCharArray()) {
        $k = $script:CarKeys["$ch"]
        if (-not $k) { throw "키보드에 없는 글자: $ch" }
        Tap $Dev $k[0] $k[1]
        Start-Sleep -Milliseconds $GapMs
    }
}
