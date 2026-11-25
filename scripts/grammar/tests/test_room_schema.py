"""Real JSON against exported Room v3 SQLite DDL, without Android runtime.
This complements (does not replace) GrammarOpenSeedTest/Robolectric.
"""
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from open_sources import ROOT


def seed(connection,bundle):
    with connection:
        for lesson in bundle['lessons']:
            connection.execute('INSERT OR IGNORE INTO grammar_lessons(id,title,level,summary,content,examples,order_index,updated_at) VALUES(?,?,?,?,?,?,?,?)',
                (lesson['id'],lesson['title'],lesson['level'],lesson['summary'],lesson['content'],json.dumps(lesson['examples']),lesson.get('orderIndex',0),None))
            for index,q in enumerate(lesson['questions']):
                identifier=q.get('id',10000+lesson['id']*10+index)
                if connection.execute('SELECT 1 FROM questions WHERE id=?',(identifier,)).fetchone():continue
                connection.execute('INSERT INTO questions(id,lesson_id,answer_type,content,explanation) VALUES(?,?,?,?,?)',
                    (identifier,lesson['id'],'SINGLE_CHOICE' if q['type']=='MC' else 'TEXT',q['q'],q['exp']))
                for pos,text in enumerate(q['opts'] if q['type']=='MC' else q['accept']):
                    correct=q['type']=='FB' or pos==q['correct']
                    connection.execute('INSERT INTO question_options(question_id,content,is_correct,order_index) VALUES(?,?,?,?)',
                        (identifier,text,correct,pos))


class RoomSchemaCompatibilityTest(unittest.TestCase):
    def test_actual_assets_fit_existing_schema_preserve_old_attempt_and_reopen(self):
        schema=json.loads((ROOT/'app/schemas/com.engmate.app.core.database.EngMateDatabase/3.json').read_text())['database']
        legacy=json.loads((ROOT/'app/src/main/assets/grammar_seed.json').read_text())
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'offline.sqlite'
            db=sqlite3.connect(path);db.execute('PRAGMA foreign_keys=ON')
            for entity in schema['entities']:
                db.execute(entity['createSql'].replace('${TABLE_NAME}',entity['tableName']))
                for index in entity.get('indices', []):
                    db.execute(index['createSql'].replace('${TABLE_NAME}',entity['tableName']))
            for identifier,title in ((1,'Grammar basics'),(2,'Present simple'),(3,'Past simple')):
                db.execute('INSERT INTO grammar_lessons(id,title,level,content,examples,order_index) VALUES(?,?,?,?,?,?)',
                    (identifier,title,'A1','Existing bundled lesson','[]',identifier))
            seed(db,legacy)
            old_lessons=db.execute('SELECT * FROM grammar_lessons ORDER BY id').fetchall()
            old_options=db.execute('SELECT * FROM question_options ORDER BY id').fetchall()
            question_id=db.execute('SELECT id FROM questions ORDER BY id LIMIT 1').fetchone()[0]
            option_id=db.execute('SELECT id FROM question_options WHERE question_id=? LIMIT 1',(question_id,)).fetchone()[0]
            db.execute("INSERT INTO user_profile VALUES('learner','test@example.test','Learner',NULL,'A1',15,1,1)")
            db.execute("INSERT INTO quiz_attempts(id,user_id,lesson_id,total_questions,completed_at) VALUES(1,'learner',101,1,1)")
            db.execute('INSERT INTO attempt_answers(attempt_id,question_id,selected_option_id,question_snapshot,is_correct) VALUES(1,?,?,?,1)',(question_id,option_id,'Original snapshot'))
            seed(db,legacy);seed(db,legacy)
            self.assertEqual(old_lessons,db.execute('SELECT * FROM grammar_lessons WHERE id>0 ORDER BY id').fetchall())
            self.assertEqual(old_options,db.execute('SELECT * FROM question_options WHERE question_id>0 ORDER BY id').fetchall())
            self.assertEqual(17,db.execute('SELECT COUNT(*) FROM grammar_lessons').fetchone()[0]) # 14 asset + 3 Kotlin callback seeds
            self.assertEqual(84,db.execute('SELECT COUNT(*) FROM questions').fetchone()[0])
            self.assertEqual([],db.execute('PRAGMA foreign_key_check').fetchall())
            db.commit();db.close()
            db=sqlite3.connect(path)
            self.assertEqual(0,db.execute('SELECT COUNT(*) FROM questions WHERE id<0').fetchone()[0])
            self.assertEqual(('Original snapshot',),db.execute('SELECT question_snapshot FROM attempt_answers').fetchone())
            db.close()

if __name__=='__main__':unittest.main()
