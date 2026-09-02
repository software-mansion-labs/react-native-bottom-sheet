package com.swmansion.reactnativebottomsheet.closerequest

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalCloseRequestControllerTest {
  private var nextEscapeDownTime = 1_000L

  @Test
  fun `clear and dispose restore fallback without emitting late requests`() {
    withActivity<ComponentActivity> { activity ->
      var fallbackCount = 0
      var closeRequestCount = 0
      activity.onBackPressedDispatcher.addCallback(countingCallback { fallbackCount++ })
      val portal = View(activity).apply { isFocusableInTouchMode = true }
      activity.setContentView(portal)
      assertTrue(portal.requestFocus())
      val controller =
        PortalCloseRequestController(
          view = portal,
          currentActivity = { activity },
          emitCloseRequest = {
            closeRequestCount++
            true
          },
        )

      controller.update(inputState(), usesPortalPresentation = true)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, closeRequestCount)

      controller.clear()
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, fallbackCount)
      assertEquals(1, closeRequestCount)

      controller.update(inputState(), usesPortalPresentation = true)
      activity.onBackPressedDispatcher.onBackPressed()
      controller.dispose()
      activity.onBackPressedDispatcher.onBackPressed()

      assertEquals(2, fallbackCount)
      assertEquals(2, closeRequestCount)
    }
  }

  @Test
  fun `handler and presentation changes route Back and Escape observably`() {
    withActivity<ComponentActivity> { activity ->
      var fallbackCount = 0
      var closeRequestCount = 0
      activity.onBackPressedDispatcher.addCallback(countingCallback { fallbackCount++ })
      val portal = EscapeDispatchingView(activity).apply { isFocusableInTouchMode = true }
      activity.setContentView(portal)
      layoutView(portal)
      assertTrue(portal.requestFocus())
      val controller =
        PortalCloseRequestController(
          view = portal,
          currentActivity = { activity },
          emitCloseRequest = {
            closeRequestCount++
            true
          },
        )
      portal.dispatchEscape = controller::dispatchEscape

      controller.update(inputState(), usesPortalPresentation = true)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, closeRequestCount)
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(2, closeRequestCount)

      controller.update(inputState(hasCloseRequestHandler = false), usesPortalPresentation = true)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, fallbackCount)
      assertEscape(activity::dispatchKeyEvent, expectedHandled = false)
      assertEquals(2, closeRequestCount)

      controller.update(
        inputState(isPresentationActive = false, isTargetResolvedAndOpen = false),
        usesPortalPresentation = true,
      )
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(2, fallbackCount)
      assertEscape(activity::dispatchKeyEvent, expectedHandled = false)
      assertEquals(2, closeRequestCount)

      controller.update(inputState(isTargetResolvedAndOpen = false), usesPortalPresentation = true)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(2, closeRequestCount)

      controller.update(inputState(), usesPortalPresentation = true)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(3, closeRequestCount)
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)

      assertEquals(2, fallbackCount)
      assertEquals(4, closeRequestCount)
      controller.dispose()
    }
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

  private fun assertEscape(dispatchKeyEvent: (KeyEvent) -> Boolean, expectedHandled: Boolean) {
    val downTime = nextEscapeDownTime
    nextEscapeDownTime += 10L
    assertEquals(expectedHandled, dispatchKeyEvent(escapeDown(downTime)))
    assertEquals(expectedHandled, dispatchKeyEvent(escapeUp(downTime)))
  }

  private fun escapeDown(downTime: Long) =
    KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0)

  private fun escapeUp(downTime: Long) =
    KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0)

  private fun layoutView(view: View) {
    val width = 1080
    val height = 1920
    view.measure(
      View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, width, height)
    shadowOf(Looper.getMainLooper()).idle()
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

private class EscapeDispatchingView(context: Context) : View(context) {
  var dispatchEscape: (KeyEvent) -> Boolean = { false }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean =
    dispatchEscape(event) || super.dispatchKeyEvent(event)
}
