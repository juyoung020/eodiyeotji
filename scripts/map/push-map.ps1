<#
  만든 지도 파일(korea-demo.pmtiles)을 실행 중인 에뮬레이터의 앱 폴더로 보내기

  실행:  powershell -ExecutionPolicy Bypass -File scripts\map\push-map.ps1
         powershell -ExecutionPolicy Bypass -File scripts\map\push-map.ps1 -Serial emulator-5554 -User 10
         powershell -ExecutionPolicy Bypass -File scripts\map\push-map.ps1 -Remove     # 보낸 지도 파일 지우기

  앱이 지도 파일을 찾는 순서 (앱과의 약속)
    1. getExternalFilesDir(null)/map/korea-demo.pmtiles   = /storage/emulated/<사용자>/Android/data/app.eodiyeotji/files/map/
    2. filesDir/map/korea-demo.pmtiles                    = /data/user/<사용자>/app.eodiyeotji/files/map/

  보내는 방법 (사용자 번호에 따라 자동으로 고른다)
    - 사용자 0: adb push 로 1번(외부 앱 폴더)에 바로 넣는다.
    - 그 밖의 사용자 (Pleos 에뮬레이터는 화면 사용자가 10): adb shell 은 사용자 0 저장소만 보이므로 1번에 쓸 수 없다.
      /data/local/tmp 에 올린 뒤 `run-as app.eodiyeotji --user 10` 으로 2번(앱 내부 폴더)에 복사한다.
      run-as 는 디버그 빌드(debuggable)에서만 된다 → 에뮬레이터에 debug APK 가 깔려 있어야 한다 (./gradlew installDebug).
      Pleos 에뮬레이터 이미지는 user 빌드라 adb root 가 안 되므로 다른 길은 없다.
  - 앱을 다시 깔면(uninstall) 2번 폴더가 지워진다 → 이 스크립트를 다시 돌린다. 덮어쓰기 설치(install -r)는 남는다.
  - 앱을 다시 설치할 필요는 없다. 지도 화면을 다시 열면 새 파일을 읽는다.
#>
param(
    [string]$Serial = '',
    [int]$User = -1,                    # 비우면 지금 화면 사용자(am get-current-user)
    [string]$Package = 'app.eodiyeotji',
    [string]$File = '',                 # 비우면 scripts\map\out\korea-demo.pmtiles
    [switch]$Remove,
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$adb = "$SdkRoot\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb가 없습니다: $adb" }
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $File) { $File = Join-Path $here 'out\korea-demo.pmtiles' }
$name = 'korea-demo.pmtiles'

if (-not $Serial) {
    $Serial = (& $adb devices | Select-String '^(emulator-\d+)\s+device' | Select-Object -First 1).Matches.Groups[1].Value
    if (-not $Serial) { throw '실행 중인 에뮬레이터가 없습니다 (scripts\emu.ps1).' }
}
function Sh([string]$cmd) { $o = & $adb -s $Serial shell $cmd 2>&1; return ($o | Out-String).Trim() }

if ($User -lt 0) { $User = [int](Sh 'am get-current-user') }
$installed = Sh "pm list packages --user $User $Package"
if ($installed -notmatch "package:$([regex]::Escape($Package))\b") { throw "$Package 가 사용자 $User 에 설치돼 있지 않습니다." }
$extDir = "/storage/emulated/$User/Android/data/$Package/files/map"
Write-Host "기기 $Serial · 사용자 $User · 앱 $Package"

if ($User -eq 0) {
    # ---- 사용자 0: 외부 앱 폴더에 바로 -------------------------------------------
    if ($Remove) { Sh "rm -f $extDir/$name" | Out-Null; Write-Host "지웠습니다: $extDir/$name"; return }
    if (-not (Test-Path $File)) { throw "지도 파일이 없습니다: $File (먼저 scripts\map\build-map.ps1)" }
    Sh "mkdir -p $extDir" | Out-Null
    & $adb -s $Serial push $File "$extDir/$name.part"
    if ($LASTEXITCODE -ne 0) { throw 'adb push 실패' }
    Sh "mv -f $extDir/$name.part $extDir/$name" | Out-Null
    $remoteHash = (Sh "sha256sum $extDir/$name") -split '\s+' | Select-Object -First 1
    $dest = "$extDir/$name"
} else {
    # ---- 그 밖의 사용자: run-as 로 앱 내부 폴더에 -------------------------------
    $probe = Sh "run-as $Package --user $User id"
    if ($probe -match 'not debuggable|unknown|error') {
        throw @"
run-as 를 쓸 수 없습니다: $probe
사용자 $User 의 앱 폴더에는 디버그 빌드(debuggable)일 때만 넣을 수 있습니다.
에뮬레이터에 debug APK 를 설치한 뒤(예: ./gradlew installDebug) 다시 실행하세요.
"@
    }
    if ($Remove) { Sh "run-as $Package --user $User rm -f files/map/$name" | Out-Null; Write-Host "지웠습니다: files/map/$name (사용자 $User)"; return }
    if (-not (Test-Path $File)) { throw "지도 파일이 없습니다: $File (먼저 scripts\map\build-map.ps1)" }
    $tmp = "/data/local/tmp/$name"
    & $adb -s $Serial push $File $tmp
    if ($LASTEXITCODE -ne 0) { throw 'adb push 실패' }
    Sh "chmod 644 $tmp" | Out-Null
    # .part 로 복사한 뒤 이름을 바꿔, 앱이 반쯤 쓰인 파일을 열지 않게 한다
    $r = Sh "run-as $Package --user $User sh -c 'mkdir -p files/map && chmod 700 files/map && cp $tmp files/map/$name.part && mv -f files/map/$name.part files/map/$name && echo OK'"
    Sh "rm -f $tmp" | Out-Null
    if ($r -notmatch 'OK') { throw "앱 폴더로 복사 실패: $r" }
    $remoteHash = (Sh "run-as $Package --user $User sha256sum files/map/$name") -split '\s+' | Select-Object -First 1
    $dest = "/data/user/$User/$Package/files/map/$name  (filesDir/map)"
}

$localHash = (Get-FileHash -Algorithm SHA256 $File).Hash.ToLower()
if ($remoteHash -ne $localHash) { throw "보낸 파일이 원본과 다릅니다 (기기 $remoteHash / PC $localHash)" }
Write-Host ("완료: {0}  {1:N1} MB, SHA-256 일치" -f $dest, ((Get-Item $File).Length / 1MB))
Write-Host '앱에서 지도 화면을 다시 열면 이 파일을 읽습니다.'
