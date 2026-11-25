import copy
import json
from collections import Counter
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from expand_practice import SOURCE, build, plan


class ExpandedPracticeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.expected, cls.desired = build()

    def test_full_round_for_every_lesson_and_existing_questions_preserved(self):
        final = self.expected | self.desired
        counts = Counter(v['lessonId'] for (c, _), v in final.items() if c == 'questions')
        self.assertEqual([10] * 18 + [12, 19], sorted(counts.values()))
        self.assertEqual(211, sum(counts.values()))
        for key, old in self.expected.items():
            if key[0] == 'questions':
                self.assertNotIn(key, self.desired)
                self.assertEqual(old, final[key])
            else:
                for field in ('content', 'examples', 'title', 'level', 'summary', 'provenance'):
                    self.assertEqual(old.get(field), final[key].get(field))
                self.assertEqual(4, final[key]['contentVersion'])

    def test_answer_shapes_and_rotation(self):
        positions = Counter()
        for (collection, _), q in self.desired.items():
            if collection != 'questions':
                continue
            self.assertEqual(4, len(q['options']))
            correct = [i for i, o in enumerate(q['options']) if o['isCorrect']]
            self.assertEqual(1, len(correct))
            positions[correct[0]] += 1
            self.assertTrue(q['explanation'].strip())
        self.assertEqual({0, 1, 2, 3}, set(positions))
        self.assertLessEqual(max(positions.values()) - min(positions.values()), 1)

    def test_atomic_plan_and_idempotence(self):
        operations = plan(self.expected, self.desired, self.expected)
        self.assertEqual(126, sum(action == 'create' for action, _, _ in operations))
        self.assertEqual(40, sum(action == 'update' for action, _, _ in operations))
        self.assertEqual([], plan(self.expected, self.desired, self.expected | self.desired))

    def test_missing_or_conflicting_documents_rejected(self):
        for collection in ('questions', 'grammar_catalog', 'grammar_lessons'):
            key = next(k for k in self.expected if k[0] == collection)
            changed = dict(self.expected)
            changed.pop(key)
            with self.assertRaises(ValueError):
                plan(self.expected, self.desired, changed)
        new_key = next(k for k in self.desired if k[0] == 'questions')
        with self.assertRaises(ValueError):
            plan(self.expected, self.desired, self.expected | {new_key: {'content': 'existing'}})

    def test_invalid_answers_and_incomplete_coverage_rejected(self):
        source = json.loads(SOURCE.read_text())
        bad = copy.deepcopy(source)
        bad['questions'][0]['answer'] = 'not an option'
        with self.assertRaises(ValueError):
            build(bad)
        bad = copy.deepcopy(source)
        bad['questions'].pop()
        with self.assertRaises(ValueError):
            build(bad)
        bad = copy.deepcopy(source)
        bad['questions'][1]['prompt'] = bad['questions'][0]['prompt']
        with self.assertRaises(ValueError):
            build(bad)
