You are building a NEW Android Automotive (Pleos Connect) app in /home/opc/orca/projects/hmg-visit (fresh git repo, nothing
committed). It is a second entry for the Hyundai Motor Group in-vehicle app contest "Code your App into Pleos App Market".
Quality bar: a real, working, well-tested app that will be demoed live on the Pleos emulator in a technical PT. Not a mock.

## Start by reading
- `CLAUDE.md` — project rules + every hard-won emulator/SDK fact from our first app. Its last section "## 이 앱: 올라가기 전 10분"
  describes an abandoned app: replace that section with this app's facts (keep all general SDK/emulator/laptop sections).
  Change every `dev.tenmin.app` / `repo-10min` / `tenmin` mention to this app's names (below).
- `pleos-kit/README.md` and its sources — our SDK wrapper (dedicated threads, foreground service, control result checks, Turns
  for TTS/STT, listen window from onReady). App code uses pleos-kit, never the SDKs directly. Extend kit via VehicleKit.call/observe.
- `docs/contest/03_SDK_API_레퍼런스/` — grep EVERY SDK signature you use. Never invent APIs. For AAOS standard props use
  android.car.VehiclePropertyIds names that exist at API 34.
- `docs/contest/02_Pleos_개발가이드/connect-guide/docs-design/` (UI design guide) and the driver-distraction / driving-restriction
  guides under `docs/contest/02_Pleos_개발가이드/pleos-connect/`.
- Read-only reference for a proven layout: /home/opc/orca/projects/hmg-appdev-challenge (쉼표): settings.gradle.kts,
  app/build.gradle.kts (release signing from keystore.properties), rest-engine/coach (actor core `CoachCore`, ports,
  virtual-time tests), app/ (Compose UI, foreground `CoachService`, adapters implementing ports with kit, `LaunchExtras`/`DevHooks`
  release-surface rules, `ReleaseSurfaceTest`, `UxRestrictionWatcher` use, `VoiceAgent`, `IntentParser` for noisy Korean STT),
  docs/ARCHITECTURE.md. Mirror the architecture; do not copy 쉼표 features. Do not modify that repo or /home/opc/orca/projects/hmg-10min.
- tools/laptop/ was copied from the abandoned app: adapt names (package below, laptop folder `repo-visit`), delete scripts that
  only made sense for the old apps. Keep `.ps1` UTF-8 with BOM.

## The app: working label "진료동행", package/applicationId `dev.carevisit.app`, core module `visit-core` (package dev.carevisit.core)
Keep the label in ONE place (strings.xml `app_name` + a constant used by docs/README) so it can be renamed in one edit.

Moment: an adult child drives an elderly parent to a hospital appointment (the parent in the passenger seat). The 20–40 minute
ride there and back is the quiet time when the family could prepare for and remember the visit, but today the questions are
forgotten in the 3-minute consultation and afterwards siblings ask "의사가 뭐래?" by phone. Evidence (cite as given in docs):
서울 병원안심동행 서비스 이용 649건(2021) → 23,048건(2025) (한국경제). Value: the car interviews the parent by voice on the way
(no screen, no typing — things a phone in a pocket can't do while driving) and turns it into a visit memo, then on the way home
records what the doctor said and, with consent, shares a briefing with family.

### Hard principles (write them in SPEC and enforce them structurally)
1. NO medical judgment. The app never diagnoses, triages, interprets symptoms, suggests drugs or doses, or says whether something is
   serious. It only records what was said, in the speaker's own words, organised into fixed slots. No free generation of medical
   content. Every slot value must be traceable to a transcript utterance (store the utterance id). If someone says something
   alarming (e.g. 가슴이 아파, 숨이 차) the app does NOT assess it; it may only say the fixed line "불편이 심하면 진료 전에 병원에
   바로 말씀하세요" — and a fixed safety line for 119 only when the user literally asks for emergency help ("119", "응급실 가자").
2. Health info is sensitive personal information (개인정보 보호법 제23조): separate explicit consent before the first interview
   (spoken + on-screen when parked), stored only on the device (app-private storage), retention limit (default 180 days, configurable,
   auto-purge), one-tap delete-all. Sharing to family needs its own consent at setup AND a per-briefing confirmation ("가족에게
   보낼까요?"). Family relay receives only the briefing text the user approved; never location, never raw transcripts.
   Use 호칭 (e.g. "어머니", "아버지", configurable) — never real names anywhere (code, docs, demo data).
3. LLM is optional polish, never required. The core produces complete memos/briefings deterministically from slots. A
   `Polisher` port may rewrite the assembled text for fluency (adapter: Pleos LLM SDK ai.pleos.playground:LLM 2.1.3.2, ON_DEVICE —
   it did NOT respond on our emulator (40 s), so: hard timeout ~8 s, fallback to the template text, off by default, setting toggle).
   A polished text is accepted only if a deterministic check passes: every number/date/drug-like token and every body-part word in
   the output appears in the source slots, and length ≤ 1.5× source; otherwise use the template. Unit-test this guard.

### Flow (state machine in pure-Kotlin `visit-core`, JVM-tested with virtual time)
1. Trip detection: a visit trip starts when (a) Pleos navi destination looks like a hospital (NaviHelper `getDestinationInfo` name
   contains 병원/의원/의료원/클리닉/한의원/치과/보건소 or matches a saved regular hospital), or (b) the user says/taps "병원 가는 길".
   Then once, at a safe moment (not during a turn: use TBT distance like 쉼표 if cheap, else simply after ≥ 1 min of driving),
   TTS: "어머니 진료 가시는 길이죠? 의사 선생님께 드릴 말씀을 차에서 정리해 둘까요?" and listen (yes/no). Ask only if consent given;
   if consent not yet given, ask for it first (short spoken consent line + record of consent time).
2. Pre-visit interview (가는 길): fixed slots asked one by one by voice, each with a short question, listen window, one re-ask on
   silence/unclear, skippable ("넘어가", "몰라", "없어"):
   - 어디가 불편하세요 (주 증상, body-part dictionary + verbatim)
   - 언제부터요 (duration: "사흘 전부터", "일주일", "지난달", "어제부터" → normalised period + verbatim)
   - 어떨 때 더 그러세요 / 어떻게 아프세요 (verbatim)
   - 요즘 드시는 약이나 바뀐 게 있으세요 (verbatim; drug names are NOT validated or interpreted)
   - 의사 선생님께 꼭 물어볼 거 있으세요 (repeatable: "또 있어" → next question; up to 5)
   Plus continuity: if the previous visit to the same hospital recorded "다음에 확인할 것" or a medication change, ask first:
   "지난번에 ○○ 바꾸셨는데, 그 뒤로 어떠세요?" (verbatim). Noisy Korean STT must be handled: '응/네/어/그래', '아니/아니요/없어',
   '넘어가/다음', numbers in words ('사흘', '일주일', '이틀', '열흘', '한 달'). Write parser tests from realistic STT outputs
   (dropped endings, spacing errors — see CLAUDE.md STT section).
   Result: 진료 메모 card (parked screen, large type, readable to show the doctor) and kept for the 진료실: "물어볼 것" checklist.
3. Arrival & drop-off (정문 하차): when the destination is reached (navi route ends / arrival) and gear P: if the passenger door opens,
   say "천천히 내리세요. 진료 메모는 가족 화면에서도 볼 수 있어요" (only if family sharing is on). Offer "정문에서 내려 드리고 주차
   후 합류" mode: driver parks; the companion (or parent) taps "나왔어" on the family web page after the visit → car speaks
   "어머니가 정문에 나오셨대요" and shows it. Only status text over the relay.
   Car control: only controls verified on this emulator (see CLAUDE.md). Do NOT build seat-slide or seat heater features unless you
   find them in the references AND design them behind capability checks; if unverified on the emulator, leave them out and list
   them as unverified. No feature may depend on seat occupancy (unreliable on the emulator).
4. Post-visit briefing (오는 길): when the car leaves the hospital after a stay ≥ 15 min (gear D after P at the hospital, or a new
   trip from the hospital location), ask "진료 어떠셨어요? 의사 선생님 말씀 정리해 둘까요?" then slots:
   - 의사 선생님이 뭐라고 하셨어요 (verbatim, may be multiple sentences)
   - 약이 바뀌었나요 (yes/no + verbatim)
   - 검사나 다음 예약이 있나요 (date/time parsing: "2주 뒤", "다음 달 3일", "12월 10일 오전 10시" → absolute date relative to today;
     ambiguity kept verbatim and marked 확인 필요)
   - 조심하라고 하신 거 있나요 (verbatim)
   - 다음 진료 때 확인할 것 (verbatim)
   Then assemble the briefing by template: "오늘 ○○병원 진료 — 의사 말씀: …, 약: …, 다음 예약: 12월 10일(수) 오전 10시, 주의: …"
   Read it back by TTS, then "가족에게 보낼까요?" → send via FamilyLink only on yes. Also offer to set the next appointment as a
   reminder (stored on device; the trip detection uses it next time).
5. Regular-visit mode (정기 진료): on-device visit history per hospital (date, memo, briefing, next appointment). Screen: timeline of
   visits, next appointment, "지난번 물어볼 것" carried over. A day-before/that-morning reminder is out of scope for the car (car
   is off); instead when the car starts on the appointment day, say "오늘 ○시 ○○병원 진료가 있어요. 길안내 할까요?" → NaviHelper
   route request to the saved hospital coordinates (only if saved coordinates exist; no POI search API exists).

### Driving restriction & UI
- CarUxRestrictions: while moving, the screen shows only a glance state (current question / "듣는 중" / progress n of 5); no text
  entry, no reading long memos. Full memo/briefing/history only when parked. Voice is the primary interface while driving.
- Pleos design guide (layout, colors, typography), light + dark; must fit portrait split window (840×1184 dp) and full landscape.
  Rotary/CCP: declare unsupported like 쉼표 (check its manifest) or give a sensible focus order.

### Family relay (tools/family/, Python stdlib only, no external services, no accounts)
- `FamilyLink` port in core; 1st adapter = HTTP to a configurable base URL (emulator → laptop host 10.0.2.2:port; relay runs on the
  laptop). Car POSTs approved briefings / memos / status ("정문 도착", "주차 중"), polls replies ("나왔어", "잘 받았어"). Family pairing:
  random 128-bit token shown as a short code/QR-free URL on the car screen; the relay keeps data in memory with a 24 h TTL; mobile web
  page (Korean, large text) shows briefing + "나왔어" button. Document that production would need HTTPS + real auth.
- Python tests for the relay (unittest).

### Demo mode for the emulator
- `adb shell am start -n dev.carevisit.app/.MainActivity --es demo visit` simulates a hospital trip: destination "○○대학교병원"
  (fictional/generic name), parent "어머니". The emulator runs `-no-audio`, so STT cannot hear: in demo, answers come from a scripted
  `SpeechPort` fake that returns realistic STT strings (with typical errors) after each real TTS question, clearly labelled
  "시연: 미리 녹음한 대답" on screen. Also a "다음" step control on the demo panel to advance (go to hospital → interview → arrive →
  visit done → briefing → send). Real gear/door from Virtual Console must still drive transitions where natural (P + door open =
  drop-off; D after the visit = briefing). Add a VC scenario file in tools/vc/ for the demo.
- Release builds accept only the `--es demo` extra (port 쉼표's LaunchExtras/ReleaseSurfaceTest idea: dev hooks debug-only).

### Engineering requirements
- Architecture like 쉼표: pure-Kotlin core (state machine, slot parsers, date normaliser, consent/retention policy, briefing
  templates, polish guard, records) with ports; one actor owns state; Android app = Compose UI + foreground service + adapters with
  pleos-kit. `./gradlew -p visit-core test` must run on this aarch64 server without the Android SDK. Document it in
  docs/ARCHITECTURE.md — the entrant (CS major) must explain the structure in PT Q&A; pick what is technically right.
- Persistence: atomic file writes (temp + rename) for visit records & consent; restart mid-interview resumes or cleanly abandons
  (test both). Retention purge tested with virtual time.
- Permissions: declare per references; request dangerous ones at runtime (CAR_SPEED, FUSED_LOCATION pattern in kit). Release
  manifest declares only what the product uses (test like ReleaseSurfaceTest with a product map of uses).
- Tests: failing test first per behaviour. Core JVM tests covering: trip detection (destination keywords, saved hospital, manual),
  consent gating (no interview/record/send without consent; withdrawal deletes), each slot parser with noisy STT, re-ask and skip,
  continuity question from last visit, arrival/drop-off, "나왔어" reply spoken, post-visit trigger (stay ≥ 15 min), date parsing
  (relative/absolute/ambiguous, month rollover, weekday), briefing template, polish guard (accept/reject cases), per-briefing send
  confirmation, retention purge, delete-all, restart recovery, alarming-utterance fixed line (and that NO assessment text exists).
  App unit tests (app/src/test) for UI models and release surface. You cannot build Android here (no Android SDK; it builds on the
  user's Windows laptop) — be extremely careful with Android compile correctness and API-34 symbols; keep Android code thin and
  mirror the patterns that already compile in 쉼표 (same AGP/Kotlin/Compose versions as 쉼표's build files).
- release signing reads `keystore.properties` at repo root (gitignored) exactly like 쉼표.
- README.md (contest-required, Korean): 앱 이름과 한 줄 소개; 실행 방법; 테스트 환경과 필요한 설정; 개발에 사용한 Pleos Connect
  에뮬레이터 버전과 화면 규격 (Pleos Connect Emulator v2.1.0, API 34, 2560×1440 가로, 160dpi); APK 위치 `apk/팀명_진료동행_release.apk`
  (placeholder until the team name is decided); 시연 모드; 가족 relay 실행법; 개인정보 원칙; 구조 링크.
- docs/product/SPEC.md (behaviour spec, every threshold with justification, the hard principles), docs/ARCHITECTURE.md.

## Rules
- Never invent SDK/Android APIs (grep the references). Never add secrets. Don't commit — leave everything in the working tree.
- No real personal names anywhere. Do not read files outside this repo except the read-only 쉼표 repo above.
- Don't use `-q` on the final test run.
- Final message: files/modules overview, design decisions with justification, test counts, and a precise list of anything
  unverified (esp. Android code that only compiles on the laptop, SDK behaviours to confirm on the emulator, e.g. what
  `getDestinationInfo` returns for the name, arrival events).
