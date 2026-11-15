# 진료 기록 앱 구조

## UI 계약

### 심사 개선 계약 (2026-11-15)

- enum은 유지한다. `Prompt.confirming=true`는 약/예약 원문 확인 단계이며 `kind=SLOT`, `slot`은 해당 항목 그대로다. `correction=true`는 이미 한 번 정정을 요청한 상태다. `Event.Answer`로 네/맞아요는 원문 확인, 아니요/다시 말할게요는 최초 한 번 재입력, 넘어가/확인 필요는 미확인 상태로 진행한다. 주행 화면은 원문을 노출하지 않는 짧은 확인 문구를 사용한다. 재시작·RetryVoice에도 플래그와 재질문 횟수가 유지된다.
- `SlotValue.needsConfirmation`은 약/예약 STT 확인 상태다. 재입력한 새 슬롯 값만 현재 값으로 연결하고 이전 Utterance와 ID는 보존한다. 확인되지 않은 값을 템플릿에 넣으면 `(확인 필요)`를 붙인다. `Appointment.needsConfirmation`은 날짜/시간 파싱의 모호함까지 포함하므로 “맞아요” 후에도 true일 수 있다. 오전 10시 반=10:30, 복수/정정 후보는 자동 확정·알림 설정하지 않는다.
- `Session.returnPending=true` + `Phase.WAIT_SAFE`는 귀가 대기다. 체류15분 뒤 P→D는 이 상태만 만들고 발화하지 않는다. D+유효 차속>0.5m/s의 별도 누적60초 후 현재도 움직이는 D일 때 POST_OFFER를 묻는다. P+제한 해제 시 기존 `Event.PrepareParked`로 명시 시작할 수 있다. UNKNOWN/OTHER(후진 포함)는 새 자동 제안을 시작하지 않는다.
- `Hospital.address`(기본 빈 문자열), `Session.pendingHospital: Hospital?` 추가. 목적지 변경 후보는 P에서 `Event.ConfirmDestination`으로 승인하면 이전 부분 visit를 visits에 보존하고 새 세션을 만든다. `Event.DismissDestination`은 후보만 숨긴다. 주행 중 두 명령은 무시한다. 기존 병원 기록을 새 병원 이름으로 덮어쓰지 않는다.
- 서비스만 `Event.NavigationArrived(name,address)` / `Event.NavigationCleared`를 사용한다. 키트는 레퍼런스의 DestinationArrivedInfo.name/address를 그대로 전달한다. 현재 목적지·방문과 이름이 같고, 알고 있는 주소도 같을 때만 자동 후보로 인정한다. 변경/취소/연결 오류는 후보를 지우고, 30초 안에 P가 되지 않으면 만료한다. 200m+경로 종료 휴리스틱은 제거했다. UI의 기존 `Event.Arrived`는 명시적 수동 확인 경로다.
- `DemoTrip`은 같은 가상 병원의 Destination/NavigationArrived를 주입하고, 귀가 WAIT_SAFE에서 실제 D+움직임이 확인되면 딱 한 번 60초를 시간 이동한다. 실제 모드의 대기는 단축하지 않는다. DemoAnswers의 띄어쓰기는 유지하고 확인 프롬프트에만 “맞아요”를 추가했다.

### 시연 가족 연결 (2026-11-15 추가)

- 시연 시작 시 네트워크 없는 `DemoFamilyLink`가 자동으로 붙는다. 건강/공유 동의는 자동 승인하지 않는다. 실제 relay 주소·토큰·동의는 가져오지 않는다.
- 시연 공유 동의 화면에서 `Policy.FAMILY_LINE`과 “시연용 메모리 연결 · 외부 전송 없음”을 표시하고 사용자가 동의한 뒤 `VisitStore.commands.trySend(AppCommand.DemoFamilyConsent)`를 보낸다. `state.demo && state.fullScreen && state.archive.consent.healthAt != null`일 때만 제공한다. 실제 성공 여부는 기존 `archive.consent.familyAt != null`로 확인한다. 토큰 생성은 서비스 담당이다.
- 동의 뒤 기존 `VisitStore.send(Event.ShareMemo)` / `VisitStore.send(Event.ShareBriefing)` 사용. 동의 버튼 자체는 메모/브리핑을 보내지 않는다. 코어가 내용을 읽고 개별 yes 확인을 받은 뒤에만 전송한다. 전송 완료는 기존 `notice = "가족 화면에 전달했어요."`, 약 3~4초 뒤 자동 답장 `"잘 받았어"`는 코어가 `"가족이 잘 받았대요"`로 읽고 notice에 표시한다. UI는 시연 가족 답장임을 구분해서 표시한다.
- 정문 답장 단계: `VisitStore.commands.trySend(AppCommand.DemoFamilyOutside)`. 시연 + 건강/가족 동의 + `AT_HOSPITAL` + `session.parkingMode`일 때만 동작한다. 먼저 기존 `Event.ParkingMode`를 사용한다. 같은 메모리 연결에서 `"나왔어"`를 수신하고 기존 `session.rendezvous == OUTSIDE` 및 `notice`/TTS를 갱신한다. `AppCommand.Next`도 AT_HOSPITAL에서 이 답장을 요청한 뒤 체류 15분을 진행한다.
- 두 DemoFamily 명령은 실제 모드에서 무시한다. 연결 해제/삭제는 기존 `Event.FamilyConsent(false)` / `Event.DeleteAll`; 시연 종료 시 전송 내역과 대기 답장이 폐기된다. UI 전용 상태 필드는 추가하지 않는다.

2026-11-15 CORE→UI 인계. 이 절의 기존 심볼은 호환성을 유지한다. 추가·변경은 `docs/coord/requests.md`에 알린다.

- 화면 진입점: `dev.carevisit.app.ui.VisitScreen(state: ViewState, platformRestricted: Boolean)`. `MainActivity`가 서비스의 `VisitStore.state`와 독립 `UxRestrictionWatcher`를 lifecycle-aware collect하여 전달한다.
- `dev.carevisit.app.UiContract.kt`: `AppIdentity.NAME`은 `R.string.app_name` resource id. `LaunchExtras.pick`은 `demo=visit/off`만 허용한다. 이 파일과 Activity/서비스는 CORE 소유다.
- 상태: `VisitStore.state` (`StateFlow<ViewState>`처럼 읽기 전용으로 사용). 쓰기 주체는 서비스뿐이다. UI는 state.value를 직접 수정하지 않는다.
- 명령: `VisitStore.send(Event)` 또는 `VisitStore.commands.trySend(AppCommand)`를 사용한다. commands는 구독 전에도 명령을 보존하는 Channel이다. `AppCommand.Core(event)`, `AppCommand.Demo(enabled)`, `AppCommand.Next`가 있다. UI는 새 SDK/HTTP/저장소 인스턴스를 만들지 않는다.
- `AppCommand.Core`는 호환되는 선택 인자 `sourceDemo`를 추가로 가진다. 기본값은 Activity의 SideEffect가 기록한 **화면에 반영된 모드**다. 서비스는 현재 코어 모드와 다른 출처의 명령을 버린다. 따라서 전환 직전 화면에서 보낸 Configure(저장 병원 등)가 대기열을 거쳐 시연 코어에 들어가거나 시연의 DeleteAll이 실제 기록을 지우지 않는다. UI 호출부 변경은 필요 없다.
- `ViewState`(core): `archive`, `restricted`, `gear`, `voice`, `notice`, `demo`, `fullScreen`. `fullScreen`은 **기어 P이면서 서비스 제한 해제**일 때만 true다. UI는 여기에 `!platformRestricted`까지 확인해야 상세 화면·키보드를 표시할 수 있다. 모르는 신호는 제한 상태다.
- 시연 격리: 서비스는 모드 전환 직후 빈 `ViewState(demo=enabled)`를 발행하고 이전 collector/core를 취소한다. `VisitRepositories.open(true)`는 실제 저장소 factory를 호출하지 않고 매번 빈 메모리 archive를 만든다. 실제 동의·기록·설정·가족 토큰을 시연 상태에 복사하지 않는다. Activity는 `key(state.demo)`로 화면의 기억된 입력/선택도 폐기하며, `SecureWindowPolicy.visibleState`를 거쳐 캡처 차단 적용 전의 실제 archive/notice는 빈 상태로 전달한다. 기존 UI API는 그대로다.
- `Archive`: `consent.healthAt/familyAt`(epoch ms 또는 null), `settings`, 완료된 `visits`, 현재 `session`. Session에는 `phase`, `visit`, `slotIndex`(0..4), `arrivedAt`, `droppedOff`, `parkingMode`, `prompt`, `briefing`이 있다. `Prompt`의 `id`는 응답 상관관계 키다. `resumePhase`는 복구용 내부 값이며 UI가 수정하지 않는다.
- `Visit`: `id`, `hospital(name, latitude?, longitude?, routable)`, `createdAt`, `slots`, `appointment`, `reminder`, `checkedQuestions`, `complete`. 슬롯은 `List<SlotValue(utteranceId, verbatim, normalized?)>`이며 화면은 `verbatim`을 우선 표시한다. 체크리스트 키는 `utteranceId`다. `Templates.memo(visit)`/`briefing(visit)`, `Dates.display(appointment)`는 순수 함수다.
- 화면 건강 원문: `slots`, `utterances`, `briefing`, 과거 기록, 긴 notice는 주차 상세 화면에서만 읽는다. 주행 화면에는 짧은 현재 질문/듣는 상태/진행도만. `SHARE_*` 질문의 `Prompt.text`에는 전체 메모가 있으므로 그대로 주행 화면에 넣지 말고 “가족에게 보낼까요?”로 표시한다.

| UI 동작 | 보낼 명령 | 코어 제한 |
|---|---|---|
| 병원 가는 길 | `Event.Manual(Hospital(...))` (기본 “병원”) | 진행 중 세션을 덮어쓰지 않음 |
| 주차 중 준비 시작 | `Event.PrepareParked` | P + 제한 해제 |
| 동의 안내 듣기 | `Event.RequestConsent` | P + 제한 해제, 아직 건강 동의 없음 |
| 화면 응답 | `Event.Answer(prompt.id, "네"/"아니요"/"넘어가"/"또 있어", touch=true)` | 현재 prompt.id만 수락. P 상세에서는 일반 응답, 주행에서는 정확히 네/아니요/넘어가만 허용. HEALTH 터치는 P 전용. 주행 공유 yes는 LISTENING(전체 읽기 완료) 이후만 |
| 말로 답하기/다시 듣기 | `Event.RetryVoice` | 현재 질문을 다시 말한 뒤 STT |
| 자유 명령 음성 시작 | `Event.CommandListen` | 현재 질문이 없을 때만 |
| 정문 도착 수동 확인 | `Event.Arrived` | P일 때 도착 처리 |
| 내려 드리고 주차 후 합류 | `Event.ParkingMode` | P, 건강 동의, AT_HOSPITAL |
| 브리핑 다시 공유 | `Event.ShareBriefing` | P, 두 동의, visit/briefing 존재, prompt 없음; 정확한 문구 재확인 |
| 메모 공유 | `Event.ShareMemo` | P, 두 동의, 별도의 읽기·yes 확인 |
| 물어본 것 체크 | `Event.Checked(utteranceId)` | P, 실제 QUESTION 슬롯의 ID |
| 재시작 이어가기/폐기 | `Event.Resume` / `Event.Abandon` | P; RECOVERY 화면에서 선택 |
| 설정 저장 | `Event.Configure(settings.copy(...))` | P; retention 1..365, 호칭 allowlist; 토큰은 이 명령으로 바꿀 수 없음 |
| 가족 공유 동의 | `Event.FamilyConsent(true, token)` | P, 건강 동의, token은 SecureRandom 16 bytes의 소문자 hex 32자 |
| 가족 연결 끄기 | `Event.FamilyConsent(false)` | P; relay 삭제와 로컬 연결 해제 |
| 전체 삭제/동의 철회 | `Event.DeleteAll` / `Event.Withdraw` | 동의·기록·진행 중 음성 제거, relay 삭제 시도 |
| 시연 켜기/끄기 | `AppCommand.Demo(true/false)` | 서비스가 별도 메모리 저장소 사용 |
| 시연 다음 | `AppCommand.Next` | 시연에서만; 도착 또는 체류 15분 진행, 실제 기어·제한은 유지 |

`Settings`의 호환 필드: `honorific`(어머니/아버지/부모님/보호자), `retentionDays`(기본180), `polish`(기본false), `hospitals: List<Hospital>`, `relayUrl`(기본 http://10.0.2.2:8765), `familyToken`. 가족 URL의 노트북 LAN 주소는 화면에서만 조합할 수 있으며 위치 API를 사용하지 않는다.

음성 상태는 `IDLE`, `SPEAKING`, `PREPARING`, `LISTENING`, `UNAVAILABLE`. 듣는 표시는 kit의 onReady에서만 시작한다. `demo=true`일 때 정확한 표시 문구는 “시연: 미리 정한 대답 · 가족 답장 모의”이며 실제 입력은 허구 scripted STT다.

계약 추가: `Session.rendezvous`는 `Rendezvous.NONE/AT_GATE/PARKING/OUTSIDE`다. 가족 “나왔어”를 받았는지 notice 문자열을 분석하지 말고 이 값을 사용한다. `Configure`로 relayUrl을 변경하면 이전 relay 삭제를 시도하고 가족 동의·토큰을 해제한다. 새 URL에서는 별도 가족 동의를 다시 받아야 한다. 최신 UI 사용자 지시에 따라 주행 중에는 현재 질문의 네/아니요/넘어가만 touch 응답으로 허용한다. HEALTH 동의와 자유 입력은 주차 전용이고 UNKNOWN 기어에서는 touch 응답을 받지 않는다.

## 모듈과 실행 경계

`visit-core`는 독립 Kotlin JVM composite build다. `./gradlew -p visit-core test`가 Android SDK 없이 실행된다. Kotlin 2.0.0, coroutines 1.8.1, kotlinx serialization 1.7.1을 사용한다. 앱은 `dev.carevisit:visit-core:0.1.0` 좌표를 composite substitution으로 참조한다. Android 빌드는 쉼표와 같은 AGP 8.8.2 / Kotlin 2.0.0 / Compose BOM 2024.09.03 / API34다.

- `Records.kt`: 불변 기록·동의·설정·슬롯·발화·화면 상태. 필수 원문 ID를 포함하며 직렬화 가능하다.
- `Korean.kt`: 좁은 한국어 명령 allowlist, 기간, 날짜, 고정 안전 문구 트리거, 보수적인 polish guard. 입력을 일반 의료 지식으로 분류하지 않는다.
- `VisitMachine.kt`: `(현재 상태, 사건, 시각) → 다음 상태 + 효과 목록`. Android·코루틴·파일·HTTP가 없다. 요청 ID가 다른 응답은 버린다.
- `VisitCore.kt`: Channel 우편함을 읽는 단일 액터. 초기 Boot 복구를 외부에서 미리 대기한 명령보다 먼저 처리한다. 한 전이의 작은 파일 트랜잭션을 완료한 후에만 외부 효과를 시작한다. 저장 실패 시 전이 이전 상태로 돌아가고 새 인터뷰·공유를 차단한다. 동의 전 목적지/세션은 메모리에서만 유지하고 저장 snapshot에서 제거한다. 저장소 오류 중에는 차량 이벤트로 주차 UI를 열 수만 있으며 도메인 전이·음성/전송 효과를 실행하지 않는다.
- `Ports.kt`: `Repository`, `SpeechPort`, `FamilyLink`, `Polisher`, `Navigator`. 테스트는 이 포트에 가짜를 넣고 virtual time을 진행한다.
- `Templates.kt`: 고정 슬롯 순서와 질문/메모/브리핑. 의료 문장 생성 경로가 없다.
- `AtomicRepository.kt`: 하나의 JSON 파일로 동의와 기록을 함께 저장한다. temp write → fd.sync → ATOMIC_MOVE+REPLACE_EXISTING. 읽기 실패는 동의를 복구한 것처럼 취급하지 않는다. 미완료 temp는 읽기 때 지운다. schema와 모든 원문 연결을 검사한다.

## 동시성, 취소와 장애

쉼표의 actor/ports 구조를 따른다. 다만 건강 정보는 “동의 저장에 실패했는데 인터뷰는 시작되는” 상태를 막아야 하므로 코어의 작은 저장 트랜잭션은 suspend로 끝까지 기다린다. 이 대기 중에도 별도 SDK/신호 코루틴은 실행된다. 긴 TTS/STT·내비 요청·HTTP·LLM은 액터 밖 child job으로 실행한다. 공유·삭제 HTTP는 별도 mutex로 직렬화한다.

철회/전체 삭제/relay 주소 변경은 효과 세대를 증가시키고 진행 중 효과를 취소한다. 이전 세대의 음성 결과·전송 결과는 새 상태에 적용하지 않는다. 현재 질문의 prompt ID와 polish 대상 visit ID/source도 확인한다. 이미 네트워크로 나간 전송은 철회로 되돌릴 수 없으므로 이전 연결 토큰으로 relay DELETE를 이어서 시도한다. 실패 시 로컬 삭제 성공과 relay 삭제 실패를 구별해 알린다.

음성 어댑터는 한 번에 하나의 질문을 TTS 완료→마이크 준비→onReady→12초 듣기 순서로 수행한다. TTS가 중단됐으면 듣기를 시작하지 않고 해당 질문을 남긴다. kit의 Turns가 요청 ID 없는 SDK의 늦은 종료 이벤트를 이전 요청과 묶는다. `AudioFocusKit`은 음성 포커스를 얻고 손실 시 TTS/STT를 멈춘다. 자동 재생 복귀는 하지 않는다. 실제 Pleos 음성 서비스와 오디오 포커스가 어떻게 상호작용하는지는 emulator 확인 대상이다.

## Android 연결

`VisitService : PleosForegroundService`가 SDK 구독을 소유한다. 작업은 단일 parallelism의 서비스 dispatcher에서 조율하고, SDK 연결/호출은 kit의 전용 executor에서 이뤄진다. Activity가 내비 뒤로 가도 foreground service가 살아 있다. Activity는 SDK 음성·차량 호출을 하지 않고 `UxRestrictionWatcher`와 UI만 연결한다.

- AAOS `CarState`: API34 `GEAR_SELECTION`, `PERF_VEHICLE_SPEED`, UX 제한. dangerous CAR_SPEED는 Activity가 런타임 요청한다. FusedLocation은 사용하지 않으므로 해당 권한을 선언하지 않는다.
- `VehicleKit.passengerDoor()`: `checkDoorPositionCapabilities`로 오른쪽 앞문 지원 확인 후 `doorPosition`을 구독한다. 좌석 점유나 차량 제어 권한은 없다.
- `VisitNaviKit`: 기존 `NaviKit`을 통해 목적지, guiding, 도착 이벤트, 저장된 좌표의 route를 제공한다. SDK 타입은 kit 밖 앱 코드에 노출하지 않는다. 도착 name/address와 목적지 address를 보존하며, route cancel/destination change/error는 invalidation으로 전달한다. 서비스는 무효화 세대가 바뀐 동안 대기한 목적지 조회 응답을 버린다. SDK에는 도착의 경로 ID/발생 시각이 없어 동일 이름·주소의 반복 경로를 완전히 구분할 수는 없다. 조회는 20초 간격이며 kit의 직렬 요청 규칙을 유지한다.
- `VoiceAgent`: kit `SpeechInput/Output` + optional focus. demo도 실제 TTS를 끝까지 실행한 뒤 허구 STT 답을 반환한다.
- `HttpFamilyLink`: 명시적 id/kind/text JSON만 쓴다. redirects를 따르지 않아 bearer token을 다른 호스트로 넘기지 않는다. JSON 응답은 128KiB로 제한하고 전체 데이터 serializer를 사용하지 않는다.
- `FamilyConnection`은 app 안에서 `FamilyLink`에 poll/close를 더한 인터페이스다. 서비스는 세션마다 `FamilyLinks.open`으로 실제 HTTP 또는 시연 메모리 연결 중 하나를 고르고, 동일 인스턴스를 코어 전송과 응답 조회에 쓴다. 시연에서 실제 어댑터 factory는 호출하지 않는다. `DemoFamilyLink`는 승인 DTO와 고정 답장만 보관하며 중복 전송 ID는 한 번만 처리한다. 종료/모드 전환 시 poll job을 취소·합류하고 기존 코어를 취소한 뒤 close로 메모리를 비운다. 이전 세션의 결과를 새 코어에 주입하지 않는다.
- `LlmKit`: 제한된 공백 정리. 기존 punctuation 메서드 이름은 호환용이며 기호 변경을 허용한다는 뜻이 아니다. LLM SDK 2.1.3.2 ON_DEVICE, 별도 스레드, 요청별 listener/instance, 8초 timeout. 늦은 callback은 취소된 요청을 되살리지 않는다. 서버 등록·키·자유 의료 생성은 없다.

`UxRestrictionWatcher`와 서비스 제한을 모두 확인한다. 모름을 false로 바꾸지 않는다. 라이브 기록의 screenshots/recents 유출을 막기 위해 Activity는 FLAG_SECURE를 사용한다. 시연에서만 캡처를 허용한다. 시연 진입은 새 화면 트리를 만든 뒤 두 번의 Compose frame 경계를 기다려 이전 실제 화면을 교체하고 차단을 해제한다. 시연 종료는 차단 설정 호출이 완료된 뒤 실제 기록을 화면에 전달한다. 전환 중 mode가 바뀌면 기존 LaunchedEffect는 취소된다. 실제 에뮬레이터의 화면 합성·녹화 타이밍은 노트북 검증 대상이다.

## 재시작, 보관과 시계

완료된 기록은 `Archive.visits`, 진행 중 기록은 `Archive.session`에 있다. `RECOVERY.resumePhase`와 원래 Prompt를 함께 저장하므로 재시작을 연속으로 해도 복구할 질문이 사라지지 않는다. 주차 후 명시적으로 이어가거나 폐기한다. 전송 job은 저장하지 않으므로 앱을 다시 켜도 자동 재전송하지 않는다.

보관·예약·체류의 기준은 wall clock(epoch ms, 날짜는 Asia/Seoul)이다. 실제 주행 60초는 monotonic clock의 틱·차량 사건 간 간격으로 합산하며 한 번의 scheduling gap은 최대60초로 제한한다. 시스템 시계가 크게 바뀐 경우 병원 체류의 자동 제안을 다시 확인해야 하는 한계가 있다. 차가 꺼져 있으면 파일을 지울 프로세스가 없으므로 다음 서비스 시작 시 만료 내용을 노출하기 전에 purge한다. 원자적 rename은 논리적 삭제이며 flash의 물리적 삭제 보장은 아니다.

시연은 별도 메모리 Repository와 시간 offset만 사용한다. 실제 파일에 시연 동의·발화·병원을 쓰지 않는다. 전환 시 코어 job을 취소하고 실제 기어·도어·제한을 새 코어로 즉시 전달한다. 화면 명령은 Channel에 남아 서비스가 시작되기 전 입력도 잃지 않는다.

## 검증과 PT 설명 포인트

코어 테스트는 실 SDK 대신 포트를 사용하여 동의, 60초/15분, 발화 연결, 재질문/건너뛰기, 중복 응답, 가족 승인, 철회, 보관, 8초 fallback, 파일 복구를 검증한다. 파일 테스트는 실제 임시 디렉터리와 원자적 rename을 사용한다. 앱 JVM 테스트는 화면 계약, release 권한/extra 표면, TTS 중단과 onReady 표시, launch Channel을 검증한다. 앱·kit JVM 테스트도 Android Gradle plugin 구성에는 SDK가 필요하므로 노트북에서 실행해야 한다.

범위: 단일 돌봄 대상만 지원하며 호칭 변경은 대상별 동의/기록 전환이 아니다. 도어 열림은 하차 완료 판정이 아니다. 알림은 서비스 실행 중 Tick에서 처리하며 차 시동 감지나 꺼진 차량 깨우기가 아니다. 승인 DTO의 글에는 STT 슬롯 원문이 포함될 수 있다. 시연에서 TTS SDK 요청·VC 기어/속도/도어·UX 제한은 연결 경로이고, STT 대답·목적지/도착·시간 이동·가족 답장은 모의다. 가청 출력과 실제 내비/마이크 성공은 별도 증거가 필요하다.

기술 Q&A의 핵심은 “음성 인식 오류를 의료 추론으로 보정하지 않는다”, “동의·저장 성공 뒤에만 효과를 실행한다”, “공유 포트가 전체 기록/위치를 받을 수 없다”, “LLM의 실패가 제품을 멈추지 않는다”, “차량 신호를 모르면 상세 화면을 잠근다”이다. 어떤 SDK가 실제로 전달하는지는 단위 테스트로 입증할 수 없으며 [SDK_AUDIT.md](SDK_AUDIT.md)의 미검증 목록과 구분한다.


Android 없는 어댑터 검증: `./gradlew -p visit-core adapterTest`는 실제 `app/src/main`의 VoiceAgent/UiContract/HttpFamilyLink/FamilyConnections 및 시연 저장소·화면 정책, 실제 app/src/test의 release/voice/store/HTTP/시연 개인정보·가족 통합 테스트, kit의 Android 없는 SpeechContracts를 별도 JVM source set으로 컴파일한다. Android API stub을 만들거나 Activity/Compose를 검증한 것처럼 취급하지 않는다. 이 작업은 core의 runtime 의존성이나 APK 소스셋을 바꾸지 않는다.

## 최종 CORE 검증 기록 (2026-11-15)

`./gradlew -p visit-core test adapterTest jar --console=plain` — BUILD SUCCESSFUL. 코어 **99개**, 실제 Android 없는 앱 어댑터/release 표면 **31개**, 실패/오류 0. 최종 실행에 `-q`를 사용하지 않았다. XML 결과는 `visit-core/build/test-results/test/`와 `adapterTest/`에 생성된다(빌드 산출물, git 제외).

특히 동의 전 병원 목적지의 파일 저장 금지, 저장 실패 중 차량 이벤트의 인터뷰 우회 금지, 두 번 재시작한 질문 복구, 초기 Channel 명령과 Boot 순서, 주행 고정 응답의 HEALTH 차단, 귀가 인터뷰 거절 뒤 준비 메모 보존을 회귀 테스트로 확인했다. 이 결과는 Android/Compose/SDK 연결의 컴파일·실행 성공을 뜻하지 않는다.

심사 개선 회귀: 숫자/단위 기호 변경 거절, 반 시각·복수/충돌 후보, 약/예약 원문 읽기·정정 실패·재시작 정정 한도·원문 ID 보존, 귀가 누적60초/UNKNOWN·후진 차단, 목적지 변경·취소·늦은 도착·후보 만료·기존 메모 보존, 실제 DemoAnswers를 쓰는 인터뷰→정문→나왔어→귀가→브리핑 전송→수신 답장 전체 흐름을 검증했다. 시연 테스트의 SpeechPort는 JVM fake이며 실제 TTS SDK 성공 증거가 아니다. 이번 변경 이후의 Android/키트 컴파일과 실제 내비 콜백 순서는 노트북 재검증 대상이다.
