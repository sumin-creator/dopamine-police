package dev.shortblocker.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shortblocker.app.ui.MainViewModel
import dev.shortblocker.app.ui.ShortblockerApp
import dev.shortblocker.app.ui.theme.ShortblockerTheme

class MainActivity : ComponentActivity() {
    private var requestedTab: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab = intent.getStringExtra(EXTRA_OPEN_TAB)

        val container = (application as ShortblockerApplication).container
        setContent {
            ShortblockerTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.factory(
                        store = container.store,
                        notificationController = container.notificationController,
                    ),
                )
                ShortblockerApp(
                    viewModel = viewModel,
                    requestedTab = requestedTab,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab = intent.getStringExtra(EXTRA_OPEN_TAB)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_DASHBOARD = "dashboard"

        fun goalIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_TAB, TAB_DASHBOARD)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
