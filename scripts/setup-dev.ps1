<#
  어디였지 개발환경 한 번에 설치 (Windows 10/11)
  docs/50_개발환경_세팅.md 와 같은 구성을 설치/확인한다. 이미 있는 것은 건너뛴다.

  실행:  powershell -ExecutionPolicy Bypass -File scripts\setup-dev.ps1
  확인만: powershell -ExecutionPolicy Bypass -File scripts\setup-dev.ps1 -CheckOnly

  자동으로 하지 않는 것 (마지막에 안내 출력):
    - Pleos Connect Emulator 시스템 이미지 설치 + AVD 생성 (Android Studio GUI)
#>
param(
    [switch]$CheckOnly,
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

# ---- 고정 버전 (docs/50 과 같게 유지) ----
$CmdlineToolsZip = 'commandlinetools-win-16111833_latest.zip'
$NdkVersion      = '27.3.13750724'
$SdkPackages     = [ordered]@{        # sdkmanager 패키지 경로 -> SDK 안의 설치 폴더
    'platform-tools'           = 'platform-tools'
    'emulator'                 = 'emulator'
    'platforms/android-35'     = 'platforms\android-35'
    'build-tools/35.0.0'       = 'build-tools\35.0.0'
    "ndk/$NdkVersion"          = "ndk\$NdkVersion"
    'cmake/3.22.1'             = 'cmake\3.22.1'
}
$RustTargets = @('aarch64-linux-android', 'x86_64-linux-android', 'armv7-linux-androideabi')
$PleosImageXml = 'https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml'

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Skip($msg) { Write-Host "    [skip] $msg" -ForegroundColor DarkGray }
function Info($msg) { Write-Host "    $msg" }
function Warn($msg) { Write-Host "    [!] $msg" -ForegroundColor Yellow }
function Has($cmd)  { [bool](Get-Command $cmd -ErrorAction SilentlyContinue) }
function Add-SessionPath($dir) { if ($env:Path -notlike "*$dir*") { $env:Path = "$dir;$env:Path" } }
function Add-UserPath($dir) {
    $p = [Environment]::GetEnvironmentVariable('Path', 'User')
    if (-not $p) { $p = '' }
    if ($p.Split(';') -notcontains $dir) {
        [Environment]::SetEnvironmentVariable('Path', ($p.TrimEnd(';') + ';' + $dir).TrimStart(';'), 'User')
        Info "user PATH += $dir"
    } else { Skip "PATH already has $dir" }
    Add-SessionPath $dir
}
function Set-UserEnv($name, $value) {
    if ([Environment]::GetEnvironmentVariable($name, 'User') -eq $value) { Skip "$name already set"; }
    else { [Environment]::SetEnvironmentVariable($name, $value, 'User'); Info "$name = $value" }
    Set-Item "env:$name" $value
}
function Install-Winget($id, $extra) {
    if (-not (Has winget)) { Warn "winget 이 없어 $id 를 설치할 수 없습니다. Microsoft Store에서 '앱 설치 관리자'를 설치하세요."; return }
    Info "winget install $id (관리자 권한 창(UAC)이 뜨면 '예'를 누르세요)"
    $wargs = @('install', '--id', $id, '-e', '--accept-package-agreements', '--accept-source-agreements', '--silent') + $extra
    & winget @wargs
}

# ---------------------------------------------------------------- 0. 디스크
Step '디스크 여유 공간'
$freeGB = [math]::Round((Get-PSDrive ($SdkRoot.Substring(0,1))).Free / 1GB, 1)
Info "$($SdkRoot.Substring(0,2)) 여유 공간: ${freeGB} GB"
if ($freeGB -lt 30) { Warn '권장 30GB 이상 (SDK/NDK/Android Studio 약 10GB + Pleos 에뮬레이터 이미지/AVD 15GB 이상)' }

# ---------------------------------------------------------------- 1. JDK
Step 'JDK (17 이상)'
function Find-Jdk {
    $cands = @()
    if ($env:JAVA_HOME) { $cands += $env:JAVA_HOME.TrimEnd('\') }
    $cands += Get-ChildItem 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft' -Directory -ErrorAction SilentlyContinue |
              Where-Object { $_.Name -match '^jdk-?(1[7-9]|2\d)' } | Sort-Object Name -Descending | ForEach-Object FullName
    foreach ($c in $cands) { if (Test-Path "$c\bin\java.exe") { return $c } }
    return $null
}
$jdk = Find-Jdk
if ($jdk) { Skip "JDK found: $jdk" }
elseif ($CheckOnly) { Warn 'JDK 17+ 없음' }
else { Install-Winget 'EclipseAdoptium.Temurin.17.JDK' @(); $jdk = Find-Jdk }
if ($jdk) {
    if (-not [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine') -and -not [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')) {
        if (-not $CheckOnly) { Set-UserEnv 'JAVA_HOME' $jdk }
    } else { Skip 'JAVA_HOME already set (그대로 둠)' }
    $env:JAVA_HOME = $jdk
    Add-SessionPath "$jdk\bin"
}

# ---------------------------------------------------------------- 2. Android SDK
Step "Android SDK ($SdkRoot)"
$sdkmanager = "$SdkRoot\cmdline-tools\latest\bin\sdkmanager.bat"
if (Test-Path $sdkmanager) { Skip 'cmdline-tools/latest present' }
elseif ($CheckOnly) { Warn 'cmdline-tools 없음' }
else {
    $tmp = Join-Path $env:TEMP ("clt-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Force $tmp | Out-Null
    Info "download $CmdlineToolsZip"
    curl.exe -sSL -o "$tmp\clt.zip" "https://dl.google.com/android/repository/$CmdlineToolsZip"
    Expand-Archive "$tmp\clt.zip" "$tmp\x" -Force
    New-Item -ItemType Directory -Force "$SdkRoot\cmdline-tools" | Out-Null
    Move-Item "$tmp\x\cmdline-tools" "$SdkRoot\cmdline-tools\latest"
    Remove-Item -Recurse -Force $tmp
}

if (Test-Path $sdkmanager) {
    # 새 cmdline-tools(Android CLI 래퍼)는 패키지 구분자가 '/', 예전 sdkmanager 는 ';'
    $ver = (& $sdkmanager --version 2>&1 | Out-String)
    $newCli = $ver -match 'Android CLI'
    $missing = @()
    foreach ($pkg in $SdkPackages.Keys) {
        if (Test-Path (Join-Path $SdkRoot $SdkPackages[$pkg])) { Skip $pkg }
        else { $missing += $(if ($newCli) { $pkg } else { $pkg -replace '/', ';' }) }
    }
    if ($missing.Count -gt 0) {
        if ($CheckOnly) { Warn ("없음: " + ($missing -join ', ')) }
        else {
            if (-not $newCli) { Info 'licenses 동의'; 1..30 | ForEach-Object { 'y' } | & $sdkmanager --sdk_root="$SdkRoot" --licenses | Out-Null }
            Info ("install: " + ($missing -join ', ') + "  (NDK 포함 수 분 소요)")
            & $sdkmanager --sdk_root="$SdkRoot" @missing 2>&1 | Where-Object { $_ -notmatch '^\[' } | ForEach-Object { Info $_ }
        }
    }
}

# ---------------------------------------------------------------- 3. 환경변수
Step '환경변수 (사용자 범위)'
if ($CheckOnly) {
    foreach ($n in 'ANDROID_HOME', 'ANDROID_SDK_ROOT', 'ANDROID_NDK_HOME') { Info "$n = $([Environment]::GetEnvironmentVariable($n, 'User'))" }
} else {
    Set-UserEnv 'ANDROID_HOME' $SdkRoot
    Set-UserEnv 'ANDROID_SDK_ROOT' $SdkRoot
    Set-UserEnv 'ANDROID_NDK_HOME' "$SdkRoot\ndk\$NdkVersion"
    Add-UserPath "$SdkRoot\platform-tools"
    Add-UserPath "$SdkRoot\emulator"
    Add-UserPath "$SdkRoot\cmdline-tools\latest\bin"
}
Add-SessionPath "$SdkRoot\platform-tools"; Add-SessionPath "$SdkRoot\emulator"

# ---------------------------------------------------------------- 4. Android Studio
Step 'Android Studio'
$studioCands = @("$env:LOCALAPPDATA\Programs\android-studio\bin\studio64.exe",
                 'C:\Program Files\Android\Android Studio\bin\studio64.exe')
$studio = $studioCands | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($studio) { Skip "found: $studio" }
elseif ($CheckOnly) { Warn 'Android Studio 없음' }
else { Install-Winget 'Google.AndroidStudio' @() }

# ---------------------------------------------------------------- 5. Rust
Step 'Rust (rustup stable + Android 타깃 + cargo-ndk)'
Add-SessionPath "$env:USERPROFILE\.cargo\bin"
# rustup(msvc) 와 cargo install 에는 MSVC 링커가 필요하다
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
$hasMsvc = (Test-Path $vswhere) -and [bool](& $vswhere -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath)
if ($hasMsvc) { Skip 'MSVC C++ build tools present' }
elseif ($CheckOnly) { Warn 'MSVC C++ build tools 없음' }
else { Install-Winget 'Microsoft.VisualStudio.2022.BuildTools' @('--override', '--wait --quiet --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended') }

if (Has rustup) { Skip 'rustup present' }
elseif ($CheckOnly) { Warn 'rustup 없음' }
else { Install-Winget 'Rustlang.Rustup' @(); Add-SessionPath "$env:USERPROFILE\.cargo\bin" }

if ((Has rustup) -and -not $CheckOnly) {
    if ((& rustup toolchain list) -match '^stable') { Skip 'stable toolchain present' }
    else { & rustup toolchain install stable --profile minimal; & rustup default stable }
    $installed = & rustup target list --installed
    $need = $RustTargets | Where-Object { $installed -notcontains $_ }
    if ($need) { & rustup target add @need } else { Skip 'android targets present' }
    & cargo ndk --version 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { Skip 'cargo-ndk present' } else { Info 'cargo install cargo-ndk'; & cargo install cargo-ndk --locked }
}

# ---------------------------------------------------------------- 6. 검증
Step '검증'
$results = @()
function Check($name, [scriptblock]$cmd, $pattern) {
    $global:LASTEXITCODE = 0
    $out = (& $cmd 2>&1 | Out-String).Trim()
    $ok = ($LASTEXITCODE -eq 0) -and ($out -match $pattern)
    $first = ($out -split "`r?`n" | Where-Object { $_ -match $pattern } | Select-Object -First 1)
    if (-not $first) { $first = ($out -split "`r?`n" | Select-Object -Last 1) }
    $script:results += [pscustomobject]@{ Item = $name; Result = $(if ($ok) { 'PASS' } else { 'FAIL' }); Detail = "$first".Trim() }
}
Check 'java -version'          { java -version }                                         'version "(1[7-9]|2\d)'
Check 'sdkmanager list_installed' { & $sdkmanager --sdk_root="$SdkRoot" --list_installed } "ndk.$NdkVersion"
Check 'adb version'            { adb version }                                           'Android Debug Bridge'
Check 'emulator -accel-check'  { emulator -accel-check }                                 'is installed and usable'
Check 'emulator -list-avds'    { $o = emulator -list-avds; if ($o) { $o } else { '(AVD 없음 - 수동 단계 참고)' } } '.'
Check 'rustc -V'               { rustc -V }                                              '^rustc 1\.'
Check 'rust android targets'   { rustup target list --installed }                        'aarch64-linux-android'
Check 'cargo ndk --version'    { cargo ndk --version }                                   'cargo-ndk'
Check 'Android Studio'         { if ($studio -or ($studioCands | Where-Object { Test-Path $_ })) { 'installed' } else { 'missing'; $global:LASTEXITCODE = 1 } } '^installed'
$results | Format-Table -AutoSize -Wrap

# ---------------------------------------------------------------- 7. 수동 단계 안내
$img = "$SdkRoot\system-images\android-34\pleos_car_emulator\x86_64"
Step '수동 단계'
if (Test-Path $img) { Skip "Pleos 시스템 이미지 있음: $img" }
else {
@"
    [Pleos Connect Emulator] 로그인 없이 Android Studio GUI 에서 설치 (여유 공간 15GB 이상 필요)
     1) Android Studio > Tools > SDK Manager > SDK Update Sites > '+'
        Name: Pleos Connect System Image
        URL : $PleosImageXml
     2) SDK Platforms 탭 > 'Show Package Details' 체크 > Android API 34 >
        'Pleos Connect v2.1.0 APIs Intel x86_64 Atom System Image' 체크 > OK (라이선스 동의) > 재시작
     3) Tools > Device Manager > Create device > Automotive > 'Pleos Connect v2.1.0'
        Advanced: Default boot = Cold, RAM = 4 GB / System image: API 34 'Pleos Connect v2.1.0 APIs' > Finish
     4) 실행 후 Virtual Console:  adb forward tcp:8088 tcp:8088  ->  http://127.0.0.1:8088/
"@ | Write-Host
}
Write-Host "`n새 터미널을 열어야 환경변수(PATH 등)가 적용됩니다. 자세한 내용: docs\50_개발환경_세팅.md" -ForegroundColor Green
