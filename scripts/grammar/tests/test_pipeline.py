"""Synthetic fixtures only: no redistribution of textbook content."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from pipeline import DATASET, process, rights, validate
from import_firestore import import_batches, validate_release


def row(identifier='ch01-0001', kind='concept'):
    return dict(id=identifier, chapter=1, chapter_title='Fixture topic', section='Fixture section',
                type=kind, exercise=None, messages=[dict(role='system', content='fixture'),
                dict(role='user', content='Fixture question?'), dict(role='assistant', content='Fixture response.')])


def release():
    return dict(dataset=DATASET, revision='a' * 40, contentVersion=1, releaseBlocked=False,
                grammar_lessons=[dict(id=-1, title='Fixture', content='Fixture content', level='A1',
                                     levelEvidence='Synthetic test only', sourceId='fixture', source='fixture', license='fixture', reviewer='test')],
                questions=[dict(id=-2, lessonId=-1, content='Fixture ___', answerType='FILL_BLANK',
                                options=[dict(content='fixture', isCorrect=True)], mappingEvidence='fixture',
                                sourceId='fixture2', source='fixture', license='fixture', reviewer='test')])


class PipelineTest(unittest.TestCase):
    def run_rows(self, rows):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'in').mkdir()
            (root / 'in' / 'chapter.jsonl').write_text('\n'.join(json.dumps(r) for r in rows), encoding='utf-8')
            report = process(root / 'in', root / 'out')
            artifacts = {p.name: json.loads(p.read_text()) for p in (root / 'out').glob('*.json')}
            return report, artifacts

    def test_unresolved_rights_blocks_download(self):
        with self.assertRaisesRegex(ValueError, 'Rights unresolved'):
            rights(None)

    def test_schema_validation(self):
        invalid = row()
        invalid['messages'][2]['role'] = 'user'
        with self.assertRaises(ValueError):
            validate(invalid)

    def test_chapter_id_mismatch(self):
        invalid = row(); invalid['chapter'] = 2
        with self.assertRaises(ValueError):
            validate(invalid)

    def test_duplicate_and_conflicting_ids(self):
        conflict = row(); conflict['messages'][2]['content'] = 'Other'
        report, _ = self.run_rows([row(), row(), conflict])
        self.assertEqual(3, report['rawRecords'])
        self.assertEqual({'duplicate_id': 1, 'conflicting_id': 1}, report['rejectionReasons'])

    def test_content_duplicate(self):
        report, _ = self.run_rows([row(), row('ch01-0002')])
        self.assertEqual({'duplicate_content': 1}, report['rejectionReasons'])

    def test_preserves_prose_unknown_level_and_links(self):
        concept, exercise = row(), row('ch01-0002', 'exercise')
        concept['messages'][2]['content'] = '**Fixture**\n  Original spacing.'
        report, artifacts = self.run_rows([concept, exercise])
        lesson = artifacts['lessons.staging.json'][0]
        question = artifacts['exercises.quarantine.json'][0]
        self.assertEqual(concept['messages'][2]['content'], lesson['theory'][0]['content'])
        self.assertIsNone(lesson['level'])
        self.assertIsNone(question['answer'])
        self.assertEqual(lesson['id'], question['lessonId'])
        self.assertEqual(0, report['quizReady'])
        self.assertEqual(1, report['quarantinedExercises'])

    def test_stable_ids_independent_of_order(self):
        _, first = self.run_rows([row(), row('ch01-0002', 'example')])
        _, second = self.run_rows([row('ch01-0002', 'example'), row()])
        self.assertEqual(first['lessons.staging.json'], second['lessons.staging.json'])

    def test_empty_input_not_reported_as_zero_dataset(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(ValueError):
            process(directory, Path(directory) / 'out')


class ImportTest(unittest.TestCase):
    def validate(self, value):
        return validate_release(value, {'datasetRevision': 'a' * 40})

    def test_drafts_only_and_stable_hashes(self):
        writes = self.validate(release())
        self.assertEqual(writes, self.validate(release()))
        self.assertTrue(all(not data['published'] for _, _, data in writes))

    def test_staging_and_unsupported_quiz_blocked(self):
        data = release(); data['releaseBlocked'] = True
        with self.assertRaises(ValueError): self.validate(data)
        data = release(); data['questions'][0]['answerType'] = 'ESSAY'
        with self.assertRaises(ValueError): self.validate(data)

    def test_missing_evidence_and_dangling_lesson_blocked(self):
        data = release(); data['grammar_lessons'][0].pop('levelEvidence')
        with self.assertRaises(ValueError): self.validate(data)
        data = release(); data['questions'][0]['lessonId'] = -99
        with self.assertRaises(ValueError): self.validate(data)

    def test_duplicate_options_blocked(self):
        data = release(); data['questions'][0]['options'] *= 2
        with self.assertRaises(ValueError): self.validate(data)

    def test_existing_seed_ids_blocked(self):
        data = release(); data['grammar_lessons'][0]['id'] = 101
        with self.assertRaises(ValueError): self.validate(data)

    def test_rerun_does_not_write_identical_documents(self):
        db = Mock(); writes = self.validate(release())[:1]
        snapshot = db.collection.return_value.document.return_value.get.return_value
        snapshot.exists = True; snapshot.to_dict.return_value = writes[0][2]
        self.assertEqual(0, import_batches(db, writes, 400))
        db.batch.assert_not_called()

    def test_collision_preflight_has_no_writes(self):
        db = Mock(); snapshot = db.collection.return_value.document.return_value.get.return_value
        snapshot.exists = True; snapshot.to_dict.return_value = {'contentHash': 'other'}
        with self.assertRaises(ValueError): import_batches(db, self.validate(release()), 400)
        db.batch.assert_not_called()

    def test_batch_creates_with_precondition(self):
        db = Mock(); db.collection.return_value.document.return_value.get.return_value.exists = False
        self.assertEqual(2, import_batches(db, self.validate(release()), 1))
        self.assertEqual(2, db.batch.return_value.commit.call_count)
        self.assertEqual(2, db.batch.return_value.create.call_count)

if __name__ == '__main__':
    unittest.main()
