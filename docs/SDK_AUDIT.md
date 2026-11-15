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

## 확인한 범위 — 2026-11-15

- **LEAD 검증:** Android 빌드, release APK 설치·실행, Pleos Connect Emulator v2.1.0(API 34)의 방문 시연. 기존 ‘Android 컴파일 미수행’ 문구를 이 결과로 대체한다.
- **시연 REAL:** TTS SDK 요청·완료, VC 입력의 Vehicle 도어 + AAOS 기어·속도, CarUxRestrictions 상세 화면 제한. `-no-audio`·무음 영상이므로 실제 가청 출력 품질을 검증했다는 뜻은 아니다.
- **시연 MOCK:** 정해진 STT 답, 병원 목적지·도착, 체류시간 이동, DemoFamilyLink 메모리 가족 답장. 실제 STT/NaviHelper/HTTP relay 성공과 구분한다.
- **코드·자동 검증:** 코어/어댑터 JVM 테스트, 로컬 SDK 선언·권한 대조. 이번 DOC 작업은 런타임 테스트를 새로 수행한 작업이 아니며 기존 테스트 개수를 재측정값으로 옮기지 않는다.

### 제출 파일 식별과 개선 소스의 경계

`apk/차곡_진료동행_release.apk`의 manifest를 직접 읽어 `dev.carevisit.app`, versionName `0.1.0`, versionCode `1`을 확인했다. 크기 41,847,536 bytes, SHA-256 `75f825691d12f2c2396fe7c6b4bd5c667591b61ff814af7ae81b7d4b840edef4` (2026-11-15 현재).

이 APK를 추가한 커밋은 `b72ec40`이다. **APK를 만든 소스 커밋·서명 인증서 검증 결과는 이번 DOC 점검에서 확인하지 않았으므로 동일하다고 단정하지 않는다.** LEAD가 노트북에서 확인한 release 빌드·시연 결과와, 이번 개선 소스의 새 테스트 결과는 별도다. 개선 소스로 다시 빌드하면 APK 해시·서명·실행을 재확인해야 한다. 기존 녹화는 개선 전 APK를 사용하므로 추가된 귀가 대기·정정 UX의 실행 증거가 아니다.

## 남은 실제 경로 확인

1. **내비:** 실제 병원 목적지 이름·좌표, 남은 거리, 도착 콜백과 목적지 일치, 경로 취소를 실제 도착으로 오인하지 않는지. 제출 시연에서는 모의 입력이다.
2. **차량:** 서비스 단절·재연결과 모름 상태의 화면 제한. 문 열림은 하차 확정이 아니며 시동으로 앱을 깨우는 알림도 없다. 예약 확인은 서비스 실행 중이다.
3. **음성:** 실제 마이크의 STT onReady·듣기 창·인식 오류·전화/내비 포커스 중단. TTS 연결 성공과 실제 들리는 출력은 별도로 확인한다.
4. **LLM:** 기본 꺼짐. 선택적 기기 내 실행의 성공률·품질은 미검증이다. 시간 초과·검사 거부 때 고정 서식을 사용한다. 구체적 보존 규칙은 현재 PolishGuard와 SPEC 기준으로 검증한다.
5. **가족:** 실제 모드(`demo=off`)에서 emulator→10.0.2.2 relay→휴대폰 왕복, 만료·철회 삭제·통신 실패. 승인 글에는 STT 인식문·개인정보가 들어갈 수 있으며 비식별화가 아니다. 운영용 HTTPS/계정 인증은 별도다.
6. **UI/저장:** 가로·840×1184dp 분할·라이트/다크·글꼴 확대, 프로세스 종료 복구, 실제 파일 원자 교체, 전체 삭제·보관 만료. 실제 모드는 FLAG_SECURE, 시연은 캡처 가능하다.
7. **개선 소스 재검증:** 새 APK 설치→현재 사용자 CAR_SPEED 허용→`am start -S --es demo visit`→VC 순서 전체. 귀가 질문 대기·약/예약 확인·새 진료 준비 등 이번 소스 변경이 이전 제출 APK에도 있다고 가정하지 않는다.

시트 slide/heater/seat occupancy, 차량 위치 기반 귀가 감지, POI 검색, 자동119전화, 꺼진 차를 깨우는 알림은 구현하거나 검증했다고 주장하지 않는다. 자세한 제품 경계는 SPEC을 따른다.
