# Pleos Connect v2.1.0 AVD 생성 (문서 설정: Cold boot, RAM 4GB). AVD는 프로젝트 폴더의 android-home\avd 에 만들어진다
. "$PSScriptRoot\env.ps1"
if (-not $P) { throw "env.ps1 not loaded" }
$pkg = "system-images;android-34;pleos_car_emulator;x86_64"
$out = "no" | & "$P\sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n $AVD -k $pkg -d Pleos_Connect_v2_0 --force 2>&1
$out | ForEach-Object { "$_" } | Where-Object { $_ -and $_ -notmatch '^\s*\[' } | Select-Object -Last 5
$cfg = "$env:ANDROID_AVD_HOME\$AVD.avd\config.ini"
if (-not (Test-Path $cfg)) { throw "AVD not created: $cfg" }
$set = @{ "hw.ramSize" = "4096"; "fastboot.forceColdBoot" = "yes"; "fastboot.forceFastBoot" = "no" }
$lines = Get-Content $cfg | Where-Object { $k = ($_ -split '=', 2)[0].Trim(); -not $set.ContainsKey($k) }
$lines += $set.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }
$lines | Set-Content -Encoding ASCII $cfg
"--- $cfg"
Get-Content $cfg | Select-String "image.sysdir|hw.device.name|hw.ramSize|hw.lcd|forceColdBoot|tag.id|hw.gpu"
