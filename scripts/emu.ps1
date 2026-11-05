<#
  Pleos Connect 에뮬레이터 실행 + 부팅 대기 + Virtual Console 포트 연결

  실행:  powershell -ExecutionPolicy Bypass -File scripts\emu.ps1
         powershell -ExecutionPolicy Bypass -File scripts\emu.ps1 -Avd 다른AVD이름

  - Cold boot (-no-snapshot): Quick boot면 VHAL(차량 값)이 동작하지 않는다 (docs/50 7-2).
  - -feature -QuickbootFileBacked: 에뮬레이터 RAM 크기만큼(4GB+) 디스크에 파일을 만들지 않게 한다.
  - 부팅이 끝나면 adb forward tcp:8088 → 브라우저 http://127.0.0.1:8088/ 이 Virtual Console.
#>
param(
    [string]$Avd = '',
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$emulator = "$SdkRoot\emulator\emulator.exe"
$adb = "$SdkRoot\platform-tools\adb.exe"

if (-not $Avd) {
    $Avd = & $emulator -list-avds | Where-Object { $_ -match 'Pleos' } | Select-Object -First 1
    if (-not $Avd) { throw "Pleos AVD가 없습니다. docs/50_개발환경_세팅.md 7-2절대로 만드세요." }
}

if (& $adb devices | Select-String 'emulator-\d+\s+device') {
    Write-Host "이미 실행 중인 에뮬레이터가 있습니다."
} else {
    Write-Host "에뮬레이터 시작: $Avd"
    Start-Process -FilePath $emulator -ArgumentList '-avd', $Avd, '-no-snapshot', '-feature', '-QuickbootFileBacked'
}

Write-Host "부팅 대기 중..."
& $adb wait-for-device
for ($i = 0; $i -lt 120; $i++) {
    if ((& $adb shell getprop sys.boot_completed 2>$null) -match '1') { break }
    Start-Sleep -Seconds 2
}
& $adb forward tcp:8088 tcp:8088 | Out-Null
Write-Host "부팅 완료. Virtual Console: http://127.0.0.1:8088/  (차량 조작 스크립트: scripts\vc.ps1)"
