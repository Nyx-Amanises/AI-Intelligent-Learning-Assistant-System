#!/usr/bin/env python3
"""Build colour-preserving reading layouts from the bundled original PDFs.

Requires PyMuPDF. No PDF is modified. Run --check to validate generated files.
Glyph corrections are constrained to an exact PDF hash, font and glyph sequence.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import fitz

ROOT = Path(__file__).resolve().parents[2]
BOOKS = ROOT / 'frontend/public/open-books'
MANIFEST = ROOT / 'db/catalog/github-open-books.json'
CHUNK_SIZE = 20
HELLO_SHA = '7a1bb221fe4e968b7fef16bbde24b20db07b339f0d6552ab5109cdc3a23bb5bb'
LIGATURES = {
    (1742, 1742, 1572): '===', (1742, 1571): '==', (1742, 939): '//',
    (1742, 1742, 896): '...', (1742, 895): '..', (1742, 1568): '=:',
    (1742, 900): '::', (1742, 1565): '++', (1742, 1629): '^=',
}


def correction_positions(page, sha):
    fixes = {}
    if sha != HELLO_SHA:
        return fixes
    for trace in page.get_texttrace():
        if trace['font'] != 'JetBrainsMono-Regular':
            continue
        chars = trace['chars']
        offset = 0
        while offset < len(chars):
            for glyphs, replacement in LIGATURES.items():
                group = chars[offset:offset + len(glyphs)]
                if tuple(char[1] for char in group) == glyphs:
                    for char, value in zip(group, replacement):
                        fixes[(trace['font'], round(char[2][0], 2), round(char[2][1], 2), chr(char[0]))] = value
                    offset += len(glyphs)
                    break
            else:
                offset += 1
    return fixes


def page_layout(page, sha):
    fixes = correction_positions(page, sha)
    spans = []
    corrected = 0
    for block in page.get_text('rawdict')['blocks']:
        if block['type'] != 0:
            continue
        previous_line = None
        for line in block['lines']:
            line_spans = []
            for span in line['spans']:
                text = []
                for char in span['chars']:
                    key = (span['font'], round(char['origin'][0], 2), round(char['origin'][1], 2), char['c'])
                    value = fixes.get(key, char['c'])
                    corrected += value != char['c']
                    text.append(value)
                if not ''.join(text).strip() or span.get('alpha', 255) == 0:
                    continue
                flags = span['flags']
                line_spans.append({
                    't': ''.join(text), 'x': round(span['origin'][0], 3),
                    'y': round(page.rect.height - span['origin'][1], 3),
                    'w': round(span['bbox'][2] - span['bbox'][0], 3), 'h': round(span['size'], 3),
                    'c': f"#{span['color'] & 0xffffff:06x}",
                    'b': bool(flags & 16), 'i': bool(flags & 2),
                    'm': bool(flags & 8) or bool(re.search('mono|courier|consolas', span['font'], re.I)),
                })
            if line_spans:
                if previous_line:
                    before, after = previous_line[-1], line_spans[0]
                    gap = after['x'] - before['x'] - before['w']
                    # MuPDF can split one printed line at a wide gap, including
                    # trailing code comments. Keep those spans on their baseline.
                    if abs(after['y'] - before['y']) <= 0.2 and 0 <= gap <= 3 * max(before['h'], after['h']):
                        before.pop('e', None)
                line_spans[-1]['e'] = True
                spans.extend(line_spans)
                previous_line = line_spans
    return {'number': page.number + 1, 'width': round(page.rect.width, 3),
            'height': round(page.rect.height, 3), 'spans': spans}, corrected


def encode(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n'


def write_or_check(path, data, check):
    text = encode(data)
    if check:
        assert path.read_text(encoding='utf-8') == text, f'Stale reading layout: {path}'
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8', newline='\n')


def build_chunk(row, start, check):
    folder = BOOKS / row['slug']
    pdf = folder / 'book.pdf'
    sha = hashlib.sha256(pdf.read_bytes()).hexdigest()
    assert sha == row['sha256']
    count, pages = 0, []
    with fitz.open(pdf) as doc:
        for number in range(start, min(start + CHUNK_SIZE, len(doc))):
            data, fixes = page_layout(doc[number], sha)
            pages.append(data)
            count += fixes
    write_or_check(folder / 'reading' / f'{start // CHUNK_SIZE + 1:03}.json',
                   {'schemaVersion': 1, 'pdfSha256': sha, 'pages': pages}, check)
    return count


def main(check, manifest=MANIFEST):
    records = json.loads(manifest.read_text(encoding='utf-8'))['records']
    summary = []
    for row in records:
        folder = BOOKS / row['slug']
        pdf = folder / 'book.pdf'
        sha = hashlib.sha256(pdf.read_bytes()).hexdigest()
        assert sha == row['sha256']
        count = 0
        # Bound native-library memory and avoid long-lived get_texttrace refcount
        # problems in PyMuPDF on Python 3.11. Each worker handles only 20 pages.
        for start in range(0, row['pageCount'], CHUNK_SIZE):
            command = [sys.executable, str(Path(__file__).resolve()), '--worker', row['slug'], '--start', str(start), '--manifest', str(manifest)]
            if check: command.append('--check')
            result = subprocess.run(command, check=True, capture_output=True, text=True)
            count += int(result.stdout.strip())
        index = {'schemaVersion': 1, 'pdfSha256': sha, 'pageCount': row['pageCount'], 'chunkSize': CHUNK_SIZE,
                 'glyphCorrections': count, 'extractor': f'PyMuPDF {fitz.VersionBind}'}
        write_or_check(folder / 'reading/index.json', index, check)
        summary.append({'slug': row['slug'], 'pages': row['pageCount'], 'correctedGlyphs': count})
    print(json.dumps(summary, ensure_ascii=True))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--worker')
    parser.add_argument('--start', type=int, default=0)
    parser.add_argument('--manifest', type=Path, default=MANIFEST)
    args = parser.parse_args()
    if args.worker:
        rows = json.loads(args.manifest.read_text(encoding='utf-8'))['records']
        print(build_chunk(next(row for row in rows if row['slug'] == args.worker), args.start, args.check))
    else: main(args.check, args.manifest)
