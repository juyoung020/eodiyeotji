<#
  시연 영상 · README 표지 GIF를 에뮬레이터에서 자동으로 찍는다 (한 번 실행 = 빌드 → 새로 설치 → 장면 녹화 → 편집).

  리허설 (기기 안 무료 TTS, 평소 빌드):
    powershell -ExecutionPolicy Bypass -File scripts\demo\record-demo.ps1
  최종본 (서버 고음질 TTS — 유료라 이때만):
    powershell -ExecutionPolicy Bypass -File scripts\demo\record-demo.ps1 -Final -CaptureAudio

  매개변수
    -Final          -PPREMIUM_TTS=true로 빌드한다 (최종 영상용 고음질 목소리). 없으면 평소처럼 기기 안 엔진
    -CaptureAudio   PC 스피커로 나오는 소리(에뮬레이터 TTS)를 장면마다 같이 녹음해 영상에 넣는다
                    (scripts\demo\loopback-rec.py, pip install --user soundcard 필요. PC의 다른 소리도 들어간다)
    -SkipBuild      이미 만든 APK를 그대로 쓴다
    -SkipInstall    설치 · 데이터 지우기를 건너뛴다 (장면만 다시 찍기. 이미 시연 기록이 있으면 첫 장면은 어색하다)
    -Scenes         찍을 장면만 (기본 전부): firstrun, startrec, search, replay, course, review, convoy, biz, extras, phone, recall, voice
                    (recall은 강릉 경로를 재생해 오늘 기록을 남기므로 늘 맨 마지막에 찍는다 — docs/97 4장)
    -EditOnly       녹화 없이 -Out 폴더의 장면 파일로 편집만 다시
    -Out            결과 폴더 (기본 build\demo\<날짜-시각>, git에 안 올라감)
    -Car / -Phone   에뮬레이터 (기본 emulator-5554 = Pleos 차, emulator-5556 = 폰)

  결과: <Out>\demo.mp4 (1920×1080, 장면마다 아래 자막), <Out>\cover.gif (800px, 약 10초 반복), <Out>\scenes\*.mp4 (원본 녹화)
  준비: 두 에뮬레이터를 켜 둔다 (scripts\emu.ps1). Virtual Console(8088)이 살아 있어야 한다. 오프라인 지도 파일(scripts\map\out)이 있으면 넣는다.
  소리: adb screenrecord에는 소리가 없다. -CaptureAudio를 안 쓰면 영상은 무음이다 (내레이션 · TTS는 편집 때 넣는다).
  자세한 설명: docs/96_시연영상_녹화.md
#>
param(
    [switch]$Final,
    [switch]$CaptureAudio,
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$EditOnly,
    [string[]]$Scenes = @('firstrun', 'startrec', 'search', 'replay', 'course', 'review', 'convoy', 'biz', 'extras', 'phone', 'recall', 'voice'),
    [string]$Out,
    [string]$Car = 'emulator-5554',
    [string]$Phone = 'emulator-5556'
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. (Join-Path $PSScriptRoot 'demo-lib.ps1')
$Scenes = @($Scenes | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })   # -File로 부르면 'a,b'가 한 글자열로 온다

$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $Out) { $Out = Join-Path $Repo ("build\demo\" + (Get-Date -Format 'yyyyMMdd-HHmm')) }
$SceneDir = Join-Path $Out 'scenes'
$PartDir = Join-Path $Out 'parts'
New-Item -ItemType Directory -Force $SceneDir, $PartDir | Out-Null
$CarPkg = 'app.eodiyeotji'
$PhonePkg = 'app.eodiyeotji.phone'
$NaviPkg = 'ai.umos.maps.android.navigation.app'
$Highway = Join-Path $PSScriptRoot 'routes\convoy-highway.json'
$MarksFile = Join-Path $Out 'marks.json'
$Marks = @{}
if (Test-Path $MarksFile) { (Get-Content $MarksFile -Raw -Encoding UTF8 | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $Marks[$_.Name] = $_.Value } }

function Step([string]$m) { Write-Host "`n== $m" -ForegroundColor Cyan }
<# 다른 스크립트 실행 (stderr에 쓰는 진행 글을 오류로 보지 않게 Continue) #>
function Invoke-Script([string]$Path, [string[]]$a) {
    $ErrorActionPreference = 'Continue'
    $env:ANDROID_SERIAL = $Car   # vc.ps1 · replay.ps1 · push-map.ps1 안의 adb가 차 에뮬레이터를 고르게
    & powershell -NoProfile -ExecutionPolicy Bypass -File $Path @a 2>&1 | ForEach-Object { "$_" } | Out-Host
}
function Vc([string[]]$a) { Invoke-Script (Join-Path $Repo 'scripts\vc.ps1') $a }
function Open-CarApp {
    # 내비를 끄면 시스템이 내비를 다시 앞으로 띄우기도 한다 → 앱 머리가 보일 때까지 다시 연다
    for ($i = 0; $i -lt 4; $i++) {
        $null = Invoke-Adb $Car shell am start --user 10 -n "$CarPkg/.MainActivity"
        Start-Sleep 2
        if (Find-Ui $Car '^어디였지$|^모아보기$|^내 기록$|말로 물어보기|이 코스로 안내|후보 목록' 2) { return }
    }
    Write-Host '  앱 화면이 앞에 오지 않았습니다' -ForegroundColor Yellow
}
<# 내비가 경로를 받아도 앞에 안 뜰 때가 있다 → 아래 독의 내비 버튼(가운데 화살표)을 누른다 #>
function Show-Navi { if (-not (Find-Ui $Car '안내 시작|안내 종료|경유지' 1)) { Tap $Car 1155 1370 } }
function Stop-Navi { $null = Invoke-Adb $Car shell am force-stop --user 10 $NaviPkg; Start-Sleep 3 }

# ---------- 장면 녹화 ----------
$script:Rec = $null
$script:AudioProc = $null
function Begin-Scene([string]$Name, [string]$Dev, [string]$Size) {
    Step "장면: $Name"
    $script:Marks[$Name] = [ordered]@{}
    if ($CaptureAudio) {
        $stop = Join-Path $SceneDir "$Name.stop"
        Remove-Item $stop, (Join-Path $SceneDir "$Name.wav") -ErrorAction SilentlyContinue
        $script:AudioProc = Start-Process python -ArgumentList "`"$PSScriptRoot\loopback-rec.py`"", "`"$SceneDir\$Name.wav`"", "`"$stop`"" -PassThru -WindowStyle Hidden
    }
    $script:Rec = Start-Rec $Dev $Name -Size $Size
    if ($CaptureAudio) { $script:Rec.Started = Get-Date }   # 소리와 맞추는 기준
}
function Mark([string]$Key) {
    $t = [Math]::Round(((Get-Date) - $script:Rec.Started).TotalSeconds, 2)
    $script:Marks[$script:Rec.Name][$Key] = $t
    Write-Host "  표시 $Key = ${t}s"
}
function End-Scene {
    Mark 'end'
    $null = Stop-Rec $script:Rec $SceneDir
    if ($CaptureAudio -and $script:AudioProc) {
        New-Item -ItemType File (Join-Path $SceneDir "$($script:Rec.Name).stop") -Force | Out-Null
        $script:AudioProc | Wait-Process -Timeout 10 -ErrorAction SilentlyContinue
    }
    $script:Marks | ConvertTo-Json -Depth 4 | Set-Content $MarksFile -Encoding UTF8
}

# ---------- 1. 빌드 ----------
if (-not $EditOnly -and -not $SkipBuild) {
    Step ("빌드 (" + $(if ($Final) { '최종: PREMIUM_TTS=true' } else { '리허설: 기기 안 TTS' }) + ")")
    $ga = @('assembleCarDebug', 'assembleCarRelease', 'assemblePhoneRelease', '-q')
    if ($Final) { $ga += '-PPREMIUM_TTS=true' }
    Push-Location $Repo
    try { & .\gradlew.bat @ga; if ($LASTEXITCODE -ne 0) { throw "빌드 실패" } } finally { Pop-Location }
}
$apk = @{
    CarDebug   = Join-Path $Repo 'app\build\outputs\apk\car\debug\app-car-debug.apk'
    CarRelease = Join-Path $Repo 'app\build\outputs\apk\car\release\app-car-release.apk'
    Phone      = Join-Path $Repo 'app\build\outputs\apk\phone\release\app-phone-release.apk'
}

# ---------- 2. 새로 설치 (처음 실행 = 커뮤니티 글 · 시연 기록 넣기 버튼이 보이는 상태) ----------
if (-not $EditOnly -and -not $SkipInstall) {
    Step '차: 지우고 새로 설치 (디버그로 지도 넣고 → 릴리즈를 덮어 설치, 같은 디버그 키라 데이터 유지)'
    Stop-Navi
    Invoke-Adb $Car uninstall $CarPkg | Out-Host
    Invoke-Adb $Car install -r -g $apk.CarDebug | Out-Host
    $map = Join-Path $Repo 'scripts\map\out\korea-demo.pmtiles'
    if (Test-Path $map) {
        Invoke-Script (Join-Path $Repo 'scripts\map\push-map.ps1') @('-Serial', $Car, '-User', '10')
    } else { Write-Host '  오프라인 지도 파일이 없어 온라인 지도로 찍습니다 (scripts\map\build-map.ps1)' -ForegroundColor DarkYellow }
    Invoke-Adb $Car install -r -g $apk.CarRelease | Out-Host
    Vc @('-Speed', '0', '-Gear', 'Park')
    $null = Invoke-Adb $Car emu geo fix 127.0335 37.5490   # 집(응봉동) 근처
    Open-CarApp
    $null = Wait-Ui $Car '시연 기록 넣기' 20

    Step '폰: 새로 설치 + 첫 실행 안내 넘기기 (녹화 안 함)'
    Invoke-Adb $Phone install -r -g $apk.Phone | Out-Host
    $null = Invoke-Adb $Phone shell pm clear $PhonePkg
    foreach ($p in 'ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION', 'ACCESS_BACKGROUND_LOCATION', 'POST_NOTIFICATIONS', 'RECORD_AUDIO', 'READ_MEDIA_IMAGES', 'ACCESS_MEDIA_LOCATION') {
        $null = Invoke-Adb $Phone shell pm grant $PhonePkg "android.permission.$p"
    }
    $null = Invoke-Adb $Phone shell am start -n "$PhonePkg/app.eodiyeotji.MainActivity"
    for ($i = 0; $i -lt 6; $i++) {
        Start-Sleep 2
        if (Find-Ui $Phone '시연 기록 넣기' 1) { break }
        Tap-Text $Phone '위치 허용하기|알림 허용하기|^나중에$' 2 -Optional
    }
}

# ---------- 3. 장면 ----------
if (-not $EditOnly) {
    Vc @('-Speed', '0', '-Gear', 'Park')

    if ($Scenes -contains 'firstrun') {
        Open-CarApp
        Begin-Scene 'firstrun' $Car
        Start-Sleep 2.5
        Mark 'seed'; Tap-Text $Car '시연 기록 넣기'
        $null = Wait-Ui $Car '그날로 돌아가기' 25
        Start-Sleep 4
        End-Scene
    }

    # 시동(기어 P→D) → 앱 머리 "기록 중" → 다시 P: 버튼 없이 기록이 시작되는 순간 (사양 장면 5)
    if ($Scenes -contains 'startrec') {
        Open-CarApp
        Tap-Text $Car '^내 기록$' 2 -Optional
        Begin-Scene 'startrec' $Car
        Start-Sleep 2.5
        Mark 'drive'; Vc @('-Gear', 'Drive', '-Speed', '20')
        Start-Sleep 5
        Mark 'park'; Vc @('-Speed', '0', '-Gear', 'Park')
        Start-Sleep 4
        End-Scene
    }

    if ($Scenes -contains 'search') {
        Open-CarApp
        Tap-Text $Car '^지우기$' 2 -Optional
        Begin-Scene 'search' $Car
        Start-Sleep 1
        Tap-Text $Car '장소.*물어보세요'
        Start-Sleep 1.2
        # "작년에 비 오던 날 넷이서 간 바다 어디였지" — 비는 와이퍼 기록, 넷은 좌석 센서 기록으로 거른다 (agent/runner.rs)
        Mark 'type'; Type-CarHangul $Car 'ㅈㅏㄱㄴㅕㄴㅇㅔ ㅂㅣ ㅇㅗㄷㅓㄴ ㄴㅏㄹ ㄴㅔㅅㅇㅣㅅㅓ ㄱㅏㄴ ㅂㅏㄷㅏ ㅇㅓㄷㅣㅇㅕ⇧ㅅㅈㅣ'
        Start-Sleep 2.5
        Mark 'ask'; Tap-Text $Car 'AI에게 물어보기'
        $null = Wait-Ui $Car '이 코스로 안내' 30
        Mark 'answer'
        Start-Sleep 6
        End-Scene
    }

    if ($Scenes -contains 'replay') {
        Begin-Scene 'replay' $Car
        Start-Sleep 1
        Mark 'replay'; Tap-Text $Car '그날로 돌아가기'
        Start-Sleep 15
        Tap-Text $Car '^닫기$' 3 -Region '850,90,1000,230' -Optional
        Start-Sleep 1.5
        End-Scene
    }

    if ($Scenes -contains 'course') {
        Begin-Scene 'course' $Car
        Start-Sleep 1
        Mark 'guide'; Tap-Text $Car '이 코스로 안내'
        Start-Sleep 5     # 내비가 경로를 받는 동안 앱이 "내비 안내를 시작했어요"
        Mark 'navi'; Show-Navi
        Start-Sleep 7
        End-Scene
        # 정리: 앱 코스 안내 끄기 · 내비 끄기 · 에이전트 창 닫기
        Open-CarApp
        Tap-Text $Car '^닫기$' 3 -Region '2300,90,2560,230' -Optional
        Start-Sleep 1
        Tap-Text $Car '안내 종료' 3 -Region '1900,1100,2560,1300' -Optional
        Start-Sleep 4   # 안내 종료는 내비를 잠깐 앞으로 띄운다
        Open-CarApp
    }

    # 커뮤니티: 코스 올리기(내 주행 기록에서만 · 집·회사 근처 자동 가림 — 올리지 않고 닫는다) → 여행 리뷰 강릉~양양 후기 → 이 코스로 안내 (사양 장면 10)
    if ($Scenes -contains 'review') {
        Open-CarApp
        Tap-Text $Car '^커뮤니티$'
        Start-Sleep 1.5
        Tap-Text $Car '^여행 리뷰$'
        Start-Sleep 2
        Begin-Scene 'review' $Car
        Start-Sleep 1.5
        Mark 'upload'; Tap-Text $Car '코스 올리기'
        Start-Sleep 2.5
        Tap $Car 1750 410            # "어떤 기록을 올릴까요?" 첫 기록
        Start-Sleep 4                # "집·회사 근처는 자동으로 가려요"
        Tap $Car 2283 363            # 닫기 (올리지 않는다)
        Start-Sleep 1.5
        Mark 'feed'
        Start-Sleep 2
        Tap-Text $Car '강릉에서 양양까지'
        Start-Sleep 4
        Mark 'guide'; Tap-Text $Car '^이 코스로 안내$'
        Start-Sleep 5
        Show-Navi
        Start-Sleep 4
        End-Scene
        # 정리: 코스 안내 끄기 · 목록으로
        Open-CarApp
        Tap-Text $Car '안내 종료' 3 -Region '1900,1100,2560,1300' -Optional
        Start-Sleep 4
        Open-CarApp
        Tap-Text $Car '여행 리뷰로' 2 -Optional
        Tap-Text $Car '^내 기록$' 2 -Optional
    }

    if ($Scenes -contains 'convoy') {
        Open-CarApp
        Start-Sleep 3
        Open-CarApp
        Tap-Text $Car '^지우기$' 2 -Optional
        $legStart = (Get-Content (Join-Path $PSScriptRoot 'routes\legs\yeongdong-hwy__hoengseong-rest.csv') -TotalCount 2)[1].Split(',')
        $null = Invoke-Adb $Car emu geo fix $legStart[2] $legStart[1]   # 영동고속도로 강릉방향 위에 세워 둔다
        Start-Sleep 2
        Begin-Scene 'convoy' $Car
        Start-Sleep 1
        Tap-Text $Car '장소.*물어보세요'
        Start-Sleep 1
        Type-CarHangul $Car 'ㄱㅕㅇㅍㅗ'
        Start-Sleep 1.5
        Tap-Text $Car '^경포해변$' -Region '1000,300,1750,500'
        Start-Sleep 2.5
        Tap-Text $Car '^같이 가기$' -Region '1440,600,2560,900'
        Start-Sleep 1.5
        Mark 'create'; Tap-Text $Car '새로 만들기'
        Start-Sleep 13          # 민수(3초) · 지현(6초) 참여, 민수 배터리 부족
        Mark 'drive'
        $env:ANDROID_SERIAL = $Car
        $replay = Start-Process powershell -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$Repo\scripts\replay.ps1`"", '-Scenario', "`"$Highway`"", '-LeadInSec', '0' -PassThru -WindowStyle Hidden
        try {
            $w = Find-Ui $Car '휴게소에서 기다리기' 150
            if ($w) {
                Mark 'wait'
                Start-Sleep 5        # 음성 "지현 차와 4km 멀어졌어요 … 기다릴까요?"
                $w2 = Find-Ui $Car '휴게소에서 기다리기' 3
                if ($w2) { $w = $w2 }
                Tap $Car $w[0] $w[1]; Mark 'tapped'
                Start-Sleep 2.5
                Show-Navi
                Start-Sleep 7        # 내비에 경유지(휴게소)로 안내
            } else { Write-Host '  휴게소 기다리기 제안이 오지 않았습니다' -ForegroundColor Yellow }
            End-Scene
        } finally {
            Get-CimInstance Win32_Process -Filter "ParentProcessId=$($replay.Id)" -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
            Stop-Process -Id $replay.Id -Force -ErrorAction SilentlyContinue
            Vc @('-Speed', '0', '-Gear', 'Park')
        }
        # 정리: 모임 나가기
        Open-CarApp
        Start-Sleep 2
        if (-not (Find-Ui $Car '^나가기$' 2)) { Tap-Text $Car '같이 가기|위치 공유' 3 -Region '2000,90,2560,200' -Optional; Start-Sleep 1.5 }
        Tap-Text $Car '^나가기$' 3 -Optional
        Start-Sleep 1
        Tap-Text $Car '^닫기$' 2 -Optional
    }

    if ($Scenes -contains 'biz') {
        Open-CarApp
        Begin-Scene 'biz' $Car
        Start-Sleep 1
        Tap-Text $Car '^모아보기$'
        Start-Sleep 2
        Mark 'biz'; Tap-Text $Car '^출장 관리$'
        Start-Sleep 4
        Tap-Text $Car ' 출장$' 3 -Optional
        Start-Sleep 4
        End-Scene
        $null = Invoke-Adb $Car shell input keyevent BACK
        Start-Sleep 1
        Tap-Text $Car '^내 기록$' 2 -Optional
    }

    # 그 밖에도: 첫 화면 "늘 가던 날" 카드 → 이번 달 요약 → 기억 지도 (사양 장면 14b, 폰은 phone 장면에서)
    if ($Scenes -contains 'extras') {
        Open-CarApp
        Tap-Text $Car '^내 기록$' 2 -Optional
        Begin-Scene 'extras' $Car
        Start-Sleep 3.5
        Mark 'month'; Tap-Text $Car '^모아보기$'
        Start-Sleep 1.2
        Tap-Text $Car '^이번 달 요약$'
        Start-Sleep 3.5
        $null = Invoke-Adb $Car shell input keyevent BACK
        Start-Sleep 1.2
        Mark 'map'; Tap-Text $Car '^기억 지도$'
        Start-Sleep 7
        End-Scene
        $null = Invoke-Adb $Car shell input keyevent BACK
        Start-Sleep 1
        Tap-Text $Car '^내 기록$' 2 -Optional
    }

    if ($Scenes -contains 'phone') {
        $null = Invoke-Adb $Phone shell am start -n "$PhonePkg/app.eodiyeotji.MainActivity"
        Start-Sleep 2
        Begin-Scene 'phone' $Phone -Size '720x1600'
        Start-Sleep 1.5
        Tap-Text $Phone '시연 기록 넣기' 3 -Optional
        $null = Wait-Ui $Phone '그날로 돌아가기|출퇴근' 20
        Start-Sleep 3
        Swipe $Phone 540 1700 540 900 500
        Start-Sleep 2.5
        Mark 'community'; Tap-Text $Phone '^커뮤니티$'
        Start-Sleep 3
        Swipe $Phone 540 1800 540 800 600
        Start-Sleep 3
        End-Scene
    }

    # 지나가면 회상: 강릉 경로 5구간(경포해변 → 초당)을 D로 1.5배속 재생 → 초당 300m 안에서 배너 + 음성 (사양 장면 9b)
    # 오늘 기록을 남기고 회상 제한(하루 3번 · 15분 · 같은 곳 7일)을 쓰므로 늘 맨 마지막에 찍는다.
    if ($Scenes -contains 'recall') {
        Open-CarApp
        Tap-Text $Car '^내 기록$' 2 -Optional
        $env:ANDROID_SERIAL = $Car
        $replay = Start-Process powershell -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$Repo\scripts\replay.ps1`"", '-Scenario', 'gangneung-trip', '-StartSegment', '5', '-Speed', '1.5', '-LeadInSec', '3' -PassThru -WindowStyle Hidden
        try {
            Begin-Scene 'recall' $Car
            Mark 'drive'
            $r = Find-Ui $Car '근처에 있어요' 170
            if ($r) {
                Mark 'recall'
                Start-Sleep 7        # 음성 "1년 전 가을에 들렀던 ○○가 근처에 있어요"
            } else { Write-Host '  회상 배너가 뜨지 않았습니다 (사양 4장 9b 대체안)' -ForegroundColor Yellow }
            End-Scene
        } finally {
            Get-CimInstance Win32_Process -Filter "ParentProcessId=$($replay.Id)" -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
            Stop-Process -Id $replay.Id -Force -ErrorAction SilentlyContinue
            Vc @('-Speed', '0', '-Gear', 'Park')
        }
    }

    # 말로 묻기 (사양 v4 장면 4 · 6): 마이크 → 질문 → AI 답 · 읽어 주기 → "'응'이라고 하면 안내를 시작해요".
    # 에뮬레이터엔 마이크가 없어 디버그판의 음성 입력 브로드캐스트로 "알아들은 말"을 넣는다 (그 뒤는 실제 음성과 같은 길).
    # 디버그판으로 덮어 깔아야 해서(같은 키라 기록 유지) 맨 끝에 찍는다.
    if ($Scenes -contains 'voice') {
        Invoke-Adb $Car install -r -g $apk.CarDebug | Out-Host
        Open-CarApp
        Tap-Text $Car '^내 기록$' 2 -Optional
        Begin-Scene 'voice' $Car
        Start-Sleep 2
        Mark 'mic'; Tap $Car 2313 142            # 검색칸 오른쪽 마이크 (말로 찾기)
        Start-Sleep 2.5
        Mark 'say'; $null = Invoke-Adb $Car shell am broadcast --user 10 -a app.eodiyeotji.DEBUG_VOICE -p $CarPkg --es text "'작년에 비 오던 날 넷이서 간 바다 어디였지'"
        $null = Wait-Ui $Car '이 코스로 안내' 30
        Mark 'answer'
        Start-Sleep 9                             # 답을 다 읽고 "'응'이라고 하면 안내를 시작해요" (화면 글자로는 잡히지 않는다)
        Mark 'confirm'; Start-Sleep 2
        Mark 'yes'; $null = Invoke-Adb $Car shell am broadcast --user 10 -a app.eodiyeotji.DEBUG_VOICE -p $CarPkg --es text "'응'"
        Start-Sleep 8
        End-Scene
        Stop-Navi
    }
    Vc @('-Speed', '0', '-Gear', 'Park')
    Open-CarApp
}

# ---------- 4. 편집 (ffmpeg) ----------
Step '편집'
$Font = 'malgunbd.ttf'   # 조각 폴더에 복사해 둔 글꼴 (필터 안에서 Windows 경로 이스케이프를 피한다)
Copy-Item "$env:WINDIR\Fonts\malgunbd.ttf" (Join-Path $PartDir 'malgunbd.ttf') -Force
$Tag = if ($Final) { '실제 Pleos 에뮬레이터 화면' } else { '실제 Pleos 에뮬레이터 화면 · 리허설(기기 안 TTS)' }
function Invoke-Ff([string[]]$a) {
    $ErrorActionPreference = 'Continue'
    [IO.Directory]::SetCurrentDirectory((Get-Location).ProviderPath)   # ffmpeg는 PowerShell 위치가 아니라 프로세스 현재 폴더를 본다
    & ffmpeg -hide_banner -loglevel error -y @a 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg 실패: $($a -join ' ')" }
}
function Write-Text([string]$Name, [string]$Text) {
    $p = Join-Path $PartDir $Name
    [IO.File]::WriteAllText($p, $Text, (New-Object Text.UTF8Encoding $false))
    return $Name
}
$script:PartList = New-Object System.Collections.Generic.List[string]
$script:PartNo = 0
<# 장면 녹화의 [From, To] 구간을 Speed배로, 아래 자막을 얹어 1920×1080 · 30fps 조각으로 만든다 #>
function Add-Part([string]$Scene, [double]$From, [double]$To, [double]$Speed = 1, [string]$Caption = '') {
    $src = Join-Path $SceneDir "$Scene.mp4"
    if (-not (Test-Path $src)) { Write-Host "  (녹화 없음) $Scene" -ForegroundColor DarkYellow; return }
    if ($To -le $From) { return }
    $script:PartNo++
    $name = 'p{0:d2}_{1}.mp4' -f $script:PartNo, $Scene
    $cap = Write-Text ('cap{0:d2}.txt' -f $script:PartNo) $Caption
    $tag = Write-Text 'tag.txt' $Tag
    $dur = ($To - $From) / $Speed
    $isPhone = $Scene -eq 'phone'
    $fit = if ($isPhone) {
        "scale=-2:960:flags=lanczos,pad=1920:1080:(ow-iw)/2:20:color=0xE9EEF5"
    } else { "scale=1920:1080:flags=lanczos" }
    $v = "[0:v]trim=start=${From}:end=${To},setpts=(PTS-STARTPTS)/$Speed,fps=30,$fit,setsar=1," +
    "drawbox=x=0:y=ih-92:w=iw:h=92:color=0x0B1220@0.88:t=fill," +
    "drawtext=fontfile='$Font':textfile=${cap}:fontcolor=white:fontsize=38:x=(w-tw)/2:y=h-66," +
    "drawtext=fontfile='$Font':textfile=${tag}:fontcolor=white@0.85:fontsize=22:box=1:boxcolor=0x0B1220@0.55:boxborderw=10:x=w-tw-24:y=20[v]"
    $wav = Join-Path $SceneDir "$Scene.wav"
    $inputs = @('-i', $src)
    if ($CaptureAudio -and (Test-Path $wav)) {
        $inputs += @('-i', $wav)
        $tempo = @(); $s = $Speed
        while ($s -gt 2.0) { $tempo += 'atempo=2.0'; $s /= 2.0 }
        $tempo += ('atempo={0}' -f [Math]::Round($s, 4))
        $a = "[1:a]atrim=start=${From}:end=${To},asetpts=PTS-STARTPTS," + ($tempo -join ',') + ",apad,atrim=end=$dur[a]"
    } else {
        $inputs += @('-f', 'lavfi', '-t', "$dur", '-i', 'anullsrc=r=48000:cl=stereo')
        $a = "[1:a]atrim=end=$dur[a]"
    }
    Push-Location $PartDir
    try {
        Invoke-Ff ($inputs + @('-filter_complex', "$v;$a", '-map', '[v]', '-map', '[a]', '-t', "$dur",
                '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p', '-r', '30',
                '-c:a', 'aac', '-b:a', '160k', '-ar', '48000', '-ac', '2', $name))
    } finally { Pop-Location }
    $script:PartList.Add($name)
    Write-Host ("  조각 {0}: {1} {2:n1}~{3:n1}s x{4} → {5:n1}s" -f $script:PartNo, $Scene, $From, $To, $Speed, $dur)
}
function Add-Card([string]$Title, [string]$Sub, [double]$Dur = 2.5) {
    $script:PartNo++
    $name = 'p{0:d2}_card.mp4' -f $script:PartNo
    $t = Write-Text ('card{0:d2}a.txt' -f $script:PartNo) $Title
    $s = Write-Text ('card{0:d2}b.txt' -f $script:PartNo) $Sub
    $v = "color=c=0x0B1220:s=1920x1080:r=30:d=$Dur,drawtext=fontfile='$Font':textfile=${t}:fontcolor=white:fontsize=96:x=(w-tw)/2:y=(h/2)-110," +
    "drawtext=fontfile='$Font':textfile=${s}:fontcolor=0xAFC4E6:fontsize=40:x=(w-tw)/2:y=(h/2)+30,fade=t=in:d=0.4,fade=t=out:st=$($Dur-0.4):d=0.4"
    Push-Location $PartDir
    try {
        Invoke-Ff @('-f', 'lavfi', '-i', $v, '-f', 'lavfi', '-t', "$Dur", '-i', 'anullsrc=r=48000:cl=stereo', '-t', "$Dur",
            '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p', '-r', '30', '-c:a', 'aac', '-b:a', '160k', '-ar', '48000', '-ac', '2', $name)
    } finally { Pop-Location }
    $script:PartList.Add($name)
}
function M([string]$Scene, [string]$Key, [double]$Default) {
    $m = $Marks[$Scene]
    if ($m -and $null -ne $m.$Key) { return [double]$m.$Key }
    return $Default
}

Add-Card '어디였지' '차가 대신 기억합니다 · 기록 → 기억 → 함께'
Add-Part 'firstrun' 0.6 (M 'firstrun' 'end' 12) 1 '처음 켜면: 시동만 걸어도 자동 기록 · 시연용 1년치 기록 넣기'
Add-Part 'startrec' 0.6 (M 'startrec' 'end' 12) 1 '시동(기어 P→D)만 걸면 기록 시작 · 버튼 없음'
Add-Part 'search' 0.6 ((M 'search' 'ask' 12) + 0.5) 2 '상황으로 묻기 "작년에 비 오던 날 넷이서 간 바다 어디였지" (타자 2배속)'
Add-Part 'search' ((M 'search' 'ask' 12) + 0.5) (M 'search' 'end' 30) 1 'AI가 기록에서 찾아 답하고, 그날 코스로 바로 안내'
Add-Part 'voice' ((M 'voice' 'mic' 2) - 0.5) ((M 'voice' 'yes' 20) + 7) 1 '말로 묻기: 마이크 → 질문 → 답을 읽어 주고 "응"이면 안내'
Add-Part 'replay' 0.6 (M 'replay' 'end' 18) 1 '그날로 돌아가기: 1년 전 오늘 달린 길을 3D로 다시'
Add-Part 'course' 0.6 (M 'course' 'end' 10) 1 '이 코스로 안내: 들른 곳 5곳을 내비 경유지 2개씩 구간으로 나눠'
Add-Part 'review' 0.6 (M 'review' 'end' 30) 1 '커뮤니티: 내 주행 기록에서만 코스 올리기 · 집·회사 근처 자동 가림 · 후기 코스로 바로 안내'
$cDrive = M 'convoy' 'drive' 30
$cWait = M 'convoy' 'wait' ($cDrive + 60)
Add-Part 'convoy' 0.6 ($cDrive + 4) 1 '같이 가기: 6자리 코드로 모임 · 함께 가는 차 2대 · 민수 차 배터리 부족'
if ($cWait - 2 -gt $cDrive + 4) {
    $len = ($cWait - 2) - ($cDrive + 4)
    Add-Part 'convoy' ($cDrive + 4) ($cWait - 2) ([Math]::Max(1, [Math]::Round($len / 8, 2))) '주행 중엔 큰 카드 한 장 + 음성만 (빨리 감기)'
}
Add-Part 'convoy' ($cWait - 2) (M 'convoy' 'end' ($cWait + 14)) 1 '지현 차가 3km 넘게 뒤처지면 → 앞 휴게소에서 기다릴지 묻고, 누르면 경유지로'
Add-Part 'biz' 0.6 (M 'biz' 'end' 12) 1 '출장 관리: 거래처 · 평일 주행을 출장으로 묶고 경비까지 어림'
Add-Part 'extras' 0.6 (M 'extras' 'end' 16) 1 '늘 가던 날 미리 알림 · 이번 달 요약 · 기억 지도'
Add-Part 'phone' 0.6 (M 'phone' 'end' 20) 1 '폰 앱: 같은 기록과 커뮤니티를 폰에서도'
$rc = M 'recall' 'recall' 0
if ($rc -gt 0) { Add-Part 'recall' ([Math]::Max(0.6, $rc - 6)) (M 'recall' 'end' ($rc + 7)) 1 '주행 중 예전에 들른 곳 근처를 지나면 음성 + 한 줄로 회상' }
Add-Card '달린 길은 잊혀도, 차는 기억합니다' '어디였지 · Pleos Connect' 3

$listFile = Join-Path $PartDir 'list.txt'
[IO.File]::WriteAllLines($listFile, ($script:PartList | ForEach-Object { "file '$_'" }), (New-Object Text.UTF8Encoding $false))
Push-Location $PartDir
try { Invoke-Ff @('-f', 'concat', '-safe', '0', '-i', 'list.txt', '-c', 'copy', '-movflags', '+faststart', (Join-Path $Out 'demo.mp4')) } finally { Pop-Location }

# ---------- 5. 표지 GIF (약 10초 반복: AI 답 → 3D 다시 달리기 → 같이 가기 휴게소 제안) ----------
Step '표지 GIF'
$g = @()
$gi = 0
foreach ($c in @(
        @{ S = 'search'; F = (M 'search' 'answer' 15) - 0.8; T = (M 'search' 'answer' 15) + 2.7; X = 1.0 },
        @{ S = 'replay'; F = (M 'replay' 'replay' 1) + 6; T = (M 'replay' 'replay' 1) + 14; X = 2.5 },
        @{ S = 'convoy'; F = $cWait - 0.5; T = $cWait + 3.5; X = 1.0 })) {
    $src = Join-Path $SceneDir "$($c.S).mp4"
    if (-not (Test-Path $src)) { continue }
    $gi++
    $part = Join-Path $PartDir "gif$gi.mp4"
    Invoke-Ff @('-i', $src, '-vf', "trim=start=$($c.F):end=$($c.T),setpts=(PTS-STARTPTS)/$($c.X),fps=10,scale=800:-2:flags=lanczos", '-an', '-c:v', 'libx264', '-crf', '16', '-pix_fmt', 'yuv420p', $part)
    $g += $part
}
if ($g.Count) {
    $glist = Join-Path $PartDir 'gif-list.txt'
    [IO.File]::WriteAllLines($glist, ($g | ForEach-Object { "file '$_'" }), (New-Object Text.UTF8Encoding $false))
    $gifMp4 = Join-Path $PartDir 'gif-all.mp4'
    Invoke-Ff @('-f', 'concat', '-safe', '0', '-i', $glist, '-c', 'copy', $gifMp4)
    $pal = Join-Path $PartDir 'palette.png'
    Invoke-Ff @('-i', $gifMp4, '-vf', 'palettegen=max_colors=160:stats_mode=diff', $pal)
    Invoke-Ff @('-i', $gifMp4, '-i', $pal, '-lavfi', 'paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle', '-loop', '0', (Join-Path $Out 'cover.gif'))
}

Step '끝'
foreach ($f in 'demo.mp4', 'cover.gif') {
    $p = Join-Path $Out $f
    if (Test-Path $p) {
        $d = & ffprobe -v error -show_entries format=duration -of csv=p=0 $p
        Write-Host ("  {0}  {1:n1}MB  {2:n1}s" -f $p, ((Get-Item $p).Length / 1MB), [double]$d)
    }
}
