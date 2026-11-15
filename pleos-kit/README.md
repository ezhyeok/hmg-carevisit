# pleos-kit

Pleos Connect SDK(Vehicle, NaviHelper, TTS, STT, FusedLocation)와 AAOS Car API를 코루틴/Flow로 감싼 공통 모듈이다. 에뮬레이터에서 확인한 동작 규칙을 코드로 옮겨, 앱에서는 규칙을 신경 쓰지 않고 쓸 수 있게 했다.

## kit이 대신 지켜 주는 규칙

| 규칙 (에뮬레이터에서 확인) | kit에서 처리하는 곳 |
|---|---|
| Vehicle·NaviHelper 호출은 호출 스레드를 수 초간 막는다. 메인 스레드에서 부르면 ANR | 모든 호출을 SDK 전용 스레드에서 실행 (`SdkThreads`). 앱은 어느 스레드에서든 suspend 함수로 호출 |
| 내비 앱이 화면 앞으로 오면 Activity만 있는 앱은 얼려져서 이벤트를 받지 못한다 | `PleosForegroundService`를 상속한 서비스에 로직을 둔다 |
| 제어 API는 성공 콜백이 와도 값이 안 바뀔 수 있다 | `ReadWriteSignal.setAndConfirm()`이 값을 다시 읽어 확인 |
| 내비 조회는 호출해야 응답 이벤트가 오고, 요청·응답을 짝짓는 ID가 없다 | `NaviKit`이 조회를 한 번에 하나씩 보내고 응답 이벤트를 기다려 돌려준다 |
| 내비 경로 요청을 연달아 보내면 경로가 취소되는 일이 있었다 | `NaviKit`이 명령 사이에 3초 간격을 둔다 |
| `onRouteStarted`가 앱에 오지 않는다 | `NaviKit.startRoute()`가 `naviMode`를 조회해 길안내 시작을 확인 |
| 차속·위치 권한은 실행 중에 요청해야 한다 | `PleosPermissions.requestIfNeeded()` |
| 영역(area)과 값 범위는 차종마다 다르다 | `hvacAreas()`, `windowAreas()` 등 capability 조회 함수로 얻는다 |
| SDK 객체를 메인 스레드에서 만들거나 부르면 안 된다 | kit 객체는 어디서 만들어도 된다. SDK 객체는 `connect()`가 SDK 전용 스레드에서 처음 만든다 (`LazySdk`, logcat `PleosKit: Vehicle created on pleos-vehicle`). 놓을 때(`release`) 새로 만들지 않는다 |
| 연결·구독 실패를 삼키면 값이 오지 않는 흐름을 영영 기다린다 | `CarState.connect()`, 신호의 `get()`·`changes()`는 실패하면 예외로 끝난다. 다시 시도는 쓰는 쪽이 한다 (쉼표: coach 모듈 `SignalCollector`) |
| `CarPropertyManager.registerCallback`은 권한이 없으면 예외 없이 false를 준다 | `CarState.gear()`·`speedMps()`의 구독이 false를 예외로 바꾼다 |
| Car 서비스가 죽으면 `createCar(context)`로 만든 연결은 프로세스를 죽인다 | `CarState`는 상태 리스너를 받는 `createCar`로 만들고 `connected`로 알린다 (API 30+) |
| Pleos 차량 서비스가 멈추면(`force-stop`) SDK는 다시 붙지 않고 값이 언다 | `VehicleKit.reconnect()`, `LocationKit.reconnect()`가 SDK 객체를 버리고 새로 붙는다. 신호 객체는 그대로 쓸 수 있다 |
| TTS/STT 이벤트에 요청 번호가 없다. 쉬는 TTS에 `stop()`을 보내도 `onStop`이 온다 | `TurnRunner`(Turns.kt): 요청을 하나씩 보내고, 앞 요청의 끝남 이벤트를 받은 뒤 다음을 보낸다. 하는 중이 아니면 `stop()`은 SDK를 부르지 않는다 |
| STT는 `request()` 뒤 0.2~10초가 지나야 녹음을 시작한다(`onReady`). 듣는 시간을 요청부터 재면 그만큼 줄어든다 | `SttKit.listen(timeoutMs, onMicOpen)`: 듣는 시간은 `onReady`부터 잰다(`listenWindow`). 15초 안에 열리지 않으면 null. '듣는 중' 표시는 `onMicOpen`에서 켠다 |
| 내비는 새 경유지를 늘 뒤에 붙이고, 셋째는 조용히 버리고, 경유지 좌표를 돌려주지 않는다 | `NaviKit.addWaypoint`는 이름으로 확인하고 가득 차 있으면 곧바로 false. 순서를 정하려면 `NaviKit.setWaypoints` |
| 취소를 SDK 실패로 바꾸면 쓰는 쪽이 취소를 실패로 센다 | `sdkSend`·`sdkCall`은 취소를 `CancellationException` 그대로 던진다 |
| 앱이 죽은 뒤 다시 만들어진 서비스에는 `onStartCommand` 없이 `onTaskRemoved`만 올 수 있다 | `PleosForegroundService`가 `onTaskRemoved`에서도 시작하고, `startForeground`가 거절돼도 죽지 않고 다시 시도한다 |

## 구성

| 파일 | 내용 |
|---|---|
| `VehicleKit` | Vehicle SDK. 자주 쓰는 신호를 `ReadSignal`/`ReadWriteSignal`로 제공. 그 밖의 API는 `call`, `call2`, `observe`로 감싼다 |
| `CarState` | AAOS Car API. 기어·차속은 `ReadSignal`(`gear()`, `speedMps()`), 주행 중 UX 제한과 연결 여부(`connected`)는 `StateFlow` |
| `UxRestrictions`, `UxRestrictionWatcher` | 주행 중 UX 제한(`CarUxRestrictions`). 모르는 상태를 `Unknown`으로 따로 두고 `restricted`는 모르면 true(fail closed). 기능별 비트(`UxRestrictionFlags`)와 최대 글자 수·항목 수. `UxRestrictionWatcher`는 권한 없이 제한만 구독한다(화면이 서비스 없이 쓸 때) |
| `NaviKit` | NaviHelper. 조회(`routeState`, `tbt`, `waypoints`, `tripEstimate` 등)와 경로 변경(`startRoute`, `addWaypoint`, `cancelRoute`) |
| `TtsKit`, `SttKit` | 음성 합성·인식(ON_DEVICE 기본). `speak()`는 재생이 끝날 때까지 기다린다. `SpeechOutput`/`SpeechInput` 인터페이스를 구현한다(쓰는 쪽이 가짜로 시험할 수 있게) |
| `Turns.kt` | 음성 요청과 요청 번호 없는 이벤트를 짝짓는 `TurnGate`·`TurnRunner`, 듣는 시간 규칙 `listenWindow` |
| `LocationKit` | FusedLocation. 마지막 위치, 위치 갱신 Flow, 근접 알림 Flow |
| `PleosForegroundService` | 주행 중 계속 살아 있어야 하는 로직을 두는 서비스의 바탕 |
| `PleosPermissions` | 실행 중 요청이 필요한 권한 처리 |

## 사용 예

```kotlin
class DriveService : PleosForegroundService() {
    override val notificationTitle = "My App"
    private val vehicle by lazy { VehicleKit(this) }
    private val car by lazy { CarState(this) }
    private val navi by lazy { NaviKit(this) }
    private val tts by lazy { TtsKit(this) }

    override fun onServiceStarted() {
        serviceScope.launch {
            car.connect()
            vehicle.connect()

            // 조회와 구독
            val area = vehicle.hvacAreas().first()
            val recirculation = vehicle.recirculation(area)
            val now: Boolean? = recirculation.get()
            launch { recirculation.changes().collect { on -> /* 값이 바뀔 때마다 */ } }

            // 제어: 실제로 반영됐는지까지 확인
            val applied: Boolean = recirculation.setAndConfirm(false)

            // 주행 상태
            // 연결 전·구독 실패면 true다(모르면 제한). 기능별 비트는 car.uxRestrictions(Known.has(UxRestrictionFlags.LIMIT_CONTENT) 등)
            launch { car.drivingRestricted.collect { restricted -> /* 주행 중이면 화면 조작을 줄인다 */ } }

            // 내비: 길안내 없이 거리·시간·도착 시 배터리만 조회
            navi.connect()
            val trip = navi.tripEstimate(TripRequest(37.5547, 126.9707, "", "서울역", ""))

            // 음성: 재생이 끝날 때까지 기다린다
            tts.connect()
            tts.speak("환기를 위해 외기 모드로 바꿨어요")
        }
    }
}
```

kit에 전용 래퍼가 없는 Vehicle API는 범용 어댑터로 감싼다. 콜백 인자가 하나면 `call`, 둘이면 `call2`, 리스너 등록·해제 쌍은 `observe`를 쓴다.

```kotlin
val light = vehicle.raw.getLight()
val switch = vehicle.call<LightSwitch?>("getCabinLightSwitch") { ok, fail -> light.getCabinLightSwitch(ok, fail) }
vehicle.call2<SeatArea, MoveControl?>("moveSeatBackrestAngle") { ok, fail ->
    vehicle.raw.getSeat().moveSeatBackrestAngle(area, MoveControl.BACKWARD, ok, fail)
}
```

새 API를 감쌀 때는 먼저 `docs/contest/03_SDK_API_레퍼런스/`에서 시그니처와 권한을 확인한다.

## 신호와 권한

| kit 함수 | 필요한 Manifest 권한 |
|---|---|
| `recirculation`, `hvacPower`, `hvacTemperatureC`, `hvacFanSpeed` 조회 | `pleos.car.permission.CAR_CLIMATE` |
| 위 신호의 제어(`set`, `setAndConfirm`) | `pleos.car.permission.CONTROL_CAR_CLIMATE` |
| `doorPosition` | `pleos.car.permission.CAR_DOORS` |
| `windowPosition` 조회 / 제어 | `pleos.car.permission.CAR_WINDOWS` / `CONTROL_CAR_WINDOWS` |
| `seatOccupancy` | `pleos.car.permission.CAR_SEATS` |
| `seatBackrestPosition` / `moveSeatBackrest` | `pleos.car.permission.CAR_SEATS` / `CONTROL_CAR_SEATS` |
| `cruiseControl`, `tripDrivingTimeMin`, `brakePedal` | `pleos.car.permission.CAR_DRIVE` |
| `evStateOfCharge` | `pleos.car.permission.CAR_ENERGY` |
| `CarState.gear()` | `android.car.permission.CAR_POWERTRAIN` |
| `CarState.speedMps()` | `android.car.permission.CAR_SPEED` (실행 중 요청). 없으면 조회·구독이 예외로 실패한다 |
| `NaviKit` 조회 / 경로 변경 | `pleos.car.permission.NAVI_ROUTE` / `NAVI_ROUTE_SEARCH` |
| `TtsKit` / `SttKit` | `pleos.car.permission.TTS_SERVICE` / `STT_SERVICE` |
| `LocationKit` | `pleos.car.permission.FUSED_LOCATION` (실행 중 요청) |

포그라운드 서비스 권한(`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`)은 kit의 Manifest가 선언한다. SDK 권한은 앱이 쓰는 것만 앱 Manifest에 선언한다.

## 테스트

SDK와 안드로이드를 부르지 않는 규칙은 JVM 테스트로 지킨다 (노트북에서 `gradle.ps1 :pleos-kit:testDebugUnitTest`): `TurnsTest`(음성 요청 순서, 늦은 멈춤 이벤트), `ListenWindowTest`(듣는 시간은 마이크가 열린 뒤부터), `SdkCallTest`(취소는 취소로, SDK 객체는 처음 쓸 때 한 번, 미지원 오류 분류). 내비 경유지 확인 규칙은 앱의 `KitRulesTest`에 있다.
기기에서만 볼 수 있는 것은 점검 스크립트가 본다: `tools/laptop/restart-check.sh`(서비스 다시 만들기), `tools/laptop/signals-check.sh`(차량 서비스가 죽거나 멈춘 뒤 신호가 다시 오는지).

## 에뮬레이터 확인 결과

`app`의 `KitSelfTest`가 kit의 각 기능을 실제로 호출해 본다. 실행 방법과 최신 결과는 `docs/kit-selftest.md`에 있다.
