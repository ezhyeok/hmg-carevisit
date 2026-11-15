# 진료동행
<!-- app-label: 진료동행 -->

**부모님과 병원에 가는 길에는 물어볼 말을, 돌아오는 길에는 가족에게 전할 말을 정리하는 차량용 앱입니다.** 기록 자체보다 **병원 이동과 차량 신호에 대화 시점을 연결하는 것**에 집중합니다. 의료 판단을 하지 않습니다.

## 심사자 빠른 시작 — 제출 APK

**[`apk/차곡_진료동행_release.apk`](apk/차곡_진료동행_release.apk)**를 Pleos Connect Emulator v2.1.0(API 34)에 설치합니다. 제출 APK는 **2026-11-15 현재 소스로 빌드**했고, 개발자가 설치와 에뮬레이터 시연을 확인했습니다. 아래는 소스 빌드 없이 제출 APK를 실행하는 순서입니다.

```sh
adb install -r "apk/차곡_진료동행_release.apk"
adb shell pm grant --user current dev.carevisit.app android.car.permission.CAR_SPEED
adb shell am start -S --user current -n dev.carevisit.app/.MainActivity --es demo visit
```

`--user current`는 Pleos의 현재 사용자에 권한을 줍니다. `-S`는 기존 프로세스를 종료해 시연 인텐트를 확실히 반영합니다. 다른 서명의 앱이 설치돼 있으면 설치가 실패할 수 있습니다. 삭제·재설치는 기존 방문 기록도 지우므로 자동 제거하지 않습니다.

| 현재 제출본 — 2026-11-15 | 값 |
|---|---|
| 소스 기준 | `452c8f0376333ff14812c99f7e9cdca1056b5d89` (이번 문서 정리 전 제출 커밋) |
| 패키지 / 버전 | `dev.carevisit.app` / `0.1.0` (versionCode `1`, APK manifest 직접 확인) |
| 크기 | 41,847,536 bytes |
| SHA-256 | `75f825691d12f2c2396fe7c6b4bd5c667591b61ff814af7ae81b7d4b840edef4` |
| 영상 | 155초(2분 35초), 1920×1080, 무음, 자막 13구간 |
| 기존 JVM 결과 XML | 코어 99개·어댑터 31개, 실패/오류 0. 이번 문서 작업에서 재실행하지 않음 |
| 확인된 demo 범위 | TTS SDK 요청·완료, VC 기어·속도·도어, UX 제한, 방문 기록·공유 확인 흐름 |

노트북 assembleRelease·앱/kit 단위 테스트·에뮬레이터 시연은 개발자가 직접 확인했습니다. 파일 식별과 테스트별 범위는 [SDK_AUDIT](docs/SDK_AUDIT.md)에 정리했습니다.

### 테스트 환경과 필요한 설정

개발·시연 기준은 **Pleos Connect Emulator v2.1.0, API 34, 2560×1440 가로, 160dpi**입니다. **RAM 4GB / cold boot / GPU host / -no-audio**로 실행합니다. 1920×1080은 영상 출력 크기이며, 840×1184dp 세로 분할은 추가 검증 대상입니다. 위 CAR_SPEED 권한을 현재 사용자에게 허용하고, VC 연결을 유지합니다. 기록 상세는 P이고 주행 제한이 풀린 때 열립니다. 가족 HTTP relay는 시연에 필요하지 않습니다.

### 로컬 Virtual Console 순서

심사자 PC에서 에뮬레이터와 연결된 **Virtual Console 화면을 열어 연결을 유지**한 뒤 아래 신호를 수동 조작합니다. 개발자의 SSH 별칭이나 서버 스크립트 없이 진행할 수 있습니다. 문은 **앞 오른쪽 동승석(FR)**입니다. 허구의 병원·대답·가족 답장을 쓰며 시연 기록은 메모리에 보관합니다.

| 단계 | VC 조작·대기 | 앱에서 확인할 것 |
|---|---|---|
| 가는 길 | 문 닫기 → P·속도 0으로 3초 → D·40km/h로 65초 | 누적 주행 60초 뒤 동의·질문 시작. **메모 완성까지 기다림** |
| 문 열림 | 속도 0 → P로 2초 → 앞 오른쪽 문 열고 3초 | 메모 확인. 문 열림은 실제 하차 확정이 아님 |
| 주차·합류 | P·속도 0·문 닫기로 5초 | 주차 후 합류 선택 → 시연 가족 공유 별도 동의 → 메모 전송 확인 → 시연 답장 ‘나왔어’ |
| 오는 길 | P에서 ‘진료 끝’ → 문 닫고 2초 → D·30km/h | 병원 체류와 **귀가 60초는 시연 시간 이동**. 귀가 인터뷰·약/예약 읽기 확인·브리핑 전송 확인 |
| 기록 확인 | 속도 0 → P, UX 제한 해제까지 대기 | 브리핑·기록·모의 ‘잘 받았어’ 확인. 마치면 시연 종료 |

실제 모드는 귀가 출발 뒤 누적 주행 60초를 기다립니다. 시연 영상은 이 대기를 시간 이동하며, 주차장 이탈이나 도로 안전을 판별하지 않습니다.

**개발 환경의 선택 경로:** SSH·터널을 설정한 경우에만 `tools/vc/vc.sh up` 후 `play tools/vc/<파일>`을 사용합니다. 파일 순서는 `visit-drive.vc` → 메모 완성 → `visit-dropoff.vc` → `visit-wait.vc` → 앱의 진료 끝 → `visit-return.vc`입니다. 종료는 `tools/vc/vc.sh down`입니다. [개발 노트북 도구 안내](tools/laptop/README.md)를 참고하세요.

### REAL / MOCK — 이 시연으로 확인할 수 있는 것

| 구분 | 시연에서 사용하는 것 |
|---|---|
| REAL | Pleos TTS SDK 요청·완료, Vehicle 도어 + AAOS 기어·속도, CarUxRestrictions 상세 제한 |
| MOCK | STT 답변 문자열, 병원 목적지·도착, 병원 체류 및 **귀가 60초 시간 이동** |
| MOCK | DemoFamilyLink 메모리 연결의 ‘나왔어’·‘잘 받았어’ |

**실제 마이크 인식, 실제 NaviHelper 도착, 휴대폰 relay 왕복은 미검증입니다.** 무음 녹화는 가청 출력 품질을 보여 주지 않습니다. ‘미리 정한 대답’은 scripted STT 문자열입니다. LLM은 기본 꺼짐이며 고정 서식으로 기록을 만듭니다.

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

첫 검증은 **같은 돌봄 대상에게 정기 동행하는 10가구 모집 계획**입니다. 초기 모집 대상은 Pleos에 접근 가능한 운전자와 정기 동행 가구의 교집합이며, 모집 가능한 모수는 아직 확인하지 못했습니다. 보호자·운전자 커뮤니티에 참여자를 모집하고, 지역 병원동행 운영기관에는 별도 실증 협의를 제안할 계획이며 아직 모집·접촉을 완료하지 않았습니다. 동일한 허구의 진료 상황으로 음성 메모·메신저와 비교하고 순서를 바꾸어 시행합니다. 아래 수치는 모두 **실험 전 목표(PLAN)**입니다.

| 지표 | 통과 기준(PLAN) |
|---|---|
| 도움 없이 준비→기록 확인 완료 | 10가구 중 8가구 이상 |
| 사전 지정 질문 누락 | 개인별 기존 방식 대비 누락 감소의 중앙값 1개 이상 |
| 인식 내용 정정 부담 | 방문당 정정 요청 중앙값 2회 이하; 잘못된 약·예약을 확인 없이 확정한 사례 0건 |
| 다음 방문 재사용 | 90일 관찰 내 다음 진료가 도래한 가구 중 60% 이상; 대상 5가구 미만이면 판정 유보 |

초기 실증은 단일 대상 가구가 중심이며, **운영기관은 모집·실증 협력 후보**입니다. 여러 대상이 바뀌는 기관 차량의 유료 도입은 **대상별 데이터 분리·권한·운영 검증 이후의 확장**입니다. 그때 운영기관 지불·월 활성 차량 과금 가설과 지불 의향을 검증합니다. 현재 가격·계약은 미정입니다. [기획서와 근거](docs/plan/planning-doc.md)에 자세한 출처·검증 계획이 있습니다.

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

실제 모드로 전환하는 전체 명령입니다.

```sh
adb shell am start -S --user current -n dev.carevisit.app/.MainActivity --es demo off
```

노트북 Python 3.9 이상에서 relay를 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\laptop\run-family-relay.ps1 -Port 8765
```

직접 실행은 `python3 tools/family/relay.py --host 0.0.0.0 --port 8765`입니다. 차 설정은 `http://10.0.2.2:8765`, 같은 Wi-Fi의 휴대폰은 `http://<노트북 LAN IP>:8765/`를 엽니다. 차의 전체 가족 주소에서 `#` 뒤 32자리 연결 코드를 입력합니다. 짧은 8자리 코드는 연결 구별용입니다. Windows 방화벽은 사설 네트워크의 해당 TCP 포트만 허용합니다.

relay는 승인된 글·정해진 상태·두 가지 답장만 다룹니다. **발화 원본 목록과 좌표를 보내지 않지만 승인된 글에 STT 인식문·개인정보가 포함될 수 있습니다. 비식별화 기능이 아닙니다.** 메모리 보관이며 최초 연결부터 24시간 후 만료하고 재시작하면 지웁니다. 실제 서비스에는 HTTPS와 사용자 인증이 필요합니다. [HTTP 계약](tools/family/PROTOCOL.md)을 확인하세요.

## 개인정보와 제품 범위

- 건강정보 기록 동의, 가족 연결 동의, 매번의 메모·브리핑 전송 확인을 나누어 받습니다.
- 실제 기록은 앱 전용 기기 저장소에 보관합니다. 기본 180일이며 설정 변경·만료·전체 삭제·동의 철회를 제공합니다. 원격 삭제 실패 때 relay 사본은 만료까지 남을 수 있습니다.
- **현재 한 돌봄 대상용 프로필**입니다. 호칭 변경은 환자 전환이 아닙니다. 다른 대상은 기록·동의를 전체 삭제한 뒤 새로 시작합니다. 여러 대상 관리·보호자 앱·예약 연동은 확장 계획입니다.
- ‘원문 보존’은 **STT 인식문과 발화 ID를 추적할 수 있다는 뜻**이며 음성 인식 정확성 보장이 아닙니다. 진단·중증도·처방·용량 판단을 만들지 않습니다. 약·예약 정정은 인터뷰 중 1회이며, 재정정 요청은 확인 필요로 남깁니다. 완료 후 항목별 수정은 지원하지 않습니다.
- 실제 기록 화면은 캡처와 최근 앱 미리보기를 막고, 지어낸 시연 기록만 캡처·녹화할 수 있습니다. 가족 연결 코드를 아는 사람은 공유 내용을 볼 수 있습니다.

## 구현 근거와 이용 조건

[제품 사양](docs/product/SPEC.md) · [아키텍처](docs/ARCHITECTURE.md) · [SDK 검증 범위](docs/SDK_AUDIT.md) · [Pleos 래퍼](pleos-kit/README.md)

모든 권리 보유. 공모전 심사와 열람을 위해 공개합니다([LICENSE](LICENSE)). 제3자 구성요소 고지는 [NOTICE.md](NOTICE.md)에 있습니다. 참고한 공모전·Pleos 문서 원문(`docs/contest/`)은 저작권자의 자료라 저장소에 넣지 않았습니다.
