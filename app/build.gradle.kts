import org.gradle.internal.os.OperatingSystem
import java.util.Properties

// 카카오 로컬 API REST 키 (장소 이름, place/KakaoLocalProvider.kt). local.properties의 KAKAO_REST_API_KEY → 환경 변수 → 없으면 빈 값(카카오 끔, OSM만)
val kakaoRestApiKey: String = Properties().let { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    (props.getProperty("KAKAO_REST_API_KEY") ?: System.getenv("KAKAO_REST_API_KEY") ?: "").filter { it.isLetterOrDigit() }
}

// 구글 Places API (New) 키 (들른 곳 대표 사진, place/PlacePhotoRepository.kt). 구글 클라우드 프로젝트 eodiyeotji에서 Places API (New)만 허용.
// local.properties의 GOOGLE_PLACES_API_KEY → 환경 변수 → 없으면 빈 값(사진 끔)
val googlePlacesApiKey: String = Properties().let { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    (props.getProperty("GOOGLE_PLACES_API_KEY") ?: System.getenv("GOOGLE_PLACES_API_KEY") ?: "").filter { it.isLetterOrDigit() || it == '_' || it == '-' }
}

// 기억 에이전트·하루 라벨링 LLM (agent/). OpenAI 호환 Chat Completions 서버 (개발: 개발 PC의 llama.cpp llama-server + Qwen3.5-9B).
// local.properties → 환경 변수 → 기본값. 에뮬레이터에서 개발 PC는 10.0.2.2 로 보인다.
val llmProps: Properties = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
}
fun llmSetting(name: String, default: String): String =
    (llmProps.getProperty(name) ?: System.getenv(name) ?: default).trim().filter { it != '"' && it != '\\' }
val llmBaseUrl = llmSetting("LLM_BASE_URL", "http://10.0.2.2:8181/v1")
val llmModel = llmSetting("LLM_MODEL", "qwen3.5-9b")
val llmApiKey = llmSetting("LLM_API_KEY", "")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.eodiyeotji"
    compileSdk = 35
    // AAOS 표준 Car API(CarPropertyManager: 기어·시동). 기기에서는 시스템이 제공하므로 APK에 넣지 않는다.
    useLibrary("android.car")
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "app.eodiyeotji"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Pleos 에뮬레이터(x86_64)와 실차(arm64)만 만든다
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoRestApiKey\"")
        buildConfigField("String", "GOOGLE_PLACES_API_KEY", "\"$googlePlacesApiKey\"")
        buildConfigField("String", "LLM_BASE_URL", "\"$llmBaseUrl\"")
        buildConfigField("String", "LLM_MODEL", "\"$llmModel\"")
        buildConfigField("String", "LLM_API_KEY", "\"$llmApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 공모전 제출 APK는 설치만 되면 되므로 디버그 키로 서명한다. 앱마켓에 낼 때 정식 키로 바꾼다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.lifecycle.service)
    implementation(libs.pleos.fusedlocation)
    implementation(libs.pleos.navihelper)
    implementation(libs.maplibre.android)
    implementation(libs.coil.compose)

    implementation(libs.jna) { artifact { type = "aar" } }

    testImplementation(libs.junit)
    testImplementation(libs.json)
}

// ---------------------------------------------------------------------------
// Rust 코어(core-rs) → 앱 연결. assemble 할 때 자동으로 실행된다.
//   1) cargoNdkBuild : 에뮬레이터(x86_64)·실차(arm64)용 libeodi_core.so 빌드 (release, LTO)
//   2) uniffiBindgen : 같은 코드로 Kotlin 바인딩 생성 (app.eodiyeotji.core.rust)
// 결과물은 app/build/ 아래에만 생기고 git에 올라가지 않는다.
// ---------------------------------------------------------------------------
val coreDir = rootProject.file("core-rs")
val rustJniDir = layout.buildDirectory.dir("rustJniLibs")
val uniffiOutDir = layout.buildDirectory.dir("generated/uniffi/kotlin")
val coreInputs = files(coreDir.resolve("src"), coreDir.resolve("Cargo.toml"), coreDir.resolve("Cargo.lock"), coreDir.resolve("uniffi.toml"))

val cargoNdkBuild by tasks.registering(Exec::class) {
    group = "rust"
    description = "Rust 코어를 Android용 .so로 빌드"
    workingDir = coreDir
    inputs.files(coreInputs)
    outputs.dir(rustJniDir)
    val ndkDir = android.ndkDirectory
    doFirst { environment("ANDROID_NDK_HOME", ndkDir.absolutePath) }
    commandLine(
        "cargo", "ndk", "-t", "x86_64", "-t", "arm64-v8a", "--platform", "28",
        "-o", rustJniDir.get().asFile.absolutePath, "build", "--release", "--lib",
    )
}

val cargoHostLib by tasks.registering(Exec::class) {
    group = "rust"
    description = "바인딩 생성용 호스트 라이브러리 빌드 (심볼이 남아 있는 디버그 빌드)"
    workingDir = coreDir
    inputs.files(coreInputs)
    outputs.dir(coreDir.resolve("target/debug"))
    commandLine("cargo", "build", "--lib")
}

val uniffiBindgen by tasks.registering(Exec::class) {
    group = "rust"
    description = "UniFFI로 Kotlin 바인딩 생성"
    dependsOn(cargoHostLib)
    workingDir = coreDir
    inputs.files(coreInputs)
    outputs.dir(uniffiOutDir)
    val os = OperatingSystem.current()
    val hostLib = when {
        os.isWindows -> "target/debug/eodi_core.dll"
        os.isMacOsX -> "target/debug/libeodi_core.dylib"
        else -> "target/debug/libeodi_core.so"
    }
    val outDir = uniffiOutDir.get().asFile
    doFirst { outDir.deleteRecursively() }
    commandLine(
        "cargo", "run", "-q", "--features", "bindgen", "--bin", "uniffi-bindgen", "--",
        "generate", "--library", hostLib, "--language", "kotlin", "--no-format",
        "--out-dir", outDir.absolutePath,
    )
}

android.sourceSets.getByName("main") {
    java.srcDir(uniffiOutDir)
    jniLibs.srcDir(rustJniDir)
}

tasks.named("preBuild") { dependsOn(cargoNdkBuild, uniffiBindgen) }
