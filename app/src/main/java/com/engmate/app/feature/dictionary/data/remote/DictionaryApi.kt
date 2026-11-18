package com.engmate.app.feature.dictionary.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** https://dictionaryapi.dev — free, English only, exact-word lookups; 404 means "no definitions". */
interface DictionaryApi {
    @GET("api/v2/entries/en/{word}")
    suspend fun entries(@Path("word") word: String): Response<List<EntryDto>>

    companion object { const val BASE_URL = "https://api.dictionaryapi.dev/" }
}

// The live API differs from its docs sample (fields missing, examples optional), so everything except word is nullable.
data class EntryDto(
    val word: String,
    val phonetic: String? = null,
    val phonetics: List<PhoneticDto>? = null,
    val meanings: List<MeaningDto>? = null,
)

data class PhoneticDto(val text: String? = null, val audio: String? = null)

data class MeaningDto(
    val partOfSpeech: String? = null,
    val definitions: List<DefinitionDto>? = null,
    val synonyms: List<String>? = null,
    val antonyms: List<String>? = null,
)

data class DefinitionDto(val definition: String? = null, val example: String? = null)
