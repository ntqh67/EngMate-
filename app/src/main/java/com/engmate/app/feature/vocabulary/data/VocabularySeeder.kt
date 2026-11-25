package com.engmate.app.feature.vocabulary.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.feature.vocabulary.data.local.TopicEntity
import com.engmate.app.feature.vocabulary.data.local.VocabularyDao
import com.engmate.app.feature.vocabulary.data.local.WordEntity
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Loads the starter vocabulary (assets/vocabulary_seed.json: topics, each with words that have IPA, part of speech,
 * Vietnamese and English meanings and an example) into Room. Safe to run on every launch: topics are matched by title,
 * words by their text. A word the user already looked up through the dictionary API keeps its data and only gains the
 * missing Vietnamese meaning, topic and example.
 */
@Singleton
class VocabularySeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: EngMateDatabase,
    private val dao: VocabularyDao,
    private val clock: Clock,
) {
    private data class SeedFile(val topics: List<SeedTopic> = emptyList())
    private data class SeedTopic(val title: String = "", val level: String = "", val category: String? = null, val words: List<SeedWord> = emptyList())
    private data class SeedWord(val word: String = "", val phonetic: String? = null, val pos: String? = null, val vi: String? = null,
                                val en: String = "", val example: String? = null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun seedInBackground() {
        scope.launch { runCatching { seed() }.onFailure { Log.w("VocabularySeeder", "Seeding failed", it) } }
    }

    suspend fun seed() {
        val file = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { Gson().fromJson(it, SeedFile::class.java) }
        val now = clock.millis()
        database.withTransaction {
            for (topic in file.topics) {
                val topicId = (dao.findTopic(topic.title) ?: TopicEntity(title = topic.title, description = topic.level, category = topic.category)
                    .let { it.copy(id = dao.insertTopic(it)) }).id
                for (w in topic.words) {
                    val key = w.word.trim().lowercase()
                    if (key.isEmpty() || w.en.isBlank()) continue
                    val current = dao.findWord(key)
                    if (current == null) {
                        dao.insertWord(WordEntity(topicId = topicId, word = key, phonetic = w.phonetic, partOfSpeech = w.pos, meaningEn = w.en,
                            meaningVi = w.vi, exampleSentence = w.example, createdAt = now))
                    } else if (current.meaningVi == null || current.topicId == null) {
                        dao.updateWord(current.copy(topicId = current.topicId ?: topicId, meaningVi = current.meaningVi ?: w.vi,
                            phonetic = current.phonetic ?: w.phonetic, partOfSpeech = current.partOfSpeech ?: w.pos,
                            exampleSentence = current.exampleSentence ?: w.example))
                    }
                }
            }
        }
    }

    private companion object { const val ASSET = "vocabulary_seed.json" }
}
