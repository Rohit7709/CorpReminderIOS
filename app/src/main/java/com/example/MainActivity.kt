package com.example

import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.ReminderRepository
import com.example.ui.CorporateTaskApp
import com.example.ui.ReminderViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NotificationHelper

class MainActivity : ComponentActivity() {
  companion object {
    var isAppInForeground = false
  }

  override fun onStart() {
    super.onStart()
    isAppInForeground = true
  }

  override fun onStop() {
    super.onStop()
    isAppInForeground = false
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = ReminderRepository(database.reminderDao())
    
    // Create native notification channel and schedule alarms on startup
    NotificationHelper.createNotificationChannel(applicationContext)
    NotificationHelper.scheduleRepeatingCheck(applicationContext)
    
    setContent {
      MyApplicationTheme {
        val context = LocalContext.current
        val viewModel: ReminderViewModel = viewModel(
          factory = ReminderViewModel.Factory(
            context.applicationContext as Application,
            repository
          )
        )

        // Request runtime permission for notifications on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
          ) { isGranted ->
            // Permission checked / handled
          }
          LaunchedEffect(Unit) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
          }
        }

        CorporateTaskApp(viewModel = viewModel)
      }
    }
  }
}
