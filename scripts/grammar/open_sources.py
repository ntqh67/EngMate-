"""Adapters for the actual Brehe text captures and Zanichelli column-oriented JSON.
Reuses pipeline hashing/JSON output. No generated educational content.
"""
import collections
import hashlib
import json
from pathlib import Path
import re
from pipeline import digest, write_json

ROOT = Path(__file__).resolve().parents[2]
SOURCES = Path(__file__).parent / 'sources' / 'brehe'
DATASET = 'brehe-grammar-anatomy'
UNKNOWN_LEVEL = 'Chưa phân loại'
LICENSE_URL = 'https://creativecommons.org/licenses/by-sa/4.0/'
ZAN_REVISION = '374f402068cf15bf0e71c44410ba72f3a8c9c301'


def stable_id(source_id):
    return -(int(hashlib.sha256(source_id.encode()).hexdigest()[:13], 16) + 1)


def normalize(text):
    return ' '.join(text.split())


def clean(text):
    # Strip only capture navigation markers and standalone printed page headers.
    lines = []
    for line in text.splitlines():
        if re.fullmatch(r'\s*(\d+ \| Brehe’s Grammar Anatomy|Answer Key \| \d+|.+ \| \d+)\s*', line):
            continue
        if line.strip() == '* * *':
            continue
        lines.append(re.sub(r'【\d+†([^】]+)】', r'\1', line).strip())
    return re.sub(r'\n{3,}', '\n\n', '\n'.join(lines)).strip()


def sections(lines):
    """Group exact exercise labels (e.g. 3e), retaining original wording."""
    result, current = {}, None
    for number, text in sorted(((int(k), v) for k, v in lines.items())):
        text = clean(text)
        # Page headers in answer key are not part of an answer.
        if re.fullmatch(r'CHAPTER \d+', text):
            current = None
            continue
        match = re.match(r'^(\d+[a-z])\.\s*(.*)', text)
        if match:
            current = match[1]
            if current in result:
                raise ValueError('Ambiguous repeated exercise label: ' + current)
            result[current] = {'line': number, 'lines': [match[2]]}
        elif current:
            result[current]['lines'].append(text)
    return result


def numbered_items(section):
    text = normalize(' '.join(section['lines']))
    matches = list(re.finditer(r'(?:^|\s)(\d+)\.\s*(?=[A-Za-z_])', text))
    if not matches:
        return text, {}
    instruction = text[:matches[0].start()].strip()
    result = {}
    for index, match in enumerate(matches):
        number = int(match[1])
        if number in result:
            raise ValueError('Duplicate item number')
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        result[number] = text[match.end():end].strip()
    return instruction, result


def extract_answer(prompt, solved):
    """A single blank must be the ONLY difference from the published solved sentence."""
    if len(re.findall(r'_{3,}', prompt)) != 1:
        raise ValueError('requires_exactly_one_blank')
    left, right = re.split(r'_{3,}', normalize(prompt))
    match = re.fullmatch(re.escape(left) + r'(.+?)' + re.escape(right), normalize(solved))
    if not match or not match[1].strip():
        raise ValueError('answer_key_does_not_match_prompt')
    answer = match[1].strip()
    if len(answer) > 80 or re.search(r'[_\n]', answer):
        raise ValueError('ambiguous_answer')
    return answer


def provenance(source, source_id, changes):
    return {'sourceId': source_id, 'source': source['url'], 'author': 'Steven Brehe',
            'license': 'CC-BY-SA-4.0', 'licenseUrl': LICENSE_URL,
            'licenseEvidence': source['licenseEvidence'], 'changes': changes,
            'sourceHash': digest(source), 'contentVersion': 1}


def attribution(source):
    return (f"Source: Steven Brehe, Brehe’s Grammar Anatomy (2019), University of North Georgia Press, via LibreTexts.\n"
            f"{source['url']}\nCC BY-SA 4.0 — {LICENSE_URL}\n"
            "Adaptation: selected excerpts; layout converted to plain text. No translation. CEFR not supplied by the source.")


def build_brehe():
    selections = json.loads((Path(__file__).parent / 'brehe-selection.json').read_text())
    answer_source = json.loads((SOURCES / 'answers.json').read_text())
    answer_groups = sections(answer_source['lines'])
    lessons, rejected, questions, source_manifest = [], [], [], []
    group_counts = collections.Counter()
    for key, selection in selections.items():
        chapter = int(key)
        source = json.loads((SOURCES / f'{chapter:02}.json').read_text())
        source_manifest.append({'file': f'{chapter:02}.json', 'sha256': digest(source), 'url': source['url']})
        lines = source['lines']
        selected = []
        for start, end in selection['theory']:
            if any(str(i) not in lines for i in range(start, end + 1)):
                raise ValueError('Incomplete source capture')
            selected.append(clean('\n'.join(lines[str(i)] for i in range(start, end + 1))))
        content = '\n\n'.join(selected)
        examples = [{'en': clean(lines[str(i)]), 'vi': ''} for i in selection['examples']]
        identifier = f'brehe:libretexts:1.{chapter:02}'
        lesson_id = stable_id(identifier)
        title = re.sub(r'^1\.\d+:\s*', '', source['title'])
        lesson = dict(id=lesson_id, title=title, level=UNKNOWN_LEVEL,
                      summary='Brehe’s Grammar Anatomy — English source excerpts',
                      content=content + '\n\n' + attribution(source), examples=examples,
                      orderIndex=100 + chapter, updatedAt=None, questions=[],
                      provenance=provenance(source, identifier, 'Selected theory paragraphs and example blocks; removed navigation/page headers; retained English'),
                      selection=selection)
        groups = sections(lines)
        for label, section in groups.items():
            group_counts['total'] += 1
            try:
                instruction, items = numbered_items(section)
                _, answers = numbered_items(answer_groups.get(label, {'lines': []}))
            except ValueError as exc:
                rejected.append({'sourceId': identifier + ':' + label, 'reason': str(exc)})
                continue
            # Only reviewed groups with explicit gap-fill instructions and a published key.
            # Other exercise types remain in source snapshots; no artificial distractors.
            if label not in {'3e', '3f', '3h', '8a'}:
                rejected.append({'sourceId': identifier + ':' + label, 'reason': 'unsupported_or_unreviewed_exercise_group', 'itemCount': len(items)})
                continue
            for number, prompt in items.items():
                qid = f'brehe:exercise:{label}:{number}'
                try:
                    answer = extract_answer(prompt, answers.get(number, ''))
                except ValueError as exc:
                    rejected.append({'sourceId': qid, 'reason': str(exc), 'prompt': prompt})
                    continue
                meta = provenance(source, qid, 'Matched one blank against the same numbered solved sentence in the published answer key; normalized layout whitespace')
                meta.update(answerSource=answer_source['url'], answerSourceHash=digest(answer_source), exercise=label, item=number)
                question = dict(id=stable_id(qid), type='FB', q=instruction + '\n\n' + prompt,
                                accept=[answer], exp='Source answer: ' + answers[number] + '\n\n' + attribution(source) + '\nAnswer key: ' + answer_source['url'],
                                provenance=meta)
                lesson['questions'].append(question)
                questions.append(question)
        lessons.append(lesson)
    # Never keep conflicting or duplicate prompts even across lessons.
    seen = {}
    for lesson in lessons:
        unique = []
        for q in lesson['questions']:
            fingerprint = normalize(q['q']).casefold()
            if fingerprint in seen:
                rejected.append({'sourceId': q['provenance']['sourceId'], 'reason': 'duplicate_prompt'})
                continue
            seen[fingerprint] = q['id']; unique.append(q)
        lesson['questions'] = unique
    bundle = {'version': 1, 'dataset': DATASET, 'license': 'CC-BY-SA-4.0',
              'licenseUrl': LICENSE_URL, 'lessons': lessons}
    validate_bundle(bundle)
    release = export_firestore(bundle)
    report = {'dataset': DATASET, 'downloadedChapterPages': len(list(SOURCES.glob('[0-9]*.json'))),
              'unavailableChapterPages': [13,16], 'answerKeyPages': 1,
              'integratedLessonExcerpts': len(lessons), 'selectedExampleBlocks': sum(len(l['examples']) for l in lessons),
              'integratedExercises': sum(len(l['questions']) for l in lessons), 'exerciseGroupsInspected': group_counts['total'],
              'quarantineEntries': len(rejected), 'reasons': dict(collections.Counter(r['reason'] for r in rejected)),
              'sourceManifest': source_manifest, 'answerSourceHash': digest(answer_source),
              'note': 'Counts refer to real captured text. Example blocks may contain multiple sentences. Group exclusions and item exclusions have different units.'}
    return bundle, release, report, rejected


def validate_bundle(bundle):
    if bundle.get('dataset') != DATASET or bundle.get('version') != 1 or not bundle.get('lessons'):
        raise ValueError('Invalid open-content bundle')
    lessons, questions = set(), set()
    for lesson in bundle['lessons']:
        if type(lesson.get('id')) is not int or lesson['id'] >= 0 or lesson['id'] in lessons:
            raise ValueError('Invalid/duplicate lesson id')
        lessons.add(lesson['id'])
        if not 2 <= len(lesson['title']) <= 100 or not lesson['content'].strip() or lesson['level'] != UNKNOWN_LEVEL:
            raise ValueError('Invalid lesson')
        if any(not ex['en'].strip() or ex['vi'] != '' for ex in lesson['examples']):
            raise ValueError('No invented translations permitted')
        for record in [lesson] + lesson['questions']:
            meta = record['provenance']
            if meta['license'] != 'CC-BY-SA-4.0' or meta['author'] != 'Steven Brehe' or not meta['sourceHash']:
                raise ValueError('Missing provenance')
        for question in lesson['questions']:
            if question['id'] in questions or question['id'] >= 0:
                raise ValueError('Invalid/duplicate question id')
            questions.add(question['id'])
            if question['type'] != 'FB' or not question['q'].strip() or not question['accept'] or any(not a.strip() for a in question['accept']):
                raise ValueError('Invalid question')


def export_firestore(bundle):
    validate_bundle(bundle)
    lessons, questions = [], []
    for lesson in bundle['lessons']:
        data = {k:lesson[k] for k in ('id','title','level','summary','content','examples','orderIndex','updatedAt','provenance')}
        lessons.append(data)
        for q in lesson['questions']:
            questions.append(dict(id=q['id'], lessonId=lesson['id'], topicId=None, isPlacement=False,
                                  answerType='FILL_BLANK', content=q['q'], explanation=q['exp'],
                                  options=[dict(content=a,isCorrect=True) for a in q['accept']],
                                  createdAt=None, provenance=q['provenance']))
    return dict(dataset=DATASET, contentVersion=1, releaseBlocked=False, grammar_lessons=lessons, questions=questions)


def inspect_zanichelli(path):
    """Audit all real rows; license conflict prevents release irrespective of structure."""
    data = json.loads(Path(path).read_text())
    expected = {'gapped_text','solution','distractors','filled_text','grammar_topic'}
    if set(data) != expected or not all(isinstance(data[k],dict) for k in expected):
        raise ValueError('Unexpected Zanichelli schema')
    keys = set(data['gapped_text'])
    if any(set(data[k]) != keys for k in expected):
        raise ValueError('Misaligned dataset columns')
    issues, seen, topics, candidates = [], {}, collections.Counter(), []
    conflicting_prompts = set()
    for key in sorted(keys, key=int):
        row = {field:data[field][key] for field in expected}
        reason = None
        topics[row['grammar_topic']] += 1
        options = [row['solution']] + row['distractors']
        if not all(isinstance(v,str) and v.strip() for v in options + [row['gapped_text'],row['filled_text']]):
            reason = 'empty_or_invalid_text'
        elif len(re.findall(r'_{3,}',row['gapped_text'])) != 1:
            reason = 'not_one_blank'
        elif len(options) != 4:
            reason = 'requires_four_source_options'
        elif len({normalize(v).casefold() for v in options}) != 4:
            reason = 'duplicate_options'
        elif normalize(re.sub(r'_{3,}',lambda _:row['solution'],row['gapped_text'])) != normalize(row['filled_text']):
            reason = 'filled_text_mismatch'
        fingerprint = normalize(row['gapped_text']).casefold()
        if not reason and fingerprint in seen:
            reason = 'duplicate_prompt' if seen[fingerprint] == normalize(row['solution']).casefold() else 'conflicting_answer'
            if reason == 'conflicting_answer':
                conflicting_prompts.add(fingerprint)
        if reason:
            issues.append({'row':key,'reason':reason})
        else:
            seen[fingerprint] = normalize(row['solution']).casefold()
            candidates.append({'row':key,**row})
    safe = []
    for row in candidates:
        if normalize(row['gapped_text']).casefold() in conflicting_prompts:
            issues.append({'row':row['row'],'reason':'conflicting_answer'})
        else:
            safe.append(row)
    candidates = safe
    report = dict(dataset='ZanichelliEditore/english-grammar-multiple-choice-generation', revision=ZAN_REVISION,
                  originalRows=len(keys), topics=dict(sorted(topics.items())), structurallyValidUniqueMC=len(candidates),
                  excludedRows=len(issues), reasons=dict(collections.Counter(r['reason'] for r in issues)),
                  integratedRows=0, releaseBlocked=True, reason='README says CC-BY-4.0; LICENSE says CC-BY-NC-ND-4.0; no content distributed',
                  inputSha256=hashlib.sha256(Path(path).read_bytes()).hexdigest())
    return report, issues, candidates


def build(output=None):
    bundle, release, report, rejected = build_brehe()
    write_json(ROOT/'docs/grammar/brehe-firestore.json',release)
    write_json(ROOT/'docs/grammar/brehe-report.json',report)
    write_json(ROOT/'docs/grammar/brehe-quarantine.json',rejected)
    return report


def validate_open_release(release):
    """Only the deterministic, source-backed release is accepted, not an edited answer bank."""
    expected = build_brehe()[1]
    if release != expected:
        raise ValueError('Brehe release differs from source-backed build; rebuild and review sources first')
    writes = []
    for collection in ('grammar_lessons','questions'):
        for original in release[collection]:
            data = dict(original)
            identifier = data.pop('id')
            data.update(dataset=DATASET, contentVersion=1, published=True)
            data['contentHash'] = digest(data)
            if len(json.dumps(data,ensure_ascii=False).encode()) > 800_000:
                raise ValueError('Document too large')
            writes.append((collection,str(identifier),data))
    # Android Firestore does not support field projection. A separate, small catalog
    # collection lets the app list lessons without downloading theory/examples.
    for lesson in release['grammar_lessons']:
        source = lesson['provenance']
        data = dict(
            title=lesson['title'], level=lesson['level'], summary=lesson.get('summary'),
            orderIndex=lesson.get('orderIndex', 0), contentVersion=release['contentVersion'],
            dataset=DATASET, sourceId=source['sourceId'], source=source['source'],
            author=source['author'], license=source['license'], licenseUrl=source['licenseUrl'],
            published=True,
        )
        data['contentHash'] = digest(data)
        writes.append(('grammar_catalog', str(lesson['id']), data))
    return writes


def download_zanichelli(output):
    """Pinned raw data only. Never downloads or runs upstream model/code."""
    import urllib.request
    output.mkdir(parents=True,exist_ok=True)
    base = f'https://raw.githubusercontent.com/ZanichelliEditore/english-grammar-multiple-choice-generation/{ZAN_REVISION}'
    manifest = []
    for path, sha in [('dataset/train_exercise_dataset.json','1e55a8fa8365cd14f0eeff2ced91fa2d8385990e'),
                      ('LICENSE','9badf7abf51e412ff7bbe1ce7b66c5b05145138c'),
                      ('README.md','6c345e890b44b1210437baaf452b69862a2184e1')]:
        with urllib.request.urlopen(f'{base}/{path}',timeout=60) as response:
            raw = response.read()
        actual = hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
        if actual != sha:
            raise ValueError('Upstream blob mismatch: '+path)
        (output/Path(path).name).write_bytes(raw)
        manifest.append({'url':f'{base}/{path}','gitBlobSha':sha,'sha256':hashlib.sha256(raw).hexdigest()})
    write_json(output/'manifest.json',manifest)
