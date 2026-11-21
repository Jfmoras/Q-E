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
        // Feature C: daily on-device overdue check (unique periodic work, KEEP).
        com.moraswood.qande.assets.OverdueAlerts.schedule(applicationContext)
        handleLink(intent)
        setContent { QeTheme { QeApp(vm) } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** Feature L: qe://job/<uuid> (a QR scanned with the phone camera) opens that job. */
    private fun handleLink(intent: android.content.Intent?) {
        com.moraswood.qande.logic.QeQr.parse(intent?.data?.toString())?.let { vm.setPendingJobLink(it) }
    }
}
