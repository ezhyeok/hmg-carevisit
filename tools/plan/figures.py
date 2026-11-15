"""Flow figure using the reused SVG primitives; rsvg-convert renders a 2x PNG.
Fonts: Pretendard or Noto Sans CJK KR. No simulation or measured-count dependency.
"""
import os
import subprocess
import sys

sys.dont_write_bytecode = True   # keep tools/plan free of __pycache__
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # icons.py, ooxml.py under `python3 -I`
from xml.sax.saxutils import escape


FONT = "Pretendard, 'Noto Sans CJK KR', NanumGothic, sans-serif"
INK, INK2, INK3, MUTED = "#131417", "#44464E", "#686A72", "#A4A8AE"
LINE, LINE2, SURF, SURF2, WHITE = "#E0E1E6", "#C7C9CE", "#F7F8FA", "#EDEEF2", "#FFFFFF"
ACCENT, ACCENT_D, TINT, TINT2 = "#0064FF", "#0050CC", "#EAF2FF", "#CFE0FF"
ORANGE, RED, RED_TINT, ORANGE_TINT = "#FF8A00", "#FE3D16", "#FFE9E3", "#FFF1DE"
FILLED = "#44464E"            # Pleos button_filled_enabled (light)
BASIC = "#EDEEF0"             # Pleos button_basic_enabled (#131417 5% on white)


# ------------------------------------------------------------------ primitives

def tw(s, size, bold=False):
    """Approximate rendered width of s in px for Pretendard."""
    w = 0.0
    for ch in s:
        o = ord(ch)
        if 0xAC00 <= o <= 0xD7A3 or 0x3130 <= o <= 0x318F:
            w += 0.92
        elif ch in '“”‘’':
            w += 0.32
        elif ch in '·':
            w += 0.30
        elif ch == ' ':
            w += 0.26
        elif ch.isdigit():
            w += 0.58
        elif ch.isupper():
            w += 0.64
        elif ch.isalpha():
            w += 0.53
        elif o > 0x2000:
            w += 0.9
        else:
            w += 0.34
    return w * size * (1.04 if bold else 1.0)


def num_w(value, size):
    """Width of a heavy-weight number (Pretendard ExtraBold digits are wide)."""
    return sum(0.66 * size if ch.isdigit() else tw(ch, size, True) for ch in value)


def wrap(s, size, maxw, bold=False):
    lines, cur = [], ""
    for word in s.split(' '):
        cand = (cur + ' ' + word).strip()
        if cur and tw(cand, size, bold) > maxw:
            lines.append(cur)
            cur = word
        else:
            cur = cand
    if cur:
        lines.append(cur)
    return lines


def T(x, y, s, size=14, weight=400, fill=INK, anchor="start", extra=""):
    return ('<text x="%.1f" y="%.1f" font-size="%.1f" font-weight="%d" fill="%s" text-anchor="%s"%s>%s</text>'
            % (x, y, size, weight, fill, anchor, (' ' + extra) if extra else '', escape(s)))


def ML(x, y, lines, size=13, lh=None, weight=400, fill=INK, anchor="start"):
    lh = lh or size * 1.38
    return ''.join(T(x, y + i * lh, l, size, weight, fill, anchor) for i, l in enumerate(lines))


def R(x, y, w, h, rx=0, fill=WHITE, stroke=None, sw=1, dash=None, opacity=None):
    a = '<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="%.1f" fill="%s"' % (x, y, w, h, rx, fill)
    if stroke:
        a += ' stroke="%s" stroke-width="%.1f"' % (stroke, sw)
    if dash:
        a += ' stroke-dasharray="%s"' % dash
    if opacity is not None:
        a += ' opacity="%.2f"' % opacity
    return a + '/>'


def L(x1, y1, x2, y2, stroke=LINE2, sw=1, dash=None, marker=None, cap="butt"):
    a = ('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="%s" stroke-width="%.1f" stroke-linecap="%s"'
         % (x1, y1, x2, y2, stroke, sw, cap))
    if dash:
        a += ' stroke-dasharray="%s"' % dash
    if marker:
        a += ' marker-end="url(#%s)"' % marker
    return a + '/>'


def C(cx, cy, r, fill=WHITE, stroke=None, sw=1):
    a = '<circle cx="%.1f" cy="%.1f" r="%.1f" fill="%s"' % (cx, cy, r, fill)
    if stroke:
        a += ' stroke="%s" stroke-width="%.1f"' % (stroke, sw)
    return a + '/>'


def PATH(d, fill="none", stroke=INK, sw=1, dash=None, marker=None, cap="round", join="round"):
    a = '<path d="%s" fill="%s" stroke="%s" stroke-width="%.1f" stroke-linecap="%s" stroke-linejoin="%s"' % (
        d, fill, stroke, sw, cap, join)
    if dash:
        a += ' stroke-dasharray="%s"' % dash
    if marker:
        a += ' marker-end="url(#%s)"' % marker
    return a + '/>'


DEFS = ('<defs>'
        '<marker id="arrA" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" markerHeight="7" orient="auto">'
        '<path d="M0,0 L10,5 L0,10 z" fill="%s"/></marker>'
        '<marker id="arrG" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" markerHeight="7" orient="auto">'
        '<path d="M0,0 L10,5 L0,10 z" fill="%s"/></marker>'
        '<marker id="arrK" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" markerHeight="7" orient="auto">'
        '<path d="M0,0 L10,5 L0,10 z" fill="%s"/></marker>'
        '</defs>') % (ACCENT, INK3, INK2)


def svg(w, h, body):
    return ('<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="%d" '
            'height="%d" viewBox="0 0 %d %d" font-family="%s">%s<rect width="%d" height="%d" fill="#FFFFFF"/>%s</svg>'
            % (w, h, w, h, FONT, DEFS, w, h, ''.join(body)))


def icon(name, x, y, size, fill=INK):
    """Material Symbols icon (tools/plan/icons.py) with its top-left corner at (x, y)."""
    import icons
    s = size / 960.0
    return ('<path d="%s" fill="%s" transform="translate(%.2f %.2f) scale(%.5f) translate(0 960)"/>'
            % (icons.PATHS[name], fill, x, y, s))


def fig_flow():
    from content import APP_NAME
    b = [T(30, 42, APP_NAME + " · 병원에 가는 날의 대화를 잇다", 25, 700),
         T(30, 72, "말씀 그대로 기록하고, 확인한 내용만 가족에게", 16, fill=INK3)]
    stages = [
        ("가는 길", "목적지 + 음성", ["불편한 곳 · 언제부터", "약 · 꼭 물어볼 것"], "진료 메모"),
        ("진료", "도착 + P + 도어", ["정문 하차 · 주차 후 합류", "물어볼 것 확인"], "빠뜨리지 않고"),
        ("오는 길", "병원 체류 + D", ["의사 말씀 · 바뀐 약", "다음 예약 · 확인할 것"], "진료 브리핑"),
        ("가족", "공유 동의 + 확인", ["승인한 브리핑 전달", "다음 정기 진료에 이어 쓰기"], "같이 기억하기"),
    ]
    for i, (title, signal, lines, result) in enumerate(stages):
        x = 30 + i * 244
        b += [R(x, 106, 220, 210, 16, SURF, LINE), T(x+18, 140, title, 24, 700),
              T(x+18, 169, signal, 14, 600, ACCENT_D), ML(x+18, 207, lines, 15, 25, fill=INK2),
              R(x+16, 264, 188, 34, 8, TINT), T(x+110, 287, result, 16, 600, ACCENT_D, "middle")]
        if i < 3:
            b.append(L(x+224, 210, x+240, 210, ACCENT, 2, marker="arrA"))
    b += [T(30, 355, "기기 안 보관 · 의료 판단 없음 · 주행 중 긴 글 숨김 · 가족에게 원문·위치 전송 없음", 16, fill=INK2),
          T(30, 383, "기능 흐름도이며 실제 에뮬레이터 화면이 아닙니다.", 13, fill=INK3)]
    return svg(1020, 410, b)


def render(name, fn, outdir):
    svg_path = os.path.join(outdir, name + ".svg")
    png_path = os.path.join(outdir, name + ".png")
    with open(svg_path, "w", encoding="utf-8") as f:
        f.write(fn())
    subprocess.run(["rsvg-convert", "--zoom", "2", "--format", "png", "--output", png_path, svg_path], check=True)
    return png_path


def build_all(outdir):
    os.makedirs(outdir, exist_ok=True)
    return [render("fig-flow", fig_flow, outdir)]


if __name__ == "__main__":
    assets = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../docs/plan/assets"))
    for path in build_all(assets):
        print(path)
