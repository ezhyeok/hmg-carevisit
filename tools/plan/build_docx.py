"""Fill the official application form (= planning document) from the blank template.

    python3 -I tools/plan/build_docx.py                  # figures + docx + markdown
    python3 -I tools/plan/build_docx.py --skip-figures   # reuse docs/plan/assets/*.png
    python3 -I tools/plan/build_docx.py --preview DIR    # also PDF + page PNGs in DIR (LibreOffice, pdftoppm)

Input : docs/contest/01_공모전/접수양식/contest-application-consent.docx (never modified)
Output: contest-application-consent.docx (repo root, git-ignored: the entrant adds personal
        info and a signature), docs/plan/planning-doc.md, docs/plan/assets/*.svg|png

What it changes in the form
  01  참가 단위 = 개인, 참가 인원 = 1, 앱명. Personal fields stay blank.
  02  앱명 and the eight boxes. Each box keeps its content control (alias/tag/id) but the
      control becomes rich text so it can hold tables and figures; boxes 2-7 may split
      across pages.
  03  untouched.  04  앱명 only.

Screenshots: put PNG/JPEG files in docs/plan/assets/screens/ (e.g. "1_주행 화면 질문 중.png").
They are included in section 7 when present; the caption is the file name without the
leading number. Re-run this script after replacing them (e.g. with emulator captures).

Standard library only (zipfile + string edits), so it runs with `python3 -I`.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True   # keep tools/plan free of __pycache__
import zipfile
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import ooxml as X  # noqa: E402
import content  # noqa: E402
import render_docx  # noqa: E402
import render_md  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
TEMPLATE = os.path.join(ROOT, "docs", "contest", "01_공모전", "접수양식", "contest-application-consent.docx")
OUT = os.path.join(ROOT, "contest-application-consent.docx")
PLAN_DIR = os.path.join(ROOT, "docs", "plan")
ASSETS = os.path.join(PLAN_DIR, "assets")
SCREENS = os.path.join(ASSETS, "screens")
MD_OUT = os.path.join(PLAN_DIR, "planning-doc.md")

FIGURES = {"flow": "fig-flow.png", "interview": "fig-interview.png"}



# ------------------------------------------------------------------ figures / screenshots

def screenshots():
    if not os.path.isdir(SCREENS):
        return []
    shots = []
    for name in sorted(os.listdir(SCREENS)):
        if os.path.splitext(name)[1].lower() not in ('.png', '.jpg', '.jpeg'):
            continue
        label = re.sub(r'^\d+[_\-. ]*', '', os.path.splitext(name)[0]).replace('_', ' ').strip() or name
        shots.append((os.path.join(SCREENS, name), label))
    return shots


def resolve_png(key):
    if key == "screens":
        shots = screenshots()
        if shots:
            return shots
    path = os.path.join(ASSETS, FIGURES[key])
    if not os.path.exists(path):
        raise SystemExit("missing figure %s (run without --skip-figures)" % path)
    return path


def resolve_md(key):
    target = resolve_png(key)
    if isinstance(target, list):
        return [(os.path.relpath(p, PLAN_DIR), label) for p, label in target]
    return os.path.relpath(target, PLAN_DIR)


# ------------------------------------------------------------------ document.xml edits

def _paragraph_bounds(doc, pos):
    """(start, end) of the <w:p> element that contains character index pos."""
    start = pos
    while True:
        start = doc.rfind('<w:p', 0, start)
        if start < 0:
            raise ValueError("no paragraph before %d" % pos)
        nxt = doc[start + 4]
        if nxt in ' >':
            break
    end = doc.index('</w:p>', pos) + len('</w:p>')
    return start, end


def _text_of(xml):
    return ''.join(re.findall(r'<w:t(?: [^>]*)?>([^<]*)</w:t>', xml))


def _rebuild_label(par, label, value, suffix=""):
    """Keep the paragraph's pPr and line-break runs; replace its text by label + value + suffix."""
    open_tag = re.match(r'<w:p\b[^>]*>', par).group(0)
    ppr = re.search(r'<w:pPr>.*?</w:pPr>', par, re.S)
    ppr = ppr.group(0) if ppr else ''
    runs = re.findall(r'<w:r\b[^>]*>.*?</w:r>', par, re.S)
    lead = [r for r in runs[:1] if '<w:br/>' in r and '<w:t' not in r]
    trail = [r for r in runs[-1:] if '<w:br/>' in r and '<w:t' not in r and runs[:1] != runs[-1:]]
    body = X.run(label) + X.run(value, bold=True)
    if suffix:
        body += X.run(suffix)
    return open_tag + ppr + ''.join(lead) + body + ''.join(trail) + '</w:p>'


def edit_part1_and_names(doc):
    # 참가 단위: □ 개인 -> ■ 개인
    i = doc.index('참가 단위:')
    s, e = _paragraph_bounds(doc, i)
    par = doc[s:e]
    assert '□ 개인' in par, "participation-unit checkbox not found"
    doc = doc[:s] + par.replace('□ 개인', '■ 개인', 1) + doc[e:]

    # 참가 부문은 본인이 선택할 수 있도록 빈칸을 보존한다.

    # 참가 인원: 1명
    i = doc.index('참가 인원:')
    s, e = _paragraph_bounds(doc, i)
    doc = doc[:s] + _rebuild_label(doc[s:e], "참가 인원: ", "1", "명") + doc[e:]

    # 앱명 / 프로젝트명 (01, 02 header, 04)
    count = 0
    pos = 0
    while True:
        i = doc.find('프로젝트명:', pos)
        if i < 0:
            break
        s, e = _paragraph_bounds(doc, i)
        par = doc[s:e]
        text = _text_of(par)
        if '앱명' in text and '___' in text:
            new = _rebuild_label(par, "앱명 / 프로젝트명: ", content.APP_NAME)
            doc = doc[:s] + new + doc[e:]
            pos = s + len(new)
            count += 1
        else:
            pos = e
    assert count == 3, "expected 3 app-name lines, found %d" % count
    return doc


def fill_box(doc, section, inner_xml):
    tag = '<w:tag w:val="%s"/>' % section.tag
    i = doc.index(tag)
    s, _ = _paragraph_bounds(doc, i)
    sdt_end = doc.index('</w:sdt>', i) + len('</w:sdt>')
    e = doc.index('</w:p>', sdt_end) + len('</w:p>')
    sdtpr = re.search(r'<w:sdtPr>.*?</w:sdtPr>', doc[s:e], re.S).group(0)
    sdtpr = re.sub(r'<w:text\b[^>]*/>', '', sdtpr)          # plain text -> rich text control
    new = '<w:sdt>%s<w:sdtContent>%s</w:sdtContent></w:sdt>' % (sdtpr, inner_xml)

    tbl = doc.rfind('<w:tbl>', 0, s)
    # sanity: the heading right before this box is "<n>. ..."
    hs, he = _paragraph_bounds(doc, doc.rfind('</w:p>', 0, tbl) - 1)
    heading = _text_of(doc[hs:he]).strip()
    assert heading.startswith('%d.' % section.number), (section.tag, heading)

    head = doc[tbl:s]
    # Long boxes may start with a small fragment. Keep the one-line boxes atomic.
    if section.number not in (1, 8):
        head = re.sub(r'<w:cantSplit\b[^>]*/>', '<w:cantSplit w:val="0"/>', head)
    return doc[:tbl] + head + new + doc[e:]


# ------------------------------------------------------------------ other parts

BULLET_ABSTRACT = (
    '<w:abstractNum w:abstractNumId="90"><w:multiLevelType w:val="hybridMultilevel"/>'
    '<w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/>'
    '<w:lvlJc w:val="left"/><w:pPr><w:ind w:left="260" w:hanging="200"/></w:pPr>'
    '<w:rPr><w:rFonts w:ascii="맑은 고딕" w:eastAsia="맑은 고딕" w:hAnsi="맑은 고딕" w:hint="eastAsia"/>'
    '<w:color w:val="0064FF"/></w:rPr></w:lvl>'
    '<w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="–"/>'
    '<w:lvlJc w:val="left"/><w:pPr><w:ind w:left="520" w:hanging="200"/></w:pPr>'
    '<w:rPr><w:rFonts w:ascii="맑은 고딕" w:eastAsia="맑은 고딕" w:hAnsi="맑은 고딕" w:hint="eastAsia"/>'
    '<w:color w:val="686A72"/></w:rPr></w:lvl></w:abstractNum>')
BULLET_NUM = '<w:num w:numId="%d"><w:abstractNumId w:val="90"/></w:num>' % render_docx.BULLET_NUM_ID


def edit_numbering(xml):
    assert 'w:abstractNumId="90"' not in xml and 'w:numId="%d"' % render_docx.BULLET_NUM_ID not in xml
    i = xml.index('<w:num ')
    xml = xml[:i] + BULLET_ABSTRACT + xml[i:]
    j = xml.rindex('</w:num>') + len('</w:num>')
    return xml[:j] + BULLET_NUM + xml[j:]


def edit_rels(xml, media):
    rels = ''.join('<Relationship Id="%s" Type="http://schemas.openxmlformats.org/officeDocument/2006/'
                   'relationships/image" Target="%s"/>' % (rid, part) for rid, part, _ in media.items)
    return xml.replace('</Relationships>', rels + '</Relationships>')


def edit_content_types(xml, media):
    exts = sorted({os.path.splitext(part)[1].lstrip('.') for _, part, _ in media.items})
    for ext in exts:
        if 'Extension="%s"' % ext not in xml:
            ctype = 'image/png' if ext == 'png' else 'image/jpeg'
            xml = xml.replace('<Default Extension="xml"',
                              '<Default Extension="%s" ContentType="%s"/><Default Extension="xml"' % (ext, ctype))
    return xml


def build_docx(template, out):
    media = render_docx.Media()
    with zipfile.ZipFile(template) as zin:
        names = [n for n in zin.namelist() if not n.endswith('/')]
        parts = {n: zin.read(n) for n in names}

    doc = parts['word/document.xml'].decode('utf-8')
    doc = edit_part1_and_names(doc)
    for section in content.SECTIONS:
        inner = render_docx.render_blocks(section.blocks, media, resolve_png)
        doc = fill_box(doc, section, inner)
    parts['word/document.xml'] = doc.encode('utf-8')
    parts['word/numbering.xml'] = edit_numbering(parts['word/numbering.xml'].decode('utf-8')).encode('utf-8')
    parts['word/_rels/document.xml.rels'] = edit_rels(
        parts['word/_rels/document.xml.rels'].decode('utf-8'), media).encode('utf-8')
    parts['[Content_Types].xml'] = edit_content_types(parts['[Content_Types].xml'].decode('utf-8'),
                                                      media).encode('utf-8')

    for name in ('word/document.xml', 'word/numbering.xml', 'word/_rels/document.xml.rels', '[Content_Types].xml'):
        ET.fromstring(parts[name])                       # well-formedness check

    tmp = out + '.tmp'
    with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
        for n in names:
            zout.writestr(n, parts[n])
        for _, part, src in media.items:
            with open(src, 'rb') as f:
                zout.writestr('word/' + part, f.read())
    os.replace(tmp, out)
    return media


def write_markdown():
    note = ("> 접수양식(기획서) 02의 본문을 검토용으로 옮긴 사본이다. 제출본은 repo 루트의 "
            "`contest-application-consent.docx`이고 `python3 -I tools/plan/build_docx.py`로 다시 만든다. "
            "내용을 고칠 때는 `tools/plan/content.py`를 고친다.")
    md = render_md.render_document(content.APP_NAME, content.SECTIONS, resolve_md, note)
    with open(MD_OUT, 'w', encoding='utf-8') as f:
        f.write(md)


FONTS_CONF = """<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <include ignore_missing="yes">/etc/fonts/fonts.conf</include>
  <match target="pattern"><test qual="any" name="family"><string>맑은 고딕</string></test>
    <edit name="family" mode="assign" binding="strong"><string>Noto Sans CJK KR</string></edit></match>
  <match target="pattern"><test qual="any" name="family"><string>Malgun Gothic</string></test>
    <edit name="family" mode="assign" binding="strong"><string>Noto Sans CJK KR</string></edit></match>
  <match target="pattern"><test qual="any" name="family"><string>Consolas</string></test>
    <edit name="family" mode="assign" binding="strong"><string>DejaVu Sans Mono</string></edit></match>
</fontconfig>
"""


def preview(docx, outdir):
    """PDF + PNG pages for visual QA. Malgun Gothic is substituted by Noto Sans CJK KR."""
    os.makedirs(outdir, exist_ok=True)
    conf = os.path.join(outdir, 'fonts.conf')
    with open(conf, 'w') as f:
        f.write(FONTS_CONF)
    env = dict(os.environ, FONTCONFIG_FILE=conf)
    subprocess.run(['soffice', '-env:UserInstallation=file://' + os.path.join(os.path.abspath(outdir), 'lo-profile'), '--headless', '--convert-to', 'pdf', '--outdir', outdir, docx],
                   check=True, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=300)
    pdf = os.path.join(outdir, os.path.splitext(os.path.basename(docx))[0] + '.pdf')
    for old in os.listdir(outdir):
        if old.startswith('page-') and old.endswith('.png'):
            os.remove(os.path.join(outdir, old))
    subprocess.run(['pdftoppm', '-png', '-r', '80', pdf, os.path.join(outdir, 'page')], check=True)
    shutil.rmtree(os.path.join(os.path.abspath(outdir), "lo-profile"), ignore_errors=True)
    return pdf


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('--template', default=TEMPLATE)
    ap.add_argument('--out', default=OUT)
    ap.add_argument('--skip-figures', action='store_true')
    ap.add_argument('--preview', metavar='DIR', help='write a PDF and page PNGs here')
    args = ap.parse_args()

    if os.path.abspath(args.out) == os.path.abspath(args.template):
        raise SystemExit("refusing to overwrite the template")
    os.makedirs(ASSETS, exist_ok=True)
    if not args.skip_figures:
        import figures
        figures.build_all(ASSETS)
    media = build_docx(args.template, args.out)
    write_markdown()
    print("wrote %s (%d images), %s" % (os.path.relpath(args.out, ROOT), len(media.items),
                                        os.path.relpath(MD_OUT, ROOT)))
    if args.preview:
        pdf = preview(args.out, args.preview)
        print("preview %s" % pdf)


if __name__ == '__main__':
    main()
