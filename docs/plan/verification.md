# 기획서 생성·대조 결과

2026-11-15 확인. 결과는 **10쪽**(참가신청서·기획서·동의서·서명란 포함)이다. 20쪽 이내다.

## 실행·확인

- `python3 -I tools/plan/build_docx.py`: 성공. 캡처나 테스트 개수 파일 없이 생성.
- `python3 -I tools/plan/build_docx.py --skip-figures --preview docs/plan/preview`: 성공. LibreOffice PDF와 각 페이지 PNG 생성.
- 페이지 PNG 육안 검토: 한글·표·흐름도·출처 URL·개인정보 빈칸·동의/서명란 확인. 기술 설명의 꼬리 페이지를 줄여 10쪽으로 정리.
- DOCX ZIP 내 모든 XML/rels 파싱 성공. 양식의 8개 content control alias/tag/id 보존 확인. 내용 형식만 rich text로 변경.
- 03 개인정보 수집·이용 동의서 전체 XML 요소가 원본과 동일함을 비교 확인.
- 01 개인 체크, 1명·앱명 입력. 참가 부문·개인정보·작성일은 빈칸. 04 앱명만 입력하며 서명·동의 여부는 빈칸.
- 박스의 본문에서 이메일·GitHub URL·쉼표 콘텐츠 없음 확인. 실제 인물명은 넣지 않음.
- 원본과 결과 해시·페이지 수는 `build-verification.json`. 원본 파일에 쓰는 작업을 하지 않음.
- 이번 PLAN 작업에서 앱 테스트를 실행하거나 수동으로 개수를 입력하지 않음. 문서에는 테스트 전략만 기재.
- 생성물은 루트에서 gitignored임을 `git check-ignore`로 확인. 커밋하지 않음.

## 최종 구현 대조

| 확인 파일 | 문서 반영 |
|---|---|
| `visit-core/.../VisitCore.kt`, `Ports.kt`, `VisitMachine.kt` | 단일 actor·포트, 저장 후 외부 작업, 원문 질문·승인 공유, 재시작 복구, 병원/기어/도어 전이 |
| `Records.kt`, `Korean.kt`, `Templates.kt`, `AtomicRepository.kt` | 180일 기본, 누적 60초·15분·8초 설계값, 발화 ID, 고정 서식, 문장부호/띄어쓰기만 허용하는 guard |
| `app/.../VisitService.kt`, `VoiceAgent.kt`, `HttpFamilyLink.kt`, `UiContract.kt`, `ui/` | SDK 직접 호출 대신 kit, scripted STT 시연 표시, 실제 차량 신호 유지, 가족 relay·화면 상태 연결 |
| `pleos-kit/.../VisitKit.kt`, `VoiceKit.kt`, `LlmKit.kt`, `CarState.kt`, `NaviKit.kt`, `VehicleKit.kt` | 목적지명·좌표, 도어 지원 영역, ON_DEVICE 음성/LLM, AAOS 기어·속도와 UX 제한 |
| `tools/family/relay.py`, `PROTOCOL.md` | 승인 글·상태만 전달, 24시간 절대 TTL, 실제 외부 서비스 아님 |
| `docs/product/SPEC.md`, `docs/ARCHITECTURE.md` | 제품 원칙·수치·시연 한계·UI 계약 대조. 완성 APK/실기 성공으로 표현하지 않음 |

첫 동의의 구현은 자동 제안 시 음성으로도 받을 수 있다. 주차 화면의 명시적 동의 시작과 터치 동의는 P에서만 허용한다. 문서도 ‘모든 첫 동의는 반드시 주차에서만’이라고 과장하지 않는다.

## 남은 검증·인계

- 실제 에뮬레이터 캡처는 아직 없다. `assets/screens/`에 추가 후 재생성하면 7번 박스에 자동 반영된다. 이후 페이지 수·줄바꿈을 다시 확인해야 한다.
- Android 컴파일·release APK, 실제 마이크 STT, NaviHelper 목적지명/도착 이벤트, Virtual Console의 도어·기어, 모바일 relay 왕복, 가로/세로·라이트/다크 화면, LLM 응답은 이 PLAN 작업에서 검증하지 않았다.
- Word와 LibreOffice의 글꼴·페이지 배치 차이는 있을 수 있다. 제출 PC에서 최종 Word 화면을 확인한다.
- MAU·Retention·효과는 아직 측정하지 않았다. MAU는 명시한 가정 모형이며 Retention은 측정 정의만 적었다.
- **CORE 인계:** `docs/product/SPEC.md`의 ‘기사 원문 주소·제목은 제공되지 않았으며 별도 확인이 필요’ 문장은 이제 `docs/plan/evidence.md`를 참고해 갱신할 수 있다. 수치 확인 출처는 한국경제 네이트 전재본이고, 현재 한국경제 원문에는 해당 문단이 없다. 서울시 2021년 본문/표 충돌도 함께 보존해야 한다. PLAN은 소유 범위를 지켜 SPEC을 수정하지 않았다.
