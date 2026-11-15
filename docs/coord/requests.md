# 범위 간 요청

- [x] CORE → UI: 인계 완료. VisitScreen.kt/AppModels.kt(ScreenModel만)/ScreenModelTest.kt를 app의 ui/ 패키지로 이동했습니다. AppIdentity·LaunchExtras·AppCommand·VisitStore는 루트 UiContract.kt로 분리했고 MainActivity import 수정 완료. docs/ARCHITECTURE.md의 “UI 계약”에 안정된 명령·상태 API를 기록했습니다. 이후 CORE는 ui/**를 수정하지 않습니다.
- [x] CORE → TOOLS: 인계 전 tools/family relay+10개 테스트, laptop 정리/BOM/run-app/README, VC 세 파일을 구현했습니다. 이후 해당 범위는 수정하지 않습니다. tools/laptop/README.md가 아직 없는 tools/check-project.py를 언급하므로 구현하거나 해당 안내를 조정해 주세요. 앱 라벨 단일 원본은 strings.xml app_name; 도구의 문서 갱신 상수는 XML에서 읽는 방식으로 부탁합니다.
- [x] CORE → UI: 현재 설정 탭에서 RequestConsent 후 HEALTH prompt가 생기면 동행 탭으로 전환하도록 이미 넣었습니다. 민감정보/공유 동의 전체 문구는 주차에서만, 주행 SHARE_* prompt.text에는 전체 원문이 있으니 반드시 짧은 질문으로 바꿔 표시해 주세요.

- [x] TOOLS → CORE: demo-smoke.sh는 logcat 태그 CareVisit, 정확한 메시지 `demo started: visit`를 기다리도록 구현합니다. VisitService에서 시연 코어를 시작하고 Manual 이벤트를 넣은 뒤 개인정보 없는 Log.i 한 줄을 추가해 주세요.
- [x] TOOLS → CORE: HttpFamilyLink 경로/DTO는 relay와 일치합니다. relay 응답 누적 크기를 96 KiB로 제한해 앱의 128 KiB 한도에 맞춥니다. 계약은 tools/family/PROTOCOL.md에 기록합니다. 신규 로그와 계약 확인 후 체크 부탁합니다.
- [x] UI → CORE: 주행 glance의 예/아니요/넘어가기 버튼이 실제 동작하도록 Event.Answer(touch=true)의 주행 허용 범위를 현재 prompt.id + 고정 응답 네/아니요/넘어가로 제한해 추가해 주세요. HEALTH 민감정보 동의는 주차 화면에서만 터치 허용을 유지하겠습니다. 현재는 작은 UI adapter에서 touch=true를 유지해 우회하지 않습니다.
- [x] UI → CORE: 주차 브리핑 카드의 재공유 버튼용 Event.ShareBriefing 추가 요청. P+두 동의+session.visit/briefing 존재+prompt 없음일 때 SHARE_BRIEFING 읽기 및 개별 yes 확인을 열어 주세요(기존 ShareMemo와 같은 계약). UI adapter에 연결 예정입니다.
- [x] UI → CORE: 가족 '나왔어' 수신 상태를 notice의 문구 비교 없이 보여 주도록 ViewState/Session에 구조화된 rendezvous 상태가 있으면 좋겠습니다. 당장은 UI adapter가 기존 notice 고정 문구와 parkingMode/droppedOff/arrivedAt를 이용합니다. demo 단계 패널은 현재 phase별 AppCommand.Next/RetryVoice와 기존 확인 명령만 사용합니다.

- [x] CORE → TOOLS: VisitService가 시연 세션을 실제 state로 관찰한 직후 `Log.i("CareVisit", "demo started: visit")`를 한 번 출력합니다. 방문 원문·호칭·토큰은 로그에 쓰지 않습니다. 초기 queued Manual이 Boot 뒤 처리되도록 회귀 테스트와 함께 수정했습니다. PROTOCOL.md가 생기면 HTTP 계약도 재확인하겠습니다.
- [x] CORE → UI: Event.ShareBriefing 구현 완료. P+두 동의+visit/briefing+prompt 없음에서만 읽기/yes 질문을 엽니다. Session.rendezvous: Rendezvous(NONE, AT_GATE, PARKING, OUTSIDE)도 추가했고 도착/주차/나왔어에서 갱신합니다. ViewState.archive.session.rendezvous로 읽으세요. VisitStore.state는 실제 읽기 전용 StateFlow로 바꿨으며 기존 collect/value API는 동일합니다.
- [x] CORE → UI: 주행 터치 응답 허용 요청은 원래 사용자 요구의 “주행 중 화면은 glance state만” 및 현재 SPEC과 충돌하므로 P 전용을 유지합니다. 주행 화면에서는 짧은 질문/듣는 상태/진행도/음성 시작만 노출하고, 예/아니요/넘어가 터치 버튼은 숨겨 주세요. 음성 응답은 운행 중 정상 동작합니다.
- [x] CORE → UI: relayUrl을 바꾸면 이전 relay 삭제를 시도하고 familyAt/token을 해제하도록 강화했습니다. 주소 저장 뒤 새 가족 공유 동의/토큰이 필요합니다. 현재 API 문서에 추가했습니다.

- [x] TOOLS → CORE: 인계 정리 완료. 없는 tools/check-project.py 안내 제거, 루트 README 작성, 이전 앱 measure-app.sh 삭제, 모든 ps1 BOM 보존. PROTOCOL.md 작성 완료(기존 HttpFamilyLink와 경로·DTO 동일, 응답 96 KiB 제한). 라벨 갱신 도구는 담당 범위 밖이므로 추가하지 않았으며 README는 strings.xml 원본을 링크합니다.
- [x] UI → CORE: 주행 버튼 요청 근거는 UI에게 주어진 최신 사용자 지시 원문입니다: “Driving glance (CarUxRestrictions active): only current step ... big 예/아니요/넘어가기 touch targets ...”. 최신 명시 요구가 TASK/SPEC보다 우선하므로 이 3개 고정 응답만 허용해 주세요. 원문 입력·설정·HEALTH 동의 터치는 계속 P 전용이며 화면도 3개 이외 터치는 내지 않습니다. touch=false로 우회하지 않습니다.
- [x] UI → CORE: ShareBriefing 연결 완료, rendezvous enum으로 전환 중. 건강/공유 prompt가 생기면 동행 탭으로 복귀하며 주행에서는 모든 AskKind/Slot을 고정 짧은 문구로 치환합니다. 원문/notice/기록 없는 별도 glance 모델과 30개 JVM 테스트가 통과했습니다.
- [x] UI → CORE: 현재 Policy.HEALTH_LINE은 항상 180일이라고 읽는데 UI 설정에서 보관 기간은 바꿀 수 있습니다. RequestConsent/offer 건강 동의 음성도 현재 settings.retentionDays에 맞추거나 '설정한 기간'으로 읽어 화면(현재 N일)과 일치시켜 주세요.

- [x] CORE → TOOLS: PROTOCOL.md 경로·DTO·128비트 토큰·96KiB 응답 제한을 확인했습니다. HttpFamilyLink는 POST UTF-8 payload 길이를 setFixedLengthStreamingMode로 지정해 chunked를 쓰지 않습니다.

- [x] TOOLS → CORE: README 구조 링크의 docs/SDK_AUDIT.md가 아직 없습니다. 생성 예정이 아니라면 알려 주세요(현재 링크는 빼겠습니다).
- [x] TOOLS → UI: 최신 가족 UI의 짧은 확인 코드(8 hex)는 인증 코드가 아닌 표시용임을 확인했습니다. relay는 전체 토큰(32 hex)/전체 URL만 허용하고, README·가족 웹 안내를 전체 주소/# 뒤 코드 기준으로 맞추겠습니다. 짧은 코드 조회 API는 없습니다.

- [x] CORE → TOOLS: 라벨 one-edit 요구를 위해 tools/laptop/update-label.py처럼 TOOLS 소유 경로에 app_name XML을 읽어 README 등의 표시 라벨을 동기화하는 작은 도구를 부탁합니다. CLAUDE.md에 현재 존재하지 않는 tools/update-label.py 안내가 있어 제가 새 도구 경로로 맞추겠습니다. 이름 원본은 app_name 하나, Android AppIdentity.NAME은 resource id이며 문자열 복제는 없습니다.
- [x] CORE → TOOLS: MainActivity는 건강 기록의 screenshots/recents 유출 방지로 FLAG_SECURE를 켭니다(시연에서도). 실제 화면 live PT는 가능하지만 adb screenshot/녹화는 검정일 수 있습니다. 시연 절차에서 녹화 성공을 주장하지 말고 현장 확인 사항으로 남겨 주세요.

- [x] TOOLS → CORE: tools/laptop/update-label.py 구현. app_name XML만 원본으로 읽어 README.md·tools/laptop/README.md의 표시 라벨/APK 경로를 동기화하며 --check 지원. 3개 회귀 테스트 추가. FLAG_SECURE 녹화 제약은 두 README에 이미 반영했고 SDK_AUDIT 링크는 실재하는 SDK 레퍼런스로 대체했습니다.

- [x] TOOLS → CORE/UI: TOOLS 최종 검증: family `python3 -I -m unittest` 25개 통과, laptop/VC/라벨 도구 같은 명령 12개 통과. Chromium 390px에서 실제 HTTP 토큰 연결·메모/XSS 안전 표시·두 답장·연결 지우기/재연결·삭제 후 비우기 통과. Windows PowerShell/ADB·실제 Pleos·휴대폰 Wi-Fi는 미검증. README는 최신 “내려 드리고 주차”/“진료 끝”과 전체 URL 토큰 계약에 맞췄습니다.
- [x] UI → CORE: app/src/test/java/dev/carevisit/app/ui/GlanceInteractionTest.kt에 실제 UI 모델→Event.Answer(touch=true)→VisitMachine 통합 회귀 테스트를 추가했습니다. `visibleDrivingSkipActuallyAdvancesTheCurrentQuestion`가 위 최신 사용자 요구를 검증하며 현재 parked-only guard에서는 실패합니다. `drivingTouchCannotGrantHealthConsent`와 stale id 거절은 유지되어야 합니다.

- [x] CORE → UI: 전달해 주신 최신 사용자 지시를 반영했습니다. 주행 중 Event.Answer(touch=true)는 현재 prompt.id + 정확히 네/아니요/넘어가만 허용합니다. HEALTH는 P 전용, UNKNOWN gear 차단. SHARE_*의 주행 '네'는 VoiceState.LISTENING(전체 TTS 읽기 완료) 때만 허용하므로 그 전에는 비활성화해 주세요. Policy.healthLine(settings.retentionDays)를 추가했고 동의 TTS에 현재 기간을 넣습니다. 기존 HEALTH_LINE 상수는 호환용 기본값입니다.
- [x] CORE → TOOLS: docs/SDK_AUDIT.md 생성 완료입니다. README 링크 유지해 주세요. Android 없는 어댑터 테스트도 추가하여 `./gradlew -p visit-core test adapterTest`로 실제 VoiceAgent/UiContract/HttpFamilyLink와 release surface JVM 테스트를 실행할 수 있습니다. Android Activity/Compose/SDK 구현 컴파일을 대체하지는 않습니다.
- [x] UI → CORE/TOOLS: UI 구현·인계 완료. VisitScreen/VisitTheme/VisitComponents/VisitDocuments/VisitSettings/VisitPreviews/AppModels와 UI 테스트 4개 클래스를 추가·정리했습니다. 주행 고정 3응답은 touch=true 유지, UNKNOWN/HEALTH 차단, SHARE_* yes는 LISTENING까지 비활성화. ShareBriefing과 typed Rendezvous 연결 완료. `bash app/src/test/java/dev/carevisit/app/ui/run-model-tests.sh` 최종 38개 전부 통과(실제 UI→core 전이 포함). Android/Compose 빌드·픽셀·IME·실제 접근성/VC는 노트북에서 미검증이며 ui/README.md와 7개 Preview에 점검 항목을 남겼습니다. 커밋 없음, app_name 리소스 변경 없음.

- [x] CORE → UI/TOOLS/PLAN: CORE 최종 검증 완료. `./gradlew -p visit-core test adapterTest --console=plain` BUILD SUCCESSFUL, core 78개 + 실제 Android-free app adapter/release test 11개, 실패/오류 0. UI 공개 API는 마지막 공지와 동일합니다. 동의 전 목적지는 메모리에만 있고, 저장소 오류 중 차량 이벤트로 인터뷰를 재개할 수 없으며, 귀가 인터뷰를 거절해도 가는 길 메모는 history에 보존합니다. docs/product/SPEC.md·ARCHITECTURE.md(UI 계약 포함)·SDK_AUDIT.md·CLAUDE.md 갱신 완료. Android/Compose/SDK/merged manifest/release signing/APK는 서버에서 검증하지 않았으며 노트북 확인이 필요합니다. 커밋하지 않았습니다.
- [x] UI → CORE/TOOLS: 노트북 compileDebugKotlin 오류 수정 완료. VisitSettings.kt의 호칭/보관기간 중첩 forEach에서 누락된 닫는 중괄호 2개를 복구하고 블록을 펼쳤습니다(Field가 지역 함수가 된 원인). 입력 람다는 value 매개변수를 명시했습니다. UI 전체 7파일 Kotlin 파서 검사 0오류, 앱 BOM의 Compose 1.7.3/Material3 1.3.0 + 공식 API34 android.jar + 실제 Kotlin/Compose compiler 2.0.0으로 전체 UI 소스 컴파일 성공. 재실행 도구: app/src/test/java/dev/carevisit/app/ui/check-compose-compile.py. AGP/APK/리소스 생성·실제 렌더링은 노트북 확인 대상입니다. CORE 파일 변경 없음. 현재 열린 UI 요청 없음.
- [x] LEAD → CORE: MainActivity에 시연 모드에서만 FLAG_SECURE를 푸는 SecureWindowPolicy를 넣었습니다(캡처·녹화용). 시연 모드가 실제 기록(archive)을 절대 보여 주지 않는지 확인하고, 보일 수 있으면 시연 중에는 실제 archive 대신 시연 데이터만 ViewState에 싣도록 고치고 테스트를 추가해 주세요.
- [x] CORE → LEAD/UI: 시연 저장소의 실제 파일 접근 금지·재진입 초기화·실제 기록 보존을 VisitRepositories로 검증했습니다. 전환 시 빈 ViewState 발행, Activity의 모드별 key로 기억된 입력 폐기, 실제 모드 복귀 시 FLAG_SECURE 적용 전 archive/notice 차단을 추가했습니다. 시연 해제 전 실제 데이터 노출 2개 및 대기열 명령의 모드 혼입 2개 테스트가 먼저 실패한 뒤 수정했습니다. AppCommand.Core(event)는 호환 유지하며 선택 인자 sourceDemo를 추가해 이전 화면의 Configure/DeleteAll을 다른 모드에서 거절합니다. FLAG_SECURE 설정/해제 줄 자체는 그대로이며 시연 진입 시 두 frame 경계 대기만 추가했습니다. `./gradlew -p visit-core adapterTest --console=plain`: 20개 통과(새 DemoPrivacyTest 7개 포함). UI 소유 파일 변경 없음. Android 빌드·실제 화면 전환 캡처는 노트북 확인이 필요합니다.
