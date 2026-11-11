package com.engmate.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engmate.app.core.ui.components.BackTopBar
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.feature.grammar.presentation.admin.GrammarAdminScreen
import com.engmate.app.feature.profile.presentation.ProfileScreen
import com.engmate.app.feature.quiz.presentation.history.QuizHistoryScreen
import com.engmate.app.feature.quiz.presentation.questionbank.QuestionBankScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Temporary debug-only entry point for Đông's week-3 CRUD screens.
 * TODO(Huân): replace with real routes once the app navigation/Admin area exists.
 */
@AndroidEntryPoint
class DongDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EngMateTheme { DongDebugHost() } }
    }
}

private enum class DebugRoute { HUB, QUESTIONS, GRAMMAR, HISTORY, PROFILE }

@Composable
private fun DongDebugHost() {
    var route by rememberSaveable { mutableStateOf(DebugRoute.HUB) }
    val back = { route = DebugRoute.HUB }
    BackHandler(enabled = route != DebugRoute.HUB, onBack = back)
    when (route) {
        DebugRoute.HUB -> Hub { route = it }
        DebugRoute.QUESTIONS -> QuestionBankScreen(viewModel(), onBack = back)
        DebugRoute.GRAMMAR -> GrammarAdminScreen(viewModel(), onBack = back)
        DebugRoute.HISTORY -> QuizHistoryScreen(viewModel(), onBack = back)
        // Huân's screen is reused unchanged; system back returns to the hub.
        DebugRoute.PROFILE -> ProfileScreen(viewModel())
    }
}

@Composable
private fun Hub(open: (DebugRoute) -> Unit) {
    Scaffold(topBar = { BackTopBar("Module Đông (debug)", onBack = null) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ngữ pháp · Quiz · Lịch sử làm bài — màn demo CRUD tuần 3.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { open(DebugRoute.QUESTIONS) }, Modifier.fillMaxWidth()) { Text("A. Quản lý câu hỏi") }
            Button(onClick = { open(DebugRoute.GRAMMAR) }, Modifier.fillMaxWidth()) { Text("B. Quản lý bài ngữ pháp") }
            Button(onClick = { open(DebugRoute.HISTORY) }, Modifier.fillMaxWidth()) { Text("C. Lịch sử làm bài") }
            HorizontalDivider()
            Text("Lưu lịch sử cần hồ sơ cá nhân của tài khoản hiện tại.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { open(DebugRoute.PROFILE) }, Modifier.fillMaxWidth()) { Text("Hồ sơ cá nhân (của Huân)") }
        }
    }
}
