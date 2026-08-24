package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.os.Looper
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalRequestCloseControllerTest {
  @Test
  fun `clear and dispose are idempotent and late predictive events cannot restore handlers`() {
    withActivity<ComponentActivity> { activity ->
      val portal = View(activity)
      activity.setContentView(portal)
      var requestCloseCount = 0
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
      val callbackBeforeClear = requireNotNull(controller.backCallback)
      activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent())

      controller.clear()
      controller.clear()
      assertNull(controller.backCallback)
      assertNull(controller.backDispatcher)
      assertNull(controller.escapeListener)

      callbackBeforeClear.handleOnBackCancelled()
      callbackBeforeClear.handleOnBackPressed()
      assertNull(controller.backCallback)
      assertEquals(0, requestCloseCount)

      controller.update(inputState(), enabled = true)
      val callbackBeforeDispose = requireNotNull(controller.backCallback)
      assertNotSame(callbackBeforeClear, callbackBeforeDispose)
      controller.scheduleHostSync()
      activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent())

      controller.dispose()
      controller.dispose()
      controller.clear()
      callbackBeforeDispose.handleOnBackCancelled()
      callbackBeforeDispose.handleOnBackPressed()
      controller.update(inputState(), enabled = true)
      controller.scheduleHostSync()
      shadowOf(Looper.getMainLooper()).idle()

      assertNull(controller.backCallback)
      assertNull(controller.backDispatcher)
      assertNull(controller.escapeListener)
      assertEquals(0, requestCloseCount)
    }
  }

  @Test
  fun `Escape listener installation itself is the sticky host lifetime`() {
    withActivity<ComponentActivity> { activity ->
      val portal = View(activity)
      activity.setContentView(portal)
      val controller =
        PortalRequestCloseController(
          view = portal,
          currentActivity = { activity },
          emitRequestClose = { true },
        )

      controller.update(inputState(hasHandler = false), enabled = true)
      assertNull(controller.escapeListener)

      controller.update(inputState(hasHandler = true), enabled = true)
      val installedListener = requireNotNull(controller.escapeListener)
      controller.update(inputState(hasHandler = false), enabled = true)
      assertSame(installedListener, controller.escapeListener)

      controller.clear()
      assertNull(controller.escapeListener)
      controller.dispose()
    }
  }

  private fun inputState(hasHandler: Boolean = true) =
    RequestCloseInputState(
      isAttached = true,
      isActive = true,
      isModal = true,
      hasHandler = hasHandler,
      isPresentationActive = true,
      isTargetOpen = true,
    )

  private fun backEvent() = BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)

  private inline fun <reified T : Activity> withActivity(block: (T) -> Unit) {
    val activityController = Robolectric.buildActivity(T::class.java).setup()
    try {
      block(activityController.get())
    } finally {
      activityController.close()
    }
  }
}
