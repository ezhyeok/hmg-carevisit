"""Render content blocks to WordprocessingML that goes inside a template box (table cell)."""
import os

import ooxml as X
from blocks import H, P, Bullets, Table, Callout, KeyNumbers, Figure, Storyboard, Note, Spacer, Annotated

BULLET_NUM_ID = 90           # added to numbering.xml by the build script
CIRCLED = "①②③④⑤⑥⑦⑧⑨⑩"


class Media:
    """Collects images to embed: returns relationship ids and keeps the file list."""

    def __init__(self):
        self.items = []          # (rid, part_name, src_path)
        self._next_docpr = 5001

    def add(self, src_path):
        for rid, part, src in self.items:
            if src == src_path:
                return rid, part
        n = len(self.items) + 1
        rid = "rIdPlanImg%d" % n
        ext = os.path.splitext(src_path)[1].lower().lstrip('.')
        ext = 'jpeg' if ext == 'jpg' else ext
        part = "media/plan_%02d.%s" % (n, ext)
        self.items.append((rid, part, src_path))
        return rid, part

    def docpr(self):
        self._next_docpr += 1
        return self._next_docpr


def _spacer(pt=4):
    return ('<w:p>%s<w:r>%s</w:r></w:p>' %
            (X.ppr(before=0, after=0, line=int(pt * 20), widow=True).replace('w:lineRule="auto"', 'w:lineRule="exact"'),
             X.rpr(size=2)))


def render_heading(b):
    return X.para(X.run(b.text, size=11, bold=True, color=X.INK),
                  before=220, after=90, line=252, keep_next=True, keep_lines=True,
                  border_left=(X.ACCENT, 24, 6), ind_left=150, page_break_before=b.page_break)


def render_p(b):
    color = b.color or X.INK
    return X.para(X.inline(b.text, size=b.size, color=color),
                  before=b.before, after=b.after, line=264, jc=b.align)


def render_bullets(b):
    out = []
    for item in b.items:
        out.append(X.para(X.inline(item, size=b.size), before=0, after=50, line=259,
                          num=(BULLET_NUM_ID, 0)))
    return ''.join(out)


def _cell_paras(text, size, bold=False, color=X.INK, align=None, after=0):
    """A cell's text: lines separated by '\n' become separate paragraphs."""
    out = []
    for line in str(text).split('\n'):
        out.append(X.para(X.inline(line, size=size, color=color, bold=bold),
                          before=0, after=after, line=245, jc=align))
    return ''.join(out)


def render_table(b, width=X.BOX_CONTENT_DXA):
    widths = X.scale_widths(b.widths, width)
    align = b.align or ['left'] * len(widths)
    rows = []
    hdr = []
    for i, h in enumerate(b.header):
        a = 'center' if align[i] == 'center' else ('right' if align[i] == 'right' else None)
        hdr.append(X.cell(_cell_paras(h, b.size, bold=True, color=X.INK, align=a), widths[i],
                          fill=X.SURF2, valign="center"))
    rows.append(X.row(hdr, header=True))
    for r_i, r in enumerate(b.rows):
        cells = []
        fill = X.ACCENT_TINT if r_i in b.highlight_rows else None
        for i, txt in enumerate(r):
            a = 'center' if align[i] == 'center' else ('right' if align[i] == 'right' else None)
            bold = (i == 0 and b.first_col_bold) or (r_i in b.highlight_rows and i > 0 and align[i] == 'right')
            cells.append(X.cell(_cell_paras(txt, b.size, bold=bold, align=a), widths[i], fill=fill))
        # rows may break across pages: LibreOffice mislays unsplittable rows of a table nested in the form's box
        rows.append(X.row(cells, cant_split=False))
    borders = {'top': ('single', 8, X.INK2), 'bottom': ('single', 8, X.INK2), 'left': None, 'right': None,
               'insideH': ('single', 4, X.LINE), 'insideV': None}
    return X.table(rows, widths, borders=borders) + _spacer(5)


def render_callout(b, width=X.BOX_CONTENT_DXA):
    if b.tone == 'accent':
        fill, bar = X.ACCENT_TINT, X.ACCENT
    elif b.tone == 'warn':
        fill, bar = X.WARN_TINT, "FF8A00"
    else:
        fill, bar = X.SURF, X.INK3
    inner = []
    if b.title:
        inner.append(X.para(X.run(b.title, size=b.size + 0.5, bold=True, color=X.INK),
                            before=0, after=60, line=252, keep_next=True))
    for i, line in enumerate(b.lines):
        last = i == len(b.lines) - 1
        if b.bullets:
            inner.append(X.para(X.inline(line, size=b.size), before=0, after=0 if last else 50, line=259,
                                num=(BULLET_NUM_ID, 0)))
        else:
            inner.append(X.para(X.inline(line, size=b.size), before=0, after=0 if last else 60, line=264))
    c = X.cell(''.join(inner), width, fill=fill,
               borders={'top': None, 'bottom': None, 'right': None, 'left': ('single', 24, bar)},
               margins=(110, 180, 110, 160))
    t = X.table([X.row([c], cant_split=False)], [width], borders={'top': None, 'left': None, 'bottom': None,
                                                                   'right': None, 'insideH': None, 'insideV': None},
                cell_margins=(0, 0, 0, 0))
    return t + _spacer(6)


def render_keynumbers(b, width=X.BOX_CONTENT_DXA):
    n = len(b.items)
    gap = 120
    tile = (width - gap * (n - 1)) // n
    widths = []
    cells = []
    for i, (value, label, source) in enumerate(b.items):
        content = (X.para(X.run(value, size=17, bold=True, color=X.ACCENT), before=0, after=30, line=240) +
                   X.para(X.inline(label, size=8, color=X.INK), before=0, after=40, line=240) +
                   X.para(X.run(source, size=7, color=X.INK3), before=0, after=0, line=240))
        cells.append(X.cell(content, tile, fill=X.ACCENT_TINT, margins=(110, 130, 110, 110)))
        widths.append(tile)
        if i < n - 1:
            cells.append(X.cell(X.empty_para(), gap))
            widths.append(gap)
    widths[-1] += width - sum(widths)
    t = X.table([X.row(cells, cant_split=False)], widths, borders={'top': None, 'left': None, 'bottom': None, 'right': None,
                                                 'insideH': None, 'insideV': None}, cell_margins=(0, 0, 0, 0))
    return t + _spacer(4)


def _image_para(media, path, width_cm, alt, keep_next=True, jc='center', max_h_cm=None, outline=None):
    w_px, h_px = X.image_size(path)
    if max_h_cm:
        width_cm = min(width_cm, max_h_cm * w_px / float(h_px))
    cx = int(width_cm * X.EMU_PER_CM)
    cy = int(cx * h_px / float(w_px))
    rid, part = media.add(path)
    did = media.docpr()
    return X.para(X.image_run(rid, did, os.path.basename(part), cx, cy, alt, outline=outline),
                  before=60, after=40, line=240, jc=jc, keep_next=keep_next)


def _caption(text):
    return X.para(X.inline(text, size=8, color=X.INK3), before=0, after=120, line=245, jc='center')


def render_figure(b, media, resolve):
    target = resolve(b.key)
    if isinstance(target, list):           # screenshot grid
        return render_screens(target, b, media)
    return _image_para(media, target, b.width_cm, b.alt or b.caption) + _caption(b.caption)


def render_screens(shots, b, media, width=X.BOX_CONTENT_DXA):
    """Screenshots in a grid of b.columns portrait images per row (landscape shots take a full row)."""
    rows = []
    pending = []
    ncol = max(1, b.columns)
    colw = [width // ncol] * ncol
    colw[-1] += width - sum(colw)
    half = colw[0]

    def flush():
        if not pending:
            return
        cells = []
        for i, (path, label) in enumerate(pending):
            w_cm = (colw[i] - 160) / 567.0
            cells.append(X.cell(_image_para(media, path, w_cm, label, keep_next=True, max_h_cm=b.screen_max_h_cm,
                                            outline="C7C9CE") +
                                X.para(X.inline(label, size=8, color=X.INK2), before=0, after=60, line=240,
                                       jc='center'), colw[i]))
        for i in range(len(pending), ncol):
            cells.append(X.cell(X.empty_para(), colw[i]))
        rows.append(X.row(cells))
        pending.clear()

    for path, label in shots:
        w, h = X.image_size(path)
        if w > h * 1.2:
            flush()
            w_cm = (width - 200) / 567.0
            rows.append(X.row([X.cell(_image_para(media, path, w_cm, label, keep_next=True, max_h_cm=10.5,
                                                  outline="C7C9CE") +
                                      X.para(X.inline(label, size=8, color=X.INK2), before=0, after=60,
                                             line=240, jc='center'), width, gridspan=ncol)]))
        else:
            pending.append((path, label))
            if len(pending) == ncol:
                flush()
    flush()
    t = X.table(rows, colw, borders={'top': None, 'left': None, 'bottom': None, 'right': None,
                                     'insideH': None, 'insideV': None},
                cell_margins=(40, 80, 40, 80))
    return t + _caption(b.caption_screens or b.caption)


def render_annotated(b, media, resolve, width=X.BOX_CONTENT_DXA):
    """Screen image (left) + numbered notes (right) in a borderless two-column table."""
    path = resolve(b.key)
    img_w = int(b.image_width_cm * 567) + 240
    widths = [img_w, width - img_w]
    notes = []
    for i, (title, desc) in enumerate(b.items):
        mark = CIRCLED[i] if i < len(CIRCLED) else str(i + 1)
        notes.append(X.para(X.run(mark + " ", size=10, bold=True, color=X.ACCENT) +
                            X.run(title, size=9.5, bold=True, color=X.INK),
                            before=0 if i == 0 else 110, after=20, line=245, keep_next=True))
        notes.append(X.para(X.inline(desc, size=9, color=X.INK2), before=0, after=0, line=250, ind_left=300))
    left = _image_para(media, path, b.image_width_cm, b.alt or b.caption, keep_next=False, outline="C7C9CE")
    t = X.table([X.row([X.cell(left, widths[0], valign="center"),
                        X.cell(''.join(notes), widths[1], valign="center", margins=(80, 200, 80, 60))])],
                widths, borders={'top': None, 'left': None, 'bottom': None, 'right': None,
                                 'insideH': None, 'insideV': None}, cell_margins=(40, 60, 40, 60))
    return t + _caption(b.caption)


def render_storyboard(b, width=X.BOX_CONTENT_DXA):
    widths = X.scale_widths([17, 47, 36], width)
    rows = []
    title = (X.run(b.title, size=10, bold=True, color=X.ACCENT) +
             X.run("   " + b.subtitle, size=8.5, color=X.INK3))
    rows.append(X.row([X.cell(X.para(title, before=0, after=0, line=252, keep_next=True), width,
                              fill=X.ACCENT_TINT, gridspan=3, valign="center", margins=(80, 120, 80, 120))],
                      header=True))
    hdr = ["단계", "차·앱이 하는 일", "사용자가 듣고 보는 것"]
    rows.append(X.row([X.cell(_cell_paras(h, 8, bold=True, color=X.INK2), widths[i], fill=X.SURF2,
                              valign="center") for i, h in enumerate(hdr)], header=True))
    for i, (step, action, user) in enumerate(b.steps):
        mark = CIRCLED[i] if i < len(CIRCLED) else str(i + 1)
        step_xml = X.para(X.run(mark + " ", size=9, bold=True, color=X.ACCENT) + X.inline(step, size=8.5, bold=True),
                          before=0, after=0, line=245)
        speech = user.startswith('“')
        user_xml = _cell_paras(user, 8.5, color=X.INK if not speech else "0050CC")
        rows.append(X.row([X.cell(step_xml, widths[0]), X.cell(_cell_paras(action, 8.5), widths[1]),
                           X.cell(user_xml, widths[2])], cant_split=False))
    borders = {'top': ('single', 6, X.LINE), 'bottom': ('single', 6, X.LINE), 'left': ('single', 6, X.LINE),
               'right': ('single', 6, X.LINE), 'insideH': ('single', 4, X.LINE_LIGHT), 'insideV': None}
    return X.table(rows, widths, borders=borders) + _spacer(8)


def render_note(b):
    return X.para(X.inline(b.text, size=7.5, color=X.INK3), before=20, after=80, line=245)


def render_blocks(blocks, media, resolve):
    out = []
    for b in blocks:
        if isinstance(b, H):
            out.append(render_heading(b))
        elif isinstance(b, P):
            out.append(render_p(b))
        elif isinstance(b, Bullets):
            out.append(render_bullets(b))
        elif isinstance(b, Table):
            out.append(render_table(b))
        elif isinstance(b, Callout):
            out.append(render_callout(b))
        elif isinstance(b, KeyNumbers):
            out.append(render_keynumbers(b))
        elif isinstance(b, Figure):
            out.append(render_figure(b, media, resolve))
        elif isinstance(b, Storyboard):
            out.append(render_storyboard(b))
        elif isinstance(b, Annotated):
            out.append(render_annotated(b, media, resolve))
        elif isinstance(b, Note):
            out.append(render_note(b))
        elif isinstance(b, Spacer):
            out.append(_spacer(b.pt))
        else:
            raise TypeError(b)
    xml = ''.join(out)
    if not xml.endswith('</w:p>'):
        xml += X.empty_para()
    return xml
