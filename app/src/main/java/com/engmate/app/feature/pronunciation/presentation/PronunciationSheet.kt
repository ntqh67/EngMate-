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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.*
import com.engmate.app.feature.pronunciation.domain.PronunciationResult
import com.engmate.app.feature.pronunciation.domain.PronunciationScorer

private sealed interface Phase {
    data object Idle : Phase
    data object Listening : Phase
    data class Done(val heard: String, val result: PronunciationResult) : Phase
    data class Failed(val title: String, val help: String) : Phase
}

/**
 * Listen-and-repeat practice in a bottom sheet: plays the model with text-to-speech, records the learner with Android's speech
 * recognizer (English), then scores word by word. [onScored] receives each attempt's score (0–100).
 */
@Composable
fun PronunciationSheet(target: String, phonetic: String?, onDismiss: () -> Unit, onScored: (Int) -> Unit = {}) {
    val context = LocalContext.current
    val pronounce = rememberPronouncer()
    var phase by remember { mutableStateOf<Phase>(Phase.Idle) }
    var level by remember { mutableFloatStateOf(0f) }
    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null }
    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }

    fun start() {
        val engine = recognizer ?: run {
            phase = Phase.Failed("Máy chưa hỗ trợ nhận dạng giọng nói", "Hãy cài hoặc bật ứng dụng Google rồi thử lại."); return
        }
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val best = PronunciationScorer.best(target, list)
                if (best == null) phase = Phase.Failed("Không nghe rõ", "Nói chậm và rõ hơn một chút rồi thử lại.")
                else { phase = Phase.Done(best.first, best.second); onScored(best.second.score) }
            }
            override fun onError(error: Int) {
                phase = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Phase.Failed("Không nghe thấy giọng nói",
                        "Nói gần micro hơn ngay sau khi bấm ghi âm. Trên máy ảo, hãy bật micro của máy tính trong Extended controls → Microphone.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Phase.Failed("Cần kết nối Internet",
                        "Nhận dạng giọng nói đang cần mạng. Hãy kiểm tra Internet rồi thử lại.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Phase.Failed("Chưa có quyền micro", "Cho phép EngMate dùng micro để ghi âm.")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Phase.Failed("Bộ nhận dạng đang bận", "Đợi vài giây rồi thử lại.")
                    else -> Phase.Failed("Không ghi âm được", "Đã có lỗi (mã $error). Hãy thử lại.")
                }
            }
            override fun onReadyForSpeech(params: Bundle?) { phase = Phase.Listening }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { level = 0f }
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
        if (granted) start() else phase = Phase.Failed("Chưa có quyền micro", "Bạn cần cho phép dùng micro để luyện phát âm.")
    }
    fun record() {
        if (phase == Phase.Listening) { recognizer?.stopListening(); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    AppBottomSheet("Luyện phát âm", { recognizer?.cancel(); onDismiss() }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(target, style = EngMateTheme.type.vocabulary, textAlign = TextAlign.Center)
            phonetic?.let { IpaText(it) }
            FilledTonalButton(onClick = { pronounce(target, null) }, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Icon(painterResource(R.drawable.ic_volume), null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.s)); Text("Nghe mẫu")
            }
        }
        Box(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
            when (val p = phase) {
                is Phase.Done -> ResultView(p)
                is Phase.Failed -> FeedbackBanner(p.help, kind = FeedbackKind.ERROR, title = p.title)
                else -> Spacer(Modifier.height(Spacing.s))
            }
        }
        MicButton(phase == Phase.Listening, level, ::record)
        Text(
            when (phase) {
                Phase.Listening -> "Đang nghe… chạm để dừng"
                is Phase.Done, is Phase.Failed -> "Chạm để ghi âm lại"
                Phase.Idle -> "Chạm để ghi âm rồi đọc to từ trên"
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
    }
}

/** Big round mic; while listening it turns red and pulses with the voice level. */
@Composable
private fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val reduce = rememberReduceMotion()
    val pulse by animateFloatAsState(if (listening && !reduce) 1f + level * 0.35f else 1f, tween(120), label = "pulse")
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        if (listening) Box(Modifier.size(96.dp).scale(pulse).background(c.error.copy(alpha = 0.18f), CircleShape))
        FilledIconButton(onClick = onClick, modifier = Modifier.size(76.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (listening) c.error else c.primary,
                contentColor = if (listening) c.onError else c.onPrimary)) {
            Icon(painterResource(if (listening) R.drawable.ic_stop else R.drawable.ic_mic), if (listening) "Dừng ghi âm" else "Ghi âm", Modifier.size(32.dp))
        }
    }
}

@Composable
private fun ResultView(p: Phase.Done) {
    val c = MaterialTheme.colorScheme
    val score = p.result.score
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            ProgressRing(score / 100f, size = 72.dp, color = when { score >= 80 -> c.success; score >= 50 -> c.primary; else -> c.error }) {
                Text("$score", style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(when { score >= 80 -> "Rất tốt!"; score >= 50 -> "Khá ổn"; else -> "Thử lại nhé" }, style = MaterialTheme.typography.titleLarge)
                Text(when {
                    score >= 80 -> "Phát âm rõ ràng."
                    p.result.extra.isNotEmpty() -> "Có từ thừa hoặc lặp lại; đọc đúng một lần."
                    else -> "Nghe mẫu rồi luyện thêm các từ được đánh dấu."
                }, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            p.result.words.forEach { w ->
                val (bg, fg, icon) = if (w.matched) Triple(c.successContainer, c.onSuccessContainer, R.drawable.ic_check)
                    else Triple(c.errorContainer, c.onErrorContainer, R.drawable.ic_close)
                Row(Modifier.background(bg, MaterialTheme.shapes.small).padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(icon), if (w.matched) "đúng" else "chưa đúng", Modifier.size(14.dp), tint = fg)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(w.word, style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
            p.result.extra.forEach { w ->
                Text("$w (thừa)", Modifier.background(c.surfaceContainerHigh, MaterialTheme.shapes.small).padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            }
        }
        Text("Bạn đã đọc: “${p.heard}”", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
    }
}
