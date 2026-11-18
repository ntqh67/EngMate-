package com.engmate.app.feature.dictionary.data

import com.engmate.app.feature.dictionary.data.remote.DictionaryApi
import com.engmate.app.feature.dictionary.data.remote.toCachedWord
import com.engmate.app.feature.dictionary.domain.DictionaryRepository
import com.engmate.app.feature.dictionary.domain.LookupResult
import com.engmate.app.feature.vocabulary.domain.WordKey
import com.engmate.app.feature.vocabulary.domain.WordRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ApiDictionaryRepository @Inject constructor(
    private val api: DictionaryApi,
    private val words: WordRepository,
) : DictionaryRepository {
    override suspend fun lookup(raw: String): LookupResult {
        val key = WordKey.of(raw)
        words.observe(key).first()?.let { return LookupResult.Found(it) }
        if (!DictionaryRepository.canLookup(key)) return LookupResult.NotFound
        return try {
            val response = api.entries(key)
            when {
                response.code() == 404 -> LookupResult.NotFound
                !response.isSuccessful -> LookupResult.Failed
                else -> {
                    val entry = response.body().orEmpty().toCachedWord() ?: return LookupResult.NotFound
                    words.cache(entry)
                    words.observe(entry.word).first()?.let { LookupResult.Found(it) } ?: LookupResult.Failed
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (_: IOException) { LookupResult.Offline
        } catch (_: Exception) { LookupResult.Failed }
    }
}
