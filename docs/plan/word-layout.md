# Word 박스 시작 페이지 문제: 조사와 수정

2026-11-15 후속 작업. Microsoft Word 실행 환경은 없으므로 원인은 XML 관찰과 사용자 재현을 구분해 기록한다.

## 수정 전 XML에서 확인한 것

- 공식 `7. 참고자료` 제목 문단: `w:keepNext` 켜짐.
- 박스 7의 첫 소제목 `구현 자료와 화면`: `keepNext`와 `keepLines` 켜짐.
- 본문·Note는 `keepNext`를 명시하지 않았고 기본 Normal 스타일에도 keep 설정이 없었다. **박스 전체가 하나의 연속 keepNext 체인이었다고 단정할 수는 없다.**
- 출처 표 머리행: `cantSplit` + `tblHeader`. 캡처 표 4개 행: 모두 `cantSplit`.
- 캡처 이미지 문단: `keepNext` 켜짐. 가로 캡처는 `screen_max_h_cm`을 무시하고 별도 10.5cm 상한을 썼다.
- 박스 7 바깥 행의 `cantSplit`은 이미 제거되어 있었다. 박스 7 내부의 활성 `pageBreakBefore`는 없었다.

Word는 keep 문단과 분할 불가 중첩 행을 묶어 이동시킬 수 있다. 확인된 위험 구조는 ‘첫 소제목의 keep + 중첩 표의 분할 불가 행 + 큰 캡처’다. 강제 페이지 나눔이 직접 원인은 아니었다. 정확한 Word 재현과 해결 여부는 제출 PC에서 확인해야 한다.

## 수정

- 박스 7의 첫 블록은 짧은 일반 소개 문단. 영상/APK/구현 자료 표가 바로 뒤에 온다.
- 모든 박스의 첫 H는 keepNext를 끈다. 그림 직전 H도 끈다. 본문·캡션·빈 문단은 keepNext/keepLines/pageBreakBefore의 false를 명시하여 스타일 상속에 의한 재연결을 막는다.
- 긴 박스의 바깥 행은 분할 가능. 한 줄짜리 1·8번 박스는 원본의 분할 금지를 유지해 빈 테두리만 앞 장에 남지 않게 한다. 출처 표 머리행은 반복 표제 `tblHeader`만 유지하고 분할 금지를 해제한다. 캡처 표의 모든 행도 분할 가능하다.
- 가로·세로 캡처 모두 `screen_max_h_cm`을 사용하고 생성기의 절대 상한은 8cm다. 현재 본문은 5.2cm를 지정한다. 화면과 캡션을 포함해 반 페이지 이하 크기로 제한한다.
- 캡처와 이름을 한 문단에 넣고 그 문단의 keepNext는 끈다. 문단 내부 keepLines만 사용한다. 캡처 표 전체 설명은 표 앞의 일반 문단이며 다음 표와 keep으로 묶지 않는다.
- 일반 흐름도/구조도는 그림과 바로 뒤 캡션만 함께 두며 캡션에서 keepNext를 명시적으로 끊는다. 다음 표·문단까지 연결하지 않는다.

LibreOffice는 분할 가능한 중첩 표 안의 keepLines도 Word와 다르게 처리했다. 큰 캡처에서 마지막 캡션만 다음 장으로 넘어가는 것을 확인해 표시 높이는 제출 정리 과정에서 5.2cm로 조정되었으며 최종 문서 작업에서도 그대로 유지했다. Word에서 결과를 보지 않고 ‘해결 재현 완료’라고 주장하지 않는다.

## 확인 방법

```bash
python3 -I tools/plan/build_docx.py
python3 -I tools/plan/check_layout.py
python3 -I tools/plan/build_docx.py --skip-figures --preview docs/plan/preview
```

`check_layout.py`는 8개 박스의 시작 문단, 바깥/안쪽 행 분할, 그림 keep 체인의 종료, 캡처 높이, 영상/APK 기재, XML 구문을 검사한다. Microsoft Word의 실제 페이지 계산을 대신하는 테스트는 아니다.
