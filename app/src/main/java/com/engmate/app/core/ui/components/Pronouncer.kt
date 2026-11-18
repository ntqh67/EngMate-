package com.engmate.app.core.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Plays a word's pronunciation: the dictionary's mp3 when there is one, Android text-to-speech otherwise
 * (or when the mp3 cannot be played, e.g. offline).
 */
@Composable
fun rememberPronouncer(): (word: String, audioUrl: String?) -> Unit {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) { engine?.language = Locale.US; tts = engine } }
        onDispose { engine?.shutdown(); player.release() }
    }
    return remember(tts) {
        { word, url ->
            val speak = { tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, word); Unit }
            if (url.isNullOrBlank()) speak() else runCatching {
                player.reset()
                player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                player.setDataSource(url)
                player.setOnPreparedListener { it.start() }
                player.setOnErrorListener { _, _, _ -> speak(); true }
                player.prepareAsync()
            }.onFailure { speak() }
        }
    }
}
