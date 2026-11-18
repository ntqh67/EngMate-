package com.engmate.app.feature.dictionary.data.remote

import com.engmate.app.feature.vocabulary.domain.CachedWord

/**
 * Turns the API response into the summary row stored in `words`.
 * One word can come back as several entries (e.g. "bank" has 4); they are read in order as one.
 * Returns null when no entry has a usable definition.
 */
fun List<EntryDto>.toCachedWord(): CachedWord? {
    val word = firstOrNull()?.word?.takeIf { it.isNotBlank() } ?: return null
    val meanings = flatMap { it.meanings.orEmpty() }
    val definitions = meanings.flatMap { m -> m.definitions.orEmpty().map { m.partOfSpeech to it } }
        .filter { !it.second.definition.isNullOrBlank() }
    val (partOfSpeech, first) = definitions.firstOrNull() ?: return null
    val phonetics = flatMap { it.phonetics.orEmpty() }
    return CachedWord(
        word = word,
        meaningEn = first.definition!!.trim(),
        partOfSpeech = partOfSpeech?.takeIf { it.isNotBlank() },
        // Top-level "phonetic" is often missing; fall back to the first phonetics[] entry with text.
        phonetic = (mapNotNull { it.phonetic } + phonetics.mapNotNull { it.text }).firstOrNull { it.isNotBlank() },
        audioUrl = phonetics.mapNotNull { it.audio }.firstOrNull { it.isNotBlank() }?.let { if (it.startsWith("//")) "https:$it" else it },
        exampleSentence = definitions.mapNotNull { it.second.example }.firstOrNull { it.isNotBlank() },
    )
}
