// Run ONLY with firebase emulators:exec; never targets a deployed project.
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, query, setDoc, where } from 'firebase/firestore';

if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080') {
  throw new Error('Run with the checked-in emulator config (127.0.0.1:8080).');
}
const env = await initializeTestEnvironment({
  projectId: 'demo-engmate',
  firestore: { host: '127.0.0.1', port: 8080,
    rules: await readFile(new URL('../../../firestore.rules', import.meta.url), 'utf8') },
});
try {
  await env.clearFirestore();
  const release = JSON.parse(await readFile(new URL('../../../docs/grammar/brehe-firestore.json', import.meta.url), 'utf8'));
  const admin = env.authenticatedContext('admin-user', { admin: true }).firestore();
  const learner = env.authenticatedContext('learner').firestore(); // no admin claim
  const stranger = env.unauthenticatedContext().firestore();
  for (const name of ['grammar_catalog', 'grammar_lessons', 'questions']) {
    const source = name === 'grammar_catalog'
      ? (() => {
          const lesson = release.grammar_lessons[0];
          return { id: lesson.id, title: lesson.title, level: lesson.level, summary: lesson.summary,
            orderIndex: lesson.orderIndex, contentVersion: release.contentVersion,
            sourceId: lesson.provenance.sourceId, source: lesson.provenance.source,
            author: lesson.provenance.author, license: lesson.provenance.license,
            licenseUrl: lesson.provenance.licenseUrl };
        })()
      : release[name][0];
    const { id, ...data } = source;
    const path = `${name}/${id}`;
    await assertSucceeds(setDoc(doc(admin, path), { ...data, published: true }));
    await assertSucceeds(setDoc(doc(admin, `${name}/draft`), { ...data, published: false }));
    await assertSucceeds(setDoc(doc(admin, `${name}/legacy`), data));
    await assertSucceeds(getDoc(doc(learner, path)));
    await assertFails(getDoc(doc(learner, `${name}/draft`)));
    await assertSucceeds(getDoc(doc(learner, `${name}/legacy`)));
    await assertFails(getDoc(doc(stranger, path)));
    await assertFails(setDoc(doc(learner, path), { ...data, published: true }));
    const result = await assertSucceeds(getDocs(query(collection(learner, name), where('published', '==', true))));
    assert.equal(result.size, 1); // Draft and legacy rows are deliberately excluded from new catalog queries.
  }
  await assertSucceeds(setDoc(doc(learner, 'users/learner/quiz_attempts/example'), { correctCount: 1 }));
  await assertSucceeds(getDoc(doc(learner, 'users/learner/quiz_attempts/example')));
  await assertFails(getDoc(doc(env.authenticatedContext('other').firestore(), 'users/learner/quiz_attempts/example')));
  await assertFails(setDoc(doc(learner, 'users/other/quiz_attempts/example'), { correctCount: 1 }));
  console.log('PASS: published content, admin writes, draft isolation, query constraints, UID history isolation');
} finally {
  await env.cleanup();
}
