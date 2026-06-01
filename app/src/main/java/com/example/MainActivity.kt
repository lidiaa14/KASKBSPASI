package com.example

import android.os.Bundle
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.ui.FinanceApp
import com.example.ui.FinanceViewModel
import com.example.ui.theme.FinanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        var initError: Throwable? = null
        var financeViewModel: FinanceViewModel? = null
        try {
            financeViewModel = ViewModelProvider(
                this, 
                FinanceViewModel.Factory(application)
            )[FinanceViewModel::class.java]
        } catch (t: Throwable) {
            initError = t
            android.util.Log.e("MainActivity", "Captured fatal launch exception", t)
        }
        
        setContent {
            var crashThrowable by remember { mutableStateOf<Throwable?>(initError) }
            val vm = financeViewModel
            
            if (crashThrowable != null || vm == null) {
                val displayError = crashThrowable ?: IllegalStateException("FinanceViewModel is null")
                // Self-Healing Diagnostics Screen in Jakarta Slate Theme
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        background = Color(0xFF0F172A),
                        surface = Color(0xFF1E293B),
                        primary = Color(0xFFEF4444),
                        onPrimary = Color.White
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F172A))
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "⚠️ SISTEM DIAGNOSTIK",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF87171)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Aplikasi mendeteksi kendala pada saat inisialisasi awal. Silakan pilih opsi pemulihan di bawah.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                // Beautiful error output box with stacktrace
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = displayError.javaClass.simpleName,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFFEF4444),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = displayError.stackTraceToString().take(600),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = Color(0xFFCBD5E1),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Button(
                                    onClick = {
                                        // Self-healing: clear all local storage / cache database
                                        try {
                                            applicationContext.deleteDatabase("kb_spasi_community_database")
                                            val sharedPrefs = applicationContext.getSharedPreferences("kb_spasi_prefs", Context.MODE_PRIVATE)
                                            sharedPrefs.edit().clear().apply()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        
                                        // Restart intent safely
                                        val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        }
                                        startActivity(intent)
                                        finish()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                ) {
                                    Text("KOSONGKAN CACHE & RESET DATABASE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                OutlinedButton(
                                    onClick = {
                                        // Simple retry reload
                                        val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        }
                                        startActivity(intent)
                                        finish()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    border = ButtonDefaults.outlinedButtonBorder.copy()
                                ) {
                                    Text("MUAT ULANG APLIKASI", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }
                }
            } else {
                val isDarkMode by vm.isDarkMode.collectAsState()
                FinanceTheme(darkTheme = isDarkMode) {
                    FinanceApp(viewModel = vm)
                }
            }
        }
    }
}
