# NOTICE — 제3자 구성요소

이 저장소의 코드와 문서는 [LICENSE](LICENSE)(모든 권리 보유)를 따른다. 아래 구성요소는 각각의 라이선스를 따르며,
LICENSE가 그 권리를 제한하지 않는다.

| 구성요소 | 라이선스 |
|---|---|
| Pleos Playground SDK (Vehicle, NaviHelper, TextToSpeech, SpeechToText, LLM) | 42dot Pleos Connect SDK 라이선스. 저장소에 포함하지 않으며 빌드 때 Pleos Maven 저장소에서 받는다 |
| AndroidX (Compose, Activity, Lifecycle), Kotlin 표준 라이브러리, kotlinx.coroutines | Apache License 2.0 |
| Gradle Wrapper (`gradle/wrapper/gradle-wrapper.jar`) | Apache License 2.0 |

가족 relay(`tools/family/`)는 Python 표준 라이브러리만 쓴다.

## 저장소에 넣지 않은 자료

공모전 안내 페이지, Pleos 개발 가이드·SDK 레퍼런스 원문, 접수양식 원본은 각 저작권자의 자료라 공개 저장소에 넣지 않는다.
개발 중에는 `docs/contest/`(gitignore)에 내려받아 참고했고, 코드와 문서에 그 경로를 출처로 적었다. 원문은
appdevchallenge.auto-hmg.io와 document.pleos.ai에 있다.
