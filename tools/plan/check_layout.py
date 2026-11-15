"""Structural pagination regression check; Microsoft Word rendering still needs manual review.
Run after build_docx.py: python3 -I tools/plan/check_layout.py
"""
from pathlib import Path
import sys
import zipfile
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
NS = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main',
      'wp': 'http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing'}
W = '{' + NS['w'] + '}'

def enabled(node):
    return node is not None and node.get(W + 'val', '1') not in ('0', 'false', 'off')

with zipfile.ZipFile(ROOT / 'contest-application-consent.docx') as archive:
    doc = ET.fromstring(archive.read('word/document.xml'))
    for name in archive.namelist():
        if name.endswith(('.xml', '.rels')):
            ET.fromstring(archive.read(name))
parents = {child: parent for parent in doc.iter() for child in parent}
boxes = {}
for control in doc.findall('.//w:sdt', NS):
    tag = control.find('w:sdtPr/w:tag', NS)
    if tag is not None:
        boxes[tag.get(W + 'val')] = control
assert len(boxes) == 8
for tag, control in boxes.items():
    content = control.find('w:sdtContent', NS)
    first = content.find('w:p', NS)
    assert first is not None, (tag, 'missing leading paragraph')
    assert not enabled(first.find('w:pPr/w:keepNext', NS)), (tag, 'entry keep chain')
    row = parents[parents[control]]
    assert row.tag == W + 'tr'
    if tag not in ('app_summary', 'shortform_link'):
        assert not enabled(row.find('w:trPr/w:cantSplit', NS)), (tag, 'outer row locked')
    assert not any(enabled(x) for x in content.findall('.//w:pageBreakBefore', NS)), (tag, 'page break')
    for table in content.findall('.//w:tbl', NS):
        for row in table.findall('w:tr', NS):
            assert not enabled(row.find('w:trPr/w:cantSplit', NS)), (tag, 'nested row locked')
    # A figure can keep only its caption; the caption must end that chain.
    for image_p in content.findall('.//w:p', NS):
        if image_p.find('.//w:drawing', NS) is None:
            continue
        if enabled(image_p.find('w:pPr/w:keepNext', NS)):
            siblings = list(parents[image_p])
            caption = siblings[siblings.index(image_p) + 1]
            assert caption.tag == W + 'p'
            assert not enabled(caption.find('w:pPr/w:keepNext', NS)), (tag, 'image chain continues')
box7 = boxes['reference_link']
shots = box7.findall('.//wp:extent', NS)
assert len(shots) == 4, 'expected the four supplied captures'
assert all(int(s.get('cy')) <= 8 * 360000 for s in shots), 'screenshot exceeds 8cm'
text = ''.join(box7.itertext())
assert 'docs/demo/jinryo-donghaeng-demo.mp4' in text and 'apk/' in text
for control in boxes.values():
    assert 'github.com/' not in ''.join(control.itertext()).lower()
print('PASS: 8 box starts; splittable long-box/nested rows; bounded image-caption pairs; 4 captures <=8cm; video/APK references; XML parses.')
