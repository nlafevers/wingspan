package com.wingspan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wingspan.app.ui.WingspanApp
import com.wingspan.app.ui.theme.WingspanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WingspanTheme {
                WingspanApp()
            }
        }
    }
}
