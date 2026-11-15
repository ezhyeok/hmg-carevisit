# Pleos Connect 에뮬레이터를 SSH 세션과 분리된 프로세스로 실행 (창 없음, cold boot)
# -Memory: 게스트 RAM(MB). 문서 권장은 4096, 노트북 메모리가 빠듯하면 3072
# -Gpu: 렌더링 방식. swiftshader_indirect는 CPU로 그려서 느리다(에뮬레이터 프로세스가 호스트 CPU의 80% 이상 사용).
#       host는 노트북 GPU를 쓴다
param([int]$Memory = 4096, [string]$Gpu = "host")
. "$PSScriptRoot\env.ps1"
if (-not $P) { throw "env.ps1 not loaded" }
# -crash-report-mode disabled: 크래시 보고기(crashpad)가 qemu를 덤프하려고 일시 정지시킨 채 죽어서 에뮬레이터가
#   멈춘 적이 있다(2026-11-11, emu-health.ps1 참고). 끄면 진짜 크래시는 프로세스 종료로 바로 드러난다
$emuArgs = "-avd $AVD -memory $Memory -no-window -no-snapshot -no-boot-anim -no-audio -gpu $Gpu -crash-report-mode disabled -no-metrics"
# 에뮬레이터가 띄운 adb 서버가 로그 파일 핸들을 물려받아 계속 잡고 있으므로, 실행마다 새 로그 파일을 쓴다
$log = "$P\logs\emulator-$(Get-Date -Format yyyyMMdd-HHmmss).log"
# 에뮬레이터는 ANDROID_* 환경변수가 필요하므로 cmd 안에서 설정한다 (WMI 프로세스는 환경변수를 물려받지 않음)
$envSet = "set ANDROID_HOME=$env:ANDROID_HOME&& set ANDROID_SDK_ROOT=$env:ANDROID_SDK_ROOT&& set ANDROID_USER_HOME=$env:ANDROID_USER_HOME&& set ANDROID_AVD_HOME=$env:ANDROID_AVD_HOME&& "
$cmd = "cmd /c `"$envSet`"$EMU`" $emuArgs > `"$log`" 2>&1`""
$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmd; CurrentDirectory = $P }
"emulator launched pid=$($r.ProcessId) rc=$($r.ReturnValue) log=$log"
