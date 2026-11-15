# Windows 빌드와 Pleos 시연
<!-- app-label: 진료동행 -->

기본 저장소는 `C:\Users\User\hmg-appdev-challenge\repo-visit`, 패키지는 `dev.carevisit.app`이다. SSH 별칭 `hmg-laptop`은 기존 노트북 연결 설정을 사용한다. Python 3.9 이상, Android 빌드용 JDK/SDK, Pleos 이미지와 프로젝트용 Gradle 홈이 필요하다.

## 제출 APK로 바로 시작

Android 빌드·release APK·Pleos Emulator 시연은 LEAD가 2026-11-15 검증했다. 소스 빌드 없이 확인하려면 노트북 저장소 루트에서:

```sh
adb install -r "apk/차곡_진료동행_release.apk"
adb shell pm grant --user current dev.carevisit.app android.car.permission.CAR_SPEED
adb shell am start -S --user current -n dev.carevisit.app/.MainActivity --es demo visit
```

제출 파일 해시·검증 범위는 [README](../../README.md)와 [SDK_AUDIT](../../docs/SDK_AUDIT.md)에 있다. 아래 소스 개선을 다시 빌드하면 APK 재검증도 필요하다.

## 복사·빌드·설치

1. 서버에서 다음 명령으로 복사한다. `.ps1`의 UTF-8 BOM을 먼저 검사하며 `repo-visit` 계열 폴더만 허용한다. 노트북의 `local.properties`, `keystore.properties`, 키 파일과 build 캐시는 보존한다. 소스는 `/MIR`로 맞추므로 해당 대상 폴더의 로컬 소스 수정은 덮어쓴다.

   ```sh
   LAPTOP_REPO=repo-visit tools/laptop/sync.sh
   ```

2. 노트북 repo-visit 루트에서 **에뮬레이터를 띄우기 전에** 빌드한다. `gradle.ps1`은 자기 저장소를 찾아 공용 프로젝트 폴더 안의 SDK와 gradle-home을 사용한다.

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\laptop\gradle.ps1 :app:testDebugUnitTest :pleos-kit:testDebugUnitTest :app:assembleRelease
   ```

3. 서명은 노트북 루트 `keystore.properties`에 `storeFile`, `storePassword`, `keyAlias`, `keyPassword`를 준비한다. 없으면 unsigned release이므로 제출하지 않는다. `app/build/outputs/apk/release/app-release.apk`의 서명을 확인하고 `apk/차곡_진료동행_release.apk`로 복사하고 README·SDK_AUDIT의 해시를 갱신한다. 비밀 파일은 서버 복사 대상에서 제외된다.
4. 충전기를 연결하고 `power-check.ps1`로 전원을 확인한다. `start-emulator.ps1` 또는 창이 필요한 경우 `start-emulator-gui.cmd`를 실행한다. GPU host, cold boot, RAM 4GB, `-no-audio` 설정이다. 서버의 `tools/laptop/wait-emulator.sh`가 user 10, 부팅 후 4분, 부하, Asia/Seoul을 점검한다.
5. 노트북에서 설치한다. debug/release 서명이 다르면 자동 제거하지 않는다. 필요할 때 저장 기록 삭제를 감수하고 직접 uninstall 후 설치한다.

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\laptop\run-app.ps1 -Release -SkipBuild -Demo
   ```

Pleos Connect Emulator v2.1.0, API 34, **2560×1440 가로, 160dpi** 기준이다. `create-avd.ps1`은 기존 프로젝트의 Pleos 장치 정의를 사용한다. `install-image.ps1`은 기존 v2.1.0 다운로드의 고정 크기/SHA-1을 검사하는 재설치용 도구이며, 사전에 `scripts/pleos-package.xml`이 필요하다. 생성에는 `make-pleos-package-xml.py <저장소 XML> <출력 XML>`을 사용한다. 이미 설치된 환경에서는 재실행할 필요가 없다.

## 시연 시작 확인

서버에서 설치된 앱을 한 번 재시작하고 현재 앱 PID의 로그를 검사한다. 로그 전체를 지우지 않는다.

```sh
tools/laptop/demo-smoke.sh
# adb가 있는 로컬 Bash에서 직접 검사할 때:
# ADB=/path/to/adb tools/laptop/demo-smoke.sh --local
```

실행 인텐트는 `am start -S --user current -n dev.carevisit.app/.MainActivity --es demo visit`이다. 이미 떠 있는 앱에 extra가 전달되지 않는 Pleos 창 동작 때문에 `-S`를 사용한다. 성공 기준은 `CareVisit` 태그의 정확한 `demo started: visit` 메시지다. 이 검사는 시연 코어 시작만 확인하며 TTS·전체 방문·relay 성공을 대신하지 않는다. SSH 호스트는 `LAPTOP_SSH_HOST`로 바꿀 수 있다.

## 실제 모드의 노트북 가족 relay

**`--es demo off` 실제 모드에서만** 아래 HTTP 경로를 검증한다. `demo visit`은 DemoFamilyLink 메모리 연결이며 relay·휴대폰을 쓰지 않는다. 노트북에서 별도 PowerShell 창을 열어 실행한다. Python 표준 라이브러리만 사용하며 파일에 기록하지 않는다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\laptop\run-family-relay.ps1 -Port 8765
# 에뮬레이터만 사용할 때 LAN 공개를 줄이려면 -BindAddress 127.0.0.1
```

기본 bind는 `0.0.0.0`이다. Windows 방화벽에서 **사설 네트워크의 TCP 8765**만 허용하고, 휴대폰과 노트북을 같은 Wi-Fi에 둔다. 스크립트가 방화벽을 자동 변경하지 않는다. Wi-Fi의 기기 간 통신 차단도 확인한다. 포트를 바꾸면 앱 URL과 휴대폰 URL도 함께 바꾼다.

- 차 설정 URL: `http://10.0.2.2:8765`. 10.0.2.2는 에뮬레이터에서 노트북을 가리킨다.
- 앱의 민감정보 동의 후 가족 공유에 별도로 동의한다. 시연 모드에서 실제 relay를 연결했다고 가정하지 않는다.
- 휴대폰: `http://<노트북 LAN IP>:8765/`를 열고, 차의 전체 가족 주소에서 `#` 뒤 32자리 연결 코드 입력. 또는 `http://<노트북 LAN IP>:8765/#<코드>`로 연다. 차의 짧은 8자리 확인 코드는 접속용이 아니다. 차에 표시된 10.0.2.2를 그대로 휴대폰에서 쓰면 연결되지 않는다.
- 메모는 공유 버튼 → 원문 확인 → 이번 전송 동의 뒤 표시된다. 브리핑도 별도 확인 후 전송한다. 상태·“나왔어”·“잘 받았어”만 추가 교환한다.
- 최초 메시지부터 24시간이 지나면 새 가족 연결이 필요하다. relay 종료·재시작 시 데이터가 사라진다. 실제 서비스에는 **HTTPS와 실제 사용자 인증**이 필요하다.

자세한 제한과 HTTP 계약: [가족 relay 프로토콜](../family/PROTOCOL.md).

## Virtual Console 시연 순서

서버에서 `tools/vc/vc.sh up`을 먼저 실행한다. ADB forward와 SSH 터널, 1초 heartbeat를 유지해야 차량 신호가 전달된다. 각 파일은 `vc.sh play <파일>`로 실행하며, 실패한 요청이 있으면 멈춘다. `sleep`과 실제 지원 명령 `gear`, `speed`, `door`만 사용한다.

1. `tools/vc/vc.sh play tools/vc/visit-drive.vc`: 문 닫기, P, 속도 0 → D, 40km/h, 65초. 동의와 인터뷰를 기다린다. 실제 TTS 뒤 1.8초 후 가상 STT 답이 들어오며 화면에 정해진 STT 문자열을 사용한다. 새 UI는 “시연: 미리 정한 대답 · 가족 답장 모의”로 구분한다. 이전 APK의 “미리 녹음한 대답”도 녹음 파일이 아니다. `-no-audio` 환경에서 마이크 인식이나 실제 들리는 출력을 시험한 것은 아니다.
2. **메모 완성 후** `tools/vc/vc.sh play tools/vc/visit-dropoff.vc`: 속도 0, P → 동승석 오른쪽 앞문 열기. 주차 상세 화면에서 메모·질문 체크리스트와 메모 공유 확인을 보여 준다. 필요하면 UI의 도착 확인을 사용한다.
3. `tools/vc/vc.sh play tools/vc/visit-wait.vc`: P 유지, 문 닫기. 앱에서 주차 후 합류를 선택하고 시연 가족 공유 동의·개별 전송 확인을 한다. 시연 패널의 “시연 답장 · 나왔어”로 차량 안내를 확인한다. 가족 웹을 사용하지 않는다.
4. P 화면의 시연 패널 **진료 끝**으로 진료 15분 경과를 만든 뒤 `tools/vc/vc.sh play tools/vc/visit-return.vc`: D, 30km/h. 개선 소스의 실제 모드는 출발 뒤 누적 주행 60초를 새로 기다린다. 시연 시간 이동을 실제 대기 검증으로 계산하지 않는다(기존 APK·녹화와 구분). 귀가 인터뷰 → 브리핑 읽기 → 가족 공유 확인 → 다음 예약 동의를 확인한다. 실제 시간 경계 시험은 진료 끝 버튼 없이 900초 이상 기다린 뒤 출발한다.
5. `tools/vc/vc.sh speed 0`, `tools/vc/vc.sh gear Park` 후 P이고 주행 제한이 풀리면 브리핑·기록을 확인한다. “잘 받았어”는 승인 전송 후 수 초 뒤 받는 모의 답장이다. 종료는 앱 시연 종료, `tools/vc/vc.sh down` 순서다.

**REAL:** TTS SDK 요청·완료, VC의 Vehicle 도어 + AAOS 기어·속도, CarUxRestrictions. **MOCK:** STT 답·병원 목적지/도착·시간 이동·가족 답장. 문 열림은 하차 확정이 아니며 누적 주행 60초는 운전 안정성 판단값이 아니다. 주행 화면에는 긴 메모·설정이 나타나면 안 된다. 기존 앱의 휴게소/정차 후보 전용 성능 측정 스크립트는 제거했다. 좌석 점유·차량 제어·과거 앱의 debug probe는 이 시연에서 사용하지 않는다.

## 검증과 남은 기기 확인

서버:

```sh
./gradlew -p visit-core test
(cd tools/family && python3 -I -m unittest)
(cd tools/laptop && python3 -I -m unittest)
```

2026-11-15 LEAD가 Android 빌드·release APK·에뮬레이터 시연을 확인했다. 기존 결과와 이번 개선 소스를 구분한다. 새 소스의 APK 재빌드 뒤 아래 경로를 다시 확인하고 해시를 갱신한다.

- debug/release Kotlin·Compose 컴파일, 앱/kit 테스트, merged manifest, release 서명.
- 가로 전체 창과 portrait 분할 840×1184dp, 라이트/다크, 글꼴 확대, 주행 규제 on/off 및 긴 원문/키보드 숨김.
- 목적지 이름·주소와 도착 콜백 일치, 목적지 변경·취소 시 후보 제거, VC 기어/차속/오른쪽 앞문, 서비스 재연결.
- TTS/STT onReady·듣기 창, relay 실제 휴대폰 연결/철회/만료, 앱 재시작 복원·삭제.
- release가 demo 외 개발 extra를 처리하지 않는지. 실제 기록 모드는 FLAG_SECURE로 캡처를 막고, 지어낸 기록만 쓰는 시연 모드는 녹화할 수 있다.

앱 이름 변경은 `app/src/main/res/values/strings.xml`의 `app_name`을 고친 뒤 `python3 tools/laptop/update-label.py`로 두 README의 표시 이름과 APK 파일명을 동기화한다. `--check`는 수정 없이 불일치를 검사한다. 문서의 app-label 주석은 마지막 동기화 값이며 별도 이름 원본이 아니다.
