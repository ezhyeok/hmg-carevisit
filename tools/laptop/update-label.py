#!/usr/bin/env python3
"""Sync owned README labels from Android's single app_name resource."""
import argparse
from pathlib import Path
import re
import xml.etree.ElementTree as ET

TARGETS = ('README.md', 'tools/laptop/README.md')
MARKER = re.compile(r'^<!-- app-label: (.+) -->$', re.MULTILINE)


def sync_labels(root, check=False):
    resource = ET.parse(root / 'app/src/main/res/values/strings.xml').getroot().find("string[@name='app_name']")
    if resource is None or len(resource):
        raise ValueError('app_name must be a plain string resource')
    label = (resource.text or '').strip()
    if not label or len(label) > 40 or re.search(r'[\\/\r\n<>`#\[\]]', label) or '--' in label:
        raise ValueError('app_name must be plain display text suitable for the APK filename')
    updates = []
    for name in TARGETS:
        file = root / name
        text = file.read_text(encoding='utf-8')
        markers = MARKER.findall(text)
        if len(markers) != 1:
            raise ValueError('%s needs one app-label marker' % name)
        if markers[0] != label:
            updates.append((file, text.replace(markers[0], label)))
    # Validate every target before changing any file.
    if not check:
        for file, text in updates:
            file.write_text(text, encoding='utf-8')
    return [str(file.relative_to(root)) for file, _ in updates]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='report stale labels without writing')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    try:
        changed = sync_labels(root, args.check)
    except (OSError, ValueError, ET.ParseError) as error:
        parser.error(str(error))
    print(('stale: ' if args.check else 'updated: ') + ', '.join(changed) if changed else 'app_name labels match')
    return int(args.check and bool(changed))


if __name__ == '__main__':
    raise SystemExit(main())
