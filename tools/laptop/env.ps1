# 노트북 공통 환경: 모든 경로는 프로젝트 폴더 안 (C:\Users\User\hmg-appdev-challenge)
# 프로젝트 루트 = 이 스크립트 위치에서 위로 올라가며 sdk\ 폴더가 있는 곳 (백그라운드 실행 시 환경변수가 비어 있을 수 있어 쓰지 않음)
$P = $PSScriptRoot
while ($P -and -not (Test-Path (Join-Path $P "sdk"))) { $P = Split-Path -Parent $P }
if (-not $P) { throw "project root (folder containing sdk\) not found from $PSScriptRoot" }
$env:ANDROID_HOME      = "$P\sdk"
$env:ANDROID_SDK_ROOT  = "$P\sdk"
$env:ANDROID_USER_HOME = "$P\android-home"
$env:ANDROID_AVD_HOME  = "$P\android-home\avd"
$env:GRADLE_USER_HOME  = "$P\gradle-home"
$ADB = "$P\sdk\platform-tools\adb.exe"
$EMU = "$P\sdk\emulator\emulator.exe"
$AVD = "pleos_connect_21"
