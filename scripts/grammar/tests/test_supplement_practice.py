import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from supplement_practice import build, targets, plan
from open_sources import build_brehe


class SupplementPracticeTest(unittest.TestCase):
    def setUp(self):
        self.release = build()
        self.expected, self.desired = targets(self.release)

    def test_all_twenty_lessons_have_exercises(self):
        baseline = build_brehe()[1]
        questions = baseline['questions'] + self.release['questions']
        self.assertEqual(54, len(self.release['questions']))
        self.assertEqual({l['id'] for l in baseline['grammar_lessons']},
                         {q['lessonId'] for q in questions})
        self.assertEqual(85, len({q['id'] for q in questions}))
        for question in self.release['questions']:
            self.assertEqual(1, sum(o['isCorrect'] for o in question['options']))
            self.assertTrue(question['explanation'])

    def test_atomic_plan_creates_questions_and_invalidates_caches(self):
        operations = plan(self.expected, self.desired, self.expected)
        self.assertEqual(54, sum(action == 'create' for action, _, _ in operations))
        updates = [(key, data) for action, key, data in operations if action == 'update']
        self.assertEqual(18, len(updates))
        self.assertTrue(all(key[0] == 'grammar_catalog' and data['contentVersion'] == 2
                            for key, data in updates))
        self.assertEqual([], plan(self.expected, self.desired, self.expected | self.desired))

    def test_missing_or_changed_parent_blocks_publish(self):
        for key in self.expected:
            existing = copy.deepcopy(self.expected)
            existing.pop(key)
            with self.assertRaises(ValueError):
                plan(self.expected, self.desired, existing)
            existing[key] = {'contentHash': 'unexpected'}
            with self.assertRaises(ValueError):
                plan(self.expected, self.desired, existing)

    def test_conflicting_question_is_never_overwritten(self):
        key = next(k for k in self.desired if k[0] == 'questions')
        with self.assertRaises(ValueError):
            plan(self.expected, self.desired, self.expected | {key: {'content': 'existing'}})

    def test_release_edits_require_rebuild(self):
        self.release['questions'][0]['lessonId'] = 1
        with self.assertRaises(ValueError):
            targets(self.release)
