#!/bin/bash
# 서버의 작업 트리를 노트북 프로젝트 폴더의 repo-visit\ 로 복사 (docs, 빌드 산출물 제외)
# LAPTOP_REPO=repo-visit-review 처럼 주면 다른 폴더로 복사한다 (작업 트리 여러 개를 노트북에서 따로 빌드할 때. gradle.ps1은 자기 위치의 저장소를 빌드한다)
# 루트의 .md와 .docx는 뺀다: Windows tar가 한글 파일명을 풀지 못하고, 빌드에도 필요 없다
set -euo pipefail
ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
# 노트북 PowerShell 5.1은 BOM 없는 .ps1을 CP949로 읽어 한글 주석이 다음 줄을 삼킨다(2026-11-11 gradle.ps1의
# env.ps1 불러오기가 주석으로 먹혔다). 모든 .ps1은 UTF-8 BOM으로 저장해야 하므로 복사 전에 확인한다
for f in "$ROOT"/tools/*/*.ps1 "$ROOT"/tools/*/*/*.ps1; do
  [ -f "$f" ] || continue
  if [ "$(head -c3 "$f" | od -An -tx1 | tr -d ' ')" != "efbbbf" ]; then echo "BOM 없음: $f (UTF-8 BOM으로 저장할 것)" >&2; exit 1; fi
done
DEST="${LAPTOP_REPO:-repo-visit}"
if [[ ! "$DEST" =~ ^repo-visit(-[a-z0-9][a-z0-9-]*)?$ ]]; then echo "LAPTOP_REPO는 repo-visit 또는 repo-visit-이름" >&2; exit 1; fi
# 폴더마다 tar·임시 폴더 이름을 따로 쓴다 (여러 작업 트리를 동시에 복사하면 같은 repo.tar를 서로 덮어썼다).
# 풀어 놓은 뒤 robocopy /MIR로 맞춘다: 서버에서 지우거나 옮긴 파일이 노트북에 남아 옛 테스트가 계속 돌던 문제
# (2026-11-12, 코치 계층으로 옮긴 NaviPollTest 등이 app/에 남았다). build·.gradle·local.properties는 그대로 둔다
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
TAR="sync-$DEST.tar"
STAGE="sync-stage-$DEST"
tar -C "$ROOT" --exclude=.git --exclude=.local --exclude=.agents --exclude=.codex --exclude=./docs --exclude=./apk --exclude=./build --exclude='*/build' --exclude='*/.gradle' --exclude=local.properties --exclude=keystore.properties --exclude='*.jks' --exclude='*.keystore' --exclude='*/.kotlin' --exclude='*/__pycache__' --exclude='./*.md' --exclude='./*.docx' -cf "$TMP/$TAR" .
scp -q "$TMP/$TAR" "hmg-laptop:hmg-appdev-challenge/$TAR"
rm -rf "$TMP"
ssh hmg-laptop "\$ErrorActionPreference = 'Stop'; Set-Location \$env:USERPROFILE\\hmg-appdev-challenge; if (Test-Path $STAGE) { Remove-Item -Recurse -Force $STAGE }; New-Item -ItemType Directory -Force $STAGE, $DEST | Out-Null; tar -xf $TAR -C $STAGE; if (\$LASTEXITCODE -ne 0) { throw 'tar extraction failed; destination was not mirrored' }; Remove-Item $TAR; robocopy $STAGE $DEST /MIR /XD build .gradle .kotlin .git .agents .codex .local docs /XF local.properties keystore.properties *.jks *.keystore /NFL /NDL /NJH /NJS /NP | Out-Null; \$rc = \$LASTEXITCODE; Remove-Item -Recurse -Force $STAGE; if (\$rc -ge 8) { 'robocopy failed: ' + \$rc; exit 1 }; 'synced to $DEST (mirrored)'"
