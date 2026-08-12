@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.events.BatchEventDispatchedListener
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.EventDispatcher
import com.facebook.react.uimanager.events.EventDispatcherListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class BottomSheetViewRequestCloseTest {
  private var nextEscapeDownTime = 1_000L

  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `ComponentActivity Back emits exactly one request`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)

      activity.onBackPressedDispatcher.onBackPressed()

      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `inline sheet installs no portal Back callback or Escape wrapper`() {
    withActivity<ComponentActivity> { activity ->
      val originalWindowCallback = activity.window.callback
      val originalBackCallbackCount = activity.onBackPressedDispatcher.callbackCountForTest()
      val listener = CountingBottomSheetListener()
      val sheet =
        configuredOpenSheet(activity, listener, handlerPresent = false).apply { modal = false }

      activity.setContentView(sheet)
      layoutPortal(sheet)

      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      sheet.destroy()
    }
  }

  @Test
  fun `portal modal without a handler installs no Back callback or Escape wrapper`() {
    withActivity<ComponentActivity> { activity ->
      val originalWindowCallback = activity.window.callback
      val originalBackCallbackCount = activity.onBackPressedDispatcher.callbackCountForTest()
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener, handlerPresent = false)

      activity.setContentView(sheet)
      layoutPortal(sheet)

      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      assertEquals(0, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `handler presence starts a sticky lifetime without changing later callback priority`() {
    withActivity<ComponentActivity> { activity ->
      val originalWindowCallback = activity.window.callback
      val originalBackCallbackCount = activity.onBackPressedDispatcher.callbackCountForTest()
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener, handlerPresent = false)

      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      sheet.setRequestCloseHandlerPresent(true)
      sheet.setRequestCloseEnabled(true)
      val escapeWindowCallback = activity.window.callback
      assertFalse(escapeWindowCallback === originalWindowCallback)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )

      sheet.setRequestCloseEnabled(false)
      sheet.setRequestCloseHandlerPresent(false)
      assertSame(escapeWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      var navigationCount = 0
      val navigationCallback =
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            navigationCount++
          }
        }
      activity.onBackPressedDispatcher.addCallback(navigationCallback)

      sheet.setRequestCloseHandlerPresent(true)
      sheet.setRequestCloseEnabled(true)
      assertSame(escapeWindowCallback, activity.window.callback)
      activity.onBackPressedDispatcher.onBackPressed()

      assertEquals(1, navigationCount)
      assertEquals(0, listener.requestCloseCount)
      navigationCallback.isEnabled = false
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `pause and resume preserve later Back callback priority and Escape registration`() {
    withActivity<ComponentActivity> { activity ->
      val owner = MutableTestDispatcherOwner().apply { resume() }
      val container = FrameLayout(activity)
      container.setViewTreeOnBackPressedDispatcherOwner(owner)
      container.setViewTreeLifecycleOwner(owner)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      container.addView(sheet)
      activity.setContentView(container)
      layoutPortal(sheet)
      val escapeWindowCallback = activity.window.callback

      owner.pause()
      var navigationCount = 0
      val navigationCallback =
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            navigationCount++
          }
        }
      owner.onBackPressedDispatcher.addCallback(navigationCallback)
      owner.resumeFromPause()

      assertSame(escapeWindowCallback, activity.window.callback)
      owner.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, navigationCount)
      assertEquals(0, listener.requestCloseCount)
      navigationCallback.isEnabled = false
      owner.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `close and reopen preserve later Back callback priority and Escape registration`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val escapeWindowCallback = activity.window.callback

      sheet.setIndex(0)
      assertFalse(sheet.hostForTest().isRequestCloseTargetOpen)
      var navigationCount = 0
      val navigationCallback =
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            navigationCount++
          }
        }
      activity.onBackPressedDispatcher.addCallback(navigationCallback)
      sheet.setIndex(1)

      assertTrue(sheet.hostForTest().isRequestCloseTargetOpen)
      assertSame(escapeWindowCallback, activity.window.callback)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, navigationCount)
      assertEquals(0, listener.requestCloseCount)
      navigationCallback.isEnabled = false
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `predictive Back selected before eligibility loss commits as a no-op`() {
    withActivity<ComponentActivity> { activity ->
      var navigationCount = 0
      activity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            navigationCount++
          }
        }
      )
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)

      activity.onBackPressedDispatcher.dispatchOnBackStarted(
        BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)
      )
      sheet.setRequestCloseEnabled(false)
      activity.onBackPressedDispatcher.onBackPressed()

      assertEquals(0, listener.requestCloseCount)
      assertEquals(0, navigationCount)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, navigationCount)
      sheet.destroy()
    }
  }

  @Test
  fun `custom non-Activity dispatcher handles Back through the view tree`() {
    withActivity<ComponentActivity> { activity ->
      var fallbackCount = 0
      val owner = TestDispatcherOwner { fallbackCount++ }.apply { resume() }
      val container = FrameLayout(activity)
      container.setViewTreeOnBackPressedDispatcherOwner(owner)
      container.setViewTreeLifecycleOwner(owner)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      container.addView(sheet)
      activity.setContentView(container)
      layoutPortal(sheet)

      owner.onBackPressedDispatcher.onBackPressed()

      assertEquals(1, listener.requestCloseCount)
      assertEquals(0, fallbackCount)
      sheet.destroy()
    }
  }

  @Test
  fun `custom dispatcher lifecycle controls Back and Escape without a lifecycle tag`() {
    withActivity<ComponentActivity> { activity ->
      var fallbackCount = 0
      val owner = MutableTestDispatcherOwner { fallbackCount++ }
      owner.resume()
      val container = FrameLayout(activity)
      container.setViewTreeOnBackPressedDispatcherOwner(owner)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      container.addView(sheet)
      activity.setContentView(container)
      layoutPortal(sheet)

      owner.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, listener.requestCloseCount)
      assertEscape(activity, expectedHandled = true)
      assertEquals(2, listener.requestCloseCount)

      owner.pause()
      owner.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, fallbackCount)
      assertEscape(activity, expectedHandled = false)
      assertEquals(2, listener.requestCloseCount)

      owner.resumeFromPause()
      owner.onBackPressedDispatcher.onBackPressed()
      assertEquals(3, listener.requestCloseCount)
      assertEscape(activity, expectedHandled = true)
      assertEquals(4, listener.requestCloseCount)

      owner.destroy()
      owner.onBackPressedDispatcher.onBackPressed()
      assertEquals(2, fallbackCount)
      assertEscape(activity, expectedHandled = false)
      assertEquals(4, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `Escape captured in RESUMED is consumed after pause without emission`() {
    withActivity<ComponentActivity> { activity ->
      val owner = MutableTestDispatcherOwner().apply { resume() }
      val container = FrameLayout(activity)
      container.setViewTreeOnBackPressedDispatcherOwner(owner)
      container.setViewTreeLifecycleOwner(owner)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      container.addView(sheet)
      activity.setContentView(container)
      layoutPortal(sheet)
      val downTime = 100L
      val escapeWindowCallback = activity.window.callback

      assertTrue(activity.window.callback.dispatchKeyEvent(escapeDown(downTime)))
      owner.pause()
      assertTrue(activity.window.callback.dispatchKeyEvent(escapeUp(downTime)))

      assertEquals(0, listener.requestCloseCount)
      assertSame(escapeWindowCallback, activity.window.callback)
      sheet.destroy()
    }
  }

  @Test
  fun `Escape captured while eligible is consumed after handler disables without emission`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val escapeWindowCallback = activity.window.callback
      val downTime = 125L

      assertTrue(activity.window.callback.dispatchKeyEvent(escapeDown(downTime)))
      sheet.setRequestCloseEnabled(false)
      assertSame(escapeWindowCallback, activity.window.callback)
      assertTrue(activity.window.callback.dispatchKeyEvent(escapeUp(downTime)))

      assertEquals(0, listener.requestCloseCount)
      assertSame(escapeWindowCallback, activity.window.callback)
      sheet.destroy()
    }
  }

  @Test
  fun `DESTROYED clears a captured Escape and restores the window callback`() {
    withActivity<ComponentActivity> { activity ->
      val owner = MutableTestDispatcherOwner().apply { resume() }
      val container = FrameLayout(activity)
      container.setViewTreeOnBackPressedDispatcherOwner(owner)
      container.setViewTreeLifecycleOwner(owner)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      container.addView(sheet)
      activity.setContentView(container)
      layoutPortal(sheet)
      val downTime = 150L

      assertTrue(activity.window.callback.dispatchKeyEvent(escapeDown(downTime)))
      owner.destroy()

      assertSame(activity, activity.window.callback)
      assertFalse(sheet.dispatchKeyEvent(escapeUp(downTime)))
      assertEquals(0, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `detach resets portal registrations and does not carry stickiness to reattach`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      activity.setContentView(root)
      val originalWindowCallback = activity.window.callback
      val originalBackCallbackCount = activity.onBackPressedDispatcher.callbackCountForTest()
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      root.addView(sheet)
      layoutPortal(sheet)

      assertFalse(activity.window.callback === originalWindowCallback)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )

      sheet.setRequestCloseEnabled(false)
      sheet.setRequestCloseHandlerPresent(false)
      root.removeView(sheet)

      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )

      root.addView(sheet)
      layoutPortal(sheet)
      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )

      sheet.setRequestCloseHandlerPresent(true)
      sheet.setRequestCloseEnabled(true)
      assertFalse(activity.window.callback === originalWindowCallback)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      sheet.destroy()
    }
  }

  @Test
  fun `reparenting without a handler removes old registrations and stays lazy on the new host`() {
    val firstController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val secondController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    try {
      val firstActivity = firstController.get()
      val secondActivity = secondController.get()
      val secondWindowCallback = secondActivity.window.callback
      val secondBackCallbackCount = secondActivity.onBackPressedDispatcher.callbackCountForTest()
      var firstFallbackCount = 0
      firstActivity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            firstFallbackCount++
          }
        }
      )
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(firstActivity, listener)
      firstActivity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, listener.requestCloseCount)

      sheet.setRequestCloseEnabled(false)
      sheet.setRequestCloseHandlerPresent(false)

      (sheet.parent as FrameLayout).removeView(sheet)
      secondActivity.setContentView(sheet)
      layoutPortal(sheet)

      assertSame(firstActivity, firstActivity.window.callback)
      firstActivity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, firstFallbackCount)
      assertEscape(firstActivity, expectedHandled = false)
      assertSame(secondWindowCallback, secondActivity.window.callback)
      assertEquals(
        secondBackCallbackCount,
        secondActivity.onBackPressedDispatcher.callbackCountForTest(),
      )

      sheet.setRequestCloseHandlerPresent(true)
      sheet.setRequestCloseEnabled(true)
      secondActivity.onBackPressedDispatcher.onBackPressed()
      assertEquals(2, listener.requestCloseCount)
      assertEscape(secondActivity, expectedHandled = true)
      assertEquals(3, listener.requestCloseCount)
      sheet.destroy()
    } finally {
      secondController.close()
      firstController.close()
    }
  }

  @Test
  fun `custom owner inside ComponentDialog owns Back and Escape instead of its Activity`() {
    withActivity<ComponentActivity> { activity ->
      var activityBackCount = 0
      activity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            activityBackCount++
          }
        }
      )
      val dialog = ComponentDialog(activity)
      val owner = MutableTestDispatcherOwner().apply { resume() }
      val container = FrameLayout(activity)
      container.setViewTreeOnBackPressedDispatcherOwner(owner)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      container.addView(sheet)
      dialog.setContentView(container)
      dialog.show()
      shadowOf(Looper.getMainLooper()).idle()
      layoutPortal(sheet)
      try {
        owner.onBackPressedDispatcher.onBackPressed()
        assertEquals(1, listener.requestCloseCount)
        assertEquals(0, activityBackCount)
        assertEscape(dialog, expectedHandled = true)
        assertEquals(2, listener.requestCloseCount)
        assertSame(activity, activity.window.callback)
        assertEquals(0, activityBackCount)
      } finally {
        sheet.destroy()
        dialog.dismiss()
      }
    }
  }

  @Test
  fun `plain Activity passes Back through while handling Escape from its window`() {
    withActivity<Activity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      activity.setContentView(sheet)
      sheet.onHostResume()
      layoutPortal(sheet)

      // A plain Activity exposes no OnBackPressedDispatcherOwner to the portal.
      assertEscape(activity, expectedHandled = true)
      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `stale current ComponentActivity receives no portal Back callback or Escape listener`() {
    val portalController = Robolectric.buildActivity(Activity::class.java).setup()
    val staleController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val portalActivity = portalController.get()
    val staleActivity = staleController.get()
    val reactContext = BridgeReactContext(portalActivity.applicationContext)
    reactContext.onHostResume(staleActivity)
    val themedContext = ThemedReactContext(reactContext, portalActivity, "test", 1)
    var staleBackCount = 0
    staleActivity.onBackPressedDispatcher.addCallback(
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          staleBackCount++
        }
      }
    )
    val listener = CountingBottomSheetListener()
    val sheet = configuredOpenSheet(themedContext, listener)
    try {
      portalActivity.setContentView(sheet)
      layoutPortal(sheet)

      staleActivity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, staleBackCount)
      assertEquals(0, listener.requestCloseCount)
      assertSame(staleActivity, staleActivity.window.callback)
      assertEscape(staleActivity, expectedHandled = false)
      assertEquals(0, listener.requestCloseCount)
    } finally {
      sheet.destroy()
      reactContext.onHostDestroy()
      staleController.close()
      portalController.close()
    }
  }

  @Test
  fun `most recently attached eligible portal receives Back and Escape`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperSheet = configuredOpenSheet(activity, upperListener)
      root.addView(lowerSheet)
      root.addView(upperSheet)
      activity.setContentView(root)
      layoutPortal(lowerSheet)
      layoutPortal(upperSheet)

      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity, expectedHandled = true)

      assertEquals(0, lowerListener.requestCloseCount)
      assertEquals(2, upperListener.requestCloseCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `only the eligible portal receives Back and Escape as eligibility moves`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperSheet = configuredOpenSheet(activity, upperListener)
      root.addView(lowerSheet)
      root.addView(upperSheet)
      activity.setContentView(root)
      layoutPortal(lowerSheet)
      layoutPortal(upperSheet)

      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity, expectedHandled = true)
      assertEquals(0, lowerListener.requestCloseCount)
      assertEquals(2, upperListener.requestCloseCount)

      upperSheet.setRequestCloseEnabled(false)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity, expectedHandled = true)
      assertEquals(2, lowerListener.requestCloseCount)
      assertEquals(2, upperListener.requestCloseCount)

      lowerSheet.setRequestCloseEnabled(false)
      upperSheet.setRequestCloseEnabled(true)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity, expectedHandled = true)
      assertEquals(2, lowerListener.requestCloseCount)
      assertEquals(4, upperListener.requestCloseCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `ineligible portal keeps its Escape registration and delegates to the window callback`() {
    withActivity<ComponentActivity> { activity ->
      val originalCallback = requireNotNull(activity.window.callback)
      var delegatedEventCount = 0
      val delegate =
        object : Window.Callback by originalCallback {
          override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            delegatedEventCount++
            return true
          }
        }
      activity.window.callback = delegate
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val escapeWindowCallback = activity.window.callback

      sheet.setRequestCloseEnabled(false)
      assertSame(escapeWindowCallback, activity.window.callback)
      assertTrue(activity.window.callback.dispatchKeyEvent(escapeDown(350L)))
      assertTrue(activity.window.callback.dispatchKeyEvent(escapeUp(350L)))

      assertEquals(2, delegatedEventCount)
      assertEquals(0, listener.requestCloseCount)
      sheet.destroy()
      assertSame(delegate, activity.window.callback)
    }
  }

  @Test
  fun `same Escape event is not delivered twice through view and window paths`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val callback = activity.window.callback
      val down = escapeDown(400L)
      val up = escapeUp(400L)

      assertTrue(sheet.dispatchKeyEvent(down))
      assertTrue(callback.dispatchKeyEvent(down))
      assertTrue(callback.dispatchKeyEvent(up))
      assertTrue(sheet.dispatchKeyEvent(up))

      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Suppress("DEPRECATION")
  @Test
  fun `nativeOverlay keeps dialog ownership and does not close itself`() {
    withActivity<ComponentActivity> { activity ->
      val reactContext = BridgeReactContext(activity.applicationContext)
      reactContext.onHostResume(activity)
      val themedContext = ThemedReactContext(reactContext, activity, "test", 1)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(themedContext, listener)
      sheet.eventDispatcher = NoOpEventDispatcher
      activity.setContentView(sheet)
      layoutPortal(sheet)
      sheet.setNativeOverlay(true)
      shadowOf(Looper.getMainLooper()).idle()
      val dialog = sheet.overlayDialogForTest()
      layoutView(sheet.overlayRootForTest())
      // The test context was resumed before BottomSheetView registered its listener.
      sheet.onHostResume()

      assertTrue(
        "dialog lifecycle must be active",
        dialog.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
      )
      assertTrue("overlay target must resolve open", sheet.hostForTest().isRequestCloseTargetOpen)

      dialog.onBackPressedDispatcher.onBackPressed()
      dialog.onBackPressedDispatcher.onBackPressed()
      assertEscape(dialog, expectedHandled = true)

      assertEquals(3, listener.requestCloseCount)
      assertTrue(dialog.isShowing)
      sheet.destroy()
      reactContext.onHostDestroy()
    }
  }

  @Suppress("DEPRECATION")
  @Test
  fun `nativeOverlay without a handler consumes close input while open and closing`() {
    withActivity<ComponentActivity> { activity ->
      val reactContext = BridgeReactContext(activity.applicationContext)
      reactContext.onHostResume(activity)
      val themedContext = ThemedReactContext(reactContext, activity, "test", 1)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(themedContext, listener, handlerPresent = false)
      sheet.eventDispatcher = NoOpEventDispatcher
      activity.setContentView(sheet)
      layoutPortal(sheet)
      sheet.setNativeOverlay(true)
      shadowOf(Looper.getMainLooper()).idle()
      val dialog = sheet.overlayDialogForTest()
      layoutView(sheet.overlayRootForTest())
      sheet.onHostResume()

      dialog.onBackPressedDispatcher.onBackPressed()
      assertEscape(dialog, expectedHandled = true)

      assertEquals(0, listener.requestCloseCount)
      assertTrue(sheet.hostForTest().isRequestCloseTargetOpen)
      assertTrue(dialog.isShowing)

      sheet.setIndex(0)
      assertFalse(sheet.hostForTest().isRequestCloseTargetOpen)
      dialog.onBackPressedDispatcher.onBackPressed()
      assertEscape(dialog, expectedHandled = true)

      assertEquals(0, listener.requestCloseCount)
      assertFalse(sheet.hostForTest().isRequestCloseTargetOpen)
      assertTrue(dialog.isShowing)
      sheet.destroy()
      reactContext.onHostDestroy()
    }
  }

  @Test
  fun `modal false resets portal registrations and a missing handler keeps reactivation lazy`() {
    withActivity<ComponentActivity> { activity ->
      val originalWindowCallback = activity.window.callback
      val originalBackCallbackCount = activity.onBackPressedDispatcher.callbackCountForTest()
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val firstPortalWindowCallback = activity.window.callback

      assertFalse(firstPortalWindowCallback === originalWindowCallback)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )

      sheet.setRequestCloseEnabled(false)
      sheet.setRequestCloseHandlerPresent(false)
      assertSame(firstPortalWindowCallback, activity.window.callback)
      sheet.modal = false

      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )

      sheet.modal = true
      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )

      sheet.setRequestCloseHandlerPresent(true)
      sheet.setRequestCloseEnabled(true)
      assertFalse(activity.window.callback === originalWindowCallback)
      assertFalse(activity.window.callback === firstPortalWindowCallback)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      sheet.destroy()
    }
  }

  @Test
  fun `switching to nativeOverlay removes portal handlers and switching back reinstalls them`() {
    withActivity<ComponentActivity> { activity ->
      val reactContext = BridgeReactContext(activity.applicationContext)
      reactContext.onHostResume(activity)
      val themedContext = ThemedReactContext(reactContext, activity, "test", 1)
      var activityBackCount = 0
      activity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            activityBackCount++
          }
        }
      )
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(themedContext, listener)
      sheet.eventDispatcher = NoOpEventDispatcher
      activity.setContentView(sheet)
      layoutPortal(sheet)
      sheet.onHostResume()
      val portalWindowCallback = activity.window.callback
      assertFalse(portalWindowCallback === activity)

      sheet.setNativeOverlay(true)
      shadowOf(Looper.getMainLooper()).idle()
      assertSame(activity, activity.window.callback)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, activityBackCount)
      assertEquals(0, listener.requestCloseCount)

      sheet.setNativeOverlay(false)
      layoutPortal(sheet)
      assertFalse(activity.window.callback === activity)
      assertFalse(activity.window.callback === portalWindowCallback)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
      reactContext.onHostDestroy()
    }
  }

  private fun configuredOpenSheet(
    context: Context,
    listener: CountingBottomSheetListener,
    handlerPresent: Boolean = true,
  ): BottomSheetView =
    BottomSheetView(context).apply {
      this.listener = listener
      animateIn = false
      modal = true
      setRequestCloseHandlerPresent(handlerPresent)
      setRequestCloseEnabled(handlerPresent)
      setDetents(
        listOf(
          mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
          mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
        )
      )
      setIndex(1)
    }

  private fun openPortalSheet(
    activity: Activity,
    listener: CountingBottomSheetListener,
    handlerPresent: Boolean = true,
  ): BottomSheetView =
    configuredOpenSheet(activity, listener, handlerPresent).also {
      activity.setContentView(it)
      layoutPortal(it)
    }

  private fun layoutPortal(sheet: BottomSheetView) {
    layoutView(sheet)
  }

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

  private fun assertEscape(
    activity: Activity,
    expectedHandled: Boolean,
  ) {
    val downTime = nextEscapeDownTime
    nextEscapeDownTime += 10L
    assertEquals(expectedHandled, activity.window.callback.dispatchKeyEvent(escapeDown(downTime)))
    assertEquals(expectedHandled, activity.window.callback.dispatchKeyEvent(escapeUp(downTime)))
  }

  private fun assertEscape(
    dialog: ComponentDialog,
    expectedHandled: Boolean,
  ) {
    val downTime = nextEscapeDownTime
    nextEscapeDownTime += 10L
    assertEquals(expectedHandled, dialog.window?.callback?.dispatchKeyEvent(escapeDown(downTime)))
    assertEquals(expectedHandled, dialog.window?.callback?.dispatchKeyEvent(escapeUp(downTime)))
  }

  private fun escapeDown(downTime: Long) =
    KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0)

  private fun escapeUp(downTime: Long) =
    KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0)

  private inline fun <reified T : Activity> withActivity(block: (T) -> Unit) {
    val controller = Robolectric.buildActivity(T::class.java).setup()
    try {
      block(controller.get())
    } finally {
      controller.close()
    }
  }
}

private fun BottomSheetView.overlayDialogForTest(): ComponentDialog =
  requireNotNull(
    BottomSheetView::class.java.getDeclaredField("overlayDialog").let {
      it.isAccessible = true
      it.get(this) as? ComponentDialog
    }
  )

private fun BottomSheetView.overlayRootForTest(): View =
  requireNotNull(
    BottomSheetView::class.java.getDeclaredField("overlayRoot").let {
      it.isAccessible = true
      it.get(this) as? View
    }
  )

private fun BottomSheetView.hostForTest(): BottomSheetHostView =
  requireNotNull(
    BottomSheetView::class.java.getDeclaredField("host").let {
      it.isAccessible = true
      it.get(this) as? BottomSheetHostView
    }
  )

private fun OnBackPressedDispatcher.callbackCountForTest(): Int =
  OnBackPressedDispatcher::class.java.getDeclaredField("onBackPressedCallbacks").let {
    it.isAccessible = true
    @Suppress("UNCHECKED_CAST") (it.get(this) as Collection<OnBackPressedCallback>).size
  }

private class MutableTestDispatcherOwner(fallback: () -> Unit = {}) : OnBackPressedDispatcherOwner {
  private val lifecycleRegistry = LifecycleRegistry(this)

  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  override val onBackPressedDispatcher = OnBackPressedDispatcher(fallback)

  fun resume() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }

  fun pause() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
  }

  fun resumeFromPause() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }

  fun destroy() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  }
}

private class CountingBottomSheetListener : BottomSheetViewListener {
  var requestCloseCount = 0

  override fun onIndexChange(index: Int) = Unit

  override fun onSettle(index: Int) = Unit

  override fun onPositionChange(position: Double, index: Double) = Unit

  override fun onRequestClose() {
    requestCloseCount++
  }
}

private object NoOpEventDispatcher : EventDispatcher {
  override fun dispatchEvent(event: Event<*>) = Unit

  override fun dispatchAllEvents() = Unit

  override fun addListener(listener: EventDispatcherListener) = Unit

  override fun removeListener(listener: EventDispatcherListener) = Unit

  override fun addBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  override fun removeBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  @Suppress("DEPRECATION") override fun onCatalystInstanceDestroyed() = Unit
}
