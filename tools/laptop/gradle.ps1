# 노트북에서 Gradle을 부를 때는 항상 이 스크립트를 쓴다. env.ps1로 프로젝트 폴더 안의 SDK·gradle-home을 잡고 gradlew를 실행한다.
# gradlew.bat을 SSH로 바로 부르면 GRADLE_USER_HOME이 비어 사용자 홈의 .gradle에 배포판과 의존성을 내려받는다
# (2026-11-11 실제로 그렇게 되어 지웠다). settings.gradle.kts도 프로젝트 밖 gradle 홈이면 빌드를 멈춘다.
# 실행: powershell -ExecutionPolicy Bypass -File tools\laptop\gradle.ps1 :app:testDebugUnitTest :app:assembleRelease
# 결과는 logs\gradle-<폴더>.log에도 남는다. 종료 코드는 gradlew의 것을 그대로 돌려준다
. "$PSScriptRoot\env.ps1"
if (-not $P) { throw "env.ps1 not loaded" }
# 이 스크립트가 든 저장소를 빌드한다 (repo-visit\ 또는 LAPTOP_REPO로 복사한 repo-visit-이름\)
$proj = (Resolve-Path "$PSScriptRoot\..\..").Path
New-Item -ItemType Directory -Force "$P\logs" | Out-Null
$log = "$P\logs\gradle-$(Split-Path -Leaf $proj).log"
Set-Location -LiteralPath $proj -ErrorAction Stop
"sdk.dir=$($env:ANDROID_HOME.Replace('\','\\'))" | Set-Content -Encoding ASCII "$proj\local.properties"
# 에뮬레이터가 떠 있으면 낮은 우선순위로 빌드한다 (run-app.ps1과 같은 이유)
if (Get-Process qemu-system-x86_64-headless, qemu-system-x86_64 -ErrorAction SilentlyContinue) {
  (Get-Process -Id $PID).PriorityClass = "BelowNormal"
  "emulator running: building at BelowNormal priority"
}
$gargs = ($args | ForEach-Object { "$_" }) -join " "
cmd /c "gradlew.bat --no-daemon --console=plain $gargs > `"$log`" 2>&1"
$code = $LASTEXITCODE
Select-String -Path $log -Pattern "^e: |error:|FAILED|What went wrong|tests completed|BUILD (SUCCESSFUL|FAILED)|Test.*>.*FAILED" -Context 0,2 | Select-Object -First 60 | ForEach-Object { $_.ToString() }
"gradle exit=$code"
exit $code
