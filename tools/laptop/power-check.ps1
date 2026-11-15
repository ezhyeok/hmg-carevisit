# 노트북 전원 점검 (노트북에서 실행. wait-emulator.sh가 시작할 때 부른다).
# 2026-11-13 01:26 노트북이 배터리 부족으로 최대 절전에 들어가 밤새 끊겼다(Kernel-Power 42 "Sleep Reason: Battery").
# 그 전 00:39~00:40에 전원 연결이 40초 동안 11번 바뀌었다(Kernel-Power 105): 충전기 접촉 불량이었다. 에뮬레이터를 띄우면
# 배터리로는 1시간 남짓 간다. 유휴 절전 때문이 아니었다(그래서 절전을 막는 장치는 넣지 않았다).
# 결과: 0 = AC 전원이고 안정, 3 = 배터리로 돌고 있다, 4 = 최근 1시간에 전원 연결이 자주 바뀌었다
Add-Type -AssemblyName System.Windows.Forms
$p = [System.Windows.Forms.SystemInformation]::PowerStatus
$online = $p.PowerLineStatus -eq [System.Windows.Forms.PowerLineStatus]::Online
$charge = [math]::Round($p.BatteryLifePercent * 100)
$changes = @(Get-WinEvent -FilterHashtable @{ LogName = "System"; ProviderName = "Microsoft-Windows-Kernel-Power"; Id = 105; StartTime = (Get-Date).AddHours(-1) } -ErrorAction SilentlyContinue).Count
$state = if ($online) { "AC" } else { "BATTERY" }
"power: $state charge=$charge% source-changes(1h)=$changes"
if (-not $online) { "WARN: 배터리로 돌고 있다. 충전기를 확인할 것 (에뮬레이터를 띄우면 1시간 남짓에 최대 절전)"; exit 3 }
if ($changes -ge 4) { "WARN: 최근 1시간에 전원 연결이 $changes 번 바뀌었다. 충전기 접촉을 확인할 것"; exit 4 }
exit 0
