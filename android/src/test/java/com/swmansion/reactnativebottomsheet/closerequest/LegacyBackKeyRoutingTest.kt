package com.swmansion.reactnativebottomsheet.closerequest

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationController
import com.swmansion.reactnativebottomsheet.presentation.TestReactRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29, 35])
class LegacyBackKeyRoutingTest {
  private var nextBackDownTime = 1_000L

  @Test
  fun `eligible portal receives legacy Back before the host entry point`() {
    withHost { activity, closeRequests ->
      val controller = openPortal(activity, closeRequests)

      pressBack(activity)

      assertEquals(1, closeRequests.count)
      assertEquals(0, activity.legacyBackCount)
      assertFalse(activity.isFinishing)
      controller.dispose()
    }
  }

  @Test
  fun `legacy Back reaches the host without an eligible portal and after disposal`() {
    withHost { activity, closeRequests ->
      val controller =
        openPortal(activity, closeRequests, inputState(hasCloseRequestHandler = false))

      pressBack(activity)
      assertEquals(1, activity.legacyBackCount)

      controller.update(inputState(), usesPortalPresentation = true)
      pressBack(activity)
      assertEquals(1, closeRequests.count)

      controller.dispose()
      pressBack(activity)

      assertEquals(2, activity.legacyBackCount)
      assertEquals(1, closeRequests.count)
    }
  }

  @Test
  fun `callbacks added after the portal cannot bypass the close request`() {
    withHost { activity, closeRequests ->
      val controller = openPortal(activity, closeRequests)
      var laterCallbackCount = 0
      val laterCallback = countingCallback { laterCallbackCount++ }
      activity.onBackPressedDispatcher.addCallback(laterCallback)

      pressBack(activity)
      assertEquals(0, laterCallbackCount)
      assertEquals(1, closeRequests.count)

      laterCallback.remove()
      pressBack(activity)

      assertEquals(0, laterCallbackCount)
      assertEquals(2, closeRequests.count)
      assertEquals(0, activity.legacyBackCount)
      controller.dispose()
    }
  }

  @Test
  fun `stacked portals route legacy Back to the upper portal only`() {
    withHost { activity, lowerCloseRequests ->
      val upperCloseRequests = CloseRequestCounter()
      val lowerController = openPortal(activity, lowerCloseRequests)
      val upperController = openPortal(activity, upperCloseRequests)

      pressBack(activity)
      assertEquals(1, upperCloseRequests.count)
      assertEquals(0, lowerCloseRequests.count)

      upperController.dispose()
      pressBack(activity)

      assertEquals(1, upperCloseRequests.count)
      assertEquals(1, lowerCloseRequests.count)
      assertEquals(0, activity.legacyBackCount)
      lowerController.dispose()
    }
  }

  @Test
  fun `repeats belong to one press and a canceled press emits nothing`() {
    withHost { activity, closeRequests ->
      val controller = openPortal(activity, closeRequests)
      val downTime = nextDownTime()

      assertTrue(dispatch(activity, backDown(downTime)))
      assertTrue(dispatch(activity, backDown(downTime, repeatCount = 1)))
      assertTrue(dispatch(activity, backDown(downTime, repeatCount = 2)))
      assertTrue(dispatch(activity, backUp(downTime)))
      assertEquals(1, closeRequests.count)

      val canceledDownTime = nextDownTime()
      assertTrue(dispatch(activity, backDown(canceledDownTime)))
      assertTrue(dispatch(activity, backUp(canceledDownTime, flags = KeyEvent.FLAG_CANCELED)))

      assertEquals(1, closeRequests.count)
      assertEquals(0, activity.legacyBackCount)
      controller.dispose()
    }
  }

  @Test
  fun `press captured before the portal stops receiving Back is consumed without dispatch`() {
    withHost { activity, closeRequests ->
      val controller = openPortal(activity, closeRequests)
      val downTime = nextDownTime()

      assertTrue(dispatch(activity, backDown(downTime)))
      controller.update(inputState(hasCloseRequestHandler = false), usesPortalPresentation = true)
      assertTrue(dispatch(activity, backUp(downTime)))
      assertEquals(0, closeRequests.count)
      assertEquals(0, activity.legacyBackCount)

      val disposedDownTime = nextDownTime()
      controller.update(inputState(), usesPortalPresentation = true)
      assertTrue(dispatch(activity, backDown(disposedDownTime)))
      controller.dispose()
      assertTrue(dispatch(activity, backUp(disposedDownTime)))
      assertEquals(0, closeRequests.count)
      assertEquals(0, activity.legacyBackCount)

      pressBack(activity)
      assertEquals(1, activity.legacyBackCount)
    }
  }

  @Test
  fun `up without a captured down is left to the host`() {
    withHost { activity, closeRequests ->
      val downTime = nextDownTime()
      assertTrue(dispatch(activity, backDown(downTime)))
      val controller = openPortal(activity, closeRequests)

      assertTrue(dispatch(activity, backUp(downTime)))

      assertEquals(1, activity.legacyBackCount)
      assertEquals(0, closeRequests.count)
      controller.dispose()
    }
  }

  @Test
  fun `window callback is wrapped once and keeps routing below a later wrapper`() {
    withHost { activity, closeRequests ->
      val controller = openPortal(activity, closeRequests)
      val routingCallback = activity.window.callback
      val laterWrapper = RecordingWindowCallback(routingCallback)
      activity.window.callback = laterWrapper

      controller.clear()
      pressBack(activity)
      assertEquals(1, activity.legacyBackCount)

      controller.update(inputState(), usesPortalPresentation = true)
      pressBack(activity)

      assertSame(laterWrapper, activity.window.callback)
      assertEquals(4, laterWrapper.backKeyEventCount)
      assertEquals(1, closeRequests.count)
      assertEquals(1, activity.legacyBackCount)
      controller.dispose()
    }
  }

  @Test
  fun `window callback wrapper forwards other events and default methods`() {
    withHost { activity, closeRequests ->
      val earlierWrapper = RecordingWindowCallback(activity.window.callback)
      activity.window.callback = earlierWrapper
      val controller = openPortal(activity, closeRequests)
      val downTime = nextDownTime()

      dispatch(activity, KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0))
      activity.window.callback.onPointerCaptureChanged(true)

      assertEquals(1, earlierWrapper.otherKeyEventCount)
      assertEquals(1, earlierWrapper.pointerCaptureChangeCount)
      controller.dispose()
    }
  }

  private fun openPortal(
    activity: Activity,
    closeRequests: CloseRequestCounter,
    state: CloseRequestInputState = inputState(),
  ): TestPortal {
    val portal = View(activity)
    val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
    root.addView(portal)
    return TestPortal(portal, activity, closeRequests).also {
      it.update(state, usesPortalPresentation = true)
    }
  }

  private class TestPortal(portal: View, activity: Activity, closeRequests: CloseRequestCounter) {
    private val controller =
      PortalCloseRequestController(
        view = portal,
        currentActivity = { activity },
        emitCloseRequest = closeRequests::emit,
      )
    private val presentation =
      PortalPresentationController(portal, controller::onPresentationChanged)

    fun update(state: CloseRequestInputState, usesPortalPresentation: Boolean) {
      controller.update(state, usesPortalPresentation)
      presentation.update(isPortal = usesPortalPresentation, isActive = state.isPresentationActive)
    }

    fun clear() {
      presentation.clear()
      controller.clear()
    }

    fun dispose() {
      presentation.dispose()
      controller.dispose()
    }
  }

  private fun inputState(hasCloseRequestHandler: Boolean = true) =
    CloseRequestInputState(
      isAttached = true,
      isLifecycleActive = true,
      isModal = true,
      hasCloseRequestHandler = hasCloseRequestHandler,
      isPresentationActive = true,
      isTargetResolvedAndOpen = true,
    )

  private fun countingCallback(onBack: () -> Unit) =
    object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() = onBack()
    }

  private fun pressBack(activity: Activity) {
    val downTime = nextDownTime()
    dispatch(activity, backDown(downTime))
    dispatch(activity, backUp(downTime))
  }

  /** Enters through the decor view, as the platform does, so every window callback is traversed. */
  private fun dispatch(activity: Activity, event: KeyEvent): Boolean =
    activity.window.decorView.dispatchKeyEvent(event)

  private fun nextDownTime(): Long = nextBackDownTime.also { nextBackDownTime += 10L }

  private fun backDown(downTime: Long, repeatCount: Int = 0) =
    KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, repeatCount)

  private fun backUp(downTime: Long, flags: Int = 0) =
    KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, 0, -1, 0, flags)

  private fun withHost(block: (LegacyHostActivity, CloseRequestCounter) -> Unit) {
    val activityController = Robolectric.buildActivity(LegacyHostActivity::class.java).setup()
    try {
      val activity = activityController.get()
      activity.setContentView(TestReactRoot(activity))
      block(activity, CloseRequestCounter())
    } finally {
      activityController.close()
    }
  }
}

/** Models `ReactActivity` with a host that never calls `super.onBackPressed` (Expo, API <= 30). */
class LegacyHostActivity : ComponentActivity() {
  var legacyBackCount = 0

  @Deprecated("Models the host's legacy Back entry point.")
  @Suppress("OVERRIDE_DEPRECATION")
  override fun onBackPressed() {
    legacyBackCount++
  }
}

private class CloseRequestCounter {
  var count = 0

  fun emit(): Boolean {
    count++
    return true
  }
}

private class RecordingWindowCallback(private val delegate: Window.Callback) :
  Window.Callback by delegate {
  var backKeyEventCount = 0
  var otherKeyEventCount = 0
  var pointerCaptureChangeCount = 0

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode == KeyEvent.KEYCODE_BACK) backKeyEventCount++ else otherKeyEventCount++
    return delegate.dispatchKeyEvent(event)
  }

  override fun onPointerCaptureChanged(hasCapture: Boolean) {
    pointerCaptureChangeCount++
    delegate.onPointerCaptureChanged(hasCapture)
  }
}
