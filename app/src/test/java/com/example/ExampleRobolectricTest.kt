package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.ui.FinanceApp
import com.example.ui.FinanceViewModel
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("KB SPASI COMMUNITY", appName)
  }

  @Test
  fun testAppRenderAndNavigation() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    try {
      com.google.firebase.FirebaseApp.initializeApp(application)
    } catch (e: Exception) {
      // Ignored if already initialized
    }
    val viewModel = FinanceViewModel(application)
    
    composeTestRule.setContent {
      MyApplicationTheme {
        FinanceApp(viewModel = viewModel)
      }
    }
    
    // Check if app has been composed of root node properly
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().assertExists()
  }
}
