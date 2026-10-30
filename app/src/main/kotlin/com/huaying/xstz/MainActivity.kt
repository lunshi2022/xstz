package com.huaying.xstz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // 占位：阶段 2 起替换为 XstzTheme + XstzApp()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B1220)),
                contentAlignment = Alignment.Center,
            ) {
                Text("XSTZ", color = Color.White)
            }
        }
    }
}
