package com.engmate.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Temporary body for a screen whose entry point exists but whose content ships later. */
@Composable
fun UnderConstructionScreen(title: String, onBack: () -> Unit, details: List<String> = emptyList()) {
    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Đang phát triển", style = MaterialTheme.typography.titleMedium)
            details.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
