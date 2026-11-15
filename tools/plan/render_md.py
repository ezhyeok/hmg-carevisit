"""Render content blocks to Markdown (docs/plan/planning-doc.md)."""
import os
import re

from blocks import H, P, Bullets, Table, Callout, KeyNumbers, Figure, Storyboard, Note, Spacer, Annotated


def md_inline(text):
    text = re.sub(r'\{\{(.+?)\}\}', r'**\1**', text)
    text = re.sub(r'\[\[(.+?)\]\]', r'**\1**', text)
    return text


def _cell(text):
    return md_inline(str(text)).replace('|', '\\|').replace('\n', '<br>')


def _table(header, rows, align=None):
    align = align or ['left'] * len(header)
    sep = {'left': '---', 'center': ':---:', 'right': '---:'}
    out = ['| ' + ' | '.join(_cell(h) for h in header) + ' |',
           '|' + '|'.join(sep[a] for a in align) + '|']
    for r in rows:
        out.append('| ' + ' | '.join(_cell(c) for c in r) + ' |')
    return '\n'.join(out)


def render(blocks, resolve_md):
    out = []
    for b in blocks:
        if isinstance(b, H):
            out.append('#### ' + b.text)
        elif isinstance(b, P):
            out.append(md_inline(b.text))
        elif isinstance(b, Bullets):
            out.append('\n'.join('- ' + md_inline(i) for i in b.items))
        elif isinstance(b, Table):
            out.append(_table(b.header, b.rows, b.align))
        elif isinstance(b, Callout):
            lines = []
            if b.title:
                lines.append('> **%s**' % b.title)
                lines.append('>')
            for i, l in enumerate(b.lines):
                lines.append(('> - ' if b.bullets else '> ') + md_inline(l))
                if not b.bullets and i < len(b.lines) - 1:
                    lines.append('>')
            out.append('\n'.join(lines))
        elif isinstance(b, KeyNumbers):
            out.append(_table(['수치', '내용', '출처'], [[v, l, s] for v, l, s in b.items]))
        elif isinstance(b, Figure):
            target = resolve_md(b.key)
            if isinstance(target, list):
                out.append('\n'.join('![%s](%s)' % (label, path.replace(' ', '%20')) for path, label in target))
                out.append('*' + md_inline(b.caption_screens or b.caption) + '*')
            else:
                out.append('![%s](%s)' % (b.alt or b.caption, target.replace(' ', '%20')))
                out.append('*' + md_inline(b.caption) + '*')
        elif isinstance(b, Annotated):
            out.append('![%s](%s)' % (b.alt or b.caption, resolve_md(b.key).replace(' ', '%20')))
            out.append('\n'.join('%d. **%s**: %s' % (i + 1, t, md_inline(d)) for i, (t, d) in enumerate(b.items)))
            out.append('*' + md_inline(b.caption) + '*')
        elif isinstance(b, Storyboard):
            out.append('**%s** · %s' % (b.title, b.subtitle))
            out.append(_table(['단계', '차·앱이 하는 일', '사용자가 듣고 보는 것'],
                              [['%d. %s' % (i + 1, s), a, u] for i, (s, a, u) in enumerate(b.steps)]))
        elif isinstance(b, Note):
            out.append('<sub>' + md_inline(b.text) + '</sub>')
        elif isinstance(b, Spacer):
            continue
        else:
            raise TypeError(b)
    return '\n\n'.join(out)


def render_document(app_name, sections, resolve_md, header_note):
    parts = ['# ' + app_name + ' — 기획서 02 앱 프로젝트 소개 / 심사 자료', '', header_note, '',
             '**앱명 / 프로젝트명**: ' + app_name, '']
    for s in sections:
        parts.append('## %d. %s' % (s.number, s.title))
        parts.append('')
        parts.append(render(s.blocks, resolve_md))
        parts.append('')
    return '\n'.join(parts).rstrip() + '\n'
