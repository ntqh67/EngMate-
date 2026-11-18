package com.engmate.app.feature.dictionary.domain

import com.engmate.app.feature.vocabulary.domain.Word

sealed interface LookupResult {
    data class Found(val word: Word) : LookupResult
    data object NotFound : LookupResult
    data object Offline : LookupResult
    data object Failed : LookupResult
}

interface DictionaryRepository {
    /** The word from the device if known, otherwise from dictionaryapi.dev (then saved on the device). */
    suspend fun lookup(raw: String): LookupResult

    companion object {
        /** Only plausible English entries go online: letters plus space, hyphen or apostrophe, max 50 chars. */
        private val lookupable = Regex("^[a-z][a-z' -]{0,49}$")
        fun canLookup(key: String) = lookupable.matches(key)
    }
}
