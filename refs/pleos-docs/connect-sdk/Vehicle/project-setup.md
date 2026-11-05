> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup

# Importing Vehicle SDK

Vehicle SDK 사용을 위해 SDK 의존성을 설정합니다. 퍼미션 설정은 [Permission](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)을 참조하세요.

## Maven 저장소 등록[​](#maven-저장소-등록 "Maven 저장소 등록에 대한 직접 링크")

SDK를 내려받을 Maven 저장소를 앱 프로젝트의 `settings.gradle`에 등록합니다.

- Kotlin
- Groovy

``` kotlin
dependencyResolutionManagement {
    ...
    repositories {
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
        }
        ...
    }
}
```

``` groovy
dependencyResolutionManagement {
    ...
    repositories {
        ...
        maven {
            url "https://nexus-playground.pleos.ai/repository/maven-releases/"
        }
        ...
    }
}
```

## SDK 의존성 추가[​](#sdk-의존성-추가 "SDK 의존성 추가에 대한 직접 링크")

앱 모듈의 `build.gradle`에 SDK 의존성을 선언합니다.

- Kotlin
- Groovy

``` kotlin
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
}
```

``` groovy
dependencies {
    implementation "ai.pleos.playground:Vehicle:2.3.2"
}
```
