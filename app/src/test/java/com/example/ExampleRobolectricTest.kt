package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ReminderRepository
import com.example.ui.ReminderViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CorpRemind", appName)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `test VM initialization and loading`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val database = AppDatabase.getDatabase(context)
    val repository = ReminderRepository(database.reminderDao())
    val viewModel = ReminderViewModel(context, repository)
    assertNotNull(viewModel)
    
    // Test basic operations
    val loginRes = viewModel.loginWithUserId("admin")
    // Wait for coroutine-launched db prepopulation
    viewModel.allUsers.value // Access stateflow trigger
  }
}
