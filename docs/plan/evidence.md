# 기획서 근거 확인 기록

열람일: 2026-11-15. 검색 결과 요약만으로 채택하지 않고 본문을 가져와 확인했다. 기관·매체명만 적고 기사 인물·기자 실명은 옮기지 않았다. 수치는 문제 배경이며 제품 효과·시장 규모·MAU로 환산하지 않는다.

| 번호 | 자료 · 날짜 | 실제 확인한 내용 | URL / 확인 방법 |
|---|---|---|---|
| 1 | 한국경제 네이트 전재본. 2026-07-22 06:01, 수정 2026-07-27 16:23 | 서울 병원안심동행 이용: 2021년 649건, 2025년 23,048건. 기사에서는 2025년을 ‘지난해’로 표현. | https://news.nate.com/view/20260722n02018 — urllib.request로 HTML fetch 성공, cp949로 해독 후 본문·매체·날짜 확인. |
| 1a | 한국경제 현재 원문. 2026-07-22, 수정 2026-07-27 | 부모 돌봄 때문에 자녀가 반차를 사용하고 가족이 번갈아 동행한다는 사례. 현재 원문의 fetch 결과에는 위 649건 문단이 없음. | https://www.hankyung.com/article/2026072175481 — web open 성공. urllib 직접 요청은 403. 수치의 직접 출처는 1번 전재본으로 명시. |
| 2 | 연합뉴스, 2024-07-24. ‘2023 의료서비스 경험조사’ 보도 | 외래서비스 응답자의 55.0%가 진료시간 5분 미만이라고 응답. 전체 평균 8.0분, 조사 대상 14,910명. | https://www.yna.co.kr/view/AKR20240723135000530 — web open 본문 확인. 평균이 3분이라고 쓰지 않음. |
| 3 | 서울특별시, 수정 2025-02-28. 병원안심동행 이용 개선 | 투석 동행 2,658건(2022) → 5,382건(2024). 보호자가 회사 일을 쉬지 않고 이용한 사례. | https://news.seoul.go.kr/welfare/archives/568479 — web open 본문·표 확인. |
| 4 | 통계청, 2025-09-29. 2025 고령자 통계, 현재 국가데이터처 게시 | 2025년 65세 이상 인구 비중 20.3%. | https://mods.go.kr/board.es?act=view&bid=10820&list_no=438832&mainXml=Y&mid=a10301010000 — web open 게시일·본문 확인. 최신 인구 수치라는 주장은 하지 않음. |

## 수치 충돌과 한계

- 서울시 3번 자료는 2021년 이용실적을 본문에서는 367건, 표에서는 649건으로 표시한다. 서로 맞지 않으므로 성장 배수는 쓰지 않았다. 기획서 649→23,048 표기는 한국경제 네이트 전재본 기준임을 본문과 출처에 명시했다.
- 한국경제 원문과 전재본의 현재 본문 범위가 다르다. 현재 원문에서 649→23,048을 확인했다고 주장하지 않는다.
- 공공 동행 서비스 건수는 고유 이용자 수도, 자녀가 운전한 횟수도, 차량 앱의 고객 수도 아니다.
- 2025 고령자 통계는 2025년 기준 자료로 사용했다. 2026년 최신 통계인 것처럼 쓰지 않는다.
- 자녀 동행 부담의 인구 전체 비율, 동행 시간, 질문 누락률, 앱 효과는 확인한 측정값이 없어서 넣지 않았다.
- MAU 입력값과 민감도 범위는 모두 명시적인 사업 가정이다. 실제 고객·차량 보급량의 추정치가 아니다.
- 정책 시간·보관 일수·LLM 한계는 의료 근거나 외부 통계가 아닌 제품 설계값이며 코드/사양을 근거로 표기했다.

## 기술 이름 확인

아래는 제공된 로컬 레퍼런스에서 grep한 시그니처다. API 존재와 이 앱의 실기 성공을 구분한다.

| 이름 | 제공 레퍼런스 |
|---|---|
| NaviHelper.getDestinationInfo() | `docs/contest/03_SDK_API_레퍼런스/NaviHelper/ai.pleos.playground.navi.helper.md` |
| DestinationInfo.name, latitude, longitude, remainDistance | `NaviHelper/ai.pleos.playground.navi.data.md` |
| NaviHelper.requestRoute(routeInfo: RouteInfo) | `NaviHelper/ai.pleos.playground.navi.helper.md` |
| Door.checkDoorPositionCapabilities(), registerDoorPosition(area, listener) | `Vehicle/ai.pleos.playground.vehicle.api.md` |
| DoorArea / DoorValue.ROW_1_RIGHT | `Vehicle/ai.pleos.playground.vehicle.api.model.area.md`, `Vehicle/ai.pleos.playground.vehicle.constant.area.md` |
| SpeechToText.request(), Mode.ON_DEVICE | `SpeechToText/ai.pleos.playground.stt.md`, `SpeechToText/ai.pleos.playground.stt.constant.md` |
| TextToSpeech.speak(text: String, isFinal: Boolean = true) | `TextToSpeech/ai.pleos.playground.tts.md` |
| LLM.generateContent(prompt: String, listener: ResultListener), Mode.ON_DEVICE | `LLM/ai.pleos.playground.llm.md`, `LLM/ai.pleos.playground.llm.constant.md` |

AAOS `VehiclePropertyIds.GEAR_SELECTION`, `PERF_VEHICLE_SPEED`, `CarUxRestrictions`는 Pleos API라고 쓰지 않았다. `pleos-kit`의 `CarState`·`UxRestrictionWatcher` 사용과 대조했다.
