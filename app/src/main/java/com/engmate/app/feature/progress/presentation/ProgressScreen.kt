package com.engmate.app.feature.progress.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable fun ProgressScreen(vm: ProgressViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Thống kê tiến độ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (s.loading) CircularProgressIndicator() else if (s.error) Text("Không tải được dữ liệu tiến độ. Vui lòng mở lại màn hình.") else {
            Text("7 ngày qua", modifier = Modifier.background(Color(0xFFE1F0FF), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("${s.learned}", "Lượt từ đã học", com.engmate.app.R.drawable.ic_book, Color(0xFFE1F0FF), Modifier.weight(1f))
                StatCard("${s.due}", "Từ cần ôn tập", com.engmate.app.R.drawable.ic_history, Color(0xFFFFF2EC), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("${s.streak} ngày", "Chuỗi ngày học", com.engmate.app.R.drawable.ic_bolt, Color(0xFFFFF2EC), Modifier.weight(1f))
                StatCard("${s.xp} XP", "Tổng điểm XP", com.engmate.app.R.drawable.ic_school, Color(0xFFE6E5FE), Modifier.weight(1f))
            }
            Text("Hoạt động học trong tuần (phút)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDE4EB))) {
                val max = s.dailyMinutes.maxOrNull()?.coerceAtLeast(1) ?: 1
                Row(Modifier.fillMaxWidth().height(160.dp).padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    s.dailyMinutes.forEachIndexed { i, value ->
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text("$value", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(24.dp).height((value.toFloat() / max * 90).dp.coerceAtLeast(2.dp)).background(if (value == max) MaterialTheme.colorScheme.primary else Color(0xFFA7CEFB), RoundedCornerShape(4.dp)))
                            Text(LocalDate.now().minusDays((6-i).toLong()).dayOfWeek.let { if (it == java.time.DayOfWeek.SUNDAY) "CN" else "T${it.value + 1}" }, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text("Độ chính xác bài Quiz: ${s.accuracy?.let { "$it%" } ?: "Chưa có dữ liệu"}", style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(progress = { (s.accuracy ?: 0) / 100f }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF9886FE), trackColor = Color(0xFFE1F0FF))
            Text("Số liệu được lưu trên thiết bị này.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun StatCard(value: String, label: String, icon: Int, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color)) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Icon(androidx.compose.ui.res.painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.bodySmall) } }
}
