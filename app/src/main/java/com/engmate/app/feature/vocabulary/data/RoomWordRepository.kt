package com.engmate.app.feature.vocabulary.data

import com.engmate.app.feature.vocabulary.data.local.VocabularyDao
import com.engmate.app.feature.vocabulary.data.local.WordEntity
import com.engmate.app.feature.vocabulary.domain.*
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

fun WordEntity.toDomain() = Word(id, topicId, word, phonetic, partOfSpeech, meaningEn, meaningVi, exampleSentence, exampleMeaning,
    audioUrl, collocations, idioms, isCached)

class RoomWordRepository @Inject constructor(
    private val dao: VocabularyDao, private val clock: Clock,
    private val translator: VietnameseTranslator? = null,
) : WordRepository {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** Fills the Vietnamese meaning in the background so a lookup never waits for the translation model. */
    private fun translateLater(key: String) {
        val translator = translator ?: return
        scope.launch {
            val vi = translator.translate(key) ?: return@launch
            dao.findWord(key)?.takeIf { it.meaningVi == null }?.let { dao.updateWord(it.copy(meaningVi = vi)) }
        }
    }

    override fun observeTopics(): Flow<List<Topic>> = dao.observeTopics().map { rows ->
        rows.map { Topic(it.topic.id, it.topic.title, it.topic.description, it.topic.category, it.totalWords) }
    }
    override fun observeTopicWords(topicId: Long): Flow<List<Word>> = dao.observeWords(topicId).map { list -> list.map { it.toDomain() } }
    override fun observe(word: String): Flow<Word?> = dao.observeWord(WordKey.of(word)).map { it?.toDomain() }

    override suspend fun suggest(prefix: String, limit: Int): List<String> {
        val key = WordKey.of(prefix)
        return if (key.isEmpty()) emptyList() else dao.wordsStartingWith(key, limit)
    }

    override suspend fun search(query: String, limit: Int): List<Word> {
        val key = WordKey.of(query)
        if (key.isEmpty()) return emptyList()
        return dao.search(key, limit).map { it.toDomain() }.sortedWith(compareBy({ !it.word.startsWith(key) }, { it.word.length }))
    }

    override suspend fun cache(entry: CachedWord) {
        val key = WordKey.of(entry.word)
        require(key.isNotEmpty() && entry.meaningEn.isNotBlank()) { "Từ và định nghĩa không được để trống" }
        val current = dao.findWord(key)
        if (current == null) {
            dao.insertWord(WordEntity(word = key, phonetic = entry.phonetic, partOfSpeech = entry.partOfSpeech,
                meaningEn = entry.meaningEn.trim(), exampleSentence = entry.exampleSentence, audioUrl = entry.audioUrl,
                isCached = true, createdAt = clock.millis()))
            translateLater(key)
        } else {
            // Seed words keep their curated fields; the API only fills gaps.
            dao.updateWord(current.copy(phonetic = current.phonetic ?: entry.phonetic, partOfSpeech = current.partOfSpeech ?: entry.partOfSpeech,
                exampleSentence = current.exampleSentence ?: entry.exampleSentence, audioUrl = current.audioUrl ?: entry.audioUrl,
                isCached = true))
            if (current.meaningVi == null) translateLater(key)
        }
    }
}
