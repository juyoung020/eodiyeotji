> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk

# Importing SpeechToText SDK

SpeechToText SDK를 사용하려면 Pleos Maven 저장소를 등록하고 앱 모듈에 SDK 의존성을 추가합니다.

## Maven 저장소 등록[​](#maven-저장소-등록 "Maven 저장소 등록에 대한 직접 링크")

프로젝트의 `settings.gradle.kts` 또는 `settings.gradle` 파일에 Pleos Maven 저장소를 추가합니다.

- Kotlin DSL
- Groovy

settings.gradle.kts

``` kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}
```

settings.gradle

``` groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url "https://nexus-playground.pleos.ai/repository/maven-releases/"
        }
    }
}
```

## SDK 의존성 추가[​](#sdk-의존성-추가 "SDK 의존성 추가에 대한 직접 링크")

앱 모듈의 Gradle 파일에 SpeechToText SDK 의존성을 추가합니다.

- Kotlin DSL
- Groovy

app/build.gradle.kts

``` kotlin
dependencies {
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
}
```

app/build.gradle

``` groovy
dependencies {
    implementation "ai.pleos.playground:SpeechToText:3.3.1"
}
```

Gradle 동기화를 완료한 후 [SpeechToText 권한을 선언](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions)합니다.
