package com.engmate.app.feature.notebook.data

import com.engmate.app.core.database.WordLearningStatus
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.flashcard.data.local.FlashcardCollectionEntity
import com.engmate.app.feature.flashcard.data.local.FlashcardDao
import com.engmate.app.feature.flashcard.data.local.FlashcardEntity
import com.engmate.app.feature.notebook.data.local.NotebookDao
import com.engmate.app.feature.notebook.domain.*
import com.engmate.app.feature.vocabulary.data.local.VocabularyDao
import com.engmate.app.feature.vocabulary.domain.WordKey
import com.engmate.app.feature.vocabulary.domain.WordMissing
import com.engmate.app.feature.vocabulary.domain.requireOwner
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Handbooks are flashcard_collections; each word in one is a flashcards row. */
class RoomHandbookRepository @Inject constructor(
    private val dao: FlashcardDao,
    private val words: VocabularyDao,
    private val notes: NotebookDao,
    private val session: UserSession,
    private val clock: Clock,
) : HandbookRepository {
    override fun observeHandbooks(uid: String): Flow<List<Handbook>> {
        session.requireOwner(uid)
        return dao.observeCollectionsWithCounts(uid).map { rows ->
            rows.map { Handbook(it.collection.id, it.collection.title, it.collection.description, it.wordCount, it.knownCount, it.collection.createdAt) }
        }
    }

    override fun observeEntries(uid: String, handbookId: Long): Flow<List<HandbookEntry>> {
        session.requireOwner(uid)
        return dao.observeCardsWithWordData(uid, handbookId).map { rows ->
            rows.map { HandbookEntry(it.card.id, it.card.word, it.card.meaning, it.meaningVi, it.customMeaning, it.status ?: WordLearningStatus.NEW) }
        }
    }

    override fun observeHandbooksContaining(uid: String, word: String): Flow<Set<Long>> {
        session.requireOwner(uid)
        return dao.observeCollectionIdsWithWord(uid, WordKey.of(word)).map { it.toSet() }
    }

    // shortcut: uniqueness is checked here, not by a DB index (none on main yet); a v4 unique (user_id, title) would close the race.
    private suspend fun validTitle(uid: String, title: String, exceptId: Long = 0): String {
        val taken = dao.collectionsOf(uid).filter { it.id != exceptId }.map { it.title }
        NotebookRules.titleError(title, taken)?.let { throw InvalidHandbook(it) }
        return title.trim()
    }

    private suspend fun owned(uid: String, handbookId: Long) = dao.getCollection(uid, handbookId) ?: throw HandbookMissing()

    override suspend fun create(uid: String, title: String, description: String): Long {
        session.requireOwner(uid)
        val value = validTitle(uid, title)
        return dao.insertCollection(FlashcardCollectionEntity(userUid = uid, title = value,
            description = description.trim().ifEmpty { null }, createdAt = clock.millis()))
    }

    override suspend fun rename(uid: String, handbookId: Long, title: String) {
        session.requireOwner(uid)
        val current = owned(uid, handbookId)
        val value = validTitle(uid, title, exceptId = handbookId)
        if (dao.updateCollection(uid, handbookId, value, current.description) == 0) throw HandbookMissing()
    }

    override suspend fun delete(uid: String, handbookId: Long) {
        session.requireOwner(uid)
        if (dao.deleteCollection(uid, handbookId) == 0) throw HandbookMissing()
    }

    override suspend fun addWord(uid: String, handbookId: Long, word: String) {
        session.requireOwner(uid)
        owned(uid, handbookId)
        val entry = words.findWord(WordKey.of(word)) ?: throw WordMissing()
        if (dao.cardsWithWord(handbookId, entry.word).isNotEmpty()) return
        val note = notes.getWordNote(uid, entry.id)
        // The card keeps a snapshot (ERD shape); screens show the live meaning via HandbookEntry.displayMeaning.
        dao.addCard(uid, FlashcardEntity(collectionId = handbookId, word = entry.word,
            meaning = note?.customMeaning ?: entry.meaningVi ?: entry.meaningEn, example = entry.exampleSentence, phonetic = entry.phonetic))
    }

    override suspend fun removeWord(uid: String, handbookId: Long, word: String) {
        session.requireOwner(uid)
        owned(uid, handbookId)
        dao.cardsWithWord(handbookId, WordKey.of(word)).forEach { dao.deleteCard(uid, it.id) }
    }
}
