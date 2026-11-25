# Grammar content sources and Firestore loading

Checked 2026-11-26. Brehe content is handled as published source material under CC BY-SA 4.0. No AI API is used.

## Current storage design

- The 17 baseline lessons remain unchanged: 14 in `app/src/main/assets/grammar_seed.json` plus three from `GrammarQuizSeed.Callback`.
- The APK does not bundle the new Brehe dataset. `docs/grammar/brehe-firestore.json` contains 20 lessons and 31 supported questions (51 full-content documents); the importer additionally creates 20 lightweight catalog rows. There are 71 Firestore writes in total so the client can list lesson metadata without downloading theory/examples. The release contains 63 English-only example blocks and source/license/conversion provenance. Full content and questions are lazy-cached on device.
- Firestore shared collections are `grammar_catalog/{stable-id}`, `grammar_lessons/{stable-id}`, and `questions/{stable-id}`. Catalog documents hold title/level/summary/order/contentVersion; lesson documents hold title/level/summary/content/examples/orderIndex; questions hold `lessonId`, `answerType`, question content, accepted answers/options and explanation. All carry publication/version/hash and provenance fields as applicable. A separate catalog is used because the Android SDK cannot project selected fields from a document query.
- On sign-in the client listens only to `grammar_catalog` with `published == true`. Metadata rows are cached in the existing Room `grammar_lessons` table; no Room schema or migration change is needed. On opening a new lesson, the client fetches that full lesson document and only published questions whose `lessonId` matches, then upserts them into Room. A per-lesson lock avoids duplicate concurrent downloads. A local version marker avoids repeat downloads until `contentVersion` changes. Room remains the UI source and is available offline; absent cloud data never erases local lessons, questions, attempts, or answer snapshots.
- Quiz attempts and progress remain user-specific in Room and under `users/{uid}` in Firestore. Shared grammar content is never written by learner devices. Firestore rules require authentication and `published == true` for learner reads; only a user with the custom `admin: true` claim can write shared content.

The importer checks all target document IDs before writing and refuses conflicting content hashes; it never overwrites a conflicting document. Production was inspected before import: 17 baseline lessons and 129 existing questions were retained; all target IDs were free.

## Sources and conversion counts

- Brehe’s *Grammar Anatomy*: 20 accessible chapter captures used, 63 example blocks, 31 supported fill-in exercises with answers checked against the source answer key. 85 exercise groups/items are quarantined or omitted because mapping was unsupported, duplicated, or required more than one blank. Chapters 13 and 16 could not be captured completely and were excluded. Lessons have `Chưa phân loại`; the source provides no CEFR labels. Attribution and adaptation notes are retained with content and Firestore provenance. See `docs/grammar/brehe-report.json` and `scripts/grammar/sources/brehe/`.
- Zanichelli’s GitHub dataset: 27,514 actual rows were inspected. 11,345 are structurally plausible unique candidates, 16,169 were excluded by the structural audit. The repository README and `LICENSE` conflict (CC BY 4.0 vs CC BY-NC-ND 4.0); therefore zero rows are included or imported. Raw source files are local/ignored.
- The earlier English Practical Grammar source remains excluded because the textbook reuse rights were not established.

## Dry-run and emulator

The dry-run validates the release, stable IDs, parent references, answer mappings, provenance and content hashes without initializing Firebase or using credentials:

```bash
python3 scripts/grammar/import_firestore.py docs/grammar/brehe-firestore.json --project demo-engmate --dry-run
```

Expected: `Validated 71 documents ... (published=71)` and `DRY RUN: no Firebase SDK initialized, no network writes` (51 full-content plus 20 catalog documents).

The rules emulator test is in `scripts/grammar/emulator/`. From the repository root, with Node.js/npm and Firebase CLI installed:

```bash
npm --prefix scripts/grammar/emulator install --no-save firebase-tools @firebase/rules-unit-testing firebase
scripts/grammar/emulator/node_modules/.bin/firebase emulators:exec --project demo-engmate --only firestore 'node --test scripts/grammar/emulator/rules.test.mjs'
```

The emulator suite checks published and legacy reads, rejected drafts, admin-only shared writes and UID-scoped user data.

## Import to a real Firebase project

1. Before the production import, Firestore was inspected and the target IDs were confirmed free. The deployed rules preserve reads for the old 17 lessons and 129 questions, allow signed-in users to read published shared content, and restrict shared writes to accounts carrying the trusted custom claim `admin: true`.
2. Configure the Android Firebase app locally with `app/google-services.json` (do not commit it). No API key or service-account secret is embedded in the scripts.
3. Run the dry-run above. Then, only after selecting and verifying the intended project, run:

```bash
GOOGLE_CLOUD_PROJECT=YOUR_PROJECT_ID \
  python3 scripts/grammar/import_firestore.py docs/grammar/brehe-firestore.json \
  --project YOUR_PROJECT_ID --execute --confirm-project YOUR_PROJECT_ID
```

The explicit `--confirm-project` is a guard against accidental production writes. The script preflights every document first, creates in batches of at most 400, uses create preconditions, skips identical existing hashes and stops on conflicting versions. It is safe to rerun after a partial batch commit. On 2026-11-26, 71 new documents were imported to project `engmate-572b6`: 20 catalog rows, 20 lesson bodies and 31 questions. The Firestore release was updated to ruleset `da785863-1c68-4e4c-b85d-3cfa7f8b15c6` after Emulator validation.

Production counts after import: `grammar_catalog` 20 (20 published), `grammar_lessons` 37 (20 new published plus 17 existing baseline), and `questions` 160 (31 new published plus 129 existing). All 71 imported documents were verified against their release content hashes. The 17 baseline lesson assets and user progress were not modified.

## Verification

Python tests:

```bash
python3 -m unittest discover -s scripts/grammar/tests -v
```

Android checks (from repository root, where the Gradle wrapper can write its distribution/cache and `app/google-services.json` is configured):

```bash
./gradlew :app:assembleLiveDebug :app:testLiveDebugUnitTest :app:lintLiveDebug
```

The Python suite passes (29 tests), the Firestore Rules Emulator passes, and the importer dry-run validates 71 writes without initializing Firebase. Android `:app:assembleLiveDebug`, `:app:testLiveDebugUnitTest`, and `:app:lintLiveDebug` all pass. No destructive Room migration was introduced. Existing schema version 3, the 17 baseline lessons, user progress and history remain intact.

## Supplementary practice published 2026-11-26

The original 31 source exercises cover only two of the 20 Brehe lessons. A separate
EngMate-authored supplement now adds 54 multiple-choice questions (three for each
of the other 18 lessons), each with four options, one correct answer and a Vietnamese
explanation. These are AI-assisted original exercises, not excerpts from Brehe or
answers from the textbook key. Authorship is recorded separately in provenance.
The original source-backed release remains reproducible and unchanged.

- Editable questions: `scripts/grammar/engmate-practice.json`.
- Reproducible export: `docs/grammar/engmate-practice-firestore.json`.
- Build/dry-run: `python3 scripts/grammar/supplement_practice.py`.
- Publish: `python3 scripts/grammar/supplement_practice.py --execute --project engmate-572b6 --confirm-project engmate-572b6`.

Publication used one atomic batch: 54 new `questions` documents and 18
`grammar_catalog` updates to `contentVersion: 2`. Existing question IDs, lesson
bodies and learner history were preserved. The catalog version change makes the
Android client fetch the new questions when a cached lesson is reopened online.
The importer checks baseline documents, rejects collisions, uses update-time
preconditions, and verifies every changed document after commit. Identical reruns
perform no writes.

Verified on Firebase project `engmate-572b6`: 214 total question documents,
85 published questions across all 20 Brehe lessons (18 × 3, plus 12 and 19).
All 34 Python tests pass, including missing-parent rejection, immutable-question
collision checks, cache-version updates and repeat publication planning.

## Vietnamese lesson metadata published 2026-11-26

`localize_lessons.py` updates all 20 lesson titles and summaries to Vietnamese in
both `grammar_catalog` and `grammar_lessons`, with `contentVersion: 3` to refresh
existing device caches. Learning levels are EngMate editorial estimates for the
selected excerpts: A1 (6), A2 (7), B1 (6), B2 (1). The `levelAssignment` field records
the estimate, rationale and British Council reference links; these are not official
CEFR classifications of Brehe's book. Original titles and source provenance remain
available. Lesson bodies and all 85 questions are unchanged.

Rebuild/dry-run: `python3 scripts/grammar/localize_lessons.py`.
Export: `docs/grammar/vietnamese-lesson-metadata.json`.
Publish with `--execute --project engmate-572b6 --confirm-project engmate-572b6`.
All 40 documents were published atomically with update-time preconditions and
read back for exact verification. The original source and practice importers are
historical releases: they intentionally reject the newer metadata rather than
reverting it. Rerunning the localization publisher against the same version is a no-op.

## Full practice rounds published 2026-11-26

The app selects up to ten questions per grammar quiz. The initial three-question
supplement was expanded with 126 additional original multiple-choice exercises,
with Vietnamese prompts and explanations. All 20 imported lessons now support a
full round: 18 lessons have 10 questions each; the two source-exercise lessons
retain their 12 and 19 questions. Total: 211 questions for these lessons.

- Editable authoring: `scripts/grammar/engmate-practice-expanded.json`.
- Publisher: `scripts/grammar/expand_practice.py` (default: offline dry-run).
- Export: `docs/grammar/expanded-practice-firestore.json`.
- Publish: `python3 scripts/grammar/expand_practice.py --execute --project engmate-572b6 --confirm-project engmate-572b6`.

The atomic publication created 126 questions and advanced all 20 catalog and
20 lesson documents to contentVersion 4 (166 writes). Vietnamese titles, levels,
lesson text, existing questions and learner history were preserved. All target
and original question documents were read back and verified. A live collection
scan confirmed 340 total Firebase questions and published per-lesson counts of
18 × 10, 12 and 19 for the imported lessons. All 39 Python tests pass, including
full-round coverage, answer shape, balanced option positions, existing-question
preservation, conflict rejection and idempotent publishing. Earlier importers
remain historical releases and refuse to overwrite newer versions.
