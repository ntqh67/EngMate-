package com.engmate.app.feature.flashcard.presentation.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.BackTopBar
import com.engmate.app.core.ui.components.EmptyState
import com.engmate.app.core.ui.components.MessageEffect
import com.engmate.app.core.ui.components.rememberPronouncer
import com.engmate.app.feature.flashcard.domain.CardFace

private val line = Color(0xFFDDE4EB)
private val back = Color(0xFFE1F0FF)
private val ok = Color(0xFF1E8450)
private val okBg = Color(0xFFE6F4EC)
private val mangoBg = Color(0xFFFFE1D1)

@Composable
fun FlashcardScreen(viewModel: FlashcardViewModel, handbookId: Long, onClose: () -> Unit) {
    LaunchedEffect(handbookId) { viewModel.start(handbookId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar, viewModel::messageShown)
    val s = state.session
    Scaffold(topBar = {
        BackTopBar(s.title.ifEmpty { "Flashcard" }, onClose) {
            if (!s.finished && s.cards.size > 1) TextButton(onClick = viewModel::shuffle) { Text("Xáo trộn") }
        }
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                s.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.cards.isEmpty() -> EmptyState("Sổ tay còn trống", "Thêm từ vào sổ tay rồi quay lại học flashcard.",
                    action = { OutlinedButton(onClick = onClose) { Text("Về Sổ tay") } })
                s.finished -> Finished(s, viewModel, onClose)
                else -> Study(state, viewModel)
            }
        }
    }
}

@Composable
private fun Study(state: FlashcardState, viewModel: FlashcardViewModel) {
    val s = state.session
    val pronounce = rememberPronouncer()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator({ (s.index + 1f) / s.cards.size }, Modifier.weight(1f))
            Text("${s.index + 1}/${s.cards.size}", style = MaterialTheme.typography.labelMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("✓ ${s.knownCount} đã thuộc", okBg, ok)
            Tag("${s.cards.size - s.knownCount} chưa thuộc", Color(0xFFEBF1F8), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth().weight(1f).clickable(onClick = viewModel::flip).testTag("flashcard"), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (s.flipped) back else Color.White), border = BorderStroke(1.dp, line)) {
            val word = state.word
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    word == null -> Text("Từ này không còn trong máy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    !s.flipped -> {
                        val front = CardFace.front(word, state.note)
                        Label("Định nghĩa"); Text(front.definition, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        front.example?.let { ex ->
                            Label("Ví dụ minh họa")
                            Column(Modifier.fillMaxWidth().background(Color(0xFFF0F8FF), RoundedCornerShape(10.dp)).padding(12.dp)) {
                                Text("“${ex.en}”", fontWeight = FontWeight.Medium)
                                if (ex.vi.isNotBlank()) Text(ex.vi, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (front.collocations.isNotEmpty()) {
                            Label("Cụm từ đi kèm")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                front.collocations.forEach { Tag(it, back, MaterialTheme.colorScheme.primary) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("Đoán từ rồi chạm để lật thẻ", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            word.partOfSpeech?.let { Tag(it.replaceFirstChar(Char::uppercase), Color.White, MaterialTheme.colorScheme.primary) }
                            Text(word.word, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                            word.phonetic?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            FilledIconButton(onClick = { pronounce(word.word, word.audioUrl) }, Modifier.padding(top = 12.dp).size(52.dp)) {
                                Icon(painterResource(R.drawable.ic_volume), "Nghe phát âm")
                            }
                        }
                        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Label("Ghi chú của tôi")
                            Text(state.note?.personalNote ?: "Chưa có ghi chú · thêm trong Chi tiết từ vựng",
                                color = if (state.note?.personalNote == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedIconButton(onClick = viewModel::previous, enabled = s.index > 0) { Text("‹") }
            FilledTonalButton(onClick = viewModel::flip, Modifier.weight(1f)) { Text("Lật thẻ") }
            FilledIconButton(onClick = viewModel::next) { Text("›") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { viewModel.mark(false) }, Modifier.weight(1f).height(48.dp)) { Text("Chưa thuộc") }
            Button(onClick = { viewModel.mark(true) }, Modifier.weight(1f).height(48.dp)) { Text("✓ Đã thuộc") }
        }
    }
}

@Composable
private fun Finished(s: Session, viewModel: FlashcardViewModel, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 24.dp).size(72.dp).background(okBg, CircleShape), Alignment.Center) { Text("✓", color = ok, style = MaterialTheme.typography.headlineMedium) }
        Text("Hoàn thành bộ thẻ!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Bạn đã ôn ${s.cards.size} thẻ trong “${s.title}”.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(s.knownCount, "Đã thuộc", okBg, ok, Modifier.weight(1f))
            Stat(s.unknown.size, "Chưa thuộc", mangoBg, Color(0xFF803D09), Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        if (s.unknown.isNotEmpty()) Button(onClick = { viewModel.restart(onlyUnknown = true) }, Modifier.fillMaxWidth().height(48.dp)) { Text("Học lại thẻ chưa thuộc") }
        OutlinedButton(onClick = { viewModel.restart(onlyUnknown = false) }, Modifier.fillMaxWidth().height(48.dp)) { Text("Làm lại từ đầu") }
        TextButton(onClick = onClose) { Text("Về Sổ tay") }
    }
}

@Composable private fun Stat(value: Int, label: String, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.background(bg, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, color = fg, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun Label(text: String) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable private fun Tag(text: String, bg: Color, fg: Color) =
    Text(text, Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = fg)
