package com.engmate.app.feature.dictionary.data

import com.engmate.app.feature.dictionary.data.remote.DictionaryApi
import com.engmate.app.feature.dictionary.data.remote.toCachedWord
import com.engmate.app.feature.dictionary.domain.DictionaryRepository
import com.engmate.app.feature.dictionary.domain.LookupResult
import com.engmate.app.feature.vocabulary.domain.CachedWord
import com.engmate.app.feature.vocabulary.domain.Word
import com.engmate.app.feature.vocabulary.domain.WordKey
import com.engmate.app.feature.vocabulary.domain.WordRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Not-yet-saved API result for [preview]. */
private sealed interface Fetched {
    data class Entry(val entry: CachedWord) : Fetched
    data class Miss(val result: LookupResult) : Fetched
}

class ApiDictionaryRepository @Inject constructor(
    private val api: DictionaryApi,
    private val words: WordRepository,
) : DictionaryRepository {
    // Words seen in search results but not opened yet: in memory only, newest kept.
    private val recent = object : LinkedHashMap<String, CachedWord>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedWord>) = size > MAX_RECENT
    }

    override suspend fun preview(raw: String): LookupResult {
        val key = WordKey.of(raw)
        words.observe(key).first()?.let { return LookupResult.Found(it) }
        return when (val fetched = fetch(key)) {
            is Fetched.Entry -> LookupResult.Found(fetched.entry.toUnsavedWord())
            is Fetched.Miss -> fetched.result
        }
    }

    override suspend fun lookup(raw: String, save: Boolean): LookupResult {

        val key = WordKey.of(raw)
        words.observe(key).first()?.let { return LookupResult.Found(it) }
        return when (val fetched = fetch(key)) {
            is Fetched.Miss -> fetched.result
            is Fetched.Entry -> try {
                words.cache(fetched.entry)
                synchronized(recent) { recent.remove(key) }
                words.observe(fetched.entry.word).first()?.let { LookupResult.Found(it) } ?: LookupResult.Failed
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { LookupResult.Failed }
        }
    }

    /** From memory if this word was previewed, otherwise from the API. Nothing is written to the database. */
    private suspend fun fetch(key: String): Fetched {
        if (!DictionaryRepository.canLookup(key)) return Fetched.Miss(LookupResult.NotFound)
        synchronized(recent) { recent[key] }?.let { return Fetched.Entry(it) }
        return try {
            val response = api.entries(key)
            when {
                response.code() == 404 -> Fetched.Miss(LookupResult.NotFound)
                !response.isSuccessful -> Fetched.Miss(LookupResult.Failed)
                else -> {
                    val entry = response.body().orEmpty().toCachedWord() ?: return LookupResult.NotFound
                    if (!save) return LookupResult.Found(entry.unsaved())
                    words.cache(entry)
                    words.observe(entry.word).first()?.let { LookupResult.Found(it) } ?: LookupResult.Failed
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (_: IOException) { Fetched.Miss(LookupResult.Offline)
        } catch (_: Exception) { Fetched.Miss(LookupResult.Failed) }
    }


    private fun CachedWord.toUnsavedWord() = Word(
        id = DictionaryRepository.UNSAVED_ID, topicId = null, word = WordKey.of(word), phonetic = phonetic,
        partOfSpeech = partOfSpeech, meaningEn = meaningEn, meaningVi = null, exampleSentence = exampleSentence,
        exampleMeaning = null, audioUrl = audioUrl, collocations = emptyList(), idioms = emptyList(), isCached = false,
    )

    private companion object { const val MAX_RECENT = 50 }

