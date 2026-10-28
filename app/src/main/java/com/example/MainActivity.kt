package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.core.DisplayRefreshRateManager
import com.example.core.OsShellManager
import com.example.ui.BrowserViewModel
import com.example.ui.GlassBrowserApp
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val incomingSystemIntent =
        MutableStateFlow<OsShellManager.SystemShellIntentAction?>(null)

    private val browserViewModel: BrowserViewModel by lazy {
        ViewModelProvider(this)[BrowserViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        DisplayRefreshRateManager.applyHighRefreshRate(this, enabled = true)

        // Route initial system browser / search intent if launched from an external link
        val initialAction = OsShellManager.parseIncomingSystemIntent(intent)
        if (initialAction is OsShellManager.SystemShellIntentAction.OpenUrlOrQuery) {
            incomingSystemIntent.value = initialAction
        }

        setContent {
            GlassBrowserApp(
                systemIntentFlow = incomingSystemIntent,
                onConsumeSystemIntent = { incomingSystemIntent.value = null },
                viewModel = browserViewModel
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        OsShellManager.parseIncomingSystemIntent(intent)?.let { action ->
            incomingSystemIntent.value = action
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        browserViewModel.trimMemory(level)
    }
}
