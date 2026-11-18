package com.engmate.app.feature.progress.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

private val blue = Color(0xFF2C71B7)
private val lightBlue = Color(0xFFA7CEFB)
private val ink = Color(0xFF101419)
private val muted = Color(0xFF565B62)

private fun number(n: Number) = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi")).format(n.toLong())

@Composable fun ProgressScreen(vm: ProgressViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Color(0xFFF3F9FF)).verticalScroll(rememberScrollState()).statusBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Thống kê tiến độ", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ink)
        if (s.loading) CircularProgressIndicator()
        else if (s.error) Text("Không tải được dữ liệu tiến độ. Vui lòng mở lại màn hình.")
        else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Row(Modifier.background(Color(0xFFEBF1F8), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(16.dp), tint = blue)
                    Spacer(Modifier.width(6.dp))
                    Text("7 ngày qua", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ink)
                    Text("  ▾", fontSize = 11.sp, color = muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(number(s.learned), "Từ vựng đã học", R.drawable.ic_book, Color(0xFFE1F0FF), Modifier.weight(1f))
                StatCard(number(s.due), "Từ cần ôn tập", R.drawable.ic_history, Color(0xFFFFF0DE), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("${s.streak} ngày", "Chuỗi ngày học", R.drawable.ic_bolt, Color(0xFFFFF1E8), Modifier.weight(1f))
                StatCard("${number(s.xp)} XP", "Tổng điểm XP", R.drawable.ic_school, Color(0xFFEEE8FF), Modifier.weight(1f))
            }
            Text("Hoạt động học trong tuần", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.padding(top = 6.dp))
            val max = s.dailyMinutes.maxOrNull()?.coerceAtLeast(1) ?: 1
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                s.dailyMinutes.forEachIndexed { i, value ->
                    val day = LocalDate.now().minusDays((6 - i).toLong()).dayOfWeek
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(Modifier.width(20.dp).height((value.toFloat() / max * 110).dp.coerceAtLeast(6.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(if (value == max && value > 0) blue else lightBlue))
                        Spacer(Modifier.height(8.dp))
                        Text(if (day == DayOfWeek.SUNDAY) "CN" else "T${day.value + 1}", fontSize = 11.sp, color = muted)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Độ chính xác bài Quiz", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink)
                Text(s.accuracy?.let { "$it%" } ?: "Chưa có", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = blue)
            }
            LinearProgressIndicator(progress = { (s.accuracy ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50)),
                color = Color(0xFF9886FE), trackColor = Color(0xFFE1F0FF), gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

@Composable private fun StatCard(value: String, label: String, icon: Int, color: Color, modifier: Modifier) {
    Column(modifier.background(color, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = blue)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ink)
        Text(label, fontSize = 12.sp, color = muted)
    }
}
