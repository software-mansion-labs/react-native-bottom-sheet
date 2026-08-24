@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OverlayRequestCloseControllerTest {
  @Test
  fun `unbind and dispose are idempotent and clear in-progress input`() {
    withActivity<ComponentActivity> { activity ->
      val dialog = shownDialog(activity)
      var requestCloseCount = 0
      val controller = OverlayRequestCloseController {
        requestCloseCount++
        true
      }
      controller.bind(dialog)
      controller.update(inputState(), enabled = true, interactive = false)
      val callbackBeforeUnbind = requireNotNull(controller.backCallback)
      dialog.onBackPressedDispatcher.dispatchOnBackStarted(backEvent())
      assertTrue(controller.dispatchEscape(escapeDown(10L)))

      controller.unbind()
      controller.unbind()
      assertNull(controller.backCallback)
      callbackBeforeUnbind.handleOnBackCancelled()
      callbackBeforeUnbind.handleOnBackPressed()
      assertFalse(controller.dispatchEscape(escapeUp(10L)))
      assertEquals(0, requestCloseCount)

      controller.bind(dialog)
      controller.update(inputState(), enabled = true, interactive = false)
      val callbackBeforeDispose = requireNotNull(controller.backCallback)
      assertNotSame(callbackBeforeUnbind, callbackBeforeDispose)
      dialog.onBackPressedDispatcher.dispatchOnBackStarted(backEvent())

      controller.dispose()
      controller.dispose()
      controller.unbind()
      callbackBeforeDispose.handleOnBackCancelled()
      callbackBeforeDispose.handleOnBackPressed()
      controller.bind(dialog)
      controller.update(inputState(), enabled = true, interactive = true)

      assertNull(controller.backCallback)
      assertEquals(0, requestCloseCount)
      dialog.dismiss()
    }
  }

  @Test
  fun `focusability is reconciled from current window flags without a cache`() {
    withActivity<ComponentActivity> { activity ->
      val dialog = shownDialog(activity)
      val controller = OverlayRequestCloseController { true }
      controller.bind(dialog)
      controller.update(inputState(), enabled = true, interactive = false)
      val window = requireNotNull(dialog.window)

      assertFalse(window.hasFlag(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
      window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
      controller.update(inputState(), enabled = true, interactive = false)
      assertFalse(window.hasFlag(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))

      controller.update(
        inputState(hasHandler = false),
        enabled = true,
        interactive = false,
      )
      assertTrue(window.hasFlag(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
      window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
      controller.update(
        inputState(hasHandler = false),
        enabled = true,
        interactive = false,
      )
      assertTrue(window.hasFlag(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))

      controller.dispose()
      dialog.dismiss()
    }
  }

  @Test
  fun `Back pass-through resolves the Activity from the currently bound dialog only`() {
    val firstActivityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val secondActivityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val firstActivity = firstActivityController.get()
    val secondActivity = secondActivityController.get()
    val firstDialog = shownDialog(firstActivity)
    val secondDialog = shownDialog(secondActivity)
    var firstBackCount = 0
    var secondBackCount = 0
    firstActivity.onBackPressedDispatcher.addCallback(countingCallback { firstBackCount++ })
    secondActivity.onBackPressedDispatcher.addCallback(countingCallback { secondBackCount++ })
    val controller = OverlayRequestCloseController { true }
    try {
      controller.bind(firstDialog)
      controller.update(
        inputState(hasHandler = false),
        enabled = true,
        interactive = false,
      )
      firstDialog.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, firstBackCount)
      assertEquals(0, secondBackCount)

      val firstCallback = requireNotNull(controller.backCallback)
      controller.bind(secondDialog)
      controller.update(
        inputState(hasHandler = false),
        enabled = true,
        interactive = false,
      )
      secondDialog.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, firstBackCount)
      assertEquals(1, secondBackCount)

      val secondCallback = requireNotNull(controller.backCallback)
      controller.unbind()
      firstCallback.handleOnBackPressed()
      secondCallback.handleOnBackPressed()
      assertEquals(1, firstBackCount)
      assertEquals(1, secondBackCount)
      assertNull(controller.backCallback)
    } finally {
      controller.dispose()
      firstDialog.dismiss()
      secondDialog.dismiss()
      secondActivityController.close()
      firstActivityController.close()
    }
  }

  private fun shownDialog(activity: ComponentActivity): ComponentDialog =
    ComponentDialog(activity).also { dialog ->
      dialog.setContentView(FrameLayout(activity))
      dialog.show()
      shadowOf(Looper.getMainLooper()).idle()
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

  private fun countingCallback(onBack: () -> Unit) =
    object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() = onBack()
    }

  private fun android.view.Window.hasFlag(flag: Int): Boolean = attributes.flags and flag != 0

  private fun backEvent() = BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)

  private fun escapeDown(downTime: Long) =
    KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0)

  private fun escapeUp(downTime: Long) =
    KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0)

  private inline fun <reified T : Activity> withActivity(block: (T) -> Unit) {
    val activityController = Robolectric.buildActivity(T::class.java).setup()
    try {
      block(activityController.get())
    } finally {
      activityController.close()
    }
  }
}
