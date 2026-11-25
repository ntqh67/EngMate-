#!/usr/bin/env python3
"""Conservative, offline-capable staging pipeline. No model/API generation.

The upstream card is a schema hint, not a substitute for validation of JSONL.
No staging output is a release artifact while textbook rights remain unresolved.
"""
import argparse
import collections
import hashlib
import json
import logging
from pathlib import Path
import re
import urllib.request

DATASET = 'AbhinavPullat/english-practical-grammar'
SOURCE = f'https://huggingface.co/datasets/{DATASET}'
TYPES = {'concept', 'example', 'identification', 'exercise'}


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def rights(path):
    review = json.loads(Path(path).read_text(encoding='utf-8')) if path else {}
    required = ('datasetRevision', 'reviewer', 'textbookPermissionEvidence', 'datasetLicenseEvidence', 'permittedUse')
    if review.get('approved') is not True or any(not isinstance(review.get(k), str) or not review[k].strip() for k in required):
        raise ValueError('Rights unresolved: supply a reviewed permission record covering the original textbook and dataset license')
    if not re.fullmatch(r'[0-9a-f]{40}', review['datasetRevision']):
        raise ValueError('datasetRevision must pin a full commit SHA')
    return review


def download(destination, review_path):
    review = rights(review_path)  # Gate before any network or content download.
    revision = review['datasetRevision']
    base = f'https://huggingface.co/api/datasets/{DATASET}/tree/{revision}?recursive=true&limit=1000'
    def fetch(url):
        with urllib.request.urlopen(url, timeout=60) as response:
            if response.headers.get('Link'):
                raise ValueError('Paginated tree: refuse an incomplete download')
            return response.read()
    tree = json.loads(fetch(base))
    files = sorted(x['path'] for x in tree if x.get('type') == 'file' and x['path'].endswith('.jsonl'))
    if not files:
        raise ValueError('No JSONL files in pinned revision; inspect upstream before continuing')
    manifest = {'dataset': DATASET, 'revision': revision, 'files': []}
    destination = Path(destination)
    for name in files:
        relative = Path(name)
        if relative.is_absolute() or '..' in relative.parts:
            raise ValueError('Unsafe upstream path')
        raw = fetch(f'{SOURCE}/resolve/{revision}/{name}')
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
        manifest['files'].append({'path': name, 'sha256': hashlib.sha256(raw).hexdigest()})
        logging.info('Downloaded %s (%s bytes)', name, len(raw))
    write_json(destination / 'manifest.json', manifest)


def validate(row):
    if not isinstance(row, dict):
        raise ValueError('not_object')
    if not isinstance(row.get('id'), str) or not re.fullmatch(r'ch\d{2}-\d{4}', row['id']):
        raise ValueError('invalid_id')
    if type(row.get('chapter')) is not int or row['chapter'] < 1:
        raise ValueError('invalid_chapter')
    if int(row['id'][2:4]) != row['chapter']:
        raise ValueError('chapter_id_mismatch')
    for key in ('chapter_title', 'section'):
        if not isinstance(row.get(key), str) or not row[key].strip():
            raise ValueError('missing_' + key)
    if row.get('type') not in TYPES:
        raise ValueError('unknown_type')
    if row.get('exercise') is not None and (type(row['exercise']) is not int or row['exercise'] < 1):
        raise ValueError('invalid_exercise')
    messages = row.get('messages')
    if not isinstance(messages, list) or len(messages) != 3:
        raise ValueError('ambiguous_messages')
    for item, role in zip(messages, ('system', 'user', 'assistant')):
        if not isinstance(item, dict) or item.get('role') != role or not isinstance(item.get('content'), str) or not item['content'].strip():
            raise ValueError('invalid_messages')
    return messages[1]['content'], messages[2]['content']


def process(source, output):
    """Preserve Q/A verbatim; never interpret bold text as an exact scoring key."""
    source, output = Path(source), Path(output)
    files = sorted(source.rglob('*.jsonl'))
    if not files:
        raise ValueError('No original JSONL files available; cannot report measured totals')
    seen_ids, seen_content = {}, set()
    accepted, rejected = [], []
    raw_count = 0
    for file in files:
        for line_number, line in enumerate(file.read_text(encoding='utf-8-sig').splitlines(), 1):
            if not line.strip():
                continue
            raw_count += 1
            row = None
            location = {'file': str(file.relative_to(source)), 'line': line_number}
            try:
                row = json.loads(line)
                prompt, answer = validate(row)
                identity = digest(row)
                if row['id'] in seen_ids:
                    raise ValueError('duplicate_id' if seen_ids[row['id']] == identity else 'conflicting_id')
                seen_ids[row['id']] = identity
                # Only normalize whitespace for duplicate detection, never for stored prose.
                fingerprint = digest([row['chapter'], row['section'], row['type'],
                                      ' '.join(prompt.split()), ' '.join(answer.split())])
                if fingerprint in seen_content:
                    raise ValueError('duplicate_content')
                seen_content.add(fingerprint)
                accepted.append(row)
            except (ValueError, TypeError) as exc:
                rejected.append({**location, 'reason': str(exc), 'record': row, 'raw': line if row is None else None})
    groups = collections.defaultdict(list)
    for row in accepted:
        groups[(row['chapter'], row['chapter_title'], row['section'])].append(row)
    lessons, exercises = [], []
    for (chapter, topic, section), rows in sorted(groups.items()):
        lesson_id = 'epg-section-' + digest([chapter, section])[:24]
        concepts = [r for r in rows if r['type'] == 'concept']
        examples = [r for r in rows if r['type'] == 'example']
        drills = [r for r in rows if r['type'] in {'exercise', 'identification'}]
        lessons.append({'id': lesson_id, 'title': section, 'topic': topic, 'chapter': chapter,
                        'level': None, 'levelEvidence': None,
                        'theory': [{'sourceId': r['id'], 'prompt': r['messages'][1]['content'], 'content': r['messages'][2]['content']} for r in concepts],
                        'examples': [{'sourceId': r['id'], 'prompt': r['messages'][1]['content'], 'response': r['messages'][2]['content']} for r in examples],
                        'exerciseIds': ['epg-' + r['id'] for r in drills],
                        'source': SOURCE, 'license': 'CC-BY-NC-4.0; underlying textbook rights unresolved'})
        for row in drills:
            exercises.append({'id': 'epg-' + row['id'], 'lessonId': lesson_id,
                              'sourceType': row['type'], 'exerciseNumber': row.get('exercise'),
                              'prompt': row['messages'][1]['content'], 'referenceResponse': row['messages'][2]['content'],
                              'answer': None, 'explanation': None, 'source': SOURCE,
                              'reason': 'unstructured_reference_response_requires_review'})
    counts = collections.Counter(r['type'] for r in accepted)
    report = {'rawRecords': raw_count, 'validUniqueRecords': len(accepted), 'types': dict(counts),
              'chapters': [{'chapter': c, 'title': t, 'records': sum(r['chapter'] == c and r['chapter_title'] == t for r in accepted)} for c, t in sorted({(r['chapter'], r['chapter_title']) for r in accepted})],
              'sections': len(lessons), 'lessonsWithTheory': sum(bool(l['theory']) for l in lessons),
              'quizReady': 0, 'quarantinedExercises': len(exercises), 'rejected': len(rejected),
              'rejectionReasons': dict(collections.Counter(r['reason'] for r in rejected)),
              'releaseBlocked': True, 'reason': 'Textbook rights and exact quiz mappings need review',
              'sourceFiles': [{'path': str(f.relative_to(source)), 'sha256': hashlib.sha256(f.read_bytes()).hexdigest()} for f in files]}
    write_json(output / 'lessons.staging.json', lessons)
    write_json(output / 'exercises.quarantine.json', exercises)
    write_json(output / 'rejected.json', rejected)
    write_json(output / 'report.json', report)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    dl = commands.add_parser('download')
    dl.add_argument('--output', required=True)
    dl.add_argument('--rights-review', required=True)
    proc = commands.add_parser('process')
    proc.add_argument('--input', required=True)
    proc.add_argument('--output', required=True)
    commands.add_parser('build-open', help='Build Brehe Firestore release from checked-in licensed captures')
    audit = commands.add_parser('audit-zanichelli', help='Validate real JSON; quarantine while LICENSE conflicts with README')
    audit.add_argument('--input', required=True)
    audit.add_argument('--output', required=True)
    fetch = commands.add_parser('download-zanichelli', help='Download pinned source for local review only, not distribution')
    fetch.add_argument('--output', required=True)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO)
    try:
        if args.command == 'download':
            download(args.output, args.rights_review)
        elif args.command == 'build-open':
            from open_sources import build
            logging.info('%s', build())
        elif args.command == 'download-zanichelli':
            from open_sources import download_zanichelli
            download_zanichelli(Path(args.output))
        elif args.command == 'audit-zanichelli':
            from open_sources import inspect_zanichelli
            report, issues, candidates = inspect_zanichelli(args.input)
            output = Path(args.output)
            write_json(output / 'report.json', report)
            write_json(output / 'rejected.json', issues)
            write_json(output / 'candidates.quarantine.json', candidates)
            logging.info('%s', report)
        else:
            logging.info('%s', process(args.input, args.output))
    except (OSError, ValueError) as exc:
        parser.exit(1, f'{exc}\n')

if __name__ == '__main__':
    main()
