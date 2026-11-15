# 진료동행
<!-- app-label: 진료동행 -->

**부모님과 병원에 가는 길에는 진료 메모를, 돌아오는 길에는 가족에게 전할 진료 기록을 음성으로 정리하는 차량용 앱입니다.**

앱 이름의 원본은 [`app_name`](app/src/main/res/values/strings.xml)이며 패키지는 `dev.carevisit.app`입니다. 이름 변경 뒤 `python3 tools/laptop/update-label.py`로 문서 표시를 동기화합니다. 의학적 판단을 하지 않습니다. 진단·증상 해석·중증도 판단·약이나 용량 추천 대신, 사용자가 말한 내용을 고정 질문과 항목으로 기록합니다.

## 실행 방법

Pleos Connect의 Android Automotive 환경에서 실행합니다. Windows 노트북의 프로젝트 폴더에 JDK/Android SDK, Pleos 시스템 이미지, 프로젝트 전용 Gradle 홈을 준비합니다. 기존 환경의 복사·빌드·에뮬레이터 설정은 [노트북 도구 안내](tools/laptop/README.md)를 따릅니다.

서버에서 노트북으로 복사:

```sh
LAPTOP_REPO=repo-visit tools/laptop/sync.sh
```

노트북 `repo-visit` 루트에서 에뮬레이터 실행 전에 빌드:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\laptop\gradle.ps1 :app:testDebugUnitTest :pleos-kit:testDebugUnitTest :app:assembleRelease
```

release 서명은 노트북의 `keystore.properties`에 준비합니다. 에뮬레이터 부팅·안정화 후 설치와 시연 시작:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\laptop\run-app.ps1 -Release -SkipBuild -Demo
```

이미 설치했다면 ADB로 실행할 수도 있습니다.

```sh
adb shell am start -S --user current -n dev.carevisit.app/.MainActivity --es demo visit
```

**APK: [`apk/차곡_진료동행_release.apk`](apk/차곡_진료동행_release.apk)** (release 빌드, 서명됨. 개인 참가이며 파일명의 팀명 자리는 활동명 ‘차곡’). 직접 빌드하면 `app/build/outputs/apk/release/app-release.apk`에 나옵니다.

## 테스트 환경과 설정

- **Pleos Connect Emulator v2.1.0, API 34, 2560×1440 가로, 160dpi**. RAM 4GB, cold boot, GPU host를 사용합니다. portrait 분할 840×1184dp 레이아웃도 기기 확인 대상입니다.
- 앱 최초 실행 시 차량 속도 권한을 허용합니다. 민감정보 수집 동의와 가족 공유 동의는 따로 받습니다. 긴 기록과 설정은 P이고 주행 제한이 해제된 때에만 표시합니다.
- Virtual Console의 기어·차속·동승석 오른쪽 앞문 신호를 사용합니다. 좌석 점유나 미검증 시트 제어에 의존하지 않습니다.
- 노트북 실행 도구는 `-no-audio`를 사용합니다. 시연의 응답은 가상 STT이며 실제 마이크 인식 성능을 나타내지 않습니다. TTS 요청은 실제 SDK를 사용합니다.
- 내비 경로 계산에는 네트워크가 필요합니다. 가족 공유에는 노트북 relay와 같은 사설 Wi-Fi가 필요하며 외부 계정이나 클라우드 서버는 필요하지 않습니다.
- LLM 문장부호 정리는 기본 꺼짐입니다. 켜도 기기 내 실행·8초 제한·내용 보존 검사에 실패하면 고정 템플릿을 사용합니다.

Android SDK가 없는 서버에서는 순수 Kotlin 코어와 도구를 검사합니다.

```sh
./gradlew -p visit-core test
(cd tools/family && python3 -I -m unittest)
(cd tools/laptop && python3 -I -m unittest)
```

Android 컴파일, 실제 Pleos 목적지/도착 이벤트, 화면 규격, TTS/STT, 휴대폰 연결은 Windows 에뮬레이터에서 별도로 검증해야 합니다. [노트북 검증 목록](tools/laptop/README.md)을 확인하세요.

## 시연 모드 순서

시연은 가상의 `○○대학교병원`, 호칭 `어머니`를 사용합니다. 실제 TTS 질문 뒤 정해진 대답이 들어오며 **“시연: 미리 녹음한 대답”**으로 표시됩니다. 시연 기록은 메모리에만 두고 실제 방문 기록과 분리합니다. 실제 기어·도어·주행 제한은 유지합니다.

1. `--es demo visit`로 시작합니다. 서버의 `tools/laptop/demo-smoke.sh`는 앱 재시작 후 `CareVisit: demo started: visit` 로그를 확인합니다.
2. 서버에서 `tools/vc/vc.sh up` 후 `tools/vc/vc.sh play tools/vc/visit-drive.vc`를 실행합니다. P→D, 40km/h로 1분 이상 움직이면 동의·가는 길 인터뷰가 시작됩니다. 주차 중 준비 시작도 가능합니다.
3. **메모 완성 후** `visit-dropoff.vc`를 재생합니다. P와 동승석 문 열림으로 하차를 보여 줍니다. 주차 화면에서 메모·물어볼 것 목록을 확인하고, 공유한다면 가족 연결 동의와 이번 메모 전송 확인을 진행합니다.
4. `visit-wait.vc`로 P를 유지합니다. 앱에서 주차 후 합류를 선택하고 가족 웹의 **“나왔어”**를 눌러 차량 안내를 확인합니다.
5. P 화면의 시연 패널 **진료 끝**으로 진료 15분 경과를 만든 뒤 `visit-return.vc`를 재생합니다. D 전환 후 의사 말씀·약 변경·다음 예약·주의사항·다음에 확인할 것을 기록합니다. 읽어 준 브리핑을 이번에 공유할지 다시 확인합니다.
6. 속도 0, P로 바꿔 브리핑과 기록을 확인합니다. 가족은 **“잘 받았어”**로 답할 수 있습니다. 시연 종료 시 실제 기록으로 돌아갑니다.

시나리오 파일은 각각 `tools/vc/` 아래에 있습니다. 예: `tools/vc/vc.sh play tools/vc/visit-dropoff.vc`. 세부 단계·실제 15분 경계 시험·종료 방법은 [시연 도구 안내](tools/laptop/README.md)에 있습니다. 실제 기록이 보이는 화면은 캡처·녹화를 막고(FLAG_SECURE), 지어낸 기록만 보이는 시연 모드에서는 화면을 캡처·녹화할 수 있습니다.

## 가족 relay 실행

노트북에서 Python 3.9 이상을 준비하고 별도 PowerShell 창에서 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\laptop\run-family-relay.ps1 -Port 8765
```

또는 Python으로 직접 실행:

```sh
python3 tools/family/relay.py --host 0.0.0.0 --port 8765
```

차 설정의 relay 주소는 `http://10.0.2.2:8765`입니다. 휴대폰은 **같은 Wi-Fi**에서 `http://<노트북 LAN IP>:8765/`를 열고 차의 전체 가족 주소에서 `#` 뒤 32자리 연결 코드를 입력합니다. `/#<코드>` 공유 주소도 지원합니다. 차의 짧은 8자리 확인 코드는 연결 구별용이며 로그인에는 쓸 수 없습니다. 10.0.2.2는 휴대폰용 주소가 아닙니다. Windows 방화벽은 사설 네트워크의 해당 TCP 포트만 허용합니다.

relay는 승인된 메모·브리핑·정해진 상태와 두 가지 답장만 다룹니다. 위치나 대화 원문을 보내는 API는 없습니다. 메모리 보관이며 최초 연결부터 24시간 후 만료합니다(접근 시 즉시 차단, 미접속 메모리 정리는 최대 약 30초 지연). 재시작하면 지워집니다. 만료 후에는 차에서 가족 연결을 끄고 새로 연결하세요. **시연용 HTTP이므로 실제 서비스에는 HTTPS와 실제 사용자 인증이 필요합니다.** [HTTP 계약과 크기 제한](tools/family/PROTOCOL.md)을 확인하세요.

## 개인정보와 의료 원칙

- 건강 관련 민감정보를 기록하기 전에 별도의 명시적 동의를 받습니다. 가족 공유 동의만으로 건강정보 수집 동의를 대신하지 않습니다.
- 방문 기록·발화 원문·동의는 기기 안 앱 전용 저장소에 보관합니다. 기본 보관 기간은 **180일**이며 설정에서 바꾸고, 시작·주기 검사·설정 변경 때 만료 기록을 삭제합니다. 전체 삭제는 기록과 동의를 함께 지웁니다.
- 가족 공유는 설정 시 별도 동의에 더해 **메모/브리핑을 보낼 때마다 확인**합니다. 공유한 사본만 relay에서 제한된 기간 보관하며 원본 대화와 위치는 공유하지 않습니다. 공유 끄기는 relay 삭제도 요청합니다. 통신 실패 시 원격 사본이 즉시 삭제되지 않을 수 있고 TTL로 만료됩니다.
- 실명 대신 호칭을 쓰며 코드·시연 데이터에 실제 인명을 넣지 않습니다. 실제 기록 화면은 캡처·최근 앱 미리보기를 막습니다(시연 모드 제외). 가족 공유 주소/코드를 아는 사람은 공유 내용을 볼 수 있으므로 신뢰하는 가족에게만 전달합니다.
- 앱은 **의학적 판단을 하지 않습니다**. 슬롯은 발화 ID로 원문에 연결되고 고정 템플릿으로 완성됩니다. 기록의 정확성과 약·예약 내용은 의료진에게 확인합니다.

## 구조와 구현 근거

- [제품 사양](docs/product/SPEC.md): 상태 전이, 동의, 안전 원칙, 기준 시간
- [아키텍처](docs/ARCHITECTURE.md): 순수 Kotlin `visit-core`, 단일 액터, 저장소·음성·가족 연결 포트, Android 어댑터와 UI
- [SDK API 레퍼런스](docs/contest/03_SDK_API_레퍼런스/INDEX.md), [Pleos 래퍼](pleos-kit/README.md)
- [가족 relay 계약](tools/family/PROTOCOL.md), [노트북 빌드·시연](tools/laptop/README.md)

## 라이선스

모든 권리 보유. 공모전 심사와 열람을 위해 공개한다 ([LICENSE](LICENSE)). 내장 데이터와 제3자 구성요소의 라이선스는 [NOTICE.md](NOTICE.md)에 있다. 개발 중 참고한 공모전·Pleos 문서 원문(`docs/contest/`)은 저작권자의 자료라 저장소에 넣지 않았다.
