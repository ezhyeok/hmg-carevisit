"""Small WordprocessingML builders for the planning document (standard library only).

Every function returns an XML string fragment that is valid inside <w:body> or a table
cell of the official template. Units: font sizes in points, spacing in twentieths of a
point (dxa), image extents in EMU. Colors are hex strings without '#'.
"""
import re
from xml.sax.saxutils import escape

FONT = "맑은 고딕"          # exists on every Korean Windows; the template default
MONO = "Consolas"

# Visual system: one accent (Pleos informative_positive) + Pleos grayscale
ACCENT = "0064FF"
ACCENT_TINT = "EEF4FF"
INK = "131417"
INK2 = "44464E"
INK3 = "686A72"
LINE = "D5D7DC"
LINE_LIGHT = "E6E7EB"
SURF = "F7F8FA"
SURF2 = "EDEEF2"
WARN_TINT = "FFF4E5"

EMU_PER_CM = 360000
BOX_CONTENT_DXA = 9646      # box width 9866 minus 110 + 110 cell margins


def _hp(pt):
    """points -> half-points (w:sz)"""
    return str(int(round(pt * 2)))


def rpr(size=None, bold=False, italic=False, color=None, font=None, underline=False,
        highlight=None, spacing=None):
    parts = []
    f = font or FONT
    parts.append('<w:rFonts w:ascii="%s" w:eastAsia="%s" w:hAnsi="%s" w:cs="%s"/>' % (f, FONT if f == MONO else f, f, f))
    if bold:
        parts.append('<w:b/><w:bCs/>')
    if italic:
        parts.append('<w:i/><w:iCs/>')
    if color:
        parts.append('<w:color w:val="%s"/>' % color)
    if spacing is not None:
        parts.append('<w:spacing w:val="%d"/>' % spacing)
    if size:
        parts.append('<w:sz w:val="%s"/><w:szCs w:val="%s"/>' % (_hp(size), _hp(size)))
    if highlight:
        parts.append('<w:highlight w:val="%s"/>' % highlight)
    if underline:
        parts.append('<w:u w:val="single"/>')
    parts.append('<w:lang w:val="en-US" w:eastAsia="ko-KR"/>')
    return '<w:rPr>%s</w:rPr>' % ''.join(parts)


def run(text, **kw):
    return '<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r>' % (rpr(**kw), escape(text))


def br_run():
    return '<w:r><w:br/></w:r>'


_INLINE = re.compile(r'(\*\*.+?\*\*|`[^`]+`|\{\{.+?\}\}|\[\[.+?\]\])')


def inline(text, size=9.5, color=INK, bold=False):
    """Markup: **bold**, `code`, {{accent bold}}, [[placeholder: highlighted]]"""
    out = []
    for tok in _INLINE.split(text):
        if not tok:
            continue
        if tok.startswith('**') and tok.endswith('**'):
            out.append(run(tok[2:-2], size=size, color=color, bold=True))
        elif tok.startswith('`') and tok.endswith('`'):
            out.append(run(tok[1:-1], size=max(size - 1.0, 7), color=INK2, font=MONO))
        elif tok.startswith('{{') and tok.endswith('}}'):
            out.append(run(tok[2:-2], size=size, color=ACCENT, bold=True))
        elif tok.startswith('[[') and tok.endswith(']]'):
            out.append(run(tok[2:-2], size=size, color=INK, bold=True, highlight="yellow"))
        else:
            out.append(run(tok, size=size, color=color, bold=bold))
    return ''.join(out)


def ppr(before=0, after=80, line=264, jc=None, ind_left=None, ind_hanging=None, ind_first=None,
        keep_next=False, keep_lines=False, num=None, border_left=None, border_bottom=None,
        shading=None, page_break_before=False, widow=True):
    parts = []
    if keep_next:
        parts.append('<w:keepNext/>')
    if keep_lines:
        parts.append('<w:keepLines/>')
    if page_break_before:
        parts.append('<w:pageBreakBefore/>')
    if not widow:
        parts.append('<w:widowControl w:val="0"/>')
    if num:
        parts.append('<w:numPr><w:ilvl w:val="%d"/><w:numId w:val="%d"/></w:numPr>' % (num[1], num[0]))
    if border_left or border_bottom:
        b = []
        if border_left:
            color, sz, space = border_left
            b.append('<w:left w:val="single" w:sz="%d" w:space="%d" w:color="%s"/>' % (sz, space, color))
        if border_bottom:
            color, sz, space = border_bottom
            b.append('<w:bottom w:val="single" w:sz="%d" w:space="%d" w:color="%s"/>' % (sz, space, color))
        parts.append('<w:pBdr>%s</w:pBdr>' % ''.join(b))
    if shading:
        parts.append('<w:shd w:val="clear" w:color="auto" w:fill="%s"/>' % shading)
    parts.append('<w:wordWrap w:val="0"/>')       # Korean: break lines at spaces in Word
    parts.append('<w:autoSpaceDE w:val="0"/><w:autoSpaceDN w:val="0"/>')   # no extra gap between Hangul and Latin/digits
    parts.append('<w:spacing w:before="%d" w:after="%d" w:line="%d" w:lineRule="auto"/>' % (before, after, line))
    if ind_left is not None or ind_hanging is not None or ind_first is not None:
        a = []
        if ind_left is not None:
            a.append('w:left="%d"' % ind_left)
        if ind_hanging is not None:
            a.append('w:hanging="%d"' % ind_hanging)
        if ind_first is not None:
            a.append('w:firstLine="%d"' % ind_first)
        parts.append('<w:ind %s/>' % ' '.join(a))
    if jc:
        parts.append('<w:jc w:val="%s"/>' % jc)
    return '<w:pPr>%s</w:pPr>' % ''.join(parts)


def para(runs_xml, **kw):
    return '<w:p>%s%s</w:p>' % (ppr(**kw), runs_xml)


def empty_para(size=2, after=0):
    """A near-invisible paragraph (cells must end with a paragraph)."""
    return ('<w:p>%s<w:r>%s</w:r></w:p>' %
            (ppr(before=0, after=after, line=240), rpr(size=size)))


# ------------------------------------------------------------------ tables

def _borders(tag, spec):
    """spec: dict side -> (val, sz, color) or None"""
    out = []
    for side in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
        if side not in spec:
            continue
        v = spec[side]
        if v is None:
            out.append('<w:%s w:val="nil"/>' % side)
        else:
            val, sz, color = v
            out.append('<w:%s w:val="%s" w:sz="%d" w:space="0" w:color="%s"/>' % (side, val, sz, color))
    return '<w:%s>%s</w:%s>' % (tag, ''.join(out), tag)


def cell(content_xml, width, fill=None, borders=None, valign="top", margins=None, gridspan=1):
    parts = ['<w:tcW w:w="%d" w:type="dxa"/>' % width]
    if gridspan > 1:
        parts.append('<w:gridSpan w:val="%d"/>' % gridspan)
    if borders:
        parts.append(_borders('tcBorders', borders))
    if fill:
        parts.append('<w:shd w:val="clear" w:color="auto" w:fill="%s"/>' % fill)
    if margins:
        t, l, b, r = margins
        parts.append('<w:tcMar><w:top w:w="%d" w:type="dxa"/><w:left w:w="%d" w:type="dxa"/>'
                     '<w:bottom w:w="%d" w:type="dxa"/><w:right w:w="%d" w:type="dxa"/></w:tcMar>' % (t, l, b, r))
    parts.append('<w:vAlign w:val="%s"/>' % valign)
    if not content_xml.rstrip().endswith('</w:p>'):
        content_xml += empty_para()
    return '<w:tc><w:tcPr>%s</w:tcPr>%s</w:tc>' % (''.join(parts), content_xml)


def row(cells_xml, header=False, cant_split=True, height=None):
    tr = []
    if cant_split:
        tr.append('<w:cantSplit/>')
    if height:
        tr.append('<w:trHeight w:val="%d"/>' % height)
    if header:
        tr.append('<w:tblHeader/>')
    trpr = '<w:trPr>%s</w:trPr>' % ''.join(tr) if tr else ''
    return '<w:tr>%s%s</w:tr>' % (trpr, ''.join(cells_xml))


def table(rows_xml, widths, borders=None, cell_margins=(50, 90, 50, 90), indent=0, jc=None):
    total = sum(widths)
    tblpr = ['<w:tblW w:w="%d" w:type="dxa"/>' % total]
    if jc:
        tblpr.append('<w:jc w:val="%s"/>' % jc)
    tblpr.append('<w:tblInd w:w="%d" w:type="dxa"/>' % indent)
    if borders is not None:
        tblpr.append(_borders('tblBorders', borders))
    tblpr.append('<w:tblLayout w:type="fixed"/>')
    t, l, b, r = cell_margins
    tblpr.append('<w:tblCellMar><w:top w:w="%d" w:type="dxa"/><w:left w:w="%d" w:type="dxa"/>'
                 '<w:bottom w:w="%d" w:type="dxa"/><w:right w:w="%d" w:type="dxa"/></w:tblCellMar>' % (t, l, b, r))
    tblpr.append('<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="1" '
                 'w:lastColumn="0" w:noHBand="0" w:noVBand="1"/>')
    grid = ''.join('<w:gridCol w:w="%d"/>' % w for w in widths)
    return '<w:tbl><w:tblPr>%s</w:tblPr><w:tblGrid>%s</w:tblGrid>%s</w:tbl>' % (''.join(tblpr), grid, ''.join(rows_xml))


def scale_widths(weights, total=BOX_CONTENT_DXA):
    s = float(sum(weights))
    ws = [int(total * w / s) for w in weights]
    ws[-1] += total - sum(ws)
    return ws


# ------------------------------------------------------------------ images

_A = 'http://schemas.openxmlformats.org/drawingml/2006/main'
_PIC = 'http://schemas.openxmlformats.org/drawingml/2006/picture'


def image_run(rid, docpr_id, name, cx, cy, descr="", outline=None):
    ln = ('<a:ln w="6350"><a:solidFill><a:srgbClr val="%s"/></a:solidFill></a:ln>' % outline) if outline else ''
    return (
        '<w:r><w:rPr><w:noProof/></w:rPr><w:drawing>'
        '<wp:inline distT="0" distB="0" distL="0" distR="0">'
        '<wp:extent cx="%(cx)d" cy="%(cy)d"/><wp:effectExtent l="0" t="0" r="0" b="0"/>'
        '<wp:docPr id="%(id)d" name="%(name)s" descr="%(descr)s"/>'
        '<wp:cNvGraphicFramePr><a:graphicFrameLocks xmlns:a="%(a)s" noChangeAspect="1"/></wp:cNvGraphicFramePr>'
        '<a:graphic xmlns:a="%(a)s"><a:graphicData uri="%(pic)s">'
        '<pic:pic xmlns:pic="%(pic)s"><pic:nvPicPr><pic:cNvPr id="%(id)d" name="%(name)s" descr="%(descr)s"/>'
        '<pic:cNvPicPr><a:picLocks noChangeAspect="1" noChangeArrowheads="1"/></pic:cNvPicPr></pic:nvPicPr>'
        '<pic:blipFill><a:blip r:embed="%(rid)s"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
        '<pic:spPr bwMode="auto"><a:xfrm><a:off x="0" y="0"/><a:ext cx="%(cx)d" cy="%(cy)d"/></a:xfrm>'
        '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/>%(ln)s</pic:spPr></pic:pic>'
        '</a:graphicData></a:graphic></wp:inline></w:drawing></w:r>'
    ) % dict(cx=cx, cy=cy, id=docpr_id, name=escape(name, {'"': '&quot;'}),
             descr=escape(descr, {'"': '&quot;'}), a=_A, pic=_PIC, rid=rid, ln=ln)


def image_size(path):
    """(width, height) in pixels for PNG or JPEG, standard library only."""
    with open(path, 'rb') as f:
        head = f.read(32)
        if head[:8] == b'\x89PNG\r\n\x1a\n':
            return int.from_bytes(head[16:20], 'big'), int.from_bytes(head[20:24], 'big')
        if head[:2] == b'\xff\xd8':
            f.seek(2)
            while True:
                marker = f.read(2)
                if len(marker) < 2 or marker[0] != 0xFF:
                    break
                code = marker[1]
                length = int.from_bytes(f.read(2), 'big')
                if code in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
                    data = f.read(5)
                    return int.from_bytes(data[3:5], 'big'), int.from_bytes(data[1:3], 'big')
                f.seek(length - 2, 1)
    raise ValueError("unsupported image: %s" % path)
