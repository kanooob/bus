package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.IdelisDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.IdelisRepository
import com.example.ui.screens.TransitBoardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TransitBoardViewModel
import com.example.ui.viewmodel.TransitBoardViewModelFactory

import android.content.Intent

class MainActivity : ComponentActivity() {

    private val viewModel: TransitBoardViewModel by viewModels {
        val database = IdelisDatabase.getInstance(applicationContext)
        val prefs = PreferencesManager(applicationContext)
        val repository = IdelisRepository(database, applicationContext, prefs)
        TransitBoardViewModelFactory(repository, prefs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntentStop(intent)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = uiState.isDarkTheme) {
                TransitBoardScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentStop(intent)
    }

    private fun handleIntentStop(intent: Intent?) {
        val targetStopId = intent?.getStringExtra("EXTRA_STOP_ID") ?: return
        viewModel.selectStopById(targetStopId)
    }
}
