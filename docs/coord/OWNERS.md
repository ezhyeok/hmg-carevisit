# 병렬 작업 소유 범위 (Codex 여러 개가 같은 작업 트리에서 동시에 일한다)

자기 범위 밖 파일은 고치지 않는다. 다른 범위가 바꿔 줘야 할 것이 있으면 아래 요청 파일에 한 줄로 적는다
(`- [ ] 요청자 → 담당: 무엇을, 왜`). 각 담당은 작업 중간중간 자기 앞으로 온 요청을 확인하고 처리한 뒤 `[x]`로 바꾼다.

| 담당 | 범위 |
|---|---|
| CORE | visit-core/**, pleos-kit/**, app의 서비스·어댑터·음성·가족 HTTP·MainActivity·LaunchExtras/DevHooks (app/src/main/java/dev/carevisit/app/*.kt 중 ui/ 밖), AndroidManifest.xml, app/build.gradle.kts, settings.gradle.kts, CLAUDE.md, docs/product/SPEC.md, docs/ARCHITECTURE.md, 그에 맞는 테스트 |
| UI | app/src/main/java/dev/carevisit/app/ui/**, app/src/main/res/** (strings.xml의 app_name 제외 문구 추가 가능), app/src/test/java/dev/carevisit/app/ui/** |
| TOOLS | tools/family/**, tools/laptop/**, tools/vc/**, README.md |
| PLAN | tools/plan/**, docs/plan/**, 저장소 루트 contest-application-consent.docx(생성물, gitignore) |

요청: docs/coord/requests.md
