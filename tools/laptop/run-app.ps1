# Build and install this repository only. Run Gradle through the project environment wrapper.
param([switch]$SkipBuild, [switch]$BuildOnly, [switch]$Release, [switch]$Demo)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$proj = (Resolve-Path "$PSScriptRoot\..\..").Path
$pkg = "dev.carevisit.app"
$variant = if ($Release) { "release" } else { "debug" }
$task = if ($Release) { ":app:assembleRelease" } else { ":app:assembleDebug" }
if (-not $SkipBuild) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\gradle.ps1" $task
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
if ($BuildOnly) { exit 0 }
$apk = "$proj\app\build\outputs\apk\$variant\app-$variant.apk"
if (-not (Test-Path $apk)) { throw "Signed APK not found: $apk. Configure keystore.properties for release." }
& $ADB install -r $apk
if ($LASTEXITCODE -ne 0) { throw "Install failed. Debug/release signatures differ; uninstall explicitly if changing signing key." }
& $ADB shell pm grant --user current $pkg android.car.permission.CAR_SPEED
if ($LASTEXITCODE -ne 0) { throw "CAR_SPEED grant failed. Check the foreground emulator user." }
if ($Demo) { & $ADB shell am start -S --user current -n "$pkg/.MainActivity" --es demo visit }
else { & $ADB shell am start --user current -n "$pkg/.MainActivity" }
if ($LASTEXITCODE -ne 0) { throw "App launch failed." }
