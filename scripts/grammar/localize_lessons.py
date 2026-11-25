#!/usr/bin/env python3
"""Publish Vietnamese lesson metadata with EngMate's estimated learning levels."""
import argparse
import collections
from open_sources import build_brehe, validate_open_release, ROOT
from supplement_practice import build as practice_build, targets as practice_targets
from pipeline import digest, write_json

# Levels describe the selected content, not an official CEFR certification of the book.
ROWS = [
(1, 'A1', 'Làm quen với ngữ pháp tiếng Anh', 'Hiểu vai trò của trật tự từ và dạng đại từ trong câu.'),
(2, 'A1', 'Chủ ngữ và vị ngữ', 'Nhận biết chủ ngữ, vị ngữ và các thành phần chính của câu.'),
(3, 'A2', 'Thì và các dạng của động từ', 'Ôn các dạng động từ, động từ bất quy tắc và cách diễn đạt thời gian.'),
(4, 'A1', 'Danh từ và động từ', 'Phân biệt danh từ, động từ và vai trò của chúng trong câu.'),
(5, 'A1', 'Tính từ và các dạng so sánh', 'Dùng tính từ để mô tả và so sánh người, sự vật.'),
(6, 'A2', 'Trạng từ và cách bổ nghĩa', 'Nhận biết trạng từ chỉ cách thức, thời gian, nơi chốn và mức độ.'),
(7, 'A2', 'Giới từ và cụm giới từ', 'Xác định giới từ, tân ngữ của giới từ và chức năng của cụm giới từ.'),
(8, 'A1', 'Đại từ nhân xưng', 'Phân biệt ngôi, số và các dạng chủ ngữ, tân ngữ, sở hữu của đại từ.'),
(9, 'B1', 'Động từ, tân ngữ và bổ ngữ', 'Phân tích tân ngữ và các thành phần bổ sung ý nghĩa cho động từ.'),
(10, 'A2', 'Liên từ và cách nối thành phần câu', 'Dùng liên từ để nối từ, cụm từ và các phần vị ngữ.'),
(11, 'B1', 'Câu đơn, câu ghép và câu phức', 'Phân biệt mệnh đề độc lập, mệnh đề phụ thuộc và các kiểu câu.'),
(12, 'B1', 'Mệnh đề quan hệ', 'Nhận biết mệnh đề bổ nghĩa cho danh từ và cách dùng đại từ quan hệ.'),
(14, 'B2', 'Phân biệt các loại mệnh đề phụ thuộc', 'Phân tích chức năng của mệnh đề trạng ngữ, quan hệ và danh từ.'),
(15, 'B1', 'Câu chủ động và câu bị động', 'Nhận biết thể bị động và cấu trúc trợ động từ đi cùng quá khứ phân từ.'),
(17, 'A1', 'Thán từ và cách bộc lộ cảm xúc', 'Nhận biết thán từ diễn tả ngạc nhiên, đau đớn và các phản ứng khác.'),
(18, 'A2', 'Danh động từ và chức năng trong câu', 'Nhận biết dạng động từ thêm đuôi -ing được dùng như danh từ.'),
(19, 'B1', 'Động từ nguyên mẫu và chức năng trong câu', 'Phân tích cụm động từ nguyên mẫu khi làm chủ ngữ hoặc bổ nghĩa cho danh từ.'),
(20, 'A2', 'Đại từ phản thân và đại từ chỉ định', 'Dùng đại từ để nói về chính chủ thể và chỉ người, sự vật.'),
(21, 'B1', 'Chủ ngữ giả và cách sắp xếp câu', 'Nhận biết cấu trúc dùng chủ ngữ giả để đưa nội dung dài về cuối câu.'),
(22, 'A2', 'Dấu phẩy trong thành phần chen vào', 'Đặt đủ cặp dấu phẩy để tách phần giải thích chen giữa câu.'),
]
REFERENCES = [
'https://learnenglish.britishcouncil.org/free-resources/grammar/a1-a2',
'https://learnenglish.britishcouncil.org/free-resources/grammar/b1-b2',
'https://www.teachingenglish.org.uk/sites/teacheng/files/pub-british-council-eaquals-core-inventoryv2.pdf',
]


def build():
    baseline = validate_open_release(build_brehe()[1])
    expected = {(c, i): d for c, i, d in baseline if c != 'questions'}
    _, supplement = practice_targets(practice_build())
    expected.update({k: v for k, v in supplement.items() if k[0] == 'grammar_catalog'})
    mapping = {100 + chapter: (level, title, summary) for chapter, level, title, summary in ROWS}
    assert len(mapping) == 20
    desired = {}
    for key, original in expected.items():
        level, title, summary = mapping[original['orderIndex']]
        data = {k: v for k, v in original.items() if k != 'contentHash'}
        data.update(title=title, level=level, summary=summary, contentVersion=3,
                    levelAssignment=dict(kind='EngMate editorial estimate',
                        basis=summary, references=REFERENCES,
                        note='Estimated from the selected lesson content and prerequisite complexity; not a CEFR label supplied by Brehe or an external certification.'),
                    originalTitle=original['title'])
        data['contentHash'] = digest(data)
        desired[key] = data
    return expected, desired


def plan(expected, desired, existing):
    operations = []
    for key, original in expected.items():
        actual = existing.get(key)
        if actual == desired[key]:
            continue
        if actual != original:
            raise ValueError(f'Missing or changed document: {key}')
        operations.append((key, desired[key]))
    return operations


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--project')
    parser.add_argument('--confirm-project')
    args = parser.parse_args()
    expected, desired = build()
    write_json(ROOT / 'docs/grammar/vietnamese-lesson-metadata.json',
               [dict(collection=k[0], id=k[1], data=v) for k, v in desired.items()])
    print('Levels:', dict(collections.Counter(row[1] for row in ROWS)))
    if not args.execute:
        print('DRY RUN: 20 lesson and 20 catalog updates; no network writes.')
        return
    if not args.project or args.confirm_project != args.project:
        parser.error('Provide --project and matching --confirm-project')
    import firebase_admin
    from firebase_admin import firestore
    firebase_admin.initialize_app(options={'projectId': args.project})
    db = firestore.client()
    refs = {k: db.collection(k[0]).document(k[1]) for k in expected}
    snapshots = {tuple(s.reference.path.split('/')): s for s in db.get_all(list(refs.values()))}
    existing = {k: s.to_dict() for k, s in snapshots.items()}
    operations = plan(expected, desired, existing)
    if operations:
        batch = db.batch()
        for key, data in operations:
            batch.update(refs[key], data, option=db.write_option(last_update_time=snapshots[key].update_time))
        batch.commit()
    for snapshot in db.get_all(list(refs.values())):
        key = tuple(snapshot.reference.path.split('/'))
        if snapshot.to_dict() != desired[key]:
            raise ValueError(f'Verification failed: {key}')
    print(f'Published and verified all 40 documents; {len(operations)} atomic updates.')


if __name__ == '__main__':
    main()
