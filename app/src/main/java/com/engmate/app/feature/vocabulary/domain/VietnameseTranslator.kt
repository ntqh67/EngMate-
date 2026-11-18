package com.engmate.app.feature.vocabulary.domain

/** Translates English text to Vietnamese. Returns null when it cannot (model not downloaded yet, offline, unsupported). */
interface VietnameseTranslator {
    suspend fun translate(text: String): String?
}
