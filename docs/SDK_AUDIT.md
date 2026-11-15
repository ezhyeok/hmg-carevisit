# SDK 사용 근거와 현장 확인 목록

문서 스냅샷은 `docs/contest/03_SDK_API_레퍼런스/`다. 아래 선언을 실제 grep으로 확인한 뒤 사용했다. SDK 호출은 `pleos-kit`에서만 하고 앱에는 SDK import 또는 raw 접근이 없다. 앱이 선언하는 권한은 `ReleaseSurfaceTest.productPermissionsMatchUses`의 용도 표와 동일하다.

| 기능 | 확인한 선언 | 근거 파일 |
|---|---|---|
| Vehicle 연결 | `Vehicle(context)`, `initialize()`, `getDoor()`, `release()` | `Vehicle/ai.pleos.playground.vehicle.md` |
| 도어 capability | `checkDoorPositionCapabilities(onComplete: (CapabilityConfig, Map<DoorArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | `Vehicle/ai.pleos.playground.vehicle.api.md` |
| 동승석 도어 | `DoorArea(DoorValue.ROW_1_RIGHT)` | `Vehicle/ai.pleos.playground.vehicle.api.model.area.md`, `Vehicle/ai.pleos.playground.vehicle.constant.area.md` |
| 도어 조회/이벤트 | `getCurrentDoorPosition(area, onComplete: (Int?) -> Unit, onFailed)`, `registerDoorPosition(area, listener)`, `unregisterDoorPosition(area, listener)`; `DoorPositionListener.onPositionUpdated(Int?)`, `onFailed(Exception)` | `Vehicle/ai.pleos.playground.vehicle.api.md`, `Vehicle/ai.pleos.playground.vehicle.listener.md` |
| 내비 연결/이벤트 | 기존 `NaviKit`의 `NaviHelper.initialize` 및 `NaviHelperEventListener` | `NaviHelper/ai.pleos.playground.navi.helper.md`, `NaviHelper/ai.pleos.playground.navi.helper.listener.md` |
| 목적지 | `fun getDestinationInfo()` → `onDestinationInfo(destinationInfo: DestinationInfo)` | 위 helper/listener 문서 |
| 이름/좌표/남은 거리 | `DestinationInfo(latitude: Double, longitude: Double, ..., name: String, ..., remainDistance: Int, ...)` | `NaviHelper/ai.pleos.playground.navi.data.md:777` |
| 길안내 상태 | `getRouteStateInfo()` → `RouteStateInfo.naviMode`; `ROUTE_GUIDING`, `SIMULATION_GUIDING` | helper/data/constants 문서 |
| 도착 | `onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo)` | `NaviHelper/ai.pleos.playground.navi.helper.listener.md` |
| 저장 병원 길안내 | `fun requestRoute(routeInfo: RouteInfo)`; `RouteInfo(latitude, longitude, displayLatitude?, displayLongitude?, poiName, poiId, address, poiSubId, routeOption=RECOMMENDED, ...)` | `NaviHelper/ai.pleos.playground.navi.helper.md:339`, `...navi.data.md:1329` |
| TTS | `TextToSpeech(context, Mode.ON_DEVICE)`, `initialize(OnInitializeListener)`, `addEventListener`, `removeEventListener`, `setLocale(Locale)`, `speak(text: String, isFinal: Boolean = true)`, `stop()`, `release()` | `TextToSpeech/ai.pleos.playground.tts.md` 및 listener/constant 문서 |
| TTS 완료 | `onDone()`, `onStop()`, `onError(ErrorType)` | `TextToSpeech/ai.pleos.playground.tts.listener.md` |
| STT | `SpeechToText(context, Mode.ON_DEVICE)`, `initialize(OnInitializeListener)`, `addListener`, `removeListener`, `setLocale(Locale)`, `request()`, `stop()`, `release()` | `SpeechToText/ai.pleos.playground.stt.md` 및 listener/constant 문서 |
| STT 결과/듣기 시작 | `onReady()`, `onUpdated(stt: String, completed: Boolean)`, `onEndedRecognition()`, `onError(ErrorType)` | `SpeechToText/ai.pleos.playground.stt.listener.md` |
| 선택 LLM | `LLM(context: Context, mode: Mode = Mode.SERVER)`, `Mode.ON_DEVICE`, `initialize()`, `generateContent(prompt: String, listener: ResultListener)`, `release()` | `LLM/ai.pleos.playground.llm.md`, `...llm.constant.md` |
| LLM 결과 | `ResultListener.onResponse(response: String, completed: Boolean)`, `onError(reason: String)` | `LLM/ai.pleos.playground.llm.listener.md` |

AAOS 표준은 복사한 kit의 검증된 `CarState`를 사용한다. `VehiclePropertyIds.GEAR_SELECTION`, `VehiclePropertyIds.PERF_VEHICLE_SPEED`, `VehicleGear.GEAR_PARK`/`GEAR_DRIVE`는 API34 심볼이다. `UxRestrictionWatcher`는 `CarUxRestrictionsManager` 구독과 모름 상태를 제공한다. 잘못된 `VehicleSpeedListener`나 가정한 seat occupancy를 쓰지 않는다.

권한: `CAR_POWERTRAIN`, dangerous `CAR_SPEED`, Pleos `CAR_DOORS`, `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `TTS_SERVICE`, `STT_SERVICE`, `LLM_SERVICE`, HTTP용 `INTERNET`. kit manifest가 foreground service / specialUse 권한을 선언한다. FUSED_LOCATION·CAR_SEATS·차량 제어 권한은 제품에서 선언하지 않는다.

오디오 포커스의 플랫폼 API 패턴은 `docs/contest/02_Pleos_개발가이드/pleos-connect/vehicle-app-developer-guide/audio-focus-development-guide/`의 요청·반납·손실 처리 예제를 따른다. AudioAttributes의 assistant/speech 문맥, `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, 손실 시 TTS/STT 중단을 사용한다. Pleos TTS 서비스의 포커스와 중복될 가능성은 에뮬레이터에서 확인해야 한다.

## 이 서버에서 확인한 것

- 코어의 상태 전이, 파서, 날짜, consent/retention, 원문 provenance, 공유 확인, polish guard, 재시작, 원자적 파일 저장: JVM 테스트.
- SDK 메서드 선언과 권한: 로컬 API 레퍼런스 grep 및 기존 kit/쉼표 패턴 비교.
- Android 런타임/SDK 신호는 이 서버에서 실행하지 않았다. API 선언 확인은 실제 이벤트 전달 검증과 다르다.

## 노트북·에뮬레이터에서 반드시 확인할 것

1. **Android 컴파일:** app/pleos-kit debug와 release, Kotlin/Compose 코드, unit tests, manifest merge, release signing. 서버에 Android SDK가 없어 아직 수행하지 않았다.
2. **내비:** 병원 목적지를 안내할 때 `DestinationInfo.name`의 실제 문자열과 좌표, remainDistance 단위/초기값, `onDestinationArrived` 전달. 길안내 종료+200m 후보가 단순 경로 취소를 잘 구분하는지. 미지원 시 주차 중 수동 도착을 사용한다.
3. **차량:** VC의 P/D/차속/오른쪽 앞문과 앱 전이. capability 맵에서 해당 문 영역이 있는지. foreground/내비 전환, Car/Vehicle 서비스 죽음 이후 재연결과 fail-closed 화면.
4. **음성:** TTS 완료, STT onReady 이후12초 창, -no-audio 이외 실제 마이크 입력. 시연은 scripted STT이므로 실제 인식 성공률을 입증하지 않는다. 전화/내비/Gleo의 포커스 손실 시 중단 및 수동 재시도. foreground 알림/재시작과 kit listener 해제 순서.
5. **LLM:** 기존 emulator에서 ON_DEVICE가40초 무응답이었다. 새 앱에서8초 후 fallback, 늦은 callback 격리와 release를 확인한다. 응답 성공이나 품질을 전제로 하지 않는다.
6. **가족:** emulator→10.0.2.2 relay→휴대폰, 연결 방화벽, 동의 전송 차단, 개별 승인 문구 일치, 나왔어/잘 받았어, 끊김·만료·철회 삭제. 운영용 HTTPS/계정 인증은 이 시연 relay 범위 밖이다.
7. **UI/개인정보:** 840×1184dp/2560×1440, 라이트/다크, 글꼴 확대, driving regulation on/off, 키보드 닫힘, FLAG_SECURE가 캡처/시연 녹화에 미치는 영향. 화면만 막고 core 명령을 허용하지 않는지 확인한다.
8. **파일:** 실제 Android app-private 파일의 temp+rename/fsync, 프로세스 강제 종료 직후 이어가기/폐기, 전체 삭제 후 재시작, retention 만료 시 startup purge.

시트 slide/heater/seat occupancy, 차량 위치 기반 귀가 감지, POI 검색, 자동119전화, 꺼진 차를 깨우는 알림은 구현하거나 검증했다고 주장하지 않는다. 자세한 제품 경계는 SPEC을 따른다.
