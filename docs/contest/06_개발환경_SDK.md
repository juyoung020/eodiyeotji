> 출처: https://appdevchallenge.auto-hmg.io/guide/development  
> 수집일: 2026-11-05

[홈](https://appdevchallenge.auto-hmg.io/)/[참가 가이드](https://appdevchallenge.auto-hmg.io/guide)/개발 환경

# 개발 환경

차량용 앱 개발 시 활용할 수 있는 기능과 개발 환경을 안내합니다.

이 페이지에서

- [개발 환경 요구사항](https://appdevchallenge.auto-hmg.io/guide/development#platform)
- [Pleos Playground](https://appdevchallenge.auto-hmg.io/guide/development#platforms)
- [Pleos Connect SDK](https://appdevchallenge.auto-hmg.io/guide/development#capabilities)
- [예시 시나리오·튜토리얼](https://appdevchallenge.auto-hmg.io/guide/development#examples)
- [공식 개발 문서](https://appdevchallenge.auto-hmg.io/guide/development#official-docs)

## 개발 환경 요구사항

Pleos 개발 문서의 개발 환경 요구사항 기준으로 Android API 28 이상, Gradle 8.0 이상, Java 또는 Kotlin을 사용합니다. 문서와 대응하는 SDK 버전도 같은 페이지에 있습니다.

[**Pleos Connect 개발 환경 설정 가이드 (새 창에서 열림)**↗](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)

## Pleos Playground

Pleos는 현대자동차그룹의 소프트웨어 중심 모빌리티 브랜드이며,\
Pleos Connect는 차량용 인포테인먼트 플랫폼입니다.

Pleos Playground는 Pleos의 개발자 포털입니다.\
포털에서 주요 플랫폼과 개발에 필요한 내용을 확인할 수 있습니다.

[**Pleos Playground (새 창에서 열림)**↗](https://pleos.ai/playground)

## Pleos Connect SDK

Pleos Connect SDK는 Pleos Connect 환경에서 차량용 앱을 개발할 때 사용하는 개발 도구입니다.

### 차량 상태

Vehicle SDK

### 위치 정보

FusedLocation SDK

### 길 안내 연동

NaviHelper SDK

### 음성 입출력

TextToSpeech SDK · SpeechToText SDK

각 SDK의 제공 상태와 사용 조건은 공식 문서에서 확인해 주세요.

[**Pleos Connect SDK (새 창에서 열림)**↗](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)

## 예시 시나리오·튜토리얼

아래 예시는 참고용이며 앱 주제를 제한하지 않습니다.

### 음성 메모 앱

SpeechToText SDK로 운전 중 음성을 받아 메모로 남깁니다.

### 안전운전 점수 앱

Vehicle SDK의 차량 상태 데이터로 주행 습관을 점수로 보여 줍니다.

### 배달 기사용 앱

NaviHelper SDK로 경로 안내와 연동해 배달 동선을 돕습니다.

에뮬레이터 등 개발 도구로 구동 검증과 보완을 진행할 수 있습니다.

[**Vehicle SDK 튜토리얼 (새 창에서 열림)**↗](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk)

## 공식 개발 문서

Pleos Playground에서 Pleos Connect 플랫폼과 Pleos Connect SDK 개발 문서를 확인하세요. 지원 범위와 버전은 공식 문서를 기준으로 합니다.

- [Pleos Connect 개발 환경 설정 가이드 (새 창에서 열림)](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)

  Pleos Connect 환경에서 앱 개발을 시작하는 방법 (에뮬레이터 사용법)

- [Pleos Playground 개발 문서 (새 창에서 열림)](https://pleos.ai/playground)

  개발 문서·도구·가이드 포털

- [Pleos Connect SDK 공식 문서 (새 창에서 열림)](https://document.pleos.ai/api-reference/connect-sdk-pleos)

  Pleos Connect SDK API 참조 문서
