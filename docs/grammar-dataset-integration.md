# Grammar content sources and Firestore loading

Checked 2026-11-25. Brehe content is handled as published source material under CC BY-SA 4.0. No AI API is used. No production Firebase writes were made.

## Current storage design

- The 17 baseline lessons remain unchanged: 14 in `app/src/main/assets/grammar_seed.json` plus three from `GrammarQuizSeed.Callback`.
- The APK does not bundle the new Brehe dataset. `docs/grammar/brehe-firestore.json` contains 20 lessons and 31 supported questions (51 full-content documents); the importer additionally creates 20 lightweight catalog rows. There are 71 Firestore writes in total so the client can list lesson metadata without downloading theory/examples. The release contains 63 English-only example blocks and source/license/conversion provenance. Full content and questions are lazy-cached on device.
- Firestore shared collections are `grammar_catalog/{stable-id}`, `grammar_lessons/{stable-id}`, and `questions/{stable-id}`. Catalog documents hold title/level/summary/order/contentVersion; lesson documents hold title/level/summary/content/examples/orderIndex; questions hold `lessonId`, `answerType`, question content, accepted answers/options and explanation. All carry publication/version/hash and provenance fields as applicable. A separate catalog is used because the Android SDK cannot project selected fields from a document query.
- On sign-in the client listens only to `grammar_catalog` with `published == true`. Metadata rows are cached in the existing Room `grammar_lessons` table; no Room schema or migration change is needed. On opening a new lesson, the client fetches that full lesson document and only published questions whose `lessonId` matches, then upserts them into Room. A per-lesson lock avoids duplicate concurrent downloads. A local version marker avoids repeat downloads until `contentVersion` changes. Room remains the UI source and is available offline; absent cloud data never erases local lessons, questions, attempts, or answer snapshots.
- Quiz attempts and progress remain user-specific in Room and under `users/{uid}` in Firestore. Shared grammar content is never written by learner devices. Firestore rules require authentication and `published == true` for learner reads; only a user with the custom `admin: true` claim can write shared content.

The code and release contract show the intended collection schema, but this environment has no Firebase Admin ADC/project authorization, so the live project's existing documents could not be enumerated. The importer checks all target document IDs before writing and refuses conflicting content hashes; it never overwrites a conflicting document.

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

The rules emulator test is in `scripts/grammar/emulator/`. With Node.js/npm and Firebase CLI installed:

```bash
cd scripts/grammar/emulator
npm install --no-save firebase-tools @firebase/rules-unit-testing firebase
npx firebase emulators:exec --project demo-engmate --only firestore 'node --test rules.test.mjs'
```

The emulator suite was not run in this environment because Firebase CLI is unavailable. It checks published reads, rejected drafts, admin-only shared writes and UID-scoped user data.

## Import to a real Firebase project

1. In Firebase Console, inspect Firestore and confirm the `grammar_catalog`, `grammar_lessons`, and `questions` collections do not contain conflicting IDs. Configure Firestore Rules from `firestore.rules`, create a custom auth claim `admin: true` only for the trusted admin identity, and enable Application Default Credentials (ADC) for the project.
2. Configure the Android Firebase app locally with `app/google-services.json` (do not commit it). No API key or service-account secret is embedded in the scripts.
3. Run the dry-run above. Then, only after selecting and verifying the intended project, run:

```bash
GOOGLE_CLOUD_PROJECT=YOUR_PROJECT_ID \
  python3 scripts/grammar/import_firestore.py docs/grammar/brehe-firestore.json \
  --project YOUR_PROJECT_ID --execute --confirm-project YOUR_PROJECT_ID
```

The explicit `--confirm-project` is a guard against accidental production writes. The script preflights every document first, creates in batches of at most 400, uses create preconditions, skips identical existing hashes and stops on conflicting versions. It is safe to rerun after a partial batch commit. Production import has not been executed.

## Verification

Python tests:

```bash
python3 -m unittest discover -s scripts/grammar/tests -v
```

Android checks (from repository root, where the Gradle wrapper can write its distribution/cache and `app/google-services.json` is configured):

```bash
./gradlew :app:assembleLiveDebug :app:testLiveDebugUnitTest :app:lintLiveDebug
```

The Python suite passes (29 tests), the emulator test passes JavaScript syntax checking, and the importer dry-run validates 71 writes without initializing Firebase. Android checks could not start here: the wrapper cannot create its Gradle distribution lock under `/home/ntqh/.gradle` because that path is read-only; invoking the extracted Gradle directly also fails because this environment exposes no usable wildcard IP. On Ubuntu, run the command above from the repository with a writable Gradle home. No destructive Room migration was introduced. Existing schema version 3, the 17 baseline lessons, user progress and history remain intact.
