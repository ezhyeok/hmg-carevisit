#!/usr/bin/env bash
# 개발 서버에서 실행. --local은 로컬 adb(또는 ADB 환경변수)를 사용한다.
# 설치된 dev.carevisit.app을 한 번 재시작하고 새 프로세스의 시작 로그만 확인한다.
set -euo pipefail
HOST="${LAPTOP_SSH_HOST:-hmg-laptop}"
REMOTE_ADB='C:\Users\User\hmg-appdev-challenge\sdk\platform-tools\adb.exe'
MODE=remote
if [[ "${1:-}" == --local ]]; then MODE=local; shift; fi
if (( $# )); then echo '사용: demo-smoke.sh [--local]' >&2; exit 2; fi
adb_() {
  if [[ "$MODE" == local ]]; then timeout 20 "${ADB:-adb}" "$@"
  else timeout 20 ssh -o ConnectTimeout=10 "$HOST" "$REMOTE_ADB $*"
  fi
}
adb_ shell am start -S --user current -n dev.carevisit.app/.MainActivity --es demo visit
for ((attempt=0; attempt<30; attempt++)); do
  pid=$(adb_ shell pidof dev.carevisit.app | tr -d '\r\n' || true)
  if [[ "$pid" =~ ^[0-9]+$ ]]; then
    log=$(adb_ logcat -d --pid="$pid" -v brief -s 'CareVisit:I' '*:S' || true)
    if printf '%s\n' "$log" | tr -d '\r' | grep -Eq 'CareVisit[[:space:]]*\([[:space:]]*[0-9]+\): demo started: visit$'; then
      echo "PASS: dev.carevisit.app pid=$pid, CareVisit: demo started: visit"
      exit 0
    fi
  fi
  sleep 2
done
echo 'FAIL: 30회 재시도 동안 시연 시작 로그를 찾지 못했습니다. 앱 설치·서비스 시작·CareVisit 로그를 확인하세요.' >&2
exit 1
