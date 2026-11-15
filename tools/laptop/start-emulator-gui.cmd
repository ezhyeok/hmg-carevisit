@echo off
rem Start the Pleos Connect emulator WITH a window and GPU acceleration.
rem Run this by double-clicking on the laptop desktop session (not over SSH):
rem Both GUI and headless launch use host GPU acceleration (CLAUDE.md).
setlocal
set "P=%~dp0..\..\.."
for %%I in ("%P%") do set "P=%%~fI"
if not exist "%P%\sdk\emulator\emulator.exe" (
  echo Project SDK not found under %P%
  pause
  exit /b 1
)
set "ANDROID_HOME=%P%\sdk"
set "ANDROID_SDK_ROOT=%P%\sdk"
set "ANDROID_USER_HOME=%P%\android-home"
set "ANDROID_AVD_HOME=%P%\android-home\avd"
"%P%\sdk\emulator\emulator.exe" -avd pleos_connect_21 -memory 4096 -no-snapshot -no-boot-anim -gpu host -no-audio -crash-report-mode disabled -no-metrics
if errorlevel 1 pause
