#!/usr/bin/env python3
"""Pinned Markdown dependencies. Run once online; --check makes offline builds fail closed."""
import hashlib, json, pathlib, sys, urllib.request
root = pathlib.Path(__file__).resolve().parent
for item in json.loads((root / 'markdown-dependencies.json').read_text()):
    target = root / 'downloads/markdown' / item['file']
    if not target.exists() and '--check' not in sys.argv:
        target.parent.mkdir(parents=True, exist_ok=True)
        data = urllib.request.urlopen(item['url'], timeout=30).read()
        if hashlib.sha256(data).hexdigest() != item['sha256']:
            raise SystemExit('Markdown dependency integrity failure')
        target.write_bytes(data)
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != item['sha256']:
        raise SystemExit('Run python3 app/prepare_markdown.py: missing/invalid ' + item['file'])
