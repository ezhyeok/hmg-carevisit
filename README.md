# 진료동행
<!-- app-label: 진료동행 -->

**부모님과 병원에 가는 길에는 물어볼 말을, 돌아오는 길에는 가족에게 전할 말을 정리하는 차량용 앱입니다.** 기록 자체보다 **병원 이동과 차량 신호에 대화 시점을 연결하는 것**에 집중합니다. 의료 판단을 하지 않습니다.

## 심사자 빠른 시작 — 제출 APK

**[`apk/차곡_진료동행_release.apk`](apk/차곡_진료동행_release.apk)**를 Pleos Connect Emulator v2.1.0(API 34)에 설치합니다. Android 빌드·release APK·에뮬레이터 시연은 **2026-11-15에 검증**했습니다. 아래는 소스 빌드 없이 제출 APK를 실행하는 순서입니다.

```sh
adb install -r "apk/차곡_진료동행_release.apk"
adb shell pm grant --user current dev.carevisit.app android.car.permission.CAR_SPEED
adb shell am start -S --user current -n dev.carevisit.app/.MainActivity --es demo visit
```

`--user current`는 Pleos의 현재 사용자에 권한을 줍니다. `-S`는 기존 프로세스를 종료해 시연 인텐트를 확실히 반영합니다. 다른 서명의 앱이 설치돼 있으면 설치가 실패할 수 있습니다. 삭제·재설치는 기존 방문 기록도 지우므로 자동 제거하지 않습니다.

| 제출 APK 확인값 | 값 |
|---|---|
| 패키지 / 버전 | `dev.carevisit.app` / `0.1.0` (versionCode `1`, APK manifest 직접 확인) |
| 크기 | 41,847,536 bytes |
| SHA-256 | `75f825691d12f2c2396fe7c6b4bd5c667591b61ff814af7ae81b7d4b840edef4` |

위 값은 2026-11-15 이 저장소의 소스로 만든 release 빌드 기준입니다(노트북 assembleRelease, 앱·kit 단위 테스트 통과, 에뮬레이터에서 시연 흐름 확인). 상세 검증 범위는 [SDK_AUDIT](docs/SDK_AUDIT.md)에 있습니다.

### Virtual Console 순서

시연은 허구의 병원·대답을 사용하고 메모리에만 저장합니다. **가족 HTTP relay나 휴대폰은 이 시연에 필요하지 않습니다.** VC 연결 도구가 준비된 서버에서 실행합니다([노트북·터널 준비](tools/laptop/README.md)).

```sh
tools/vc/vc.sh up
tools/vc/vc.sh play tools/vc/visit-drive.vc
```

1. **가는 길:** P→D, 40km/h, 누적 주행 60초 뒤 동의·고정 질문을 시작합니다. 60초는 제품 설계값이며 도로 안전 상태를 판정한 결과가 아닙니다. 인터뷰가 끝나 **메모가 완성될 때까지** 기다립니다.
2. **문 열림:** `tools/vc/vc.sh play tools/vc/visit-dropoff.vc`. 속도 0→P→동승석 앞문 열림을 보냅니다. 문 열림은 실제 하차의 증거가 아닙니다. P이고 주행 제한이 풀리면 메모를 확인합니다.
3. **주차·합류:** `tools/vc/vc.sh play tools/vc/visit-wait.vc`. 앱의 주차 후 합류를 선택하고 시연 패널에서 가족 공유에 별도로 동의합니다. 메모 전송도 한 번 더 확인합니다. 패널의 **시연 답장 · 나왔어**로 모의 합류 답장을 보냅니다.
4. **오는 길:** P 화면의 **진료 끝**으로 15분 체류를 건너뛴 후 `tools/vc/vc.sh play tools/vc/visit-return.vc`. 귀가 인터뷰와 브리핑·이번 전송 확인을 기다립니다. 개선 소스의 실제 모드는 D 전환 뒤 누적 주행 60초를 새로 기다립니다. 시연의 시간 이동은 이 실제 대기를 검증한 것이 아니며 기존 APK·녹화와 구분합니다([검증 기록](docs/SDK_AUDIT.md)).
5. **기록 확인:** `tools/vc/vc.sh speed 0` 후 `tools/vc/vc.sh gear Park`. 주행 제한이 풀리면 상세 기록을 봅니다. **잘 받았어**는 승인 전송 후 수 초 뒤 들어오는 모의 답장입니다. 종료는 앱의 시연 종료 → `tools/vc/vc.sh down` 순서입니다.

### REAL / MOCK — 이 시연으로 확인할 수 있는 것

| 구분 | 시연에서 사용하는 것 | 입증하지 않는 것 |
|---|---|---|
| REAL | Pleos **TTS SDK 요청·완료 이벤트** | `-no-audio`·무음 녹화이므로 실제 들리는 출력 품질 |
| REAL | **Vehicle 도어 + AAOS 기어·속도**, Virtual Console 입력과 상태 전이 | 승객의 실제 하차·도로 안전 여부 |
| REAL | **CarUxRestrictions**에 따른 상세 화면 제한 | 모든 실차·모든 창 크기의 인증 |
| MOCK | STT 답변 문자열, 병원 목적지·도착, 체류시간 이동 | 실제 마이크 인식률·실제 NaviHelper 도착 성공 |
| MOCK | `DemoFamilyLink` 메모리 연결의 나왔어·잘 받았어 | HTTP relay·휴대폰 왕복 통신 |

시연 UI의 ‘미리 정한 대답’은 음성 녹음 파일이 아닙니다. 이전 APK·캡처의 ‘미리 녹음한 대답’도 같은 scripted STT를 가리킵니다. 실제 모드에는 STT·NaviHelper·HTTP 연결 코드가 있지만 위 시연의 성공을 해당 경로의 검증으로 계산하지 않습니다. LLM은 기본 꺼짐이며 없어도 고정 서식으로 기록을 만듭니다.

## 시연 영상

[`docs/demo/jinryo-donghaeng-demo.mp4`](docs/demo/jinryo-donghaeng-demo.mp4): 2분 35초, 에뮬레이터·Virtual Console 화면 녹화와 자막, 소리 없음. 동의 → 가는 길 인터뷰 → P·문 열림과 메모 → 공유 확인 → 오는 길 브리핑 → 모의 가족 답장. 자막 원본은 `tools/video/spec.json`, 재생성 도구는 `tools/video/make_video.py`입니다. 실제/모의 범위는 위 표를 기준으로 봅니다.

## 대안과 구분되는 지점

특정 제품의 기능 평가가 아니라, 일반적인 수동 사용 방식과 현재 구현을 비교한 설계표입니다.

| 비교 축 | 휴대폰 음성 메모 | 메신저 | 진료동행 |
|---|---|---|---|
| 시작을 기억하는 부담 | 직접 녹음 시작 | 직접 대화방 열기 | 병원 목적지에서 준비 제안 |
| 병원 이동과 기록 연결 | 제목·폴더로 수동 연결 | 대화 내용으로 구분 | 같은 방문의 준비·귀가 기록 연결 |
| P/D·도어 맥락 | 사용자가 판단 | 사용자가 판단 | 차량 신호로 문 열림·귀가 시점 구분 |
| 주행 중 상세 제한 | 사용자가 조절 | 사용자가 조절 | P와 UX 제한 해제를 함께 확인 |
| 다음 정기 진료 재사용 | 이전 녹음 찾기 | 대화 검색 | 같은 병원 이전 확인할 말 불러오기 |

자동 목적지·도착은 구현 경로이며 제출 시연에서는 모의 입력입니다. 다음 예약 알림은 **서비스가 실행 중일 때** 날짜를 확인합니다. 차 시동으로 앱을 깨우는 기능은 없습니다.

## 초기 검증과 사업 가설 — PLAN

**확보 사용자 0명, 제휴·매출 0건**입니다. MAU는 한 달에 준비 또는 귀가 기록을 완료한 운전자 수로 정의하며 현재 실사용 측정값은 없습니다. 도달 10,000명 × 정기 동행 10% × 설치·동의 20% × 월 사용 50% = 100명은 모든 입력값이 미검증인 계산 예시입니다. 공공 병원동행 실적은 수요의 배경이지 차량 사용자 수나 전환율의 근거가 아닙니다.

첫 검증은 정기 진료에 동행하는 운전자 **10가구 모집 계획**입니다. 보호자·운전자 커뮤니티에 참여자를 모집하고, 지역 병원동행 운영기관에는 별도 실증 협의를 제안할 계획이며 아직 모집·접촉을 완료하지 않았습니다. 동일한 허구의 진료 상황으로 음성 메모·메신저와 비교하고 순서를 바꾸어 시행합니다. 아래 수치는 모두 **실험 전 목표(PLAN)**입니다.

| 지표 | 통과 기준(PLAN) |
|---|---|
| 도움 없이 준비→기록 확인 완료 | 10가구 중 8가구 이상 |
| 사전 지정 질문 누락 | 개인별 기존 방식 대비 누락 감소의 중앙값 1개 이상 |
| 인식 내용 정정 부담 | 방문당 정정 요청 중앙값 2회 이하; 잘못된 약·예약을 확인 없이 확정한 사례 0건 |
| 다음 방문 재사용 | 90일 관찰 내 다음 진료가 도래한 가구 중 60% 이상; 대상 5가구 미만이면 판정 유보 |

지불자는 병원동행 **운영기관**, 과금 단위는 **월 활성 차량**이라는 가설입니다. 기관 담당자 인터뷰로 도입·지원 비용과 지불 의향을 확인한 뒤 단가를 정합니다. 계약·가격은 미정입니다. [기획서와 근거](docs/plan/planning-doc.md)에 자세한 출처·검증 계획이 있습니다.

## 소스에서 빌드하기

노트북의 JDK/Android SDK, Pleos 이미지와 전용 Gradle 홈 준비는 [빌드 도구 안내](tools/laptop/README.md)를 따릅니다. 서버에서 복사한 뒤 노트북 저장소 루트에서 에뮬레이터 실행 전에 빌드합니다.

```sh
LAPTOP_REPO=repo-visit tools/laptop/sync.sh
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\laptop\gradle.ps1 :app:testDebugUnitTest :pleos-kit:testDebugUnitTest :app:assembleRelease
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\laptop\run-app.ps1 -Release -SkipBuild -Demo
```

release 서명은 노트북 `keystore.properties`에서 설정합니다. 출력은 `app/build/outputs/apk/release/app-release.apk`입니다. 앱 이름의 원본은 [`app_name`](app/src/main/res/values/strings.xml)이며 변경 뒤 `python3 tools/laptop/update-label.py`로 문서 표시를 동기화합니다.

```sh
./gradlew -p visit-core test adapterTest
(cd tools/family && python3 -I -m unittest)
(cd tools/laptop && python3 -I -m unittest)
```

## 실제 모드의 가족 relay

**`--es demo off`로 실제 모드를 시작한 뒤** 별도로 검증하는 경로입니다. 노트북 Python 3.9 이상에서 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\laptop\run-family-relay.ps1 -Port 8765
```

직접 실행은 `python3 tools/family/relay.py --host 0.0.0.0 --port 8765`입니다. 차 설정은 `http://10.0.2.2:8765`, 같은 Wi-Fi의 휴대폰은 `http://<노트북 LAN IP>:8765/`를 엽니다. 차의 전체 가족 주소에서 `#` 뒤 32자리 연결 코드를 입력합니다. 짧은 8자리 코드는 연결 구별용입니다. Windows 방화벽은 사설 네트워크의 해당 TCP 포트만 허용합니다.

relay는 승인된 글·정해진 상태·두 가지 답장만 다룹니다. **발화 원본 목록과 좌표를 보내지 않지만 승인된 글에 STT 인식문·개인정보가 포함될 수 있습니다. 비식별화 기능이 아닙니다.** 메모리 보관이며 최초 연결부터 24시간 후 만료하고 재시작하면 지웁니다. 실제 서비스에는 HTTPS와 사용자 인증이 필요합니다. [HTTP 계약](tools/family/PROTOCOL.md)을 확인하세요.

## 개인정보와 제품 범위

- 건강정보 기록 동의, 가족 연결 동의, 매번의 메모·브리핑 전송 확인을 나누어 받습니다.
- 실제 기록은 앱 전용 기기 저장소에 보관합니다. 기본 180일이며 설정 변경·만료·전체 삭제·동의 철회를 제공합니다. 원격 삭제 실패 때 relay 사본은 만료까지 남을 수 있습니다.
- **현재 한 돌봄 대상용 프로필**입니다. 호칭 변경은 환자 전환이 아닙니다. 다른 대상은 기록·동의를 전체 삭제한 뒤 새로 시작합니다. 여러 대상 관리·보호자 앱·예약 연동은 확장 계획입니다.
- ‘원문 보존’은 **STT 인식문과 발화 ID를 추적할 수 있다는 뜻**이며 음성 인식 정확성 보장이 아닙니다. 진단·중증도·처방·용량 판단을 만들지 않습니다. 약·예약은 확인이 필요합니다.
- 실제 기록 화면은 캡처와 최근 앱 미리보기를 막고, 지어낸 시연 기록만 캡처·녹화할 수 있습니다. 가족 연결 코드를 아는 사람은 공유 내용을 볼 수 있습니다.

## 구현 근거와 이용 조건

[제품 사양](docs/product/SPEC.md) · [아키텍처](docs/ARCHITECTURE.md) · [SDK 검증 범위](docs/SDK_AUDIT.md) · [Pleos 래퍼](pleos-kit/README.md)

모든 권리 보유. 공모전 심사와 열람을 위해 공개합니다([LICENSE](LICENSE)). 제3자 구성요소 고지는 [NOTICE.md](NOTICE.md)에 있습니다. 참고한 공모전·Pleos 문서 원문(`docs/contest/`)은 저작권자의 자료라 저장소에 넣지 않았습니다.
