package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.permissions.PermissionsManager
import com.example.ui.AgentViewModel
import com.example.ui.screens.MainAgentView
import com.example.ui.theme.MyApplicationTheme
import android.util.Log

class MainActivity : ComponentActivity() {
    private lateinit var permissionsManager: PermissionsManager
    private val crashError = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        var existingCrash: String? = null
        val existingHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("MainActivity", "Uncaught exception", throwable)
            }
            existingHandler?.uncaughtException(thread, throwable)
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(1)
        }

        try {
            permissionsManager = PermissionsManager(this)
            enableEdgeToEdge()
            setContent {
                MyApplicationTheme {
                    val error = crashError.value
                    if (error != null) {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState())) {
                                Text("CRASHED: $error", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        val agentViewModel: AgentViewModel = viewModel()
                        MainAgentView(viewModel = agentViewModel, permissionsManager = permissionsManager)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            crashError.value = e.stackTraceToString()
        }
    }
}
