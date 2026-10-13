package com.timelog.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.timelog.app.reminders.EVENT_EXTRA
import com.timelog.app.ui.TimeLogApp
import com.timelog.app.ui.TimeLogTheme

class MainActivity : ComponentActivity() {
    private var entryRequest by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        entryRequest = requestFrom(intent)
        setContent {
            TimeLogTheme { TimeLogApp(requestKey = entryRequest, onRequestHandled = { entryRequest = null; intent.removeExtra(EVENT_EXTRA) }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        entryRequest = requestFrom(intent)
    }

    private fun requestFrom(intent: Intent) = runCatching { intent.getStringExtra(EVENT_EXTRA)?.takeIf { it.length <= 256 } }.getOrNull()
}
