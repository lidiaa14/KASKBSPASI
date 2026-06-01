package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.FinanceApp
import com.example.ui.FinanceViewModel
import com.example.ui.theme.FinanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: FinanceViewModel = viewModel(
                factory = FinanceViewModel.Factory(application)
            )
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            FinanceTheme(darkTheme = isDarkMode) {
                FinanceApp(viewModel = viewModel)
            }
        }
    }
}
