package com.engmate.app.feature.pronunciation.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.engmate.app.core.ui.components.rememberPronouncer
import com.engmate.app.feature.pronunciation.domain.PronunciationResult
import com.engmate.app.feature.pronunciation.domain.PronunciationScorer

private sealed interface Phase {
    data object Idle : Phase
    data object Listening : Phase
    data class Done(val heard: String, val result: PronunciationResult) : Phase
    data class Failed(val message: String) : Phase
}

private val good = Color(0xFF2E7D32)
private val bad = Color(0xFFC62828)

/**
 * Listen-and-repeat practice: plays the model sentence with text-to-speech, records the learner with Android's speech recognizer
 * (English), then scores the words that were heard. [onScored] receives each attempt's score (0–100).
 */
@Composable
fun PronunciationDialog(target: String, onDismiss: () -> Unit, onScored: (Int) -> Unit = {}) {
    val context = LocalContext.current
    val pronounce = rememberPronouncer()
    var phase by remember { mutableStateOf<Phase>(Phase.Idle) }
    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null }
    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }

    fun start() {
        val engine = recognizer ?: run { phase = Phase.Failed("Thiết bị chưa hỗ trợ nhận dạng giọng nói. Hãy cài hoặc bật ứng dụng Google."); return }
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val best = PronunciationScorer.best(target, list)
                if (best == null) phase = Phase.Failed("Không nghe rõ, hãy thử lại.")
                else { phase = Phase.Done(best.first, best.second); onScored(best.second.score) }
            }
            override fun onError(error: Int) {
                phase = Phase.Failed(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe thấy giọng nói, hãy nói gần micro hơn và thử lại."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Nhận dạng giọng nói cần kết nối mạng. Hãy kiểm tra Internet."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Cần cấp quyền micro để ghi âm."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Bộ nhận dạng đang bận, hãy thử lại sau giây lát."
                    else -> "Không ghi âm được (mã lỗi $error). Hãy thử lại."
                })
            }
            override fun onReadyForSpeech(params: Bundle?) { phase = Phase.Listening }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        phase = Phase.Listening
        engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        })
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else phase = Phase.Failed("Bạn cần cho phép dùng micro để luyện phát âm.")
    }
    fun record() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    AlertDialog(
        onDismissRequest = { recognizer?.cancel(); onDismiss() },
        title = { Text("Luyện phát âm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(target, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { pronounce(target, null) }) { Text("Nghe câu mẫu") }
                when (val p = phase) {
                    Phase.Idle -> Text("Bấm Ghi âm rồi đọc to câu trên.", style = MaterialTheme.typography.bodyMedium)
                    Phase.Listening -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Đang nghe… hãy đọc câu trên")
                    }
                    is Phase.Failed -> Text(p.message, color = MaterialTheme.colorScheme.error)
                    is Phase.Done -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${p.result.score}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            color = if (p.result.score >= 80) good else if (p.result.score >= 50) MaterialTheme.colorScheme.onSurface else bad)
                        Text(when { p.result.score >= 80 -> "Rất tốt!"; p.result.score >= 50 -> "Khá ổn, hãy luyện thêm các từ màu đỏ."; else -> "Hãy nghe câu mẫu và thử lại." })
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.result.words.forEach { Text(it.word, color = if (it.matched) good else bad, fontWeight = FontWeight.SemiBold) }
                        }
                        Text("Bạn đã đọc: “${p.heard}”", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = ::record, enabled = phase != Phase.Listening) { Text(if (phase is Phase.Done || phase is Phase.Failed) "Ghi âm lại" else "Ghi âm") } },
        dismissButton = { TextButton(onClick = { recognizer?.cancel(); onDismiss() }) { Text("Đóng") } },
    )
}
