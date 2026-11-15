# 노트북 repo-visit 루트와 무관하게 실행할 수 있는 가족 relay.
param(
    [string]$BindAddress = "0.0.0.0",
    [ValidateRange(1,65535)][int]$Port = 8765
)
$ErrorActionPreference = "Stop"
$relay = (Resolve-Path "$PSScriptRoot\..\family\relay.py").Path
Write-Host "차량 relay 주소: http://10.0.2.2:$Port"
Write-Host "휴대폰: http://<노트북 LAN IP>:$Port/ 에서 가족 주소의 # 뒤 32자리 코드 입력"
Write-Host "Windows 방화벽은 사설 네트워크의 이 TCP 포트만 허용하세요. 자동 변경하지 않습니다."
Write-Host "시연용 HTTP입니다. 운영에는 HTTPS + 실제 인증이 필요합니다. 종료: Ctrl+C (메모리 삭제)"
if (Get-Command py -ErrorAction SilentlyContinue) {
    & py -3 $relay --host $BindAddress --port $Port
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    & python $relay --host $BindAddress --port $Port
} else {
    throw "Python 3.9 이상이 필요합니다. Python 설치 후 다시 실행하세요."
}
exit $LASTEXITCODE
