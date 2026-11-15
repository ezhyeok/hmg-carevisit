#!/bin/bash
# 에뮬레이터가 시험할 수 있는 상태가 될 때까지 기다린다 (개발 서버에서 실행).
# 조건: 부팅 완료 + 운전자 프로필(user 10) 활성 + 가동 4분 이상 + 1분 load average가 기준 미만.
# (부팅 직후에는 load average가 아직 오르기 전이라 낮게 나온다. 그래서 가동 시간도 함께 본다)
#
# 부팅 직후에는 시스템 서비스가 한꺼번에 떠서 CPU가 모자란다. 이때 빌드나 앱 실행을 겹치면 carwatchdogd가
# VHAL 응답 지연을 장애로 보고 차량 서비스를 재시작시키고, 시스템이 user 0으로 돌아가 앱 실행이 실패한다.
# 사용: wait-emulator.sh [load 기준, 기본 8] [최대 대기 초, 기본 1200]
set -u
ADB='C:\Users\User\hmg-appdev-challenge\sdk\platform-tools\adb.exe'
MAX_LOAD="${1:-8}"
MIN_UPTIME=240
DEADLINE=$(( $(date +%s) + ${2:-1200} ))
HEALTH='powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\User\hmg-appdev-challenge\repo-visit\tools\laptop\emu-health.ps1 -Resume'
# 전원부터 본다: 배터리로 돌면 1시간 남짓에 노트북이 최대 절전에 들어간다 (2026-11-13 01:26, power-check.ps1)
timeout 40 ssh hmg-laptop 'powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\User\hmg-appdev-challenge\repo-visit\tools\laptop\power-check.ps1' 2>/dev/null | tr -d '\r' | grep -E "^(power|WARN)"
stalls=0
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  # adb devices에는 보이는데 adb shell이 응답하지 않으면 qemu가 일시 정지됐을 수 있다 (emu-health.ps1 참고)
  if ! boot=$(timeout 25 ssh hmg-laptop "$ADB shell getprop sys.boot_completed" 2>/dev/null | tr -d '\r\n'); then boot=""; fi
  if [ -z "$boot" ] && timeout 20 ssh hmg-laptop "$ADB devices" 2>/dev/null | grep -q "emulator-.*device$"; then
    stalls=$((stalls + 1))
    if [ "$stalls" -ge 2 ]; then
      echo "$(date +%T) adb shell 무응답 → qemu 상태 점검"
      timeout 60 ssh hmg-laptop "$HEALTH" 2>/dev/null | grep -v "CLIXML\|<Objs"
      stalls=0
    fi
  else
    stalls=0
  fi
  if [ "$boot" = "1" ]; then
    user=$(timeout 25 ssh hmg-laptop "$ADB shell am get-current-user" 2>/dev/null | tr -d '\r\n')
    load=$(timeout 25 ssh hmg-laptop "$ADB shell uptime" 2>/dev/null | sed 's/.*load average: \([0-9.]*\).*/\1/' | tr -d '\r\n')
    up=$(timeout 25 ssh hmg-laptop "$ADB shell cat /proc/uptime" 2>/dev/null | cut -d. -f1 | tr -d '\r\n')
    echo "$(date +%T) user=$user uptime=${up}s load1=$load"
    if [ "$user" = "10" ] && [ "${up:-0}" -ge "$MIN_UPTIME" ] && awk -v l="$load" -v m="$MAX_LOAD" 'BEGIN{exit !(l<m)}'; then
      # 다시 부팅하면 시각대가 America/Los_Angeles로 돌아올 수 있다(자동 시각대, 2026-11-12). 앱의 상대 날짜·다음 진료 예약은
      # 기기 시각대를 쓰므로 한국 시각으로 맞춰 둔다
      tz=$(timeout 25 ssh hmg-laptop "$ADB shell getprop persist.sys.timezone" 2>/dev/null | tr -d '\r\n')
      if [ "$tz" != "Asia/Seoul" ]; then
        timeout 25 ssh hmg-laptop "$ADB shell cmd time_zone_detector set_auto_detection_enabled false" >/dev/null 2>&1
        timeout 25 ssh hmg-laptop "$ADB shell service call alarm 3 s16 Asia/Seoul" >/dev/null 2>&1
        echo "time zone was ${tz:-unknown}: set to Asia/Seoul"
      fi
      echo READY
      exit 0
    fi
  fi
  sleep 20
done
echo "TIMEOUT: emulator not ready"
exit 1
