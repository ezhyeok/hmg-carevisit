# CLAUDE.md — 현대자동차그룹 차량용 앱 개발 공모전 (Code your App into Pleos App Market)

공모전 자료는 `docs/contest/`에 있다. 공모전 공식 사이트(appdevchallenge.auto-hmg.io), 온라인 설명회 영상, Pleos 개발자 문서(document.pleos.ai)를 2026-11-10(KST)에 수집해 정리한 것이다. 이 파일의 경로는 모두 repo 루트 기준이다.

## 먼저 읽을 파일
1. `docs/contest/00_핵심요약.md` — 일정, 제출물, 심사 기준, 규정 요약 (설명회 내용 반영)
2. `docs/contest/01_공모전/OT_설명회_정리.md` — 온라인 설명회 내용. 홈페이지에 없는 운영 규칙(SDK 승인 불필요, 실차 시연 없음, 생성형 AI 사용 조건, 외부 API 허용, 복수 출품, 혼합 팀 부문 등)이 여기에 있다
3. `docs/contest/01_공모전/접수양식/접수양식_원문.md` — 기획서(=접수양식) 8개 항목 구조. 실제 제출은 같은 폴더의 `contest-application-consent.docx`에 작성
4. `docs/contest/02_Pleos_개발가이드/pleos-connect/getting-started.md` — 프로젝트 생성 → 에뮬레이터 → Vehicle SDK 첫 호출. 단, 예제의 `Vehicle:2.3.1`은 Maven에 없으므로 2.3.2를 쓴다
5. `docs/contest/02_Pleos_개발가이드/INDEX.md` — 전체 가이드 목록
6. `docs/contest/03_SDK_API_레퍼런스/INDEX.md` — SDK별·패키지별 API 명세 (메서드 시그니처, 권한, 콜백). 코드 작성 전에 반드시 여기서 실제 시그니처를 grep해서 확인하고, 존재하지 않는 API를 지어내지 말 것

## 확정된 기술 조건
- 플랫폼: Pleos Connect (Android Automotive OS 기반 인포테인먼트). 앱은 일반 Android 앱 (Kotlin 권장, Java 가능)
- minSdk 28 이상 (차량 사이버보안 요건), Gradle 8.0 이상. 에뮬레이터는 Pleos Connect Emulator v2.1.0 (API 34, Automotive AVD, Cold boot, RAM 4GB)
- 에뮬레이터 이미지: Windows/Linux는 x86_64(`crp-sys-img.xml`), Mac은 arm64(`crp-arm64-sys-img.xml`). 설치 URL과 절차는 `docs/contest/02_Pleos_개발가이드/connect-guide/getting-started/application-development/setup-connect-sdk-emulator.md`
- Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` (로그인 없이 접근 가능)
- SDK 좌표 (Maven 최신 버전, 2026-11-10 확인)
  - `ai.pleos.playground:Vehicle:2.3.2`
  - `ai.pleos.playground:NaviHelper:3.0.5`
  - `ai.pleos.playground:TextToSpeech:3.3.1`
  - `ai.pleos.playground:SpeechToText:3.3.1`
  - `ai.pleos.playground:FusedLocation:1.1.3`
  - 참고: `ai.pleos.playground:LLM:2.1.3.2`도 있지만 API 레퍼런스만 공개됐고 공모전 안내에는 없는 SDK다
- Vehicle SDK 사용 패턴: `Vehicle(context).initialize()` → `vehicle.getXxx()`로 도메인 객체 획득 → `checkXxxCapabilities` / `getCurrentXxx` / `setXxx` / `registerXxx` 호출 → 결과는 onComplete/onFailed 비동기 콜백 → 사용 종료 시 `release()` (콜백이 끝나기 전에 release 금지)
- 권한: AndroidManifest에 `pleos.car.permission.*` 선언. 조회는 `CAR_*`, 제어는 `CONTROL_CAR_*`. 도메인별 권한은 `docs/contest/03_SDK_API_레퍼런스/Vehicle/_misc.md`와 각 API의 `@RequiresPermission` 참고
- AAOS 표준 Car API로 얻을 수 있는 데이터(속도, 기어, 외기온도, 연료레벨 등)는 표준 API를 우선 쓰고, Pleos SDK는 특화 기능에 쓴다 (`docs/contest/02_Pleos_개발가이드/pleos-connect/vehicle-app-planning-guide/pleos-connect-sdk-api.md`)
- 외부 API와 퍼블릭 네트워크 연결 사용 가능 (설명회)

## 개발 환경 메모 (2026-11-11 확인)
- 이 개발 서버에서는 Pleos Connect Emulator를 돌릴 수 없다. 에뮬레이터 실행과 검증은 사용자의 로컬 PC에서 한다
  - Google은 Android Emulator를 linux-x64, macOS(x64/arm64), windows-x64용으로만 배포한다. 이 서버는 aarch64 Linux라 공식 바이너리가 없다 (SDK repository XML 확인)
  - 이 서버 자체가 KVM 게스트 VM(Neoverse-N1)이고 중첩 가상화를 지원하지 않아 `/dev/kvm`이 없다. 하드웨어 가속 없이는 Android 14 Automotive를 실용적인 속도로 돌릴 수 없다
- 에뮬레이터 실행과 빌드는 사용자의 Windows 노트북에서 SSH로 한다. 노트북 작업은 `C:\Users\User\hmg-appdev-challenge` 안에서만 한다 (프로젝트 전용 SDK `sdk\`, `android-home\`, `gradle-home\`). 노트북용 스크립트는 `tools/laptop/` (`sync.sh`로 코드 복사, `start-emulator.ps1`, `run-probe.ps1`)
  - 노트북의 PowerShell 실행 정책이 Restricted라 스크립트는 `-ExecutionPolicy Bypass`로 실행한다
  - 최신 cmdline-tools의 `sdkmanager`는 "Android CLI"로 위임되며(사용 통계 수집이 기본으로 켜짐) Pleos 이미지 경로를 처리하지 못한다. 이미지는 `tools/laptop/install-image.ps1`로 설치한다
  - 노트북에서 Google·Pleos 서버로의 단일 연결 다운로드가 매우 느리다(초당 0.1~0.2MB). 큰 파일은 분할 병렬 다운로드를 쓴다
- **노트북에서 Gradle은 항상 `tools/laptop/gradle.ps1`로 부른다** (`powershell -ExecutionPolicy Bypass -File ...\repo\tools\laptop\gradle.ps1 :app:testDebugUnitTest`). `gradlew.bat`을 SSH로 바로 부르면 GRADLE_USER_HOME이 비어 사용자 홈 `.gradle`에 내려받는다(2026-11-11 실제로 생겨 지웠다). `settings.gradle.kts`가 프로젝트 밖 gradle 홈이면 빌드를 멈춘다. 노트북 `.ps1`은 UTF-8 BOM으로 저장한다(없으면 PowerShell 5.1이 CP949로 읽어 한글 주석이 다음 줄을 삼킨다. `sync.sh`가 확인한다). 작업 트리 여러 개는 `LAPTOP_REPO=repo-이름 sync.sh`로 따로 복사해 빌드한다
- Android 앱의 JVM 단위 테스트도 컴파일할 때는 `android.jar`의 Java API 표면을 사용한다. `com.sun.net.httpserver.HttpServer`처럼 일반 JDK에만 있는 클래스를 쓰면 독립 JVM 어댑터 테스트가 통과해도 `:app:compileDebugUnitTestKotlin`은 실패한다. 로컬 HTTP 테스트 서버는 `java.net.ServerSocket`으로 만든다(2026-11-15 노트북 빌드, `HttpFamilyLinkTest`).
- 노트북 빌드가 **있는 파일을 못 찾는다**(`Unresolved reference`)고 하면 파일이 소스 세트를 옮겨 다닌 뒤(`main` → `debug` 등) 남은 Kotlin 증분 캐시 탓이다(2026-11-13 `DevToolRunner`의 `SdkVerify`). `gradle.ps1 :app:clean :pleos-kit:clean …`으로 한 번 정리하고 다시 빌드한다
- 노트북에서 돌던 것을 모두 멈출 때: 노트북은 `adb emu kill` → `adb kill-server`(에뮬레이터·qemu·netsimd가 함께 끝난다), 서버는 `tools/vc/vc.sh down`(유지 신호와 터널). 프로세스를 `pkill -f`·`pgrep -f`로 찾을 때 명령줄 일부를 그대로 주면 그 명령을 실행하는 셸 자신이 걸려 죽는다(2026-11-11, 11-12 두 번): PID로 죽이거나 `grep "[1]8088"`처럼 자기 자신이 안 걸리는 패턴을 쓴다
- Virtual Console 조작 도구: `tools/vc/vc.sh` (개발 서버에서 실행. `up`으로 시뮬레이터를 켜고 `gear Drive`, `speed 40`, `play 시나리오.vc` 등으로 차량 상태를 바꾼다)
  - 에뮬레이터가 띄운 adb 서버가 에뮬레이터 로그 파일 핸들을 물려받아 계속 잡는다. 그래서 `start-emulator.ps1`은 실행할 때마다 새 로그 파일(`logs\emulator-날짜-시각.log`)을 쓴다
  - 에뮬레이터 이미지가 RAM을 4096MB 이상으로 올린다(`-memory 3072`를 줘도 4096). 노트북 여유 메모리가 1GB 안팎이면 호스트가 스왑을 쓰므로 Edge 같은 프로그램을 닫는 편이 좋다
- 에뮬레이터 성능 (측정 결과). 아래 세 가지를 지키지 않으면 SDK 호출이 수십 초씩 밀리고 `carwatchdogd`가 VHAL을 죽인다
  - **GPU 가속을 쓴다.** `start-emulator.ps1`의 기본값은 `-gpu host`다(SSH로 창 없이 띄워도 GeForce MX450을 쓴다). `swiftshader_indirect`(소프트웨어 렌더링)로 띄우면 에뮬레이터 프로세스가 호스트 CPU의 88%를 쓰고, `host`면 40% 정도다
  - **노트북에 충전기를 꽂는다.** 배터리 모드에서는 CPU가 정격의 43% 수준으로 제한된다. 확인: `(Get-CimInstance Win32_Battery).BatteryStatus`가 2면 AC
  - **충전기가 꽂혀 있어도 전원을 확인한다.** 2026-11-13 00:39~00:40에 전원 연결이 40초 동안 11번 바뀌었다(충전기 접촉 불량, Kernel-Power 105). 그 뒤 배터리가 바닥나 01:26에 노트북이 최대 절전에 들어가 밤새 끊겼다(Kernel-Power 42 `Sleep Reason: Battery`). 유휴 절전이 아니었다. 긴 작업 전에는 `tools/laptop/power-check.ps1`로 AC와 최근 1시간의 전원 변경 수를 본다(`wait-emulator.sh`가 시작할 때 부른다). 최대 절전을 거친 에뮬레이터는 살아 있었지만 게스트 시계가 9시간 건너뛰었다
  - **부팅 후 `tools/laptop/wait-emulator.sh`로 기다린다**(가동 4분 이상, 운전자 프로필 user 10 활성). 그 전에 앱을 실행하거나 빌드하면 VHAL이 재시작되면서 시스템이 user 0으로 돌아가고 내비 앱이 조회에 응답하지 않는다. 그렇게 되면 에뮬레이터를 다시 부팅한다. 빌드는 에뮬레이터를 띄우기 전에 `run-app.ps1 -BuildOnly`로 해 둔다
  - **에뮬레이터가 멈춘 것처럼 보이면 재부팅 전에 `tools/laptop/emu-health.ps1`로 qemu 상태를 본다.** 증상은 `adb devices`에는 보이는데 `adb shell`이 응답하지 않고 qemu CPU가 0%다. 2026-11-11에는 에뮬레이터의 크래시 보고기(crashpad_handler)가 qemu를 덤프하려고 일시 정지시킨 뒤 자신이 죽어서 스레드가 모두 Suspended로 남았다. `emu-health.ps1 -Resume`으로 재개된다(`wait-emulator.sh`가 adb 무응답을 보면 자동으로 부른다). 재발 방지로 `start-emulator.ps1`은 `-crash-report-mode disabled -no-metrics`로 띄운다
  - 노트북 화면에서 창으로 띄우려면(시연 영상 녹화용) `tools/laptop/start-emulator-gui.cmd`를 더블클릭한다
- 에뮬레이터 이미지는 user 빌드다(`adb root` 불가). 프로세스를 죽이는 방법: `am crash <패키지>`(크래시), `am force-stop --user 10 <패키지>`(멈춤, 다시 뜨지 않는다), debug 빌드의 앱은 `run-as dev.carevisit.app --user 10 kill -9 <pid>`(크래시가 아닌 죽음). `run-as`에 `--user 10`을 빼면 user 0의 uid라 앱 프로세스에 닿지 않는다
- 이미 떠 있는 앱에 보낸 `am start … --es …`는 Pleos 홈 화면이 창 다시 띄우기로 처리해 앱의 `onNewIntent`에 닿지 않는다(2026-11-12). 옵션을 주려면 `am start -S`(앱 재시작)로 보내거나, debug 빌드는 `adb shell am broadcast --user current -a dev.carevisit.app.DEV -p dev.carevisit.app --es probe wp`처럼 브로드캐스트로 보낸다
- 에뮬레이터 부하: 평소 load average는 5~7이다. APK 설치 직후나 앱을 연달아 다시 시작하면 20을 넘는다(2026-11-12). 설치·재시작 뒤에는 `tools/laptop/wait-emulator.sh 8`로 가라앉기를 기다리고, 시간을 재는 점검은 그 뒤에 한다
- 2026-11-12 에뮬레이터가 `libgfxstream_backend.dll` 접근 위반(0xc0000005)으로 두 번 죽었다(`-gpu host`, 7시간·2시간 가동). 두 번 모두 앱을 `am start -S`로 10~15번 다시 시작한 뒤였고, 창이 다시 만들어질 때마다 에뮬레이터 로그에 `Failed to find ColorBuffer`가 쌓였다. **앱 재시작은 아껴 쓴다**: debug 빌드의 시연 전환·실험은 브로드캐스트(`am broadcast -a dev.carevisit.app.DEV --es demo holiday`)로 보내면 창을 다시 만들지 않는다. 증상: `adb devices`가 비고 qemu 프로세스가 없다. `start-emulator.ps1`로 다시 띄운다. 다시 부팅하면 내비의 길안내는 사라지고 시각대가 America/Los_Angeles로 돌아올 수 있다(`wait-emulator.sh`가 Asia/Seoul로 맞춘다: `cmd time_zone_detector set_auto_detection_enabled false` + `service call alarm 3 s16 Asia/Seoul`)
- GitHub private repo: https://github.com/ezhyeok/hmg-appdev-challenge (기본 브랜치 `main`)

## SDK 사용 조건 (설명회 기준, Playground 문서보다 우선)
- 공모전 개발 단계에서는 SDK 권한 신청이나 승인이 필요 없다. Vehicle, NaviHelper, STT, TTS, FusedLocation 모두 AndroidManifest에 문서의 uses-permission을 선언하기만 하면 에뮬레이터에서 호출할 수 있다
- Playground 문서(`pleos-connect/create-my-project.md`)의 "준비 중(Coming Soon)" 표기는 App Market 배포용 신청 절차 기준이다. 공모전 개발에는 해당하지 않는다
- Vehicle 제어 API: 에뮬레이터에서 `setHvacRecirculation`(`CONTROL_CAR_CLIMATE`)으로 값이 실제로 바뀌고 변경 이벤트도 온다 (2026-11-11 sdk-probe, Virtual Console 시뮬레이터가 켜진 상태). 시뮬레이터가 꺼져 있으면 성공 콜백만 오고 값은 그대로다. 다른 제어 API는 쓰기 전에 같은 방식으로 직접 확인할 것
- TTS/STT: SERVER·HYBRID 모드는 `registerApp()`에 Playground 프로젝트의 Client ID/Secret이 필요하고, ON_DEVICE 모드는 등록이 필요 없다 (각 SDK의 `app-registration.md`). 막히면 사무국에 문의
- 설명회에서 "현재 공개되지 않은 SDK도 공모전에 제공할 예정"이라고 했지만 구체적인 방식은 안내되지 않았다. 공개된 SDK 기준으로 설계할 것

## API 사용 시 주의 (레퍼런스 확인 결과)
- 차속과 기어는 Vehicle SDK에 없다 (`VehicleSpeedListener`는 2.3.2에서 삭제). AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`, `GEAR_SELECTION`을 쓴다. Virtual Console에 기어 카드는 있지만 차속 카드는 없다
- **앱 코드는 SDK를 직접 부르지 말고 `pleos-kit`을 쓴다.** 아래 규칙(전용 스레드, 포그라운드 서비스, 제어 결과 확인, 내비 요청 간격)이 kit에 들어 있다. kit에 없는 API는 `VehicleKit.call`/`call2`/`observe`로 감싼다
- 에뮬레이터에서 확인한 제어 API: 내기순환, 설정 온도(공조 전원이 켜져 있어야 함), 창문 위치, 실내등, 앰비언트 라이트 밝기, 시트 등받이 이동(`moveSeatBackrestAngle`, 방향 명령만 있고 위치 지정은 없음). 시트 등받이 위치 조회는 에뮬레이터에서 계속 0을 준다
- 주행 시간(`getTripCurrentDrivingTime` 등 Trip 계열)은 에뮬레이터가 지원하지 않는다(`ERROR_NOT_SUPPORTED_PROPERTY`). 주행 시간이 필요하면 앱이 기어·차속으로 직접 잰다
- 가속 페달 값을 주는 API는 Vehicle SDK에 없다. 조향각(`getCurrentSteeringWheelAngle`)과 브레이크 페달 on/off는 있다
- 텔테일 API(`checkTelltalesCapabilities`, `registerTelltales`, `getTelltalesInfo`)는 `PRIVATE_CAR_SAFETY`(signature|privileged) 권한이라 서드파티 앱에서 쓸 수 없다
- `getLowTirePressure`는 현재 공기압이 아니라 저압 경고 임계값(kPa)을 반환한다
- **SDK 호출은 백그라운드 스레드에서 한다.** Vehicle·NaviHelper 호출은 서비스 연결을 기다리며 호출 스레드를 수 초간 막는다(sdk-probe 측정, Vehicle 호출 합계 약 9초). 메인 스레드에서 부르면 ANR로 앱이 종료된다
- `android.car.permission.CAR_SPEED`, `pleos.car.permission.FUSED_LOCATION`은 위험(dangerous) 권한이다. Manifest 선언만으로는 SecurityException이 나므로 실행 중에 `requestPermissions`로 요청한다. adb로 줄 때는 AAOS 포그라운드 사용자(user 10)에게 줘야 한다: `adb shell pm grant --user current <패키지> <권한>`
- NaviHelper `requestRoute`를 보내면 Pleos 내비 앱이 포그라운드로 올라오고 실제 길안내가 시작된다. 경로 계산은 서버에서 하므로 네트워크가 필요하다
- **내비가 떠 있는 동안에도 이벤트를 받으려면 포그라운드 서비스가 필요하다.** Activity만 있는 앱은 내비가 앞으로 오면 cached 프로세스가 되어 Android 14 freezer에 얼려지고(logcat `ActivityManager: freezing <pid> <패키지>`), 그동안 Vehicle·NaviHelper 이벤트를 받지 못한다. 포그라운드 서비스에서 리스너를 유지하면 받는다 (sdk-probe `ProbeService`로 확인)
- NaviHelper 정보 조회(`getTBTInfo`, `getWaypointInfo`, `getDestinationInfo`, `getRouteStateInfo`, `getChargerOperatorInfo`)는 호출해야 응답 이벤트가 온다. 길안내 중 `onTBTInfo`는 앞으로의 회전 목록(type, distance, description)을 준다. `onRouteCancelled`, `onNavigationStatus`, `onDrivingInfo`, `onCurrentRoadSpeedLimit`는 내비가 먼저 보낸다. `onRouteStarted`는 내비 앱 로그에 `notifyRouteStarted`가 찍혀도 앱에서 받지 못했다(원인 미확인). 길안내 여부는 `RouteStateInfo.naviMode`가 `ROUTE_GUIDING`(내비 모의 주행이면 `SIMULATION_GUIDING`)인지로 판단할 것. `SAFE_GUIDING`은 경로 없는 안전운전 모드다(경로 전 상태, `NaviPoll.isGuiding`)
- `requestTrip(TripRequest)`는 길안내를 시작하지 않고 `onTripInfo`로 거리(m), ETA(초), 도착 시 예상 SOC(%), 소모 에너지, 구간별 교통 정보를 준다 (성남→서울역: 27.5km, 약 31분, SOC 95%)
- 내비 요청을 1초 안에 연달아 보내면(trip, route, waypoint) 시작된 경로가 곧바로 취소되는 일이 있었다. 요청 사이에 응답을 기다릴 것
- **TBT(`getTBTInfo`)에 경로 위 휴게소(`TURN_ENTER_SERVICEAREA`)와 졸음쉼터(`TURN_RESTAREA`)가 이름과 함께 온다. 거리(`TBTInfo.distance`)는 직전 항목부터의 거리라서 누적해야 경로 거리가 된다** (2026-11-11 확인: 경유지 누적 47.1km = 내비 경유지 남은 거리 47.0km)
- 정체 구간(`RouteStateInfo.trafficStatus.congestionItemList`)은 길안내 중 실제로 온다. `trafficCongestionTime` 단위는 초. `offset`의 기준(출발지/현재 위치)은 미확인
- **정체 시간(`trafficCongestionTime`)은 통과 시간이 아니다** (2026-11-12 에뮬레이터, 판교 출발 경로, `KitNaviPort`의 `congestion:` 로그): 분 단위로 반올림한 늘어난 시간이다(최소 1분). 항목마다 30~100m 구간도 모두 60초였고 통과 시간(거리÷`avrSpeed`(km/h))은 1~16초였다. 요약(`TrafficCongestionSummary`)도 3,200m·45.0km/h → 120초, 3,300m·46.1km/h → 120초, 5,900m·34.9~35.2km/h → 300초로 통과 시간(256·258·604초)의 약 절반(자유 흐름 70~85km/h 대비 늘어난 시간)이다. 그래서 앱은 엔진에 넣는 정체 시간(`CongestionSegment.durationS` = 지나는 시간)을 거리 ÷ 평균 속도로 구한다(`NaviCongestion.crossingS`, 평균 속도가 없으면 자유 흐름 80km/h + 늘어난 시간, 상한 6시간). 원래 값은 `NaviCongestion.delayS`에 남긴다
- 길안내 중 `requestTrip`은 경로를 방해하지 않는다. 휴게소 좌표로 `addWaypoint` 하면 가는 방향 휴게소로 잡힌다
- `adb emu geo fix`는 FusedLocation만 바꾸고 Pleos 내비의 위치는 바꾸지 않는다 (내비는 판교 고정)
- 조향각 리스너 값은 Virtual Console 값과 부호가 반대이고, 변화가 없어도 약 3초마다 온다
- Pleos LLM SDK 2.1.3.2: ON_DEVICE 모드는 에뮬레이터에서 응답하지 않았다(40초). 문서의 `getOnDeviceModelPromptsContents`는 jar에 없다
- **경유지(`addWaypoint`)** (2026-11-12 에뮬레이터, 판교→부산역 실제 경로, `SdkProbe wp-add`): ① 경유지는 둘까지고, 둘이 차 있으면 내비가 요청을 조용히 버린다(이벤트도 오류도 없다) ② `waypointIndex`와 상관없이 **이미 있는 경유지 뒤에 붙는다**: 옥산(101km) 뒤에 안성(47km)을 넣으니 [옥산 101km, 안성 177km]가 됐다(갔다가 되돌아온다) ③ 경유지 목록(`WaypointInfo`)의 좌표는 0.0이고 보낸 `poiName`이 `name`으로 돌아온다 ④ 넣는 동안 길안내가 취소됐다 다시 시작되고(약 14초) 그 사이 목록은 비어 있다. 순서를 지키려면 `requestRouteWithWaypoints`로 목적지와 경유지를 순서대로 다시 정한다(`NaviKit.setWaypoints`, 목적지 좌표는 `getDestinationInfo`가 준다). 이 앱은 목적지 조회·도착 감지와 저장된 병원 길안내를 사용한다
- **STT 인식은 녹음 입력(`sendAudio`)으로 확인했다** (2026-11-13). 에뮬레이터를 `-no-audio`로 띄우므로 마이크로는 볼 수 없다. Windows 한국어 음성(Heami)으로 만든 16kHz·16bit·mono 녹음을 ON_DEVICE로 넣으면 0.8~2.8초에 결과가 온다: "응"→'응', "네, 들를게요"→'네 들을게요', "아니, 괜찮아"→'아니', "화장실 급해"→'화장실 급'(끝 글자가 잘린다. 해석기가 급해로 읽게 했다, `IntentParserTest.whatTheEmulatorSttActuallyReturnedIsUnderstood`). 점검: `tools/laptop/stt-pcm-check.sh`(debug 빌드, `SdkProbe stt-pcm`). debug와 release는 서명이 달라 바꿔 깔려면 `adb uninstall dev.carevisit.app` 먼저
- TTS/STT 이벤트에는 요청 번호가 없다(API 레퍼런스). 2026-11-12 에뮬레이터(`SdkProbe tts`/`stt`): TTS는 `stop()`을 부르면 말하는 중이 아니어도 `onStop`이 오고(약 10ms), 말하는 중이면 약 15ms 뒤에 온다. STT는 `request()` 뒤 0.2~10초에 `onReady`가 오고(요청마다 다르다: 시연 여덟 번에서 0.2~10.3초, 부하가 높을 때 길다) 그 전에 `stop()` 하면 `onEndedRecognition`이 오지 않는다. STT 서비스는 `onReady` 때 녹음을 시작한다(서비스 로그 `AudioRecorderImpl start`). 듣는 시간을 request부터 재면 6초 듣기가 1초 남짓이 되므로 kit은 `onReady`부터 잰다(`listenWindow`, `ListenWindowTest`, 15초 안에 열리지 않으면 포기. 고친 뒤 서비스 로그의 녹음 횟수 10 → 54). 화면의 '듣는 중'도 그때 켠다(`VoiceAgentTest`). 그래서 kit은 요청을 하나씩 보내고 앞 요청의 끝남 이벤트를 받은 뒤 다음을 보내며, 쉬는 SDK에는 stop을 보내지 않는다(`pleos-kit` `Turns.kt`, `TurnsTest`)
- Pleos 차량 서비스(`ai.pleos.playground.service.vehicle`)가 죽었다 살아나면(`am crash`) Vehicle SDK 2.3.2가 스스로 다시 붙고 걸어 둔 리스너를 모두 다시 걸어 현재 값을 한 번씩 준다(약 1.4초). 서비스가 멈추면(`am force-stop`) SDK는 다시 붙지 않는다: 이벤트가 끊기고 조회는 답이 없다(예외도 `onFailed`도 없이 kit의 20초 시간 초과). 앱은 30초마다 값을 다시 읽다가 모두 실패하고 이벤트도 없으면 `VehicleKit.reconnect()`로 새로 붙는다(약 1분, coach `SignalCollector`, `tools/laptop/signals-check.sh`)
- Vehicle 2.3.2 jar에는 문서의 `VehicleException`이 난독화된 이름(`callback.exception.a`)으로 들어 있어 `errorCode`를 타입으로 쓸 수 없다. 메시지에 오류 코드 이름이 들어 있다(`ERROR_NOT_SUPPORTED_PROPERTY`): `VehicleKit.notSupported(e)`
- `CarPropertyManager.registerCallback`은 읽을 권한이 없는 속성에 예외 없이 false를 돌려준다(CAR_SPEED를 받기 전의 차속). 반환값을 보지 않으면 차속이 로그 없이 빈다(`CarState.speedMps()`가 예외로 바꾼다)
- `Car.createCar(context)`로 만든 연결은 Car 서비스가 죽으면 플랫폼이 앱 프로세스를 죽인다. `createCar(Context, Handler, long, CarServiceLifecycleListener)`(android.car.jar, API 30+)는 죽이지 않고 알려 준다(`CarState.connected`). 한 프로세스의 Car 연결이 모두 이 방식이어야 한다(화면의 `UxRestrictionWatcher`는 아직 `createCar(context)`다)
  - 2026-11-12 에뮬레이터에서 Car 서비스를 죽여 봤다(`am crash <com.android.car의 pid>`, `ro.car.recovery.restart_runtime.enabled`가 꺼져 있어 시스템은 다시 시작하지 않는다): 플랫폼이 `UxRestrictionWatcher.kt:83`에서 만든 연결을 이유로 앱 프로세스를 죽였고(logcat `CAR.L: Car service has crashed. Client(null) is not handling it` + 만든 곳의 스택), 서비스는 10초 뒤 `onTaskRemoved` 경로로 다시 시작했다. Car 서비스는 죽은 뒤 약 23초 동안 준비되지 않는다: `car failed: … car service not ready in 5000ms (retry in 2s)` → `car: connected`. 그때 CarServiceHelper가 VHAL도 죽여서 Vehicle 기능 확인이 한 번 시간 초과(20초)된 뒤 다시 시도에서 붙었다. 시스템 UI와 Pleos 앱 여럿이 함께 다시 시작하지만 user 10은 유지되고 재부팅은 필요 없었다
- 내비가 죽을 때(2026-11-12, 부산역 길안내 중): 내비 앱(`ai.umos.maps.android.navigation.app`)을 `am force-stop` 하면 내비가 스스로 다시 뜨지만(앞 화면으로 온다) 경로는 사라진다. 앱은 조회 한 번이 15초 시간 초과된 뒤 30초 안에 `ROUTE_GUIDING -> SAFE_GUIDING`을 보고 주변 후보로 돌아간다. NaviHelper가 붙는 서비스(`ai.pleos.playground.service.navi`)를 `am force-stop` 하면 SDK 3.0.5가 1.4초 뒤 스스로 다시 붙고(`onBindingDied` → `onServiceConnected` → `registerCallbackV3`) 걸어 둔 리스너와 조회가 그대로 된다
- **앱이 화면에 떠 있다가 죽으면** 시스템이 화면을 닫고 태스크를 지운 뒤 START_STICKY 서비스를 다시 만드는데, 다시 만든 서비스에는 `onStartCommand` 없이 `onTaskRemoved`만 온다(2026-11-12 `am crash`). `PleosForegroundService`는 `onTaskRemoved`에서도 시작한다. 화면 없이 서비스만 돌다 죽으면(`kill -9`) `onStartCommand(null)`이 오고 `startForeground`도 허용됐다(`tools/laptop/restart-check.sh`)
- 서비스가 끝나는 순서(리스너 해제 → SDK release → 여정 파일 마지막 저장)는 기기에서만 볼 수 있다: `tools/laptop/shutdown-check.sh`(debug 빌드). 이 이미지에서 `am stopservice`는 exported가 아닌 서비스에 닿지 않아(`Error stopping service`) `SdkProbe stop-self`로 끝낸다
- 개발용 점검(`KitSelfTest`, `SdkVerify`, `SdkProbe`)은 debug 빌드에만 있다(`app/src/debug`). 제출용(release)이 받는 인텐트 옵션은 `--es demo` 하나다(`DevHooks`, `LaunchExtras`). 제출용 APK를 만들면 `tools/laptop/devhooks-check.sh`를 돌린다(2026-11-12: 예전 release는 `--ez selftest true`로 좌석·창문을 움직였다)
- `onCurrentRoadSpeedLimit`(NaviHelper)는 리스너를 건 뒤 매초 온다. 에뮬레이터 내비는 판교에 서 있어 값이 늘 0(모름)이다(sdk-probe 2026-11-10 로그). 앱은 0을 모름으로 보고, 실차에서 70 이상이면 도시고속도로·자동차전용도로로 보아 길가 화장실(일상 정차 후보)을 내지 않는다(`NaviTracker.roadLimit`, `NearbyStops`). 시연은 이 값을 쓰지 않는다(`CoachWorld.nearbyMode`)
- NaviHelper에는 POI 검색 API가 없다. 목적지·경유지의 좌표와 poiId는 앱이 외부에서 구해야 한다. 요청은 Pleos 내비 앱으로 전달되고, 어떤 이벤트가 오는지는 내비 앱 구현에 따라 다르다
- 에뮬레이터에서 재현할 수 있는 차량 상태는 `docs/contest/02_Pleos_개발가이드/pleos-connect/app-test-guide/virtual-console-guide.md`의 카드 목록이 기준이다
- Virtual Console은 에뮬레이터 8088 포트의 HTTP API로도 조작할 수 있다 (`adb forward tcp:8088 tcp:8088`). 시연 시나리오를 스크립트로 재생할 수 있다
  - 차량 시뮬레이터는 브라우저 세션이 있을 때만 켜진다. 페이지를 열어 두거나 `POST /browser/session?client=<이름>`을 1초마다 보낸다(3초 동안 없으면 꺼짐). 꺼져 있으면 제어 API 결과와 VC 변경이 반영되지 않는다
  - `GET /vehicle/catalog`(조작 가능한 63개 항목과 요청 형식), `GET /vehicle/state`(현재 상태). 변경은 PUT + 쿼리스트링. 예: `/vehicle/gear?gear=Drive`, `/vehicle/controls?speed=40&accelerator=20&brake=0`, `/vehicle/body?door_fl=true`, `/vehicle/hvac?recirc=false`, `/vehicle/odometer?usage_time_min=120`
  - 문서의 카드 목록에는 차속이 없지만 `/vehicle/controls`로 차속을 바꿀 수 있다. 앱에서 확인한 것: 기어 D + 40km/h → `GEAR_SELECTION`=8, `PERF_VEHICLE_SPEED`=11.1m/s, `CarUxRestrictions` requiresDO=true(active=0x50). P로 돌리면 해제. 도어 열림·닫힘도 `registerDoorPosition` 리스너로 온다
  - 좌석 점유는 VC 상태(모두 비어 있음)와 SDK 조회값(대부분 true)이 달랐다. 에뮬레이터에서는 좌석 점유 값을 믿을 수 없다
- 주행 규제 테스트: `adb shell cmd hmg_car_service --service drivingstate driving_regulation on` / `off` (`docs/contest/02_Pleos_개발가이드/pleos-connect/vehicle-app-developer-guide/adb-command-reference-guide.md`)

## 설계·구현 원칙
- 실차 시연은 없다. 모든 검증과 2차 PT 시연은 Pleos Connect Emulator + Virtual Console 기준으로 준비한다 (기어, 도어, 배터리 잔량 등 상태를 바꿔가며 앱 반응을 보여줄 수 있게)
- SDK를 많이 쓰는 것이 목적이 아니다. 차량 환경에서만 줄 수 있는 핵심 가치 하나를 정하고, 그 가치를 SDK로 실제 동작하게 만드는 것을 우선한다
- 차량 모델별로 지원 API가 다를 수 있으므로 항상 capability를 확인한 뒤 UI를 활성화한다 (`docs/contest/02_Pleos_개발가이드/pleos-connect/vehicle-app-planning-guide/api-compatibility-availability.md`)
- 주행 중 UX 제한: `CarUxRestrictions` 연동 필수 (`docs/contest/02_Pleos_개발가이드/pleos-connect/vehicle-app-developer-guide/driving-restriction-development-guide.md`, `docs/contest/02_Pleos_개발가이드/pleos-connect/vehicle-app-planning-guide/driver-distraction-guideline.md`). 심사 항목 "차량특화성"과 직결된다
- 로터리 컨트롤러(CCP), 오디오 포커스, IME 리사이즈 같은 차량 특화 동작은 `docs/contest/02_Pleos_개발가이드/pleos-connect/vehicle-app-developer-guide/` 참고
- UI는 `docs/contest/02_Pleos_개발가이드/connect-guide/docs-design/`의 Pleos Connect UI Design Guide(레이아웃, 컬러, 타이포, 컴포넌트)를 따른다
- 화면·인텐트가 서비스에 보내는 명령은 `CoachStore`의 Channel로 보낸다. 구독 전 값을 버리는 SharedFlow(replay 0)를 쓰면 첫 실행 때 `--es demo` 명령이 서비스의 collect보다 먼저 와서 사라진다(2026-11-11, `CoachStoreTest`가 지킨다). 앱 JVM 테스트는 노트북에서 `gradlew :app:testDebugUnitTest`
- 성능 개선은 결과가 같다는 테스트와 함께 한다(데이터: StopTableTest, 내비 조회 간격: NaviPollTest). 측정은 에뮬레이터에서 `top -p`, `dumpsys meminfo`, `dumpsys gfxinfo`, logcat의 내비 요청 수
- 버그를 고칠 때는 같은 문제가 다시 생기지 않게 테스트·점검 스크립트·이 문서 중 맞는 곳에 막는 장치를 함께 남긴다
- 생성형 AI 사용은 허용되지만, 참가자가 2차 PT 기술 Q&A에서 구조와 로직을 직접 설명해야 한다. 코드를 짤 때 아키텍처와 주요 흐름을 `docs/ARCHITECTURE.md` 같은 문서로 함께 정리한다. 참가자는 전산 전공이므로 구조는 설명하기 쉬운 쪽이 아니라 기술적으로 적절한 쪽으로 정한다

## 참가·제출 (공모전 규정 + 설명회)
- 이 프로젝트는 개인으로 출전한다. 숏폼 링크는 내지 않는다. 기획서는 repo 루트의 `contest-application-consent.docx`에 작성한다 (개인정보가 들어가므로 커밋하지 않는다)
- 부문: 대학생 / 일반(스타트업 포함). 일반인이 한 명이라도 있는 팀은 일반 부문, 스타트업을 운영하는 대학생은 대학생 부문 (설명회)
- 서로 다른 작품을 개수 제한 없이 여러 개 출품할 수 있다. 작품은 앱명으로 구분한다 (설명회)
- 필수: 접수양식(기획서) docx + 참가자 전원 서명 동의서. 마감 2026-11-15(일) 23:59 KST, 이메일 appdevchallenge@hyundai-partners.com, 참가자 전원 CC
- 메일 제목: 홈페이지는 "[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명", 설명회는 앱명을 포함하라고 안내. 복수 출품이면 앱명을 반드시 넣을 것
- 선택(가산점): 시연 영상(목업, 프로토타입, 에뮬레이터 화면 녹화 인정), GitHub 저장소(소스 + APK)
- GitHub 저장소 규칙: APK는 저장소의 `apk/` 폴더에, 파일명은 `팀명_앱이름_release.apk`, 실행 가능한 release 빌드. 용량을 초과하면 GitHub Releases 링크
- Private 저장소도 제출할 수 있고 Collaborator 초대는 필요 없다(2026-11-15 지원·제출 방법 페이지 기준, 예전 안내를 대체). 대신 **접수 마감 후 11월 16일(월)까지 Public으로 전환하고 11월 23일(1차 발표)까지 유지**해야 가산점이 반영된다. 공개가 어려우면 소스+APK 압축 파일 내려받기 주소를 마감 전에 메일로 보낸다
- 접수 메일에 저장소 주소와 최종 커밋 해시를 적는다. 심사는 그 커밋 기준이다
- README.md 필수 내용: 앱 이름과 한 줄 소개 / 실행 방법 / 테스트 환경과 필요한 설정 / 개발에 사용한 에뮬레이터 버전과 화면 규격 / APK 파일 위치 또는 Release 링크
- 2차 PT(2026-12-03~04, 오프라인) 대상 20팀은 실제 동작하는 앱 시연 필수. 아이디어 특별상을 포함한 모든 수상작은 개발 산출물이 있어야 한다
- 기획서 02 "앱 프로젝트 소개"와 공유 링크, 첨부자료에 개인정보, 실명, 계정정보 노출 금지
- 표절이나 다른 공모전 수상작은 금지. 오픈소스를 쓰면 라이선스를 지키고 출처를 표기

## 심사 기준 (1차, 세부 배점 비공개)
차량특화성 / 창의성 / 기술 활용성(Pleos Playground SDK·API 활용) / 사업성(타깃, 예상 MAU, Retention, 확장 계획) + 개발 산출물 가산점

## 폴더 구조 (`docs/contest/`)
- `00_핵심요약.md` 일정·제출물·심사·규정 요약
- `01_공모전/pages/` 공모전 사이트 전체 페이지 원문 (가이드 8종, FAQ, 공지 3건, 약관, 개인정보방침)
- `01_공모전/OT_설명회_정리.md` 온라인 설명회 정리
- `01_공모전/접수양식/` 원본 docx와 텍스트 변환본
- `01_공모전/images/` 포스터, 설명회 배너, 숏폼 예시 이미지
- `02_Pleos_개발가이드/` document.pleos.ai 가이드 원문 (pleos-connect = Pleos Connect 개발자 가이드, connect-guide = 에뮬레이터·UI 디자인 가이드)
- `03_SDK_API_레퍼런스/` Vehicle / NaviHelper / SpeechToText / TextToSpeech / FusedLocation / LLM SDK API 명세 (패키지별 1파일)

`pages/`, `02_Pleos_개발가이드/`, `03_SDK_API_레퍼런스/`는 수집 원문이므로 수정하지 않는다. 각 파일 상단 주석에 원문 URL이 있다. 문서가 바뀌었을 수 있으니 중요한 결정 전에는 원문과 공모전 공지사항을 다시 확인할 것.

## 이 앱: 병원 방문 준비·기록
- package/applicationId `dev.carevisit.app`, core package `dev.carevisit.core`. 앱 이름의 원본은 `app/src/main/res/values/strings.xml`의 `app_name`, Android 상수는 `AppIdentity.NAME`(resource id)이다. 문서 이름은 `tools/laptop/update-label.py`로 동기화한다.
- Android SDK 없는 서버: `./gradlew -p visit-core test`. 독립 Kotlin composite build이며 Android/SDK 의존성이 없다.
- `VisitCore` 우편함 액터 → 순수 `VisitMachine` 전이 → 동의·기록의 원자적 저장 → 음성/relay/내비/공백 정리 효과. 저장 실패 시 효과를 실행하지 않는다.
- 진단·중증도·약·용량 제안 금지. 모든 슬롯·예약 원문에 발화 ID가 있다. 정해진 질문·템플릿만 사용한다. LLM은 기본 끔, ON_DEVICE 8초 제한, 공백 이외의 모든 문자·기호와 토큰 순서를 보존하는 제한된 공백 정리만 허용한다.
- 민감정보 수집 동의와 가족 공유 동의를 분리한다. 메모와 브리핑은 보낼 때마다 원문을 읽고 확인한다. 기본 180일(30/90/180/365일), 시작·틱·설정 변경 시 삭제. 전체 삭제는 동의도 철회한다.
- 앱은 가족 relay에 승인한 메모/브리핑과 고정 상태만 보낸다. 위치·대화 원본은 보내지 않는다. 토큰은 128비트이며 relay 데이터는 메모리에서 24시간 후 만료한다.
- 목적지 이름 키워드/저장 병원 또는 수동 시작. D에서 차속 0.5m/s 초과로 60초 움직인 뒤 한 번 질문한다. 주차 중 명시적으로 준비 시작도 가능. 도착+P, 동승석 오른쪽 앞문 열림으로 하차 안내. 병원 정차 15분 이후 P→D에서 귀가 대기에 들어가고, D+움직임 누적60초 뒤 귀가 질문.
- 차량 제어·좌석 점유·FusedLocation은 제품에서 사용하지 않는다. 내비 도착 이름/주소와 현재 목적지·방문 병원의 일치, 30초 내 P 또는 주차 중 수동 도착 확인을 사용한다. 근접200m+경로 종료 추론은 제거했고 변경/취소/오류는 후보를 폐기한다. 에뮬레이터의 실제 목적지 이름·도착 이벤트 전달은 새 앱에서 검증 필요.
- 화면은 `UxRestrictionWatcher`와 서비스의 기어/제한을 함께 확인한다. 모름·주행 상태는 짧은 질문/진행/음성 상태만 보여 준다. 긴 메모·기록·설정은 P이고 제한 해제일 때만. CCP 미지원 선언.
- 시연: `adb shell am start -n dev.carevisit.app/.MainActivity --es demo visit`. 실 TTS 뒤 Scripted SpeechPort 응답, 화면에 “시연: 미리 정한 대답 · 가족 답장 모의”. 실제 기어·도어·제한 유지, 진료 15분은 시연 다음으로 진행. 시연 기록은 메모리에만 있고 실제 기록과 분리된다.
- 시연 가족 연결은 `DemoFamilyLink`만 사용한다(HTTP 호출 없음). 화면에서 시연 공유 동의 후 기존 개별 전송 확인을 거쳐 보내면 3초 후 “잘 받았어”, ParkingMode의 시연 답장 명령/다음 단계에서 “나왔어”를 받는다. 동의를 자동 승인하지 않고 종료/철회 때 메모리를 지운다. API는 ARCHITECTURE의 UI 계약, 회귀 검증은 `DemoFamilyLinkTest`.
- 시연 캡처만 허용한다. `VisitRepositories`는 시연에서 실제 저장소를 열지 않고, 모드 전환은 빈 ViewState 발행·화면 key 교체·이전 화면 명령 거절로 실제 archive/입력값의 유입을 막는다. 실제 화면은 FLAG_SECURE 적용 확인 뒤에만 노출한다. 진입/종료의 캡처 회귀 검증은 `DemoPrivacyTest`와 노트북 화면 녹화로 확인한다.
- Windows 저장소: `C:\Users\User\hmg-appdev-challenge\repo-visit`. `tools/laptop/sync.sh` 기본 대상도 repo-visit이다. PowerShell은 UTF-8 BOM. 다른 앱의 저장소를 수정하지 않는다.
- 노트북 빌드·서명·시연 체크리스트: `tools/laptop/README.md`, SDK 근거: `docs/SDK_AUDIT.md`, PT 구조: `docs/ARCHITECTURE.md`, 제품 사양: `docs/product/SPEC.md`.
- 위 일반 SDK 관찰에 등장하는 이전 앱 실험명·스크립트는 관찰 출처다. 그 실험을 이 앱에 넣었다는 뜻이 아니다. 이전 앱 전용 probe/시나리오 도구는 삭제했다.
- 새 동작은 실패 테스트 → 구현 → 통과. 최종 테스트는 -q 금지. 이 서버에서 Android 빌드/에뮬레이터 검증을 했다고 주장하지 않는다. 커밋은 사용자만 한다.
