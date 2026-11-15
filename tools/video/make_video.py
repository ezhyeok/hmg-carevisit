"""Assemble the demo video from emulator screen recordings.

usage (repo root): <venv>/bin/python tools/video/make_video.py tools/video/spec.json docs/demo/shimpyo-demo.mp4
       venv: python3 -m venv <venv> && <venv>/bin/pip install av pillow numpy  (PyAV carries FFmpeg and libx264)
       clips: tools/video/scene_holiday.sh, tools/video/scene_stop.sh record build/video/clips/*.mp4 on the emulator

spec.json: {"segments": [
  {"type": "title", "lines": ["...", "..."], "dur": 4},
  {"type": "clip", "file": "clips/a.mp4", "start": 0, "end": 60, "speed": 4,
   "caption": "...", "sub": "...", "badge": "4배속"}
]}
Screen recordings (adb screenrecord) are variable frame rate: frames only when the screen changes. Each output
frame takes the last source frame at or before its source time, so speeding up or holding is exact in time.
"""
import json
import sys

import av
import numpy as np
from PIL import Image, ImageDraw, ImageFont

W, H, FPS = 1920, 1080, 30
import os
FONT_DIR = os.environ.get("KO_FONT_DIR", os.path.expanduser("~/.local/share/fonts/omnidoc-demo-ko"))
BOLD = os.path.join(FONT_DIR, "NotoSansCJKkr-Bold.otf")
REG = os.path.join(FONT_DIR, "NotoSansCJKkr-Regular.otf")


def font(path, size):
    return ImageFont.truetype(path, size)


F_CAP = font(BOLD, 44)
F_SUB = font(REG, 30)
F_BADGE = font(BOLD, 28)
F_TITLE = font(BOLD, 72)
F_TITLE2 = font(REG, 40)


def wrap(draw, text, f, max_w):
    lines, cur = [], ""
    for word in text.split(" "):
        test = (cur + " " + word).strip()
        if draw.textlength(test, font=f) <= max_w or not cur:
            cur = test
        else:
            lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines


def overlay(img, caption=None, sub=None, badge=None):
    if not (caption or sub or badge):
        return img
    base = img.convert("RGBA")
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    if caption or sub:
        cap_lines = wrap(d, caption or "", F_CAP, W - 160) if caption else []
        sub_lines = wrap(d, sub or "", F_SUB, W - 160) if sub else []
        h = 40 + len(cap_lines) * 60 + len(sub_lines) * 44 + 30
        d.rectangle([0, H - h, W, H], fill=(10, 14, 22, 205))
        y = H - h + 34
        for ln in cap_lines:
            d.text((80, y), ln, font=F_CAP, fill=(255, 255, 255, 255))
            y += 60
        for ln in sub_lines:
            d.text((80, y), ln, font=F_SUB, fill=(200, 210, 225, 255))
            y += 44
    if badge:
        tw = d.textlength(badge, font=F_BADGE)
        d.rounded_rectangle([W - tw - 80, 30, W - 30, 86], radius=14, fill=(255, 80, 40, 230))
        d.text((W - tw - 55, 38), badge, font=F_BADGE, fill=(255, 255, 255, 255))
    return Image.alpha_composite(base, layer).convert("RGB")


def title_frame(lines):
    img = Image.new("RGB", (W, H), (247, 248, 250))
    d = ImageDraw.Draw(img)
    total = 0
    sizes = []
    for i, ln in enumerate(lines):
        f = F_TITLE if i == 0 else F_TITLE2
        sizes.append((f, 110 if i == 0 else 64))
        total += sizes[-1][1]
    y = (H - total) // 2
    for ln, (f, lh) in zip(lines, sizes):
        tw = d.textlength(ln, font=f)
        d.text(((W - tw) / 2, y), ln, font=f, fill=(20, 24, 32) if f is F_TITLE else (70, 78, 92))
        y += lh
    return img


def frames_of(path):
    """(time_s, PIL image) for every decoded frame, in order"""
    with av.open(path) as c:
        s = c.streams.video[0]
        for fr in c.decode(s):
            if fr.pts is None:
                continue
            yield float(fr.pts * s.time_base), fr.to_image()


def clip_frames(seg):
    start, end, speed = seg["start"], seg["end"], seg.get("speed", 1.0)
    n = int(round((end - start) / speed * FPS))
    src = frames_of(seg["file"])
    last = None
    nxt = next(src, None)
    for i in range(n):
        t = start + i * speed / FPS
        while nxt is not None and nxt[0] <= t:
            last = nxt[1]
            nxt = next(src, None)
        if last is None:
            last = nxt[1] if nxt else Image.new("RGB", (W, H))
        img = last if last.size == (W, H) else last.resize((W, H), Image.LANCZOS)
        yield overlay(img, seg.get("caption"), seg.get("sub"), seg.get("badge"))


def main(spec_path, out_path):
    spec = json.load(open(spec_path, encoding="utf-8"))
    out = av.open(out_path, "w")
    st = out.add_stream("libx264", rate=FPS)
    st.width, st.height, st.pix_fmt = W, H, "yuv420p"
    st.options = {"crf": "22", "preset": "medium"}
    count = 0
    for seg in spec["segments"]:
        if seg["type"] == "title":
            img = title_frame(seg["lines"])
            frames = (img for _ in range(int(seg["dur"] * FPS)))
        else:
            frames = clip_frames(seg)
        for img in frames:
            vf = av.VideoFrame.from_ndarray(np.asarray(img), format="rgb24")
            for p in st.encode(vf):
                out.mux(p)
            count += 1
    for p in st.encode():
        out.mux(p)
    out.close()
    print("wrote %s: %d frames, %.1f s" % (out_path, count, count / FPS))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
