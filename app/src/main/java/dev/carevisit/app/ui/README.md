# 진료 화면 구현

`VisitScreen(state, platformRestricted)`가 서비스 상태만 읽고 `VisitStore` 명령으로 응답한다.
SDK·HTTP·영구 저장소를 UI에서 만들지 않는다. `AppModels.kt`는 Android/Compose 없는 순수 Kotlin이다.

- 주행: `platformRestricted || !state.fullScreen`이면 원문·기록 없는 모델로 즉시 교체한다. 모든 질문 종류를 고정 문구로 매핑한다. 공유 읽기 원문, 이전 방문 발화, notice를 주행 화면에 전달하지 않는다. 48자/3줄 상한, 28sp 이상, 응답 버튼 최대 3개/100dp. HEALTH 동의 터치는 주차 전용. 신호가 모름이면 응답 버튼을 끄고, 공유의 ‘예’는 전체 읽기 후 LISTENING에서 켠다. 제한 진입 시 포커스·키보드를 닫고 주차 확인창도 구성에서 제거한다.
- 주차: 1100dp부터 동행/기록 2열, 세로 창은 1열 스크롤. 글을 잘라내지 않는 진료 메모·체크리스트·브리핑, 병원별 날짜순 기록과 최근 진료의 다음 예약. `Rendezvous`로 정문/주차/나왔어 수신을 표시한다.
- 동의: 건강 동의 전에는 문서 모델 자체가 비어 있다. 가족 동의는 별도 확인창. 공유는 코어의 `ShareMemo`/`ShareBriefing`을 통해 원문 읽기와 개별 yes 확인을 거친다. 전체 삭제·동의 철회·가족 연결 해제에는 확인창이 있다.
- 가족 연결: 짧은 8자리 확인 코드는 식별 표시다. 인증을 줄이지 않는다. 가족이 접속할 때는 별도로 입력한 노트북 LAN 주소와 전체 128비트 토큰 URL을 사용한다. 주소 변경 전 기존 연결을 끄도록 한다.
- Pleos: 가이드의 중립 grayscale/명암 반전, 40·48dp 여백, 24dp 간격, 80·100dp 버튼, 24·32dp 모서리, 48/36/32/28sp 계층. 선택은 체크 표시와 semantics, 예약 불명확은 ‘확인 필요’ 글자로도 표시한다. 시스템 한글 글꼴을 사용한다. 세부 토큰 근거는 쉼표의 검증된 theme 구현과 `docs-design`이다.
- 시연: 활성 상태에서만 배지·정확한 입력 설명·6단계 버튼을 보여 준다. 가능한 현재 단계만 활성화한다. 실제 기어·도어를 우회하지 않으며 기록·동의를 임의 주입하지 않는다.

서버 UI 소스 컴파일 검사와 모델 테스트:

```sh
python3 app/src/test/java/dev/carevisit/app/ui/check-compose-compile.py
bash app/src/test/java/dev/carevisit/app/ui/run-model-tests.sh
```

첫 명령은 앱의 BOM/Kotlin 버전에 맞는 공식 Compose AAR와 Android API jar를 받아 `/tmp`에서 UI 파일 **전체**를 실제 Kotlin/Compose 컴파일러로 검사한다. 문법·중괄호·import·람다 타입·Compose 호출을 확인하며 `UiContract.kt`와 `AppIdentity.kt`도 실제 소스를 사용한다. Android 리소스 처리 대신 `R.string.app_name` ID 하나만 임시로 제공한다.

둘째 명령은 별도의 JVM Gradle 빌드에서 `AppModels.kt`와 앱 UI 테스트를 실행한다. 모델 테스트만으로는 Compose 파일의 컴파일 오류를 검출하지 못하므로 첫 검사도 함께 실행한다. 두 검사 모두 APK 빌드를 대체하지 않는다. 노트북에서는 `:app:compileDebugKotlin`과 `:app:testDebugUnitTest`도 실행한다.

노트북에서 반드시 확인할 것:

1. debug/release Android 빌드와 UI 단위 테스트. `VisitPreviews.kt`의 840×1184, 2560×1440, 1700×1184 미리보기.
2. 라이트/다크, 글자 배율 1.0/1.3에서 주행 질문/버튼·주차 스크롤·확인창 줄바꿈.
3. 설정의 키보드나 삭제 확인창을 연 채 주행 제한 on → 즉시 glance, off + P → 주차.
4. 실제 TTS/STT의 PREPARING/LISTENING 표시, 고정 응답 버튼, 공유 시 보내기/거절, 체크리스트 저장.
5. 가족 연결과 ‘나왔어’, 삭제·철회 실패 안내, 6단계 시연과 실제 VC 기어·도어 연동.

서버 검사는 Android 빌드 도구나 에뮬레이터를 사용하지 않는다. 전체 AGP/APK 빌드·리소스 처리·픽셀 렌더링·접근성 서비스는 노트북에서 확인해야 한다.
