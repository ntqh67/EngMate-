#!/usr/bin/env python3
"""Build/publish original EngMate practice without changing source-backed questions.

Publishing atomically creates new questions and advances the affected catalog
versions, so existing Android caches reload them on the next lesson open.
"""
import argparse
import json
from pathlib import Path

from open_sources import ROOT, build_brehe, stable_id, validate_open_release
from pipeline import digest, write_json

SOURCE = Path(__file__).with_name('engmate-practice.json')
OUTPUT = ROOT / 'docs/grammar/engmate-practice-firestore.json'
DATASET = 'engmate-brehe-practice-v1'


def build(source=None):
    source = source if source is not None else json.loads(SOURCE.read_text())
    baseline = build_brehe()[1]
    lessons = {l['orderIndex'] - 100: l for l in baseline['grammar_lessons']}
    covered = {q['lessonId'] for q in baseline['questions']}
    missing = {chapter for chapter, lesson in lessons.items() if lesson['id'] not in covered}
    questions, seen, prompts, counts = [], set(), set(), {}
    for row in source['questions']:
        chapter = row['chapter']
        if chapter not in missing:
            raise ValueError('Supplement must target a lesson without source exercises')
        if row['key'] in seen or row['prompt'].strip().casefold() in prompts:
            raise ValueError('Duplicate question key or prompt')
        seen.add(row['key']); prompts.add(row['prompt'].strip().casefold())
        choices = row['choices']
        if (len(choices) != 4 or len({c.strip().casefold() for c in choices}) != 4
                or any(not c.strip() for c in choices) or choices.count(row['answer']) != 1
                or not row['prompt'].strip() or not row['explanation'].strip()):
            raise ValueError('Invalid choices, answer, prompt or explanation')
        counts[chapter] = counts.get(chapter, 0) + 1
        # Spread correct answers across positions, reproducibly.
        offset = len(questions) % 4
        choices = choices[offset:] + choices[:offset]
        identifier = stable_id('engmate:practice:v1:' + row['key'])
        questions.append(dict(
            id=identifier, lessonId=lessons[chapter]['id'], topicId=None,
            isPlacement=False, answerType='MULTIPLE_CHOICE', content=row['prompt'],
            explanation=row['explanation'],
            options=[dict(content=c, isCorrect=c == row['answer']) for c in choices],
            createdAt=None, dataset=DATASET, contentVersion=1, published=True,
            provenance=dict(sourceId=row['key'], author=source['author'],
                            origin=source['origin'], sourceHash=digest(row),
                            relatedLessonSource=lessons[chapter]['provenance']['source']),
        ))
    if set(counts) != missing or any(n < 3 for n in counts.values()):
        raise ValueError('Every missing lesson needs at least three exercises')
    if len({q['id'] for q in questions}) != len(questions):
        raise ValueError('Question ID collision')
    if {q['id'] for q in questions} & {q['id'] for q in baseline['questions']}:
        raise ValueError('Collision with source exercise')
    return dict(dataset=DATASET, version=1, questions=questions)


def targets(release):
    if release != build():
        raise ValueError('Rebuild the supplement before publishing')
    baseline = validate_open_release(build_brehe()[1])
    lesson_ids = {str(q['lessonId']) for q in release['questions']}
    expected = {(c, i): d for c, i, d in baseline
                if c in ('grammar_catalog', 'grammar_lessons') and i in lesson_ids}
    desired = {}
    for q in release['questions']:
        data = dict(q)
        identifier = str(data.pop('id'))
        data['contentHash'] = digest(data)
        desired['questions', identifier] = data
    for (collection, identifier), old in expected.items():
        if collection == 'grammar_catalog':
            data = {k: v for k, v in old.items() if k != 'contentHash'}
            data.update(contentVersion=2, practiceDataset=DATASET)
            data['contentHash'] = digest(data)
            desired[collection, identifier] = data
    return expected, desired


def plan(expected, desired, existing):
    """Refuse unexpected remote changes; never overwrite an existing question."""
    operations = []
    for key, original in expected.items():
        actual = existing.get(key)
        if actual != original and (key not in desired or actual != desired[key]):
            raise ValueError(f'Missing or changed baseline document: {key}')
    for key, data in desired.items():
        actual = existing.get(key)
        if actual == data:
            continue
        if key[0] == 'questions':
            if actual is not None:
                raise ValueError(f'Conflicting immutable question: {key}')
            operations.append(('create', key, data))
        else:
            operations.append(('update', key, data))
    return operations


def publish(project, release):
    import firebase_admin
    from firebase_admin import firestore
    firebase_admin.initialize_app(options={'projectId': project})
    db = firestore.client()
    expected, desired = targets(release)
    refs = {key: db.collection(key[0]).document(key[1]) for key in expected.keys() | desired.keys()}
    snapshots = {tuple(s.reference.path.split('/')): s for s in db.get_all(list(refs.values()))}
    existing = {key: s.to_dict() if s.exists else None for key, s in snapshots.items()}
    operations = plan(expected, desired, existing)
    if operations:
        batch = db.batch()
        for action, key, data in operations:
            if action == 'create':
                batch.create(refs[key], data)
            else:
                batch.update(refs[key], data,
                             option=db.write_option(last_update_time=snapshots[key].update_time))
        batch.commit()
    for snapshot in db.get_all([refs[key] for key in desired]):
        key = tuple(snapshot.reference.path.split('/'))
        if snapshot.to_dict() != desired[key]:
            raise ValueError(f'Post-publish verification failed: {key}')
    print(f'Published/verified {len(release["questions"])} questions and 18 catalog versions; '
          f'{len(operations)} writes in one atomic batch.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--project')
    parser.add_argument('--confirm-project')
    args = parser.parse_args()
    release = build()
    write_json(OUTPUT, release)
    expected, desired = targets(release)
    print(f'Validated {len(release["questions"])} new questions for 18 previously empty lessons.')
    if args.execute:
        if not args.project or args.confirm_project != args.project:
            parser.error('--project and matching --confirm-project required')
        publish(args.project, release)
    else:
        print('DRY RUN: no network writes. Existing 31 source questions remain unchanged.')


if __name__ == '__main__':
    main()
