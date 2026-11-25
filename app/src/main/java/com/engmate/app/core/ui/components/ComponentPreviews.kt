package com.engmate.app.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.engmate.app.R
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.core.ui.theme.Spacing

/** Every component preview renders in light and dark. */
@Preview(name = "Sáng", widthDp = 412)
@Preview(name = "Tối", widthDp = 412, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

@Composable
private fun PreviewBox(content: @Composable ColumnScope.() -> Unit) = EngMateTheme {
    Column(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m), content = content)
}

@ThemePreviews @Composable private fun FramePreview() = PreviewBox {
    AppTopBar("Chi tiết từ vựng", onBack = {})
    AppSegmentedTabs(listOf("Ngữ pháp", "Từ vựng"), 0, {})
    AppSearchField("", {}, "Tìm từ tiếng Anh…")
    SectionHeader("Gợi ý cho trình độ A2", actionLabel = "Xem tất cả", onAction = {})
}

@ThemePreviews @Composable private fun ButtonsPreview() = PreviewBox {
    PrimaryButton("Làm bài tập (10 câu)", {}, Modifier.fillMaxWidth())
    SecondaryButton("Làm lại", {}, Modifier.fillMaxWidth())
    PrimaryButton("Đang lưu", {}, Modifier.fillMaxWidth(), loading = true)
    StickyBottomBar { ButtonPair("Hủy", {}, "Lưu hồ sơ", {}) }
}

@ThemePreviews @Composable private fun CardsPreview() = PreviewBox {
    HeroCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            ProgressRing(0.66f) { Text("10/15'", style = MaterialTheme.typography.labelLarge) }
            Column { Text("Mục tiêu hôm nay", style = MaterialTheme.typography.titleMedium); Text("Còn 5 phút nữa") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) { StreakChip(6); XpChip(120) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        StatCard("6 ngày", "Chuỗi ngày học", R.drawable.ic_flame, Modifier.weight(1f))
        StatCard("120 XP", "Tổng XP", R.drawable.ic_bolt, Modifier.weight(1f), tint = MaterialTheme.colorScheme.tertiary)
    }
    InfoCard { Text("12 từ cần ôn", style = MaterialTheme.typography.titleMedium) }
    SurfaceGroup {
        ListRow("Hiện tại đơn (Present Simple)", supporting = "Thói quen, sự thật hiển nhiên", leading = { LevelChip("A1", large = true) },
            trailing = { StatusTag(ItemStatus.DONE, "80%") }, onClick = {})
        GroupDivider()
        ListRow("Hiện tại tiếp diễn", supporting = "Hành động đang xảy ra", leading = { LevelChip("A2", large = true) },
            trailing = { StatusTag(ItemStatus.NEW, "Mới") }, onClick = {})
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        ShortcutTile(R.drawable.ic_layers, "Ôn tập", {}, Modifier.weight(1f))
        ShortcutTile(R.drawable.ic_assignment, "Ngữ pháp", {}, Modifier.weight(1f))
    }
}

@ThemePreviews @Composable private fun ProgressPreview() = PreviewBox {
    LinearProgressWithLabel(3 / 17f, "Đã học 3/17 bài", trailing = "18%")
    SegmentedProgress(3, 10, Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) { LevelChip("A1"); LevelChip("A2"); LevelChip("B1"); LevelChip("B2"); LevelChip("C1") }
    XpChip(10, gain = true)
    IpaText("/ˈkʌmf.tə.bəl/")
}

@ThemePreviews @Composable private fun StatesPreview() = PreviewBox {
    EmptyState("Gom từ muốn nhớ vào Sổ tay", "Tra một từ rồi bấm Thêm vào Sổ tay.", action = { PrimaryButton("Tra từ", {}) })
    ErrorState("Không tải được bài học.", onRetry = {})
    FeedbackBanner("Không có kết nối Internet. Đang hiện từ đã lưu trong máy.", kind = FeedbackKind.WARNING)
    FeedbackBanner("Đã cập nhật trình độ vào hồ sơ", kind = FeedbackKind.SUCCESS)
    LoadingState(rows = 2)
}
