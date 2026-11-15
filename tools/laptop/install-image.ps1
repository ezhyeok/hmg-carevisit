# Pleos Connect Emulator 2.1.0 system image 설치 (8개 연결로 분할 다운로드 → SHA-1 검증 → 프로젝트 SDK에 배치)
# 프로젝트 루트 = 이 스크립트 위치에서 위로 올라가며 sdk\ 폴더가 있는 곳 (백그라운드 실행 시 환경변수가 비어 있을 수 있어 쓰지 않음)
$P = $PSScriptRoot
while ($P -and -not (Test-Path (Join-Path $P "sdk"))) { $P = Split-Path -Parent $P }
if (-not $P) { throw "project root (folder containing sdk\) not found from $PSScriptRoot" }
$log  = "$P\logs\image.log"
function L($m) { "$(Get-Date -Format s) $m" | Add-Content -Encoding UTF8 $log }
$u    = "https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp_car_emulator.zip"
$size = 4087751964
$sha1 = "b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa"
$n    = 8
$dl   = "$P\downloads"
$dest = "$P\sdk\system-images\android-34\pleos_car_emulator\x86_64"
New-Item -ItemType Directory -Force $dl | Out-Null
$chunk = [math]::Ceiling($size / $n)
$ranges = 0..($n-1) | ForEach-Object { $s = $_ * $chunk; [pscustomobject]@{ i = $_; s = $s; e = [math]::Min($s + $chunk - 1, $size - 1) } }
L "download start ($n parts)"
for ($attempt = 1; $attempt -le 4; $attempt++) {
  $todo = $ranges | Where-Object { -not (Test-Path "$dl\part$($_.i)") -or (Get-Item "$dl\part$($_.i)").Length -ne ($_.e - $_.s + 1) }
  if (-not $todo) { break }
  L "attempt $attempt : $(@($todo).Count) parts"
  $procs = $todo | ForEach-Object { Start-Process curl.exe -ArgumentList "-s -S --retry 10 --retry-all-errors --retry-delay 3 -r $($_.s)-$($_.e) -o `"$dl\part$($_.i)`" $u" -PassThru -WindowStyle Hidden }
  $procs | Wait-Process
}
$bad = $ranges | Where-Object { (Get-Item "$dl\part$($_.i)" -ErrorAction SilentlyContinue).Length -ne ($_.e - $_.s + 1) }
if ($bad) { L "FAILED: incomplete parts $($bad.i -join ',')"; exit 1 }
L "download done, joining"
$out = [IO.File]::Create("$dl\crp.zip")
foreach ($r in $ranges) { $in = [IO.File]::OpenRead("$dl\part$($r.i)"); $in.CopyTo($out); $in.Close() }
$out.Close()
$h = (Get-FileHash "$dl\crp.zip" -Algorithm SHA1).Hash.ToLower()
L "sha1=$h"
if ($h -ne $sha1) { L "FAILED: SHA-1 mismatch"; exit 1 }
$ranges | ForEach-Object { Remove-Item "$dl\part$($_.i)" }
L "extracting"
New-Item -ItemType Directory -Force "$dl\extract", $dest | Out-Null
tar.exe -xf "$dl\crp.zip" -C "$dl\extract"
if ($LASTEXITCODE -ne 0) { L "FAILED: tar exit $LASTEXITCODE"; exit 1 }
Get-ChildItem "$dl\extract\crp_avd_out_x86_64" -Force | Move-Item -Destination $dest -Force
# package.xml 은 make-pleos-package-xml.py 로 생성해 노트북 scripts\ 에 둔다
Copy-Item "$P\scripts\pleos-package.xml" "$dest\package.xml" -Force
Remove-Item "$dl\crp.zip"; Remove-Item "$dl\extract" -Recurse -Force
L "INSTALLED -> $dest"
L "DONE"
