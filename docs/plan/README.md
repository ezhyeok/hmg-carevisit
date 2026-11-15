# 공식 접수양식 생성

```bash
python3 -I tools/plan/build_docx.py
python3 -I tools/plan/build_docx.py --preview docs/plan/preview
```

- 출력: 저장소 루트 `contest-application-consent.docx` (gitignored), 검토용 `docs/plan/planning-doc.md`.
- 원본: `docs/contest/01_공모전/접수양식/contest-application-consent.docx`. 원본은 수정하지 않는다.
- 내용 원본: `tools/plan/content.py`. 문서와 그림의 앱명은 `app/src/main/res/values/strings.xml`의 `app_name` 하나에서 읽는다. 이름을 바꾼 뒤 재생성한다.
- 01: 개인, 1명, 앱명만 입력. 참가 부문·개인정보·작성일·서명·동의 체크는 비워 둔다. 제출자가 직접 작성한다.
- 02: 양식의 8개 content control을 유지해 내용·표·그림을 넣는다. 숏폼은 제출하지 않음.
- 03 동의서 원문은 유지. 04는 앱명만 입력하며 동의·서명은 대신 하지 않는다.
- Python 표준 라이브러리 사용. SVG→PNG에 기존 생성기와 같은 `rsvg-convert`가 필요하다. 미리보기는 `soffice`, `pdftoppm`을 사용한다. 한글 폰트는 Pretendard 또는 Noto Sans CJK KR.
- 검증된 출처와 충돌 기록: [evidence.md](evidence.md). 구현·문서 확인: [verification.md](verification.md).

## 화면 추가

`docs/plan/assets/screens/`에 PNG/JPG/JPEG 파일을 넣고 다시 빌드한다. 예: `01_가는_길_질문.png`, `02_주차_진료_메모.png`, `03_귀가_브리핑.png`. 파일명에서 번호를 뺀 부분이 캡션이 된다. 실명·계정명·가족 연결 토큰·주소가 화면이나 파일명에 들어가지 않게 한다. 실제 캡처가 없으면 7번에 삽입 예정 안내만 표시한다. 기능 흐름도는 실기 화면으로 표시하지 않는다.

## 재사용 범위

사용자 지시에 따라 읽기 전용 쉼표 저장소의 `build_docx.py`, `blocks.py`, `ooxml.py`, `render_docx.py`, `render_md.py`, `icons.py`, `figures.py`를 복사했다. 기존 OOXML·렌더러와 SVG primitive를 재사용하고 이 앱의 본문과 흐름도를 작성했다. 쉼표 시뮬레이션·시험 개수 로더·화면 목업은 제거했다. 테스트 개수는 적지 않았으며 집계 파일이 없어도 문서가 생성된다.

재생성은 출력 docx를 덮어쓴다. 개인 정보나 서명을 넣기 전 최종 생성하고, 작성한 제출본은 별도로 보관한다.


## Word 레이아웃 회귀 확인

빌드 후 `python3 -I tools/plan/check_layout.py`로 OOXML 분할 설정을 확인한다. 조사·수정 내용은 [word-layout.md](word-layout.md). 현재 4개 캡처는 높이 7cm로 넣으며, 가로 캡처도 `screen_max_h_cm` 설정을 따른다. 생성기 상한은 8cm다. 영상은 녹화 중으로 기재했으므로 녹화 완료 뒤 `content.py`의 상태를 갱신한다. Word 최종 배치는 제출 PC에서 확인한다.
