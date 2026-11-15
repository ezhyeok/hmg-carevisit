"""Pleos 저장소 XML에서 system image용 package.xml(localPackage)을 만든다.

최신 cmdline-tools의 sdkmanager는 Android CLI로 위임되면서 Pleos 이미지 경로를 처리하지 못해,
이미지를 직접 받아 SDK에 배치하고 이 스크립트로 만든 package.xml을 함께 둔다.

사용: python3 make-pleos-package-xml.py crp-sys-img.xml pleos-package.xml
  crp-sys-img.xml: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml
"""
import re
import sys

src, dst = sys.argv[1], sys.argv[2]
t = open(src, encoding="utf-8").read()
lic = re.search(r'<license id="pleos-connect-sdk-license"[^>]*>.*?</license>', t, re.S).group(0)
pkg = re.search(r'<remotePackage (path="[^"]+")>(.*?)</remotePackage>', t, re.S)
body = pkg.group(2)
body = re.sub(r"\s*<channelRef[^>]*/>", "", body)
body = re.sub(r"\s*<archives>.*?</archives>", "", body, flags=re.S)
body = body.replace('xsi:type="sys-img:sysImgDetailsType"', 'xsi:type="ns3:sysImgDetailsType"')
out = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/01" '
    'xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01" '
    'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">\n'
    f"{lic}\n<localPackage {pkg.group(1)} obsolete=\"false\">{body}</localPackage>\n</ns2:repository>\n"
)
open(dst, "w", encoding="utf-8").write(out)
