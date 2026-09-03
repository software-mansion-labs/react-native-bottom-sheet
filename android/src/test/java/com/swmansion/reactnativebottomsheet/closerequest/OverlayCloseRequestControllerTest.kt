package com.swmansion.reactnativebottomsheet.closerequest

import android.app.Activity
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OverlayCloseRequestControllerTest {
  @Test
  fun `handler restoration does not reactivate an in-progress Escape press`() {
    withActivity<ComponentActivity> { activity ->
      var closeRequestCount = 0
      val controller = OverlayCloseRequestController {
        closeRequestCount++
        true
      }
      val dialog = shownDialog(activity)
      controller.bind(dialog)
      controller.update(inputState(), usesOverlayDialog = true, isSheetInteractive = false)

      assertTrue(dialog.dispatchKeyEvent(escapeDown(10L)))
      controller.update(
        inputState(hasCloseRequestHandler = false),
        usesOverlayDialog = true,
        isSheetInteractive = false,
      )
      controller.update(inputState(), usesOverlayDialog = true, isSheetInteractive = false)

      assertTrue(dialog.dispatchKeyEvent(escapeUp(10L)))
      assertEquals(0, closeRequestCount)

      dispatchEscape(dialog)
      assertEquals(1, closeRequestCount)
      controller.dispose()
    }
  }

  @Test
  fun `unbind and dispose leave the dialog fallback without emitting`() {
    withActivity<ComponentActivity> { activity ->
      var closeRequestCount = 0
      val controller = OverlayCloseRequestController {
        closeRequestCount++
        true
      }
      val boundDialog = shownDialog(activity)
      controller.bind(boundDialog)
      controller.update(inputState(), usesOverlayDialog = true, isSheetInteractive = false)
      boundDialog.onBackPressedDispatcher.onBackPressed()
      dispatchEscape(boundDialog)
      assertEquals(2, closeRequestCount)

      controller.unbind()
      dispatchEscape(boundDialog)
      boundDialog.onBackPressedDispatcher.onBackPressed()
      assertFalse(boundDialog.isShowing)
      assertEquals(2, closeRequestCount)

      val disposedDialog = shownDialog(activity)
      controller.bind(disposedDialog)
      controller.update(inputState(), usesOverlayDialog = true, isSheetInteractive = false)
      controller.dispose()
      dispatchEscape(disposedDialog)
      disposedDialog.onBackPressedDispatcher.onBackPressed()

      assertFalse(disposedDialog.isShowing)
      assertEquals(2, closeRequestCount)
    }
  }

  @Test
  fun `handler and presentation state control input routing and window flags`() {
    withActivity<ComponentActivity> { activity ->
      var fallbackCount = 0
      var closeRequestCount = 0
      activity.onBackPressedDispatcher.addCallback(countingCallback { fallbackCount++ })
      val dialog = shownDialog(activity)
      val controller = OverlayCloseRequestController {
        closeRequestCount++
        true
      }
      controller.bind(dialog)
      controller.update(inputState(), usesOverlayDialog = true, isSheetInteractive = false)
      val window = requireNotNull(dialog.window)

      assertTrue(window.hasFlag(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
      assertFalse(window.hasFlag(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
      dialog.onBackPressedDispatcher.onBackPressed()
      dispatchEscape(dialog)

      controller.update(
        inputState(hasCloseRequestHandler = false),
        usesOverlayDialog = true,
        isSheetInteractive = false,
      )
      assertTrue(window.hasFlag(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
      assertTrue(window.hasFlag(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
      dialog.onBackPressedDispatcher.onBackPressed()
      dispatchEscape(dialog)

      controller.update(
        inputState(isPresentationActive = false, isTargetResolvedAndOpen = false),
        usesOverlayDialog = true,
        isSheetInteractive = false,
      )
      assertTrue(window.hasFlag(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
      dispatchEscape(dialog)
      dialog.onBackPressedDispatcher.onBackPressed()

      assertFalse(dialog.isShowing)
      assertEquals(1, fallbackCount)
      assertEquals(2, closeRequestCount)
      controller.dispose()
    }
  }

  @Test
  fun `Back pass-through follows the currently bound dialog activity`() {
    val firstActivityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val secondActivityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val firstActivity = firstActivityController.get()
    val secondActivity = secondActivityController.get()
    val firstDialog = shownDialog(firstActivity)
    val secondDialog = shownDialog(secondActivity)
    var firstFallbackCount = 0
    var secondFallbackCount = 0
    firstActivity.onBackPressedDispatcher.addCallback(countingCallback { firstFallbackCount++ })
    secondActivity.onBackPressedDispatcher.addCallback(countingCallback { secondFallbackCount++ })
    val controller = OverlayCloseRequestController { true }
    try {
      controller.bind(firstDialog)
      controller.update(
        inputState(hasCloseRequestHandler = false),
        usesOverlayDialog = true,
        isSheetInteractive = false,
      )
      firstDialog.onBackPressedDispatcher.onBackPressed()

      controller.bind(secondDialog)
      controller.update(
        inputState(hasCloseRequestHandler = false),
        usesOverlayDialog = true,
        isSheetInteractive = false,
      )
      secondDialog.onBackPressedDispatcher.onBackPressed()

      assertEquals(1, firstFallbackCount)
      assertEquals(1, secondFallbackCount)
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

  private fun inputState(
    hasCloseRequestHandler: Boolean = true,
    isPresentationActive: Boolean = true,
    isTargetResolvedAndOpen: Boolean = true,
  ) =
    CloseRequestInputState(
      isAttached = true,
      isLifecycleActive = true,
      isModal = true,
      hasCloseRequestHandler = hasCloseRequestHandler,
      isPresentationActive = isPresentationActive,
      isTargetResolvedAndOpen = isTargetResolvedAndOpen,
    )

  private fun countingCallback(onBack: () -> Unit) =
    object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() = onBack()
    }

  private fun android.view.Window.hasFlag(flag: Int): Boolean = attributes.flags and flag != 0

  private fun dispatchEscape(dialog: ComponentDialog) {
    val downTime = 10L
    dialog.dispatchKeyEvent(escapeDown(downTime))
    dialog.dispatchKeyEvent(escapeUp(downTime))
  }

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
