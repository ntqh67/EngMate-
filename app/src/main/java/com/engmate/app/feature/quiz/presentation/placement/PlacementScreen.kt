package com.engmate.app.feature.quiz.presentation.placement

import androidx.compose.runtime.Composable
import com.engmate.app.core.ui.components.UnderConstructionScreen

/**
 * Placement test. Calls [onFinished] with the resulting level (A1–B2), or null when the user skips it.
 * Onboarding calls this screen. The body is a placeholder until the placement flow ships.
 */
@Composable
fun PlacementScreen(onFinished: (level: String?) -> Unit, onBack: () -> Unit) {
    UnderConstructionScreen("Kiểm tra trình độ", onBack, listOf("Kết quả trả về: trình độ A1–B2, hoặc null nếu bỏ qua"))
}
