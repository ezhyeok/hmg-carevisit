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

## 현재 제출본 — 2026-11-15

현재 APK는 현재 소스로 빌드한 최종 제출본이다(개발자 확인). 소스 기준은 `452c8f0376333ff14812c99f7e9cdca1056b5d89`이며 이번 작업은 문서·자막만 정리한다. **APK·영상·기존 JVM 결과를 묶은 제출본 표는 [루트 README](../README.md#심사자-빠른-시작--제출-apk)**에 있다.

`apk/차곡_진료동행_release.apk`: 패키지 `dev.carevisit.app`, versionName `0.1.0`, versionCode `1`, 41,847,536 bytes. SHA-256 `75f825691d12f2c2396fe7c6b4bd5c667591b61ff814af7ae81b7d4b840edef4`를 현재 파일에서 확인했다. 빌드 로그에 기록된 별도 소스 리비전과 서명 인증서 검증 로그는 이번 문서 점검에서 확인하지 않았다.

- 개발자 확인: 노트북 assembleRelease·앱/kit 단위 테스트, APK 설치와 에뮬레이터 시연.
- REAL: TTS SDK 요청·완료, VC 입력의 Vehicle 도어 + AAOS 기어·속도, CarUxRestrictions 상세 제한.
- MOCK: STT 문자열, 병원 목적지·도착, 병원 체류와 귀가 60초 시간 이동, DemoFamilyLink 가족 답장.
- 영상: 155초·1920×1080·무음. 인터뷰, 약/예약 확인, 메모·브리핑·가족 답장과 ‘새 진료 준비’를 보여 준다. 귀가 대기는 시간 이동한다. 캡처는 질문 화면과 주차 중 기록을 보여 준다.

**실제 마이크 인식·실제 NaviHelper 도착·휴대폰 relay 왕복은 미검증이다.** 무음 영상은 실제 들리는 출력 품질을 보여 주지 않는다.

## 반례와 현재 방어 — JVM 검증 범위

기존 [core XML 결과 폴더](../visit-core/build/test-results/test/)는 99개, [adapter XML 결과 폴더](../visit-core/build/test-results/adapterTest/)는 31개이며 실패·오류는 모두 0이다. 이번 문서 작업에서 테스트를 재실행하지 않았다. 생성된 XML은 로컬 빌드 결과이므로 공개 저장소에서 없으면 아래 소스와 README 실행 명령을 사용한다.

| 반례 | 현재 방어 | 테스트 파일 | 남은 한계 |
|---|---|---|---|
| `1~2정`이 `1.2정`으로 변함 | 공백 외 모든 문자 동일성 검사로 수치·단위·문장부호 변경 거부 | [ParserTest](../visit-core/src/test/kotlin/dev/carevisit/core/ParserTest.kt) | JVM 문자열 검사. 입력 STT의 정확성이나 의학적 의미를 검증하지 않음 |
| A병원 방문 중 B 도착·취소 뒤 늦은 도착 | 이름/주소 대조, 변경·취소 무효화, 후보 30초 만료 | [ArrivalTest](../visit-core/src/test/kotlin/dev/carevisit/core/ArrivalTest.kt) | JVM 이벤트 모형. 같은 이름/주소의 반복 경로를 완전히 구별하지 못함 |
| 재시작 후 정정 반복·출차 즉시 질문 | 이전 발화 보존, 정정 예산 복구, 새 귀가 주행 60초 누적 | [ReviewFlowTest](../visit-core/src/test/kotlin/dev/carevisit/core/ReviewFlowTest.kt) | JVM 가상 시간. 실제 음성/내비 품질을 측정하지 않음 |

약·예약 정정은 **인터뷰 중 1회**다. 이후 재정정 요청·불명확한 답은 확인 필요로 남기며 완료 후 항목별 수정은 지원하지 않는다. 두 번째 읽기 확인의 TTS에도 재정정을 권하는 표현이 남아 있어, 실제 허용 횟수는 이 설명과 화면 버튼을 기준으로 한다.

## 추가 확인 대상과 제품 범위

- 서비스 단절·재연결, 음성 포커스 중단, LLM 기기 내 응답 품질, 실제 파일 복구·삭제, 840×1184dp 분할·다크·글꼴 확대는 추가 확인 대상이다.
- 문 열림은 하차 확정이 아니다. 누적 주행 60초는 주차장 이탈·도로 안전 판정이 아니다. 예약은 서비스 실행 중 확인하며 꺼진 앱이나 차를 깨우지 않는다.
- 승인 글에는 STT 인식문·개인정보가 들어갈 수 있다. 발화 목록·좌표 제외는 비식별화를 뜻하지 않는다. 실제 서비스의 HTTPS·사용자 인증은 확장 범위다.
- 실제 모드는 FLAG_SECURE로 캡처를 막고 시연 모드는 캡처를 허용한다. 시트 제어·좌석 점유·POI 검색·자동119전화는 제품 범위가 아니다.
