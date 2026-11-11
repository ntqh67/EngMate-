package com.engmate.app.feature.profile

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.feature.auth.domain.*
import com.engmate.app.feature.profile.data.RoomProfileRepository
import com.engmate.app.feature.profile.presentation.*
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProfileScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: EngMateDatabase
    @After fun close() { db.close() }
    @Test fun createReadEditCancelAndDeleteFromScreen() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EngMateDatabase::class.java).build()
        val session = object : UserSession {
            override val isDemo = true
            override val user = MutableStateFlow<SessionUser?>(SessionUser("demo", "demo@example.com"))
        }
        val vm = ProfileViewModel(RoomProfileRepository(db.profiles(), session, Clock.systemUTC()), session)
        compose.setContent { EngMateTheme { ProfileScreen(vm) } }
        compose.waitUntil(5000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); !vm.state.value.loading }
        compose.onNodeWithText("Tạo hồ sơ").performClick()
        compose.onNodeWithText("Họ tên").performTextInput("Nguyễn An")
        compose.onNodeWithText("Lưu hồ sơ").performScrollTo().performClick()
        compose.waitUntil(5000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); vm.state.value.profile != null && !vm.state.value.editing }
        compose.onNodeWithText("Nguyễn An").assertIsDisplayed()
        compose.onNodeWithText("Chỉnh sửa hồ sơ").performScrollTo().performClick()
        compose.onNodeWithText("Họ tên").performTextReplacement("Nguyễn Bình")
        compose.onNodeWithText("Lưu hồ sơ").performScrollTo().performClick()
        compose.waitUntil(5000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); vm.state.value.profile?.displayName == "Nguyễn Bình" && !vm.state.value.editing }
        compose.onNodeWithText("Nguyễn Bình").assertIsDisplayed()
        compose.onNodeWithText("Chỉnh sửa hồ sơ").performScrollTo().performClick()
        compose.onNodeWithText("Họ tên").performTextReplacement("Tên không lưu")
        compose.onNodeWithText("Hủy").performScrollTo().performClick()
        compose.onNodeWithText("Nguyễn Bình").assertIsDisplayed()
        compose.onNodeWithText("Xóa hồ sơ trên thiết bị").performScrollTo().performClick()
        compose.onNodeWithText("Hủy").performClick()
        compose.onNodeWithText("Nguyễn Bình").assertExists()
        compose.onNodeWithText("Xóa hồ sơ trên thiết bị").performScrollTo().performClick()
        compose.onNodeWithText("Xóa hồ sơ", substring = false).performClick()
        compose.waitUntil(5000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); vm.state.value.profile == null && !vm.state.value.busy }
        compose.onNodeWithText("Tạo hồ sơ").assertIsDisplayed()
    }
}
