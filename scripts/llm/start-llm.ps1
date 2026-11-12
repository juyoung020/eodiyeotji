<#
  기억 에이전트용 LLM 서버 (Qwen3.5-9B, llama.cpp llama-server, OpenAI 호환 API)

  실행:  powershell -ExecutionPolicy Bypass -File scripts\llm\start-llm.ps1
         powershell -ExecutionPolicy Bypass -File scripts\llm\start-llm.ps1 -Model D:\models\Qwen3.5-9B-Q8_0.gguf -Port 8181

  - 주소: http://127.0.0.1:8181/v1  (에뮬레이터 앱에서는 http://10.0.2.2:8181/v1 = 이 PC)
  - 모델: unsloth/Qwen3.5-9B-MTP-GGUF (Q5_K_M, 6.7GB). 받기:
      curl -L -o C:\llm\models\Qwen3.5-9B-MTP-Q5_K_M.gguf https://huggingface.co/unsloth/Qwen3.5-9B-MTP-GGUF/resolve/main/Qwen3.5-9B-Q5_K_M.gguf
  - llama.cpp: CUDA 빌드의 llama-server.exe (기본 C:\llm\build\bin). 없으면 github.com/ggml-org/llama.cpp 릴리스의 CUDA 판을 받는다.
  - 옵션 이유:
      --jinja                 Qwen 채팅 템플릿으로 도구 호출(tool_calls)을 처리
      --reasoning-format deepseek  생각 과정은 reasoning_content로 분리 (앱은 무시, 요청에서 enable_thinking=false)
      --spec-type draft-mtp   MTP 머리로 다음 토큰을 미리 맞혀 답을 빠르게 (결과는 같다)
      --cache-reuse           매 턴 같은 시스템 프롬프트·도구 정의를 다시 계산하지 않음
      -np 2                   하루 라벨링(백그라운드)과 질문 응답이 서로 기다리지 않게 슬롯 2개
#>
param(
    [string]$Model = 'C:\llm\models\Qwen3.5-9B-MTP-Q5_K_M.gguf',
    [string]$Server = 'C:\llm\build\bin\llama-server.exe',
    [int]$Port = 8181,
    [int]$Context = 32768,
    # 중계 서버로 밖에 열 때(expose-llm.ps1) 쓰는 비밀번호. 비우면 local.properties의 LLM_ORIGIN_KEY, 그것도 없으면 비밀번호 없이
    [string]$ApiKey = ''
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path $Model)) { throw "모델 파일이 없습니다: $Model (위 설명의 curl 명령으로 받으세요)" }
if (-not (Test-Path $Server)) { throw "llama-server.exe가 없습니다: $Server" }
if (-not $ApiKey) {
    $lp = Join-Path $PSScriptRoot '..\..\local.properties'
    if (Test-Path $lp) { $ApiKey = (Get-Content $lp | Where-Object { $_ -match '^LLM_ORIGIN_KEY=' } | Select-Object -First 1) -replace '^LLM_ORIGIN_KEY=', '' }
}
$keyArgs = if ($ApiKey) { @('--api-key', $ApiKey) } else { @() }
$env:CUDA_SCALE_LAUNCH_QUEUES = '4x'
Write-Host "LLM 서버 시작: http://127.0.0.1:$Port/v1  (에뮬레이터: http://10.0.2.2:$Port/v1)"
& $Server -m $Model --alias qwen3.5-9b `
    --host 127.0.0.1 --port $Port `
    -c $Context -ngl 99 -fa on `
    -b 2048 -ub 512 --threads 8 `
    --cache-reuse 256 `
    --spec-type draft-mtp --spec-draft-n-max 3 `
    --jinja --reasoning-format deepseek `
    --temp 0.6 --top-p 0.95 --top-k 20 --min-p 0 `
    -np 2 @keyArgs
