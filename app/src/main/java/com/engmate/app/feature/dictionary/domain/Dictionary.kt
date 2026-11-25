package com.engmate.app.feature.dictionary.domain

import com.engmate.app.feature.vocabulary.domain.Word

sealed interface LookupResult {
    /** [word] has id 0 when it came from the API and was not saved. */
    data class Found(val word: Word) : LookupResult
    data object NotFound : LookupResult
    data object Offline : LookupResult
    data object Failed : LookupResult
}

interface DictionaryRepository {
    /**
     * The word from the device if known, otherwise from dictionaryapi.dev.
     * API hits are written to the device only when [save] is true (opening, studying or editing a word),
     * so words passed while typing a search are not stored.
     */
    suspend fun lookup(raw: String, save: Boolean = false): LookupResult

    companion object {
        /** Only plausible English entries go online: 2–50 letters, plus space, hyphen or apostrophe. */
        private val lookupable = Regex("^[a-z][a-z' -]{1,49}$")
        fun canLookup(key: String) = lookupable.matches(key)
    }
}
