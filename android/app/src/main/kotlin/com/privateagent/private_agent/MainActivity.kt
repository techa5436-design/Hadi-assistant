package com.privateagent.private_agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.privateagent.private_agent.service.TelegramService
import com.privateagent.private_agent.ui.PrivateAgentApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Telegram polling in background if configured
        TelegramService.getInstance(applicationContext).startIfEnabled()

        setContent {
            PrivateAgentApp()
        }
    }
}
