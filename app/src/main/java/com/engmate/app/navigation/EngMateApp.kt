package com.engmate.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engmate.app.feature.auth.presentation.*
import com.engmate.app.feature.profile.presentation.*
import com.engmate.app.feature.progress.presentation.*

@Composable fun EngMateApp(auth: AuthViewModel = viewModel(), profile: ProfileViewModel = viewModel(), progress: ProgressViewModel = viewModel()) {
    val user by auth.session.user.collectAsStateWithLifecycle()
    val profileState by profile.state.collectAsStateWithLifecycle()
    var tab by remember(user?.uid) { mutableStateOf(0) }
    if (user == null) AuthScreen(auth) else if (profileState.user?.uid != user?.uid || profileState.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
    } else if (profileState.profile == null && !profileState.loadFailed) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f)) { ProfileScreen(profile) }
            TextButton(onClick = auth::signOut, modifier = Modifier.padding(horizontal = 24.dp)) { Text("Đăng xuất") }
        }
    } else {
        BackHandler(enabled = tab != 0) { tab = 0 }
        Scaffold(bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("◷") }, label = { Text("Tiến độ") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("○") }, label = { Text("Hồ sơ") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("⚙") }, label = { Text("Cài đặt") })
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                Box(Modifier.fillMaxHeight().widthIn(max = 480.dp).fillMaxWidth()) {
                when(tab) {
                    0 -> ProgressScreen(progress)
                    1 -> ProfileScreen(profile, onSettings = { tab = 2 })
                    else -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Cài đặt", style = MaterialTheme.typography.headlineSmall)
                        Text(user?.email.orEmpty())
                        OutlinedButton(onClick = auth::signOut, modifier = Modifier.fillMaxWidth()) { Text("Đăng xuất") }
                    }
                }
                }
            }
        }
    }
}
