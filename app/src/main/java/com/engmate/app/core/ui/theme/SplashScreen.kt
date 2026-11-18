package com.engmate.app.core.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Color(0xFFA7CEFB))) {
        Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFF5896D9), radius = size.width * .62f, center = Offset(size.width * .19f, 0f)) }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp).background(Color.White, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Text("E", fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0D4172))
                Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFFF2843A), radius = size.width * .08f, center = Offset(size.width * .72f, size.height * .55f)); val p = Path().apply { moveTo(size.width*.1f,size.height*.85f); lineTo(size.width*.1f,size.height*1.12f); lineTo(size.width*.4f,size.height*.93f); close() }; drawPath(p, Color.White) }
            }
            Spacer(Modifier.height(42.dp))
            Text("EngMate", color = Color(0xFF0D4172), fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.height(12.dp))
            Text("Học tiếng Anh cùng người bạn đồng hành", color = Color.White, textAlign = TextAlign.Center, fontSize = 14.sp)
            Spacer(Modifier.height(60.dp))
            Box(Modifier.width(140.dp).height(5.dp).background(Color(0xFF82B5EF), RoundedCornerShape(10.dp))) { Box(Modifier.fillMaxHeight().fillMaxWidth(.55f).background(Color.White, RoundedCornerShape(10.dp))) }
        }
        Text("v1.0", color = Color(0xFF0D4172), fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
    }
}
