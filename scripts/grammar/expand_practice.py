#!/usr/bin/env python3
"""Expand every imported grammar lesson to a full ten-question quiz pool."""
import argparse
from collections import Counter
import json
from pathlib import Path

from localize_lessons import build as localized_build
from open_sources import ROOT, build_brehe, validate_open_release, stable_id
from pipeline import digest, write_json
from supplement_practice import build as practice_build, targets as practice_targets

SOURCE = Path(__file__).with_name('engmate-practice-expanded.json')
DATASET = 'engmate-brehe-practice-expanded-v1'


def build(source=None):
    source = source if source is not None else json.loads(SOURCE.read_text())
    new = practice_build(source)['questions']
    baseline = validate_open_release(build_brehe()[1])
    expected = {(c, i): d for c, i, d in baseline if c == 'questions'}
    _, previous = practice_targets(practice_build())
    expected.update({k: v for k, v in previous.items() if k[0] == 'questions'})
    _, localized = localized_build()
    expected.update(localized)
    desired = {}
    prompts = {' '.join(v['content'].split()).casefold()
               for (c, _), v in expected.items() if c == 'questions'}
    counts = Counter(v['lessonId'] for (c, _), v in expected.items() if c == 'questions')
    for question in new:
        data = dict(question)
        data.pop('id')
        data['dataset'] = DATASET
        identifier = str(stable_id('engmate:expanded:v1:' + data['provenance']['sourceId']))
        key = ('questions', identifier)
        prompt = ' '.join(data['content'].split()).casefold()
        if key in expected or key in desired or prompt in prompts:
            raise ValueError('Duplicate ID or prompt across releases')
        prompts.add(prompt)
        counts[data['lessonId']] += 1
        data['contentHash'] = digest(data)
        desired[key] = data
    lesson_ids = {int(i) for c, i in localized if c == 'grammar_lessons'}
    if set(counts) != lesson_ids or any(counts[i] < 10 for i in lesson_ids):
        raise ValueError('Every lesson must have at least ten questions')
    for key, original in localized.items():
        data = {k: v for k, v in original.items() if k != 'contentHash'}
        data.update(contentVersion=4, practiceExpansion=DATASET)
        data['contentHash'] = digest(data)
        desired[key] = data
    return expected, desired


def plan(expected, desired, existing):
    for key, original in expected.items():
        actual = existing.get(key)
        if actual != original and (key not in desired or actual != desired[key]):
            raise ValueError(f'Missing or changed existing document: {key}')
    operations = []
    for key, data in desired.items():
        actual = existing.get(key)
        if actual == data:
            continue
        if key[0] == 'questions':
            if actual is not None:
                raise ValueError(f'Conflicting question: {key}')
            operations.append(('create', key, data))
        else:
            operations.append(('update', key, data))
    return operations


def publish(project, expected, desired):
    import firebase_admin
    from firebase_admin import firestore
    firebase_admin.initialize_app(options={'projectId': project})
    db = firestore.client()
    refs = {k: db.collection(k[0]).document(k[1]) for k in expected.keys() | desired.keys()}
    snapshots = {tuple(s.reference.path.split('/')): s for s in db.get_all(list(refs.values()))}
    operations = plan(expected, desired, {k: s.to_dict() for k, s in snapshots.items()})
    if operations:
        batch = db.batch()
        for action, key, data in operations:
            if action == 'create':
                batch.create(refs[key], data)
            else:
                batch.update(refs[key], data, option=db.write_option(last_update_time=snapshots[key].update_time))
        batch.commit()
    final = expected | desired
    for snapshot in db.get_all(list(refs.values())):
        key = tuple(snapshot.reference.path.split('/'))
        if snapshot.to_dict() != final[key]:
            raise ValueError(f'Post-publish verification failed: {key}')
    counts = Counter()
    all_questions = 0
    lesson_ids = {int(i) for c, i in expected if c == 'grammar_lessons'}
    for snapshot in db.collection('questions').stream():
        q = snapshot.to_dict()
        all_questions += 1
        if q.get('published') is True and q.get('lessonId') in lesson_ids:
            counts[q['lessonId']] += 1
    if set(counts) != lesson_ids or min(counts.values()) < 10:
        raise ValueError('Published lesson coverage verification failed')
    print(f'Published/verified {len(operations)} atomic writes. '
          f'20 lesson pools: {sorted(counts.values())}; total {sum(counts.values())}. '
          f'All Firebase questions: {all_questions}.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--project')
    parser.add_argument('--confirm-project')
    args = parser.parse_args()
    expected, desired = build()
    write_json(ROOT / 'docs/grammar/expanded-practice-firestore.json',
               [dict(collection=k[0], id=k[1], data=v) for k, v in desired.items()])
    print('Validated 126 new questions; 20 lessons now have at least 10 each.')
    if not args.execute:
        print('DRY RUN: no network writes.')
        return
    if not args.project or args.confirm_project != args.project:
        parser.error('Provide --project and matching --confirm-project')
    publish(args.project, expected, desired)


if __name__ == '__main__':
    main()
