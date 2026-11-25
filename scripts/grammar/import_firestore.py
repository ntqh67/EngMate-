#!/usr/bin/env python3
"""Import reviewed EngMate documents; default is an offline dry-run.

Staging output is deliberately not accepted. A reviewer must provide precise
quiz mappings, level evidence and permission evidence before a release exists.
"""
import argparse
import json
import logging
import os
from pathlib import Path
from pipeline import DATASET, digest, rights


def validate_release(release, review=None):
    if release.get('dataset') == 'brehe-grammar-anatomy':
        from open_sources import validate_open_release
        return validate_open_release(release)
    if review is None:
        raise ValueError('Rights review required for this dataset')
    if release.get('dataset') != DATASET or release.get('revision') != review['datasetRevision']:
        raise ValueError('Dataset/revision does not match rights review')
    if type(release.get('contentVersion')) is not int or release['contentVersion'] < 1:
        raise ValueError('Positive contentVersion required')
    if release.get('releaseBlocked', True) is not False:
        raise ValueError('Staging content cannot be imported')
    writes, ids, lesson_ids = [], set(), set()
    for collection in ('grammar_lessons', 'questions'):
        documents = release.get(collection)
        if not isinstance(documents, list):
            raise ValueError('Expected document arrays')
        for data in documents:
            data = dict(data)
            identifier = data.pop('id', None)
            # Reserved negative ids never advance Room AUTOINCREMENT or collide with seeds.
            if type(identifier) is not int or not -(2**53) < identifier < 0:
                raise ValueError('Reviewed imports require stable negative safe-integer ids')
            key = (collection, identifier)
            if key in ids:
                raise ValueError('Duplicate document id')
            ids.add(key)
            if not isinstance(data.get('content'), str) or not data['content'].strip():
                raise ValueError('Empty content')
            for field in ('sourceId', 'source', 'license', 'reviewer'):
                if not isinstance(data.get(field), str) or not data[field].strip():
                    raise ValueError('Missing provenance: ' + field)
            if collection == 'grammar_lessons':
                lesson_ids.add(identifier)
                if not isinstance(data.get('title'), str) or not 2 <= len(data['title']) <= 100:
                    raise ValueError('Invalid title')
                if data.get('level') not in ('A1', 'A2', 'B1', 'B2') or not data.get('levelEvidence'):
                    raise ValueError('Current app requires reviewed CEFR evidence; do not guess a level')
                if not isinstance(data.get('examples', []), list):
                    raise ValueError('Invalid examples')
                for example in data.get('examples', []):
                    if not isinstance(example, dict) or not all(isinstance(example.get(k), str) and example[k].strip() for k in ('en', 'vi')):
                        raise ValueError('Current remote validator requires bilingual examples; do not fabricate translations')
            else:
                if data.get('lessonId') not in lesson_ids or data.get('topicId') is not None or data.get('isPlacement', False) is not False:
                    raise ValueError('Invalid grammar lesson reference')
                options = data.get('options', [])
                if not isinstance(options, list) or not options or any(not isinstance(o, dict) or not isinstance(o.get('content'), str) or not o['content'].strip() or type(o.get('isCorrect')) is not bool for o in options):
                    raise ValueError('Invalid answer options')
                normalized = [' '.join(o['content'].split()).casefold() for o in options]
                if len(set(normalized)) != len(options):
                    raise ValueError('Duplicate answer options')
                if data.get('answerType') == 'MULTIPLE_CHOICE':
                    if len(options) != 4 or sum(o['isCorrect'] for o in options) != 1:
                        raise ValueError('MC needs four source options and one correct answer')
                elif data.get('answerType') == 'FILL_BLANK':
                    if not all(o['isCorrect'] for o in options):
                        raise ValueError('Fill blank requires accepted answers only')
                else:
                    raise ValueError('Unsupported quiz type; keep in quarantine')
                if not data.get('mappingEvidence'):
                    raise ValueError('Exact source mapping evidence required')
            data.update(dataset=DATASET, revision=release['revision'], contentVersion=release['contentVersion'], published=False)
            data['contentHash'] = digest(data)
            if len(json.dumps(data, ensure_ascii=False).encode()) > 800_000:
                raise ValueError('Document exceeds conservative size limit')
            writes.append((collection, str(identifier), data))
    return writes


def import_batches(db, writes, batch_size):
    """Preflight all collisions before any write; reruns skip identical documents.

Run one importer at a time. Existing versions are immutable: use a new stable ID
for revised question content so historical attempts never change meaning.
"""
    pending = []
    for collection, identifier, data in writes:
        ref = db.collection(collection).document(identifier)
        snapshot = ref.get()
        if snapshot.exists:
            existing = snapshot.to_dict()
            if existing.get('contentHash') != data['contentHash']:
                raise ValueError(f'Conflicting immutable document: {collection}/{identifier}')
        else:
            pending.append((ref, data))
    for offset in range(0, len(pending), batch_size):
        batch = db.batch()
        for ref, data in pending[offset:offset + batch_size]:
            batch.create(ref, data)  # Precondition protects against concurrent overwrite.
        batch.commit()
        logging.info('Committed batch: %s documents', len(pending[offset:offset + batch_size]))
    return len(pending)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('release')
    parser.add_argument('--rights-review', help='Required for EPG; Brehe uses checked-in publisher evidence and pinned source captures')
    parser.add_argument('--project', required=True)
    parser.add_argument('--batch-size', type=int, default=400)
    action = parser.add_mutually_exclusive_group()
    action.add_argument('--dry-run', action='store_true')
    action.add_argument('--execute', action='store_true')
    parser.add_argument('--confirm-project', help='Required for non-emulator writes, after owner approval')
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO)
    try:
        if not 1 <= args.batch_size <= 400:
            raise ValueError('batch-size must be 1..400')
        release = json.loads(Path(args.release).read_text(encoding='utf-8'))
        review = None if release.get('dataset') == 'brehe-grammar-anatomy' else rights(args.rights_review)
        writes = validate_release(release, review)
        logging.info('Validated %s documents for project %s (published=%s)', len(writes), args.project,
                     sum(data.get('published') is True for _, _, data in writes))
        if not args.execute:
            logging.info('DRY RUN: no Firebase SDK initialized, no network writes')
            return
        if not os.environ.get('FIRESTORE_EMULATOR_HOST') and args.confirm_project != args.project:
            raise ValueError('Production requires explicit --confirm-project after owner approval')
        import firebase_admin
        from firebase_admin import firestore
        firebase_admin.initialize_app(options={'projectId': args.project})  # ADC; no embedded secrets.
        logging.info('Created %s documents', import_batches(firestore.client(), writes, args.batch_size))
    except Exception as exc:
        logging.error('Import stopped; committed batches may be safely rerun: %s', exc)
        raise SystemExit(1) from exc

if __name__ == '__main__':
    main()
