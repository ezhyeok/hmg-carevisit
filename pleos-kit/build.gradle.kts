plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.hmg.pleoskit"
    compileSdk = 34
    // AAOS 표준 Car API (CarPropertyManager, CarUxRestrictionsManager)
    useLibrary("android.car")

    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // JVM 단위 테스트: SDK·안드로이드를 부르지 않는 kit의 규칙만 시험한다 (요청 순서 Turns, sdkSend, 실패 분류).
    // android.util.Log 같은 호출은 예외 대신 기본값을 돌려주게 한다
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // 앱에서 SDK 타입(SeatArea, RouteInfo 등)을 그대로 쓰므로 api로 노출한다
    api("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:LLM:2.1.3.2")
    api("ai.pleos.playground:NaviHelper:3.0.5")
    api("ai.pleos.playground:TextToSpeech:3.3.1")
    api("ai.pleos.playground:SpeechToText:3.3.1")
    api("ai.pleos.playground:FusedLocation:1.1.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    // 가상 시간으로 요청 순서와 시간 초과를 시험한다
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
