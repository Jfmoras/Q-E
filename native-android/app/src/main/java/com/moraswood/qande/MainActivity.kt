package com.moraswood.qande

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.moraswood.qande.ui.QeApp
import com.moraswood.qande.ui.theme.QeTheme

/**
 * Native Q&E (V10.1 port). No WebView: every screen is Jetpack Compose, data lives in Room.
 * Same component name as V10.1's launcher activity so the in-place update keeps shortcuts.
 */
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { QeTheme { QeApp(vm) } }
    }
}
