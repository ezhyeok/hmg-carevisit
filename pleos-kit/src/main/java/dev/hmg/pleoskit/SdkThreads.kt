package dev.hmg.pleoskit

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Pleos SDK 호출 전용 스레드.
 *
 * Vehicle·NaviHelper 호출은 서비스 연결을 기다리며 호출 스레드를 수 초간 막는다. 메인 스레드에서 부르면 ANR이
 * 나므로 kit의 모든 SDK 호출은 여기서 실행한다. SDK별로 스레드를 나눈 이유는 Vehicle 호출이 길게 막혀도
 * 내비·음성 요청이 밀리지 않게 하기 위해서다.
 */
internal object SdkThreads {
    val vehicle = single("pleos-vehicle")
    val navi = single("pleos-navi")
    val voice = single("pleos-voice")
    val location = single("pleos-location")
    val llm = single("pleos-llm")

    private fun single(name: String): ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, name).apply { isDaemon = true } }.asCoroutineDispatcher()
}
