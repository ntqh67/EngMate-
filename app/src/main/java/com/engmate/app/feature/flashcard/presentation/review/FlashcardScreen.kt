package com.engmate.app.feature.flashcard.presentation.review

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.*
import com.engmate.app.feature.flashcard.domain.CardFace

/** A flashcard round over a handbook, or over every due word when [handbookId] is [FlashcardViewModel.DUE]. */
@Composable
fun FlashcardScreen(viewModel: FlashcardViewModel, handbookId: Long, onClose: () -> Unit, onFindWords: () -> Unit = onClose) {
    LaunchedEffect(handbookId) { viewModel.start(handbookId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar, viewModel::messageShown)
    val s = state.session
    val due = s.handbookId == FlashcardViewModel.DUE
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), "Đóng") }
                if (!s.loading && !s.finished && s.cards.isNotEmpty()) {
                    LinearProgressIndicator(progress = { (s.index + 1f) / s.cards.size }, modifier = Modifier.weight(1f).height(8.dp),
                        gapSize = 0.dp, drawStopIndicator = {})
                    Text("${s.index + 1}/${s.cards.size}", style = MaterialTheme.typography.labelLarge)
                    Box {
                        TopBarAction(R.drawable.ic_more, "Tuỳ chọn", { menu = true })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Thẻ trước") }, enabled = s.index > 0, onClick = { menu = false; viewModel.previous() },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_arrow_back), null) })
                            DropdownMenuItem(text = { Text("Xáo trộn") }, enabled = s.cards.size > 1, onClick = { menu = false; viewModel.shuffle() },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_shuffle), null) })
                        }
                    }
                } else Text(s.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
        },
        bottomBar = {
            when {
                s.loading || s.cards.isEmpty() -> Unit
                s.finished -> StickyBottomBar {
                    if (s.unknown.isNotEmpty()) PrimaryButton("Học lại ${s.unknown.size} thẻ chưa thuộc", { viewModel.restart(onlyUnknown = true) }, Modifier.fillMaxWidth())
                    else PrimaryButton("Xong", onClose, Modifier.fillMaxWidth())
                    SecondaryButton("Làm lại từ đầu", { viewModel.restart(onlyUnknown = false) }, Modifier.fillMaxWidth())
                }
                !s.flipped -> StickyBottomBar { PrimaryButton("Lật thẻ", viewModel::flip, Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh) }
                else -> StickyBottomBar {
                    val c = MaterialTheme.colorScheme
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        PrimaryButton("Chưa thuộc", { viewModel.mark(false) }, Modifier.weight(1f), icon = R.drawable.ic_cancel,
                            colors = ButtonDefaults.buttonColors(containerColor = c.errorContainer, contentColor = c.onErrorContainer))
                        PrimaryButton("Đã thuộc", { viewModel.mark(true) }, Modifier.weight(1f), icon = R.drawable.ic_check_circle,
                            colors = ButtonDefaults.buttonColors(containerColor = c.successContainer, contentColor = c.onSuccessContainer))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                s.loading -> LoadingState(Modifier.padding(screenPadding), rows = 0)
                s.cards.isEmpty() && due -> EmptyState("Bạn đã ôn hết từ đến hạn", "Các từ sẽ quay lại đây đúng ngày cần ôn.", icon = R.drawable.ic_check_circle,
                    action = { PrimaryButton("Xong", onClose) })
                s.cards.isEmpty() -> EmptyState("Sổ tay còn trống", "Tra một từ rồi thêm vào sổ tay để học bằng flashcard.", icon = R.drawable.ic_notebook,
                    action = { PrimaryButton("Tra từ", onFindWords) })
                s.finished -> Finished(s)
                else -> Card(state, viewModel)
            }
        }
    }
}

@Composable
private fun Card(state: FlashcardState, viewModel: FlashcardViewModel) {
    val s = state.session
    val pronounce = rememberPronouncer()
    val reduce = rememberReduceMotion()
    val angle by animateFloatAsState(if (s.flipped) 180f else 0f, if (reduce) tween(0) else tween(300), label = "flip")
    val back = angle > 90f
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(screenPadding), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge, color = if (back) c.primaryContainer else c.surfaceContainerLow,
            modifier = Modifier.fillMaxSize().testTag("flashcard")
                .graphicsLayer { rotationY = angle; cameraDistance = 12f * density }
                .clickable(role = Role.Button, onClickLabel = if (s.flipped) "Xem mặt trước" else "Lật thẻ", onClick = viewModel::flip),
        ) {
            // The back is drawn mirrored so it reads normally after the card turns.
            Column(Modifier.fillMaxSize().graphicsLayer { rotationY = if (back) 180f else 0f }.verticalScroll(rememberScrollState()).padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                val word = state.word
                when {
                    word == null -> Text("Từ này không còn trong máy.", color = c.onSurfaceVariant)
                    !back -> {
                        val front = CardFace.front(word, state.note)
                        Text("Định nghĩa", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                        Text(front.definition, style = MaterialTheme.typography.headlineSmall)
                        front.example?.let { ex ->
                            Text("Ví dụ", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.s))
                            Column(Modifier.fillMaxWidth().background(c.surfaceContainer, MaterialTheme.shapes.medium).padding(Spacing.m)) {
                                Text(ex.en, style = MaterialTheme.typography.bodyLarge)
                                if (ex.vi.isNotBlank()) Text(ex.vi, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                            }
                        }
                        if (front.collocations.isNotEmpty()) {
                            Text("Cụm từ đi kèm", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.s))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                front.collocations.forEach { Text(it, Modifier.background(c.surfaceContainerHigh, MaterialTheme.shapes.small)
                                    .padding(horizontal = Spacing.s, vertical = 2.dp), style = MaterialTheme.typography.labelLarge) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_refresh), null, Modifier.size(16.dp), tint = c.onSurfaceVariant)
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Đoán từ rồi chạm để lật thẻ", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                    }
                    else -> {
                        Spacer(Modifier.weight(1f))
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            word.partOfSpeech?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = c.onPrimaryContainer) }
                            Text(word.word, style = EngMateTheme.type.vocabulary, color = c.onPrimaryContainer, textAlign = TextAlign.Center)
                            word.phonetic?.let { IpaText(it, color = c.onPrimaryContainer) }
                            FilledIconButton(onClick = { pronounce(word.word, word.audioUrl) }, Modifier.size(56.dp)) {
                                Icon(painterResource(R.drawable.ic_volume), "Nghe phát âm")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        val note = state.note?.personalNote
                        Column(Modifier.fillMaxWidth().background(c.surface, MaterialTheme.shapes.medium).padding(Spacing.m)) {
                            Text("Ghi chú của tôi", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                            Text(note ?: "Chưa có ghi chú. Thêm trong Chi tiết từ.", style = MaterialTheme.typography.bodyMedium,
                                color = if (note == null) c.onSurfaceVariant else c.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Finished(s: Session) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Spacer(Modifier.height(Spacing.l))
        ProgressRing(if (s.cards.isEmpty()) 0f else s.knownCount / s.cards.size.toFloat(), size = 148.dp, strokeWidth = 12.dp,
            color = MaterialTheme.colorScheme.success) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${s.knownCount}/${s.cards.size}", style = EngMateTheme.type.stat)
                Text("đã thuộc", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(if (s.unknown.isEmpty()) "Thuộc hết rồi!" else "Hoàn thành phiên ôn", style = MaterialTheme.typography.headlineSmall)
        Text("Bạn đã ôn ${s.cards.size} thẻ trong “${s.title}”.", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            StatCard("${s.knownCount}", "Đã thuộc", R.drawable.ic_check_circle, Modifier.weight(1f), tint = MaterialTheme.colorScheme.success)
            StatCard("${s.unknown.size}", "Chưa thuộc", R.drawable.ic_refresh, Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary)
        }
    }
}
