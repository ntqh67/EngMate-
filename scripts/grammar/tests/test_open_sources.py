import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from open_sources import (ROOT, SOURCES, build_brehe, extract_answer, inspect_zanichelli,
                          numbered_items, sections, stable_id, validate_open_release)


class ActualBreheDataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundle, cls.release, cls.report, cls.rejected = build_brehe()

    def test_committed_artifacts_are_reproducible(self):
        self.assertEqual(self.release, json.loads((ROOT/'docs/grammar/brehe-firestore.json').read_text()))
        self.assertEqual(self.report, json.loads((ROOT/'docs/grammar/brehe-report.json').read_text()))

    def test_actual_counts_and_valid_parent_links(self):
        self.assertEqual(20,len(self.bundle['lessons']))
        self.assertEqual(63,sum(len(l['examples']) for l in self.bundle['lessons']))
        self.assertEqual(31,len(self.release['questions']))
        ids = {l['id'] for l in self.bundle['lessons']}
        self.assertTrue(all(q['lessonId'] in ids for q in self.release['questions']))
        self.assertEqual(31,len({q['id'] for q in self.release['questions']}))

    def test_every_accepted_answer_matches_published_key(self):
        key = sections(json.loads((SOURCES/'answers.json').read_text())['lines'])
        for lesson in self.bundle['lessons']:
            for q in lesson['questions']:
                meta=q['provenance']
                _, answers=numbered_items(key[meta['exercise']])
                prompt=q['q'].split('\n\n',1)[1]
                self.assertEqual(q['accept'],[extract_answer(prompt, answers[meta['item']])])
                self.assertIn('CC BY-SA 4.0',q['exp'])

    def test_examples_are_source_text_and_have_no_fake_translation(self):
        for lesson in self.bundle['lessons']:
            source=next(json.loads(p.read_text()) for p in SOURCES.glob('[0-9]*.json')
                        if json.loads(p.read_text())['url']==lesson['provenance']['source'])
            for ex in lesson['examples']:
                self.assertIn(ex['en'],source['lines'].values())
                self.assertEqual('',ex['vi'])
            self.assertEqual('Chưa phân loại',lesson['level'])

    def test_release_does_not_collide_with_old_seed_or_autoincrement(self):
        legacy=json.loads((ROOT/'app/src/main/assets/grammar_seed.json').read_text())['lessons']
        self.assertEqual(14,len(legacy))  # plus 3 GrammarQuizSeed lessons = 17
        legacy_ids={l['id'] for l in legacy}|{1,2,3}
        self.assertFalse(legacy_ids & {l['id'] for l in self.bundle['lessons']})
        self.assertTrue(all(-(2**53)<q['id']<0 for q in self.release['questions']))

    def test_source_backed_release_is_published_and_tampering_fails(self):
        writes=validate_open_release(self.release)
        self.assertEqual(71,len(writes)) # 51 full-content docs + 20 metadata-only catalog rows
        self.assertEqual(20,sum(collection == 'grammar_catalog' for collection,_,_ in writes))
        self.assertTrue(all(d['published'] is True for _,_,d in writes))
        edited=copy.deepcopy(self.release)
        edited['questions'][0]['options'][0]['content']='invented answer'
        with self.assertRaises(ValueError):validate_open_release(edited)

    def test_multiple_blanks_and_changed_prompt_are_not_guessed(self):
        with self.assertRaises(ValueError):extract_answer('___ and ___','a and b')
        with self.assertRaises(ValueError):extract_answer('I ___ here.','You sit here.')
        self.assertEqual('sit',extract_answer('I ___ here.','I sit here.'))

    def test_unsupported_groups_are_reported(self):
        self.assertTrue(any(r['reason']=='unsupported_or_unreviewed_exercise_group' for r in self.rejected))
        self.assertTrue(any(r['reason']=='requires_exactly_one_blank' for r in self.rejected))


class ZanichelliAdapterTest(unittest.TestCase):
    def inspect(self, rows):
        columns={k:{str(i):row[k] for i,row in enumerate(rows)} for k in rows[0]}
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'actual-shape.json';p.write_text(json.dumps(columns))
            return inspect_zanichelli(p)

    def row(self):
        return dict(gapped_text='Fixture ___',solution='key',distractors=['a','b','c'],filled_text='Fixture key',grammar_topic='articles')

    def test_license_conflict_blocks_even_structurally_valid_row(self):
        report,_,candidates=self.inspect([self.row()])
        self.assertEqual(1,len(candidates));self.assertTrue(report['releaseBlocked'])
        self.assertEqual(0,report['integratedRows'])

    def test_no_invented_fourth_option(self):
        row=self.row();row['distractors']=['a','b']
        report,rejected,candidates=self.inspect([row])
        self.assertEqual([],candidates)
        self.assertEqual('requires_four_source_options',rejected[0]['reason'])

    def test_invalid_answer_and_duplicate_options(self):
        bad=self.row();bad['filled_text']='Fixture wrong'
        duplicate=self.row();duplicate['distractors']=['KEY','b','c']
        _,rejected,candidates=self.inspect([bad,duplicate])
        self.assertEqual([],candidates)
        self.assertEqual({'filled_text_mismatch','duplicate_options'},{r['reason'] for r in rejected})

    def test_conflicting_answers_quarantine_both_versions(self):
        first=self.row(); second=self.row()
        second['solution']='other';second['filled_text']='Fixture other'
        _,rejected,candidates=self.inspect([first,second])
        self.assertEqual([],candidates)
        self.assertEqual(2,len(rejected))
        self.assertTrue(all(r['reason']=='conflicting_answer' for r in rejected))

if __name__=='__main__':unittest.main()
