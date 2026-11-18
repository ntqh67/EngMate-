package com.engmate.app.feature.vocabulary.data

import com.engmate.app.feature.vocabulary.domain.VietnameseTranslator
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Google ML Kit on-device translation. The ~30 MB language model downloads on first use (needs network), then works offline. */
@Singleton
class MlKitVietnameseTranslator @Inject constructor() : VietnameseTranslator {
    private val translator by lazy {
        Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.VIETNAMESE).build())
    }

    override suspend fun translate(text: String): String? = try {
        withTimeoutOrNull(TIMEOUT_MS) {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            translator.translate(text).await().trim().takeIf { it.isNotEmpty() }
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnCompleteListener { t ->
            if (!c.isActive) return@addOnCompleteListener
            if (t.isSuccessful) c.resume(t.result) else c.resumeWithException(t.exception ?: IllegalStateException("Translate failed"))
        }
    }

    private companion object { const val TIMEOUT_MS = 45_000L }
}
