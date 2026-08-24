package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalRequestCloseControllerTest {
  @Test
  fun `clear and dispose restore fallback without emitting late requests`() {
    withActivity<ComponentActivity> { activity ->
      var fallbackCount = 0
      var requestCloseCount = 0
      activity.onBackPressedDispatcher.addCallback(countingCallback { fallbackCount++ })
      val portal = View(activity).apply { isFocusableInTouchMode = true }
      activity.setContentView(portal)
      assertTrue(portal.requestFocus())
      val controller =
        PortalRequestCloseController(
          view = portal,
          currentActivity = { activity },
          emitRequestClose = {
            requestCloseCount++
            true
          },
        )

      controller.update(inputState(), enabled = true)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, requestCloseCount)

      controller.clear()
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, fallbackCount)
      assertEquals(1, requestCloseCount)

      controller.update(inputState(), enabled = true)
      activity.onBackPressedDispatcher.onBackPressed()
      controller.dispose()
      activity.onBackPressedDispatcher.onBackPressed()

      assertEquals(2, fallbackCount)
      assertEquals(2, requestCloseCount)
    }
  }

  @Test
  fun `handler and presentation changes route Back and Escape observably`() {
    withActivity<ComponentActivity> { activity ->
      var fallbackCount = 0
      var requestCloseCount = 0
      activity.onBackPressedDispatcher.addCallback(countingCallback { fallbackCount++ })
      val portal = View(activity).apply { isFocusableInTouchMode = true }
      activity.setContentView(portal)
      assertTrue(portal.requestFocus())
      val controller =
        PortalRequestCloseController(
          view = portal,
          currentActivity = { activity },
          emitRequestClose = {
            requestCloseCount++
            true
          },
        )

      controller.update(inputState(), enabled = true)
      activity.onBackPressedDispatcher.onBackPressed()

      controller.update(inputState(hasHandler = false), enabled = true)
      activity.onBackPressedDispatcher.onBackPressed()

      controller.update(
        inputState(isPresentationActive = false, isTargetOpen = false),
        enabled = true,
      )
      activity.onBackPressedDispatcher.onBackPressed()

      controller.update(inputState(isTargetOpen = false), enabled = true)
      activity.onBackPressedDispatcher.onBackPressed()

      controller.update(inputState(), enabled = true)
      activity.onBackPressedDispatcher.onBackPressed()

      assertEquals(2, fallbackCount)
      assertEquals(2, requestCloseCount)
      controller.dispose()
    }
  }

  private fun inputState(
    hasHandler: Boolean = true,
    isPresentationActive: Boolean = true,
    isTargetOpen: Boolean = true,
  ) =
    RequestCloseInputState(
      isAttached = true,
      isActive = true,
      isModal = true,
      hasHandler = hasHandler,
      isPresentationActive = isPresentationActive,
      isTargetOpen = isTargetOpen,
    )

  private fun countingCallback(onBack: () -> Unit) =
    object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() = onBack()
    }

  private inline fun <reified T : Activity> withActivity(block: (T) -> Unit) {
    val activityController = Robolectric.buildActivity(T::class.java).setup()
    try {
      block(activityController.get())
    } finally {
      activityController.close()
    }
  }
}
