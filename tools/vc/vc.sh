#!/bin/bash
# Pleos Connect Virtual Console을 HTTP API로 조작한다 (브라우저 없이 차량 상태 변경, 시연 시나리오 재생).
#
# 개발 서버에서 실행한다. 노트북의 에뮬레이터 8088 포트를 SSH 터널로 가져온다.
#   vc.sh up                     터널을 열고 시뮬레이터를 켠다 (heartbeat를 백그라운드로 보냄)
#   vc.sh down                   heartbeat와 터널을 끈다
#   vc.sh gear Drive             기어: Park | Reverse | Neutral | Drive
#   vc.sh speed 40               차속(km/h). 주행 제한을 걸려면 기어를 Drive로 먼저 바꾼다
#   vc.sh brake 50               브레이크 페달(%)
#   vc.sh door fl open           도어: fl|fr|rl|rr  open|close
#   vc.sh window fl 30           창문 위치(0~100)
#   vc.sh recirc on              내기순환: on|off
#   vc.sh hvac-power on          공조 전원: on|off
#   vc.sh temp driver 23.5       설정 온도: driver|passenger
#   vc.sh fan 3                  풍량(0~8)
#   vc.sh cruise longitudinal    크루즈: not_ready | ready | longitudinal | lateral | full_autonomous
#   vc.sh trip-min 90            이번 주행의 주행 시간(분)
#   vc.sh soc 35                 배터리 잔량(%)
#   vc.sh seat driver on         좌석 점유: driver|passenger|rear_left|rear_right  on|off
#   vc.sh put /vehicle/hvac ac=true       그 밖의 항목 (vc.sh catalog로 목록 확인)
#   vc.sh state hvac.recirc_on   현재 상태 조회 (점으로 구분한 경로, 생략하면 전체)
#   vc.sh catalog                조작 가능한 항목과 요청 형식
#   vc.sh play demo.vc           시나리오 파일 재생. 한 줄에 명령 하나, `sleep 초`, #은 주석
#
# 차량 시뮬레이터는 브라우저 세션(heartbeat)이 있을 때만 동작한다. 꺼져 있으면 여기서 바꾼 값도,
# 앱이 보낸 제어 요청도 차량 상태에 반영되지 않는다. 그래서 먼저 `vc.sh up`을 실행한다.
set -euo pipefail

HOST="${VC_SSH_HOST:-hmg-laptop}"
PORT="${VC_LOCAL_PORT:-18088}"
ADB="${VC_ADB:-C:\\Users\\User\\hmg-appdev-challenge\\sdk\\platform-tools\\adb.exe}"
BASE="http://127.0.0.1:$PORT"
RUN="${VC_RUN_DIR:-$(cd "$(dirname "$0")/../.." && pwd)/.local/vc}"
mkdir -p "$RUN"

put() {  # put <path> <k=v>...
  local path="$1"; shift
  local q; q=$(IFS='&'; echo "$*")
  local code; code=$(curl -s -m 15 -X PUT "$BASE$path?$q" -o /dev/null -w '%{http_code}') || code="000"
  echo "PUT $path?$q -> $code"
  [ "$code" = "200" ]
}

onoff() { case "$1" in on|true|open) echo true ;; off|false|close) echo false ;; *) echo "expected on/off, got: $1" >&2; exit 2 ;; esac; }

run() {
  local cmd="${1:-help}"; shift || true
  case "$cmd" in
    up)
      ssh "$HOST" "$ADB forward tcp:8088 tcp:8088" >/dev/null
      if ! curl -s -m 3 -o /dev/null "$BASE/vehicle/catalog"; then
        ssh -f -N -o ExitOnForwardFailure=yes -L "$PORT:127.0.0.1:8088" "$HOST"
      fi
      if [ -f "$RUN/hb.pid" ] && kill -0 "$(cat "$RUN/hb.pid")" 2>/dev/null; then
        echo "heartbeat already running"
      else
        nohup bash -c "while true; do curl -s -m 2 -X POST '$BASE/browser/session?client=vc-sh' -o /dev/null; sleep 1; done" >/dev/null 2>&1 &
        echo $! > "$RUN/hb.pid"
      fi
      sleep 2
      run state browser.active
      ;;
    down)
      if [ -f "$RUN/hb.pid" ]; then kill "$(cat "$RUN/hb.pid")" 2>/dev/null || true; rm -f "$RUN/hb.pid"; fi
      pkill -f "ssh -f -N -o ExitOnForwardFailure=yes -L $PORT:127.0.0.1:8088" 2>/dev/null || true
      echo "stopped"
      ;;
    gear)       put /vehicle/gear "gear=$1" ;;
    speed)      put /vehicle/controls "speed=$1" "accelerator=${2:-0}" "brake=0" ;;
    brake)      put /vehicle/controls "speed=${2:-0}" "accelerator=0" "brake=$1" ;;
    door)       put /vehicle/body "door_$1=$(onoff "$2")" ;;
    window)     put /vehicle/body "window_$1=$2" ;;
    recirc)     put /vehicle/hvac "recirc=$(onoff "$1")" ;;
    hvac-power) put /vehicle/hvac "power=$(onoff "$1")" ;;
    temp)       put /vehicle/hvac "$1_temp=$2" ;;
    fan)        put /vehicle/hvac "fan_speed=$1" ;;
    cruise)     put /vehicle/driving-mode "ad_mode_state=$1" ;;
    trip-min)   put /vehicle/odometer "usage_time_min=$1" ;;
    soc)        put /vehicle/ev-battery "field=soc" "value=$1" ;;
    seat)       put /vehicle/seat "kind=occupied" "area=$1" "value=$(onoff "$2")" ;;
    put)        put "$@" ;;
    state)
      curl -s -m 15 "$BASE/vehicle/state" | python3 -I -c '
import json, sys
node = json.load(sys.stdin)
for key in filter(None, (sys.argv[1] if len(sys.argv) > 1 else "").split(".")):
    node = node[key]
print(json.dumps(node, ensure_ascii=False, indent=1) if isinstance(node, (dict, list)) else node)' "${1:-}"
      ;;
    catalog)
      curl -s -m 15 "$BASE/vehicle/catalog" | python3 -I -c '
import json, sys
for f in json.load(sys.stdin)["features"]:
    c = f.get("control", {})
    fields = c.get("field") or ",".join((c.get("field_by_area") or {}).values())
    extra = "&".join(f"{k}={v}" for k, v in (c.get("params") or {}).items())
    print("%-12s %-28s PUT %-32s %s %s" % (f["domain"], f["id"], f.get("write_endpoint", ""), fields, extra))'
      ;;
    play)
      while IFS= read -r line || [ -n "$line" ]; do
        line="${line%%#*}"
        [ -z "${line// /}" ] && continue
        # shellcheck disable=SC2086
        set -- $line
        if [ "$1" = "sleep" ]; then sleep "$2"; else echo "$(date +%T) $line"; run "$@" || return $?; fi
      done < "$1"
      ;;
    help|--help|-h) sed -n '2,29p' "$0" ;;
    *) echo "알 수 없는 VC 명령: $cmd" >&2; return 2 ;;
  esac
}

run "$@"
