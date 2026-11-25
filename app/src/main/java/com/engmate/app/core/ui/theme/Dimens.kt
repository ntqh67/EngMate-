package com.engmate.app.core.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale: 4 / 8 / 12 / 16 / 24 / 32. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Minimum touch target. */
    val touch = 48.dp
    /** Height of primary and secondary buttons. */
    val button = 52.dp
}

/** Side margin of a screen: 16dp, 20dp from 412dp wide. */
val screenPadding: Dp @Composable get() = if (LocalConfiguration.current.screenWidthDp >= 412) 20.dp else 16.dp

/** True when the system "Remove animations" setting is on; skip decorative motion then. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
