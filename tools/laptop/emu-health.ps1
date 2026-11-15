# 에뮬레이터 프로세스(qemu) 상태 점검. 노트북에서 실행: powershell -ExecutionPolicy Bypass -File emu-health.ps1 [-Resume]
#
# 2026-11-11: 에뮬레이터의 크래시 보고기(sdk\emulator\crashpad_handler.exe)가 qemu를 덤프하려고 일시 정지시킨 뒤
# 자신이 죽어서(예외 0x80000003) qemu의 스레드가 모두 Suspended로 남았다. 증상: adb devices에는 보이지만
# adb shell이 응답하지 않고, qemu CPU 사용률이 0%. 재부팅하지 않고 NtResumeProcess로 재개하면 돌아온다
# (멈춘 동안 게스트의 system_server가 watchdog으로 재시작되므로 wait-emulator.sh로 다시 기다린다).
# 재발 방지: start-emulator.ps1이 -crash-report-mode disabled로 띄운다.
param([switch]$Resume)
$q = Get-Process qemu-system-x86_64-headless, qemu-system-x86_64 -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $q) { "qemu: not running"; exit 2 }
$threads = $q.Threads
$suspended = @($threads | Where-Object { $_.ThreadState -eq 'Wait' -and $_.WaitReason -eq 'Suspended' }).Count
$c0 = $q.CPU; Start-Sleep -Seconds 3; $q.Refresh()
$cpu = [math]::Round(100 * ($q.CPU - $c0) / 3 / [Environment]::ProcessorCount, 1)
"qemu pid=$($q.Id) started=$($q.StartTime.ToString('HH:mm:ss')) cpu=$cpu% threads=$($threads.Count) suspended=$suspended"
$since = (Get-Date).AddHours(-2)
Get-WinEvent -FilterHashtable @{ LogName = 'Application'; Id = 1000; StartTime = $since } -ErrorAction SilentlyContinue |
  Where-Object { $_.Message -match 'crashpad_handler|qemu-system' } |
  ForEach-Object { "app error $($_.TimeCreated.ToString('HH:mm:ss')): $(($_.Message -split "`n")[0].Trim())" }
if ($suspended -gt 0 -and $suspended -eq $threads.Count) {
  if (-not $Resume) { "SUSPENDED: run with -Resume"; exit 3 }
  Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class EmuNtProc { [DllImport("ntdll.dll")] public static extern int NtResumeProcess(IntPtr h); }
"@
  $rc = [EmuNtProc]::NtResumeProcess($q.Handle)
  Start-Sleep -Seconds 2
  $left = @((Get-Process -Id $q.Id).Threads | Where-Object { $_.WaitReason -eq 'Suspended' }).Count
  "RESUMED rc=$rc suspended_after=$left (게스트 system_server가 재시작될 수 있으니 wait-emulator.sh로 기다린다)"
  exit 0
}
"OK"
