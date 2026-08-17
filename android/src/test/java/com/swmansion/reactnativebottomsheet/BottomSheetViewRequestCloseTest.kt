@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
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
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [27, 35])
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
  fun `unhandled Portal Escape emits once without replacing the Window callback`() {
    withActivity<ComponentActivity> { activity ->
      val originalWindowCallback = activity.window.callback
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)

      assertSame(originalWindowCallback, activity.window.callback)
      assertPortalEscape(activity, expectedHandled = true)

      assertEquals(1, listener.requestCloseCount)
      assertSame(originalWindowCallback, activity.window.callback)
      sheet.destroy()
      assertSame(originalWindowCallback, activity.window.callback)
    }
  }

  @Test
  fun `eligible portal intercepts the full Escape sequence before a focused descendant and emits once`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val focusedView = EscapeConsumingView(activity)
      focusedView.isFocusableInTouchMode = true
      sheet.addView(focusedView, ViewGroup.LayoutParams(1, 1))
      layoutView(sheet)
      assertTrue(focusedView.requestFocus())

      assertPortalEscape(activity, expectedHandled = true)

      assertEquals(0, focusedView.escapeEventCount)
      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `ordinary keys still reach a focused portal descendant`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val focusedView = EscapeConsumingView(activity)
      focusedView.isFocusableInTouchMode = true
      sheet.addView(focusedView, ViewGroup.LayoutParams(1, 1))
      layoutView(sheet)
      assertTrue(focusedView.requestFocus())
      val downTime = 90L

      assertTrue(
        focusedView.dispatchKeyEvent(
          KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0)
        )
      )
      assertTrue(
        focusedView.dispatchKeyEvent(
          KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0)
        )
      )

      assertEquals(2, focusedView.ordinaryEventCount)
      assertEquals(0, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Config(sdk = [27])
  @Test
  fun `ComponentActivity compat fallback receives unhandled Escape outside the portal`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      val sibling = EscapeConsumingView(activity, consumesEscape = false)
      sibling.isFocusableInTouchMode = true
      root.addView(sheet)
      root.addView(sibling, FrameLayout.LayoutParams(1, 1))
      activity.setContentView(root)
      layoutPortal(sheet)
      assertTrue(sibling.requestFocus())

      assertPortalEscape(activity, expectedHandled = true)

      assertEquals(1, sibling.escapeEventCount)
      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `inline sheet installs no portal Back callback or Escape listener`() {
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
      assertTrue(sheet.unhandledKeyListenerRegistrationsForTest().isEmpty())
      sheet.destroy()
    }
  }

  @Test
  fun `portal modal without a handler installs no Back callback or Escape listener`() {
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
      assertTrue(sheet.unhandledKeyListenerRegistrationsForTest().isEmpty())
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
      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      val installedBackCallback = activity.onBackPressedDispatcher.callbacksForTest().last()
      assertEquals(1, sheet.unhandledKeyListenerRegistrationsForTest().size)

      sheet.setRequestCloseHandlerPresent(false)
      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      assertSame(installedBackCallback, activity.onBackPressedDispatcher.callbacksForTest().last())
      assertEquals(1, sheet.unhandledKeyListenerRegistrationsForTest().size)
      var navigationCount = 0
      val navigationCallback =
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            navigationCount++
          }
        }
      activity.onBackPressedDispatcher.addCallback(navigationCallback)

      sheet.setRequestCloseHandlerPresent(true)
      assertSame(originalWindowCallback, activity.window.callback)
      assertSame(
        installedBackCallback,
        activity.onBackPressedDispatcher.callbacksForTest()[originalBackCallbackCount],
      )
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
      val originalWindowCallback = activity.window.callback

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

      assertSame(originalWindowCallback, activity.window.callback)
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
  fun `Escape listener identity stays stable through transient eligibility changes`() {
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
      val registration = sheet.unhandledKeyListenerRegistrationsForTest().single()

      owner.pause()
      assertSame(registration, sheet.unhandledKeyListenerRegistrationsForTest().single())
      sheet.setIndex(0)
      assertSame(registration, sheet.unhandledKeyListenerRegistrationsForTest().single())
      sheet.setRequestCloseHandlerPresent(false)
      assertSame(registration, sheet.unhandledKeyListenerRegistrationsForTest().single())

      owner.resumeFromPause()
      sheet.setIndex(1)
      sheet.setRequestCloseHandlerPresent(true)
      assertSame(registration, sheet.unhandledKeyListenerRegistrationsForTest().single())
      sheet.destroy()
      assertTrue(sheet.unhandledKeyListenerRegistrationsForTest().isEmpty())
    }
  }

  @Test
  fun `close and reopen preserve later Back callback priority and Escape registration`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val originalWindowCallback = activity.window.callback

      sheet.setIndex(0)
      assertFalse(sheet.hostForTest().isRequestCloseTargetOpen)
      assertPortalEscape(activity, expectedHandled = false)
      assertEquals(0, listener.requestCloseCount)
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
      assertSame(originalWindowCallback, activity.window.callback)
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
      sheet.setIndex(0)
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
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(2, listener.requestCloseCount)

      owner.pause()
      owner.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, fallbackCount)
      assertPortalEscape(activity, expectedHandled = false)
      assertEquals(2, listener.requestCloseCount)

      owner.resumeFromPause()
      owner.onBackPressedDispatcher.onBackPressed()
      assertEquals(3, listener.requestCloseCount)
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(4, listener.requestCloseCount)

      owner.destroy()
      owner.onBackPressedDispatcher.onBackPressed()
      assertEquals(2, fallbackCount)
      assertPortalEscape(activity, expectedHandled = false)
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
      val originalWindowCallback = activity.window.callback

      assertTrue(activity.dispatchKeyEvent(escapeDown(downTime)))
      owner.pause()
      assertTrue(activity.dispatchKeyEvent(escapeUp(downTime)))

      assertEquals(0, listener.requestCloseCount)
      assertSame(originalWindowCallback, activity.window.callback)
      sheet.destroy()
    }
  }

  @Test
  fun `Escape captured while eligible is consumed after handler disables without emission`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val originalWindowCallback = activity.window.callback
      val downTime = 125L

      assertTrue(activity.dispatchKeyEvent(escapeDown(downTime)))
      sheet.setRequestCloseHandlerPresent(false)
      assertSame(originalWindowCallback, activity.window.callback)
      assertTrue(activity.dispatchKeyEvent(escapeUp(downTime)))

      assertEquals(0, listener.requestCloseCount)
      assertSame(originalWindowCallback, activity.window.callback)
      sheet.destroy()
    }
  }

  @Test
  fun `Portal Escape accepts only an initial unmodified press and emits on terminal key-up`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)

      val modifiedDownTime = 130L
      assertFalse(
        activity.dispatchKeyEvent(escapeDown(modifiedDownTime, metaState = KeyEvent.META_SHIFT_ON))
      )
      assertFalse(
        activity.dispatchKeyEvent(escapeUp(modifiedDownTime, metaState = KeyEvent.META_SHIFT_ON))
      )

      val repeatedDownTime = 140L
      assertTrue(activity.dispatchKeyEvent(escapeDown(repeatedDownTime)))
      assertTrue(activity.dispatchKeyEvent(escapeDown(repeatedDownTime, repeatCount = 1)))
      assertEquals(0, listener.requestCloseCount)
      assertTrue(activity.dispatchKeyEvent(escapeUp(repeatedDownTime)))
      assertEquals(1, listener.requestCloseCount)

      val canceledDownTime = 145L
      assertTrue(activity.dispatchKeyEvent(escapeDown(canceledDownTime)))
      assertTrue(activity.dispatchKeyEvent(escapeUp(canceledDownTime, canceled = true)))
      assertEquals(1, listener.requestCloseCount)

      val modifiedUpDownTime = 146L
      assertTrue(activity.dispatchKeyEvent(escapeDown(modifiedUpDownTime)))
      assertTrue(
        activity.dispatchKeyEvent(escapeUp(modifiedUpDownTime, metaState = KeyEvent.META_SHIFT_ON))
      )
      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `Portal Escape replaces an orphaned captured press when its up never arrives`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val orphanedDownTime = 148L
      val replacementDownTime = 149L

      assertTrue(activity.dispatchKeyEvent(escapeDown(orphanedDownTime)))
      assertTrue(activity.dispatchKeyEvent(escapeDown(replacementDownTime)))
      assertTrue(activity.dispatchKeyEvent(escapeUp(replacementDownTime)))

      assertEquals(1, listener.requestCloseCount)

      val nextDownTime = 150L
      assertTrue(activity.dispatchKeyEvent(escapeDown(nextDownTime)))
      assertTrue(activity.dispatchKeyEvent(escapeUp(nextDownTime)))
      assertEquals(2, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `Portal Escape press that begins ineligible remains unclaimed`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val downTime = 147L

      sheet.setRequestCloseHandlerPresent(false)
      assertFalse(activity.dispatchKeyEvent(escapeDown(downTime)))
      sheet.setRequestCloseHandlerPresent(true)
      assertFalse(activity.dispatchKeyEvent(escapeDown(downTime, repeatCount = 1)))
      assertFalse(activity.dispatchKeyEvent(escapeUp(downTime)))
      assertEquals(0, listener.requestCloseCount)

      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `Portal Escape up without an active down is unhandled and does not emit`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)

      assertFalse(activity.dispatchKeyEvent(escapeUp(148L)))
      assertEquals(0, listener.requestCloseCount)

      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(1, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `DESTROYED consumes a captured Escape without emission or Window callback changes`() {
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
      val originalWindowCallback = activity.window.callback

      assertTrue(activity.dispatchKeyEvent(escapeDown(downTime)))
      owner.destroy()

      assertSame(originalWindowCallback, activity.window.callback)
      assertTrue(activity.dispatchKeyEvent(escapeUp(downTime)))
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

      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      assertEquals(1, sheet.unhandledKeyListenerRegistrationsForTest().size)

      sheet.setRequestCloseHandlerPresent(false)
      root.removeView(sheet)

      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      assertTrue(sheet.unhandledKeyListenerRegistrationsForTest().isEmpty())

      root.addView(sheet)
      layoutPortal(sheet)
      assertSame(originalWindowCallback, activity.window.callback)
      assertPortalEscape(activity, expectedHandled = false)
      assertEquals(
        originalBackCallbackCount,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      assertTrue(sheet.unhandledKeyListenerRegistrationsForTest().isEmpty())

      sheet.setRequestCloseHandlerPresent(true)
      assertSame(originalWindowCallback, activity.window.callback)
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(1, listener.requestCloseCount)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      assertEquals(1, sheet.unhandledKeyListenerRegistrationsForTest().size)
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
      secondActivity.onBackPressedDispatcher.onBackPressed()
      assertEquals(2, listener.requestCloseCount)
      assertPortalEscape(secondActivity, expectedHandled = true)
      assertEquals(3, listener.requestCloseCount)
      sheet.destroy()
    } finally {
      secondController.close()
      firstController.close()
    }
  }

  @Test
  fun `dispatcher change in the same root preserves membership position`() {
    withActivity<ComponentActivity> { activity ->
      val firstOwner = MutableTestDispatcherOwner().apply { resume() }
      val secondOwner = MutableTestDispatcherOwner().apply { resume() }
      val root = FrameLayout(activity)
      val upperHost = FrameLayout(activity)
      upperHost.setViewTreeOnBackPressedDispatcherOwner(firstOwner)
      upperHost.setViewTreeLifecycleOwner(firstOwner)
      val earlierListener = CountingBottomSheetListener()
      val laterListener = CountingBottomSheetListener()
      val earlierSheet = configuredOpenSheet(activity, earlierListener)
      val laterSheet = configuredOpenSheet(activity, laterListener)
      upperHost.addView(earlierSheet)
      root.addView(upperHost)
      root.addView(laterSheet)
      activity.setContentView(root)
      layoutView(root)

      assertEquals(1, firstOwner.onBackPressedDispatcher.callbackCountForTest())
      assertEquals(0, secondOwner.onBackPressedDispatcher.callbackCountForTest())

      upperHost.setViewTreeOnBackPressedDispatcherOwner(secondOwner)
      upperHost.setViewTreeLifecycleOwner(secondOwner)
      earlierSheet.onWindowFocusChanged(true)

      assertEquals(0, firstOwner.onBackPressedDispatcher.callbackCountForTest())
      assertEquals(1, secondOwner.onBackPressedDispatcher.callbackCountForTest())
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(0, earlierListener.requestCloseCount)
      assertEquals(1, laterListener.requestCloseCount)

      earlierSheet.isFocusableInTouchMode = true
      assertTrue(earlierSheet.requestFocus())
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(0, earlierListener.requestCloseCount)
      assertEquals(2, laterListener.requestCloseCount)
      laterSheet.destroy()
      earlierSheet.destroy()
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
      val originalDialogWindowCallback = dialog.window?.callback
      try {
        owner.onBackPressedDispatcher.onBackPressed()
        assertEquals(1, listener.requestCloseCount)
        assertEquals(0, activityBackCount)
        assertPortalEscape(dialog, expectedHandled = true)
        assertEquals(2, listener.requestCloseCount)
        assertSame(activity, activity.window.callback)
        assertSame(originalDialogWindowCallback, dialog.window?.callback)
        assertEquals(0, activityBackCount)
      } finally {
        sheet.destroy()
        dialog.dismiss()
      }
    }
  }

  @Test
  fun `plain Activity passes Back through while handling unhandled Portal Escape`() {
    withActivity<Activity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      activity.setContentView(sheet)
      sheet.onHostResume()
      layoutPortal(sheet)

      // A plain Activity exposes no OnBackPressedDispatcherOwner to the portal.
      assertPortalEscape(activity, expectedHandled = true)
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
      assertPortalEscape(activity, expectedHandled = true)

      assertEquals(0, lowerListener.requestCloseCount)
      assertEquals(2, upperListener.requestCloseCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `zero and positive upper target move Back and Escape ownership without reordering`() {
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

      upperSheet.setIndex(0)
      activity.onBackPressedDispatcher.onBackPressed()
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(2, lowerListener.requestCloseCount)
      assertEquals(0, upperListener.requestCloseCount)

      upperSheet.setIndex(1)
      assertTrue(upperSheet.requestFocus())
      activity.onBackPressedDispatcher.onBackPressed()
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(2, lowerListener.requestCloseCount)
      assertEquals(2, upperListener.requestCloseCount)

      upperSheet.setIndex(0)
      assertTrue(lowerSheet.requestFocus())
      activity.onBackPressedDispatcher.onBackPressed()
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(4, lowerListener.requestCloseCount)
      assertEquals(2, upperListener.requestCloseCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `upper content target moves ownership from unresolved zero to positive and back`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperContent = MutableContentHeightView(activity)
      val upperSheet = configuredContentSheet(activity, upperListener, upperContent)
      root.addView(lowerSheet)
      root.addView(upperSheet)
      activity.setContentView(root)
      layoutView(root)
      lowerSheet.isFocusableInTouchMode = true
      assertTrue(lowerSheet.requestFocus())

      assertFalse(upperSheet.hostForTest().isRequestCloseTargetOpen)
      activity.onBackPressedDispatcher.onBackPressed()
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(2, lowerListener.requestCloseCount)
      assertEquals(0, upperListener.requestCloseCount)

      upperContent.contentHeight = 300
      upperContent.requestLayout()
      layoutView(root)
      upperSheet.setDetents(contentDetents())
      assertEquals(300, upperContent.markerTop)
      assertTrue(upperSheet.hostForTest().isRequestCloseTargetOpen)
      activity.onBackPressedDispatcher.onBackPressed()
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(2, lowerListener.requestCloseCount)
      assertEquals(2, upperListener.requestCloseCount)

      upperContent.contentHeight = 0
      upperContent.requestLayout()
      layoutView(root)
      upperSheet.setDetents(contentDetents())
      assertFalse(upperSheet.hostForTest().isRequestCloseTargetOpen)
      activity.onBackPressedDispatcher.onBackPressed()
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(4, lowerListener.requestCloseCount)
      assertEquals(2, upperListener.requestCloseCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `open upper without handler blocks lower while Back falls through and Escape is unhandled`() {
    withActivity<ComponentActivity> { activity ->
      var navigationCount = 0
      activity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            navigationCount++
          }
        }
      )
      val root = FrameLayout(activity)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperSheet = configuredOpenSheet(activity, upperListener, handlerPresent = false)
      root.addView(lowerSheet)
      root.addView(upperSheet)
      activity.setContentView(root)
      layoutView(root)
      lowerSheet.isFocusableInTouchMode = true
      assertTrue(lowerSheet.requestFocus())

      activity.onBackPressedDispatcher.onBackPressed()
      assertPortalEscape(activity, expectedHandled = false)

      assertEquals(1, navigationCount)
      assertEquals(0, lowerListener.requestCloseCount)
      assertEquals(0, upperListener.requestCloseCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `upper portal without handler lets its focused descendant consume Escape without notifying lower portals`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperSheet = configuredOpenSheet(activity, upperListener, handlerPresent = false)
      val focusedUpperChild = EscapeConsumingView(activity)
      focusedUpperChild.isFocusableInTouchMode = true
      upperSheet.addView(focusedUpperChild, ViewGroup.LayoutParams(1, 1))
      root.addView(lowerSheet)
      root.addView(upperSheet)
      activity.setContentView(root)
      layoutView(root)
      assertTrue(focusedUpperChild.requestFocus())
      val downTime = nextEscapeDownTime
      nextEscapeDownTime += 10L
      val down = escapeDown(downTime)
      val up = escapeUp(downTime)

      // Robolectric does not continue this direct parent dispatch to the focused child, so model
      // the normal parent-to-descendant continuation explicitly after the portal declines it.
      assertFalse(upperSheet.dispatchKeyEvent(down))
      assertTrue(focusedUpperChild.dispatchKeyEvent(down))
      assertFalse(upperSheet.dispatchKeyEvent(up))
      assertTrue(focusedUpperChild.dispatchKeyEvent(up))

      assertEquals(2, focusedUpperChild.escapeEventCount)
      assertEquals(0, lowerListener.requestCloseCount)
      assertEquals(0, upperListener.requestCloseCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `open upper with inactive lifecycle blocks lower without handling requests`() {
    withActivity<ComponentActivity> { activity ->
      var navigationCount = 0
      activity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            navigationCount++
          }
        }
      )
      val inactiveOwner =
        MutableTestDispatcherOwner().apply {
          resume()
          pause()
        }
      val root = FrameLayout(activity)
      val upperContainer = FrameLayout(activity)
      upperContainer.setViewTreeOnBackPressedDispatcherOwner(inactiveOwner)
      upperContainer.setViewTreeLifecycleOwner(inactiveOwner)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperSheet = configuredOpenSheet(activity, upperListener)
      root.addView(lowerSheet)
      upperContainer.addView(upperSheet)
      root.addView(upperContainer)
      activity.setContentView(root)
      layoutView(root)
      lowerSheet.isFocusableInTouchMode = true
      assertTrue(lowerSheet.requestFocus())

      activity.onBackPressedDispatcher.onBackPressed()
      assertPortalEscape(activity, expectedHandled = false)

      assertEquals(1, navigationCount)
      assertEquals(0, lowerListener.requestCloseCount)
      assertEquals(0, upperListener.requestCloseCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `Escape focused in a lower portal routes to the only eligible upper portal`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val focusedLowerChild = EscapeConsumingView(activity)
      focusedLowerChild.isFocusableInTouchMode = true
      lowerSheet.addView(focusedLowerChild, ViewGroup.LayoutParams(1, 1))
      root.addView(lowerSheet)
      activity.setContentView(root)
      layoutView(root)
      assertTrue(focusedLowerChild.requestFocus())

      val upperSheet = configuredOpenSheet(activity, upperListener)
      root.addView(upperSheet)
      layoutView(root)

      assertTrue(focusedLowerChild.isFocused)
      assertPortalEscape(activity, expectedHandled = true)

      assertTrue(focusedLowerChild.isFocused)
      assertEquals(0, focusedLowerChild.escapeEventCount)
      assertEquals(0, lowerListener.requestCloseCount)
      assertEquals(1, upperListener.requestCloseCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `captured portal Escape is not transferred when eligibility moves`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperSheet = configuredOpenSheet(activity, upperListener)
      root.addView(lowerSheet)
      root.addView(upperSheet)
      activity.setContentView(root)
      layoutView(root)
      upperSheet.setIndex(0)
      lowerSheet.isFocusableInTouchMode = true
      assertTrue(lowerSheet.requestFocus())
      val downTime = 340L

      assertTrue(activity.dispatchKeyEvent(escapeDown(downTime)))
      lowerSheet.setIndex(0)
      upperSheet.setIndex(1)
      assertTrue(activity.dispatchKeyEvent(escapeUp(downTime)))

      assertEquals(0, lowerListener.requestCloseCount)
      assertEquals(0, upperListener.requestCloseCount)
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(0, lowerListener.requestCloseCount)
      assertEquals(1, upperListener.requestCloseCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `predictive Back is not transferred when portal ownership moves`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperSheet = configuredOpenSheet(activity, upperListener).apply { setIndex(0) }
      root.addView(lowerSheet)
      root.addView(upperSheet)
      activity.setContentView(root)
      layoutView(root)

      activity.onBackPressedDispatcher.dispatchOnBackStarted(
        BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)
      )
      upperSheet.setIndex(1)
      activity.onBackPressedDispatcher.onBackPressed()

      assertEquals(0, lowerListener.requestCloseCount)
      assertEquals(0, upperListener.requestCloseCount)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(0, lowerListener.requestCloseCount)
      assertEquals(1, upperListener.requestCloseCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `existing Window integration that consumes Escape prevents Portal emission`() {
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

      assertSame(delegate, activity.window.callback)
      assertTrue(activity.window.callback.dispatchKeyEvent(escapeDown(350L)))
      assertTrue(activity.window.callback.dispatchKeyEvent(escapeUp(350L)))

      assertEquals(2, delegatedEventCount)
      assertEquals(0, listener.requestCloseCount)
      sheet.destroy()
      assertSame(delegate, activity.window.callback)
    }
  }

  @Test
  fun `focused view that consumes Escape prevents Portal emission`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      val focusedView = EscapeConsumingView(activity)
      root.addView(sheet)
      root.addView(focusedView, FrameLayout.LayoutParams(1, 1))
      activity.setContentView(root)
      layoutPortal(sheet)
      focusedView.isFocusableInTouchMode = true
      assertTrue(focusedView.requestFocus())

      assertPortalEscape(activity, expectedHandled = true)

      assertEquals(2, focusedView.escapeEventCount)
      assertEquals(0, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Config(sdk = [27])
  @Test
  fun `plain Activity has local interception but no external compat fallback`() {
    withActivity<Activity> { activity ->
      val root = FrameLayout(activity)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      val sibling = EscapeConsumingView(activity, consumesEscape = false)
      sibling.isFocusableInTouchMode = true
      root.addView(sheet)
      root.addView(sibling, FrameLayout.LayoutParams(1, 1))
      activity.setContentView(root)
      sheet.onHostResume()
      layoutPortal(sheet)
      assertTrue(sibling.requestFocus())

      assertPortalEscape(activity, expectedHandled = false)

      assertEquals(2, sibling.escapeEventCount)
      assertEquals(0, listener.requestCloseCount)
      sheet.destroy()
    }
  }

  @Test
  fun `removing portal integration never restores an earlier Window callback`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val installedCallback = requireNotNull(activity.window.callback)
      val laterCallback = object : Window.Callback by installedCallback {}
      activity.window.callback = laterCallback

      sheet.modal = false
      assertSame(laterCallback, activity.window.callback)
      sheet.destroy()
      assertSame(laterCallback, activity.window.callback)
    }
  }

  @Test
  fun `nativeOverlay presentation failure clears portal registration and captured Escape`() {
    withActivity<ComponentActivity> { activity ->
      val reactContext = BridgeReactContext(activity.applicationContext)
      val themedContext = ThemedReactContext(reactContext, activity, "test", 1)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(themedContext, listener)
      activity.setContentView(sheet)
      layoutPortal(sheet)
      val downTime = 360L

      assertEquals(1, sheet.unhandledKeyListenerRegistrationsForTest().size)
      assertTrue(activity.dispatchKeyEvent(escapeDown(downTime)))
      sheet.setNativeOverlay(true)

      assertTrue(sheet.unhandledKeyListenerRegistrationsForTest().isEmpty())
      assertFalse(activity.dispatchKeyEvent(escapeUp(downTime)))
      assertEquals(0, listener.requestCloseCount)
      sheet.destroy()
      reactContext.onHostDestroy()
    }
  }

  @Suppress("DEPRECATION")
  @Test
  fun `nativeOverlay intercepts the full Escape sequence before a focused descendant and emits once without closing`() {
    withActivity<ComponentActivity> { activity ->
      val reactContext = BridgeReactContext(activity.applicationContext)
      reactContext.onHostResume(activity)
      val themedContext = ThemedReactContext(reactContext, activity, "test", 1)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(themedContext, listener)
      val focusedView = EscapeConsumingView(activity)
      focusedView.isFocusableInTouchMode = true
      sheet.addView(focusedView, ViewGroup.LayoutParams(1, 1))
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
      assertTrue(focusedView.requestFocus())

      assertEscape(dialog, expectedHandled = true)

      assertEquals(0, focusedView.escapeEventCount)
      assertEquals(1, listener.requestCloseCount)
      assertTrue(sheet.hostForTest().isRequestCloseTargetOpen)
      assertTrue(dialog.isShowing)
      sheet.destroy()
      reactContext.onHostDestroy()
    }
  }

  @Suppress("DEPRECATION")
  @Test
  fun `nativeOverlay without a handler consumes close input before a focused descendant while open and closing`() {
    withActivity<ComponentActivity> { activity ->
      val reactContext = BridgeReactContext(activity.applicationContext)
      reactContext.onHostResume(activity)
      val themedContext = ThemedReactContext(reactContext, activity, "test", 1)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(themedContext, listener, handlerPresent = false)
      val focusedView = EscapeConsumingView(activity)
      focusedView.isFocusableInTouchMode = true
      sheet.addView(focusedView, ViewGroup.LayoutParams(1, 1))
      sheet.eventDispatcher = NoOpEventDispatcher
      activity.setContentView(sheet)
      layoutPortal(sheet)
      sheet.setNativeOverlay(true)
      shadowOf(Looper.getMainLooper()).idle()
      val dialog = sheet.overlayDialogForTest()
      layoutView(sheet.overlayRootForTest())
      sheet.onHostResume()
      assertTrue(focusedView.requestFocus())

      dialog.onBackPressedDispatcher.onBackPressed()
      assertEscape(dialog, expectedHandled = true)

      assertEquals(0, focusedView.escapeEventCount)
      assertEquals(0, listener.requestCloseCount)
      assertTrue(sheet.hostForTest().isRequestCloseTargetOpen)
      assertTrue(dialog.isShowing)

      sheet.setIndex(0)
      assertFalse(sheet.hostForTest().isRequestCloseTargetOpen)
      dialog.onBackPressedDispatcher.onBackPressed()
      assertEscape(dialog, expectedHandled = true)

      assertEquals(0, focusedView.escapeEventCount)
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

      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )

      sheet.setRequestCloseHandlerPresent(false)
      assertSame(originalWindowCallback, activity.window.callback)
      sheet.modal = false

      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      assertTrue(sheet.unhandledKeyListenerRegistrationsForTest().isEmpty())

      sheet.modal = true
      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(
        originalBackCallbackCount,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      assertTrue(sheet.unhandledKeyListenerRegistrationsForTest().isEmpty())

      sheet.setRequestCloseHandlerPresent(true)
      assertSame(originalWindowCallback, activity.window.callback)
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(1, listener.requestCloseCount)
      assertEquals(
        originalBackCallbackCount + 1,
        activity.onBackPressedDispatcher.callbackCountForTest(),
      )
      assertEquals(1, sheet.unhandledKeyListenerRegistrationsForTest().size)
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
      val originalWindowCallback = activity.window.callback

      sheet.setNativeOverlay(true)
      shadowOf(Looper.getMainLooper()).idle()
      assertSame(originalWindowCallback, activity.window.callback)
      assertTrue(sheet.unhandledKeyListenerRegistrationsForTest().isEmpty())
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, activityBackCount)
      assertEquals(0, listener.requestCloseCount)

      sheet.setNativeOverlay(false)
      layoutPortal(sheet)
      assertSame(originalWindowCallback, activity.window.callback)
      assertEquals(1, sheet.unhandledKeyListenerRegistrationsForTest().size)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, listener.requestCloseCount)
      assertPortalEscape(activity, expectedHandled = true)
      assertEquals(2, listener.requestCloseCount)
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
      setDetents(
        listOf(
          mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
          mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
        )
      )
      setIndex(1)
    }

  private fun configuredContentSheet(
    context: Context,
    listener: CountingBottomSheetListener,
    content: MutableContentHeightView,
  ): BottomSheetView =
    BottomSheetView(context).apply {
      this.listener = listener
      animateIn = false
      animateContentHeight = false
      modal = true
      setRequestCloseHandlerPresent(true)
      addSheetChild(content, 0)
      setDetents(contentDetents())
      setIndex(1)
    }

  private fun contentDetents(): List<Map<String, Any>> =
    listOf(
      mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
      mapOf("value" to 0.0, "kind" to "content", "programmatic" to false),
    )

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
    sheet.isFocusableInTouchMode = true
    assertTrue(sheet.requestFocus())
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
    assertEquals(expectedHandled, activity.dispatchKeyEvent(escapeDown(downTime)))
    assertEquals(expectedHandled, activity.dispatchKeyEvent(escapeUp(downTime)))
  }

  private fun assertPortalEscape(
    activity: Activity,
    expectedHandled: Boolean,
  ) {
    val downTime = nextEscapeDownTime
    nextEscapeDownTime += 10L
    assertEquals(
      expectedHandled,
      activity.dispatchKeyEvent(escapeDown(downTime)),
    )
    assertEquals(
      expectedHandled,
      activity.dispatchKeyEvent(escapeUp(downTime)),
    )
  }

  private fun assertPortalEscape(
    dialog: ComponentDialog,
    expectedHandled: Boolean,
  ) {
    val downTime = nextEscapeDownTime
    nextEscapeDownTime += 10L
    assertEquals(
      expectedHandled,
      dialog.dispatchKeyEvent(escapeDown(downTime)),
    )
    assertEquals(
      expectedHandled,
      dialog.dispatchKeyEvent(escapeUp(downTime)),
    )
  }

  private fun assertEscape(
    dialog: ComponentDialog,
    expectedHandled: Boolean,
  ) {
    val downTime = nextEscapeDownTime
    nextEscapeDownTime += 10L
    assertEquals(expectedHandled, dialog.dispatchKeyEvent(escapeDown(downTime)))
    assertEquals(expectedHandled, dialog.dispatchKeyEvent(escapeUp(downTime)))
  }

  private fun escapeDown(
    downTime: Long,
    repeatCount: Int = 0,
    metaState: Int = 0,
  ) =
    if (repeatCount == 0 && metaState == 0) {
      KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0)
    } else {
      KeyEvent(
        downTime,
        downTime + repeatCount,
        KeyEvent.ACTION_DOWN,
        KeyEvent.KEYCODE_ESCAPE,
        repeatCount,
        metaState,
        -1,
        0,
        0,
        0,
      )
    }

  private fun escapeUp(
    downTime: Long,
    metaState: Int = 0,
    canceled: Boolean = false,
  ): KeyEvent {
    val event =
      if (metaState == 0) {
        KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0)
      } else {
        KeyEvent(
          downTime,
          downTime + 2,
          KeyEvent.ACTION_UP,
          KeyEvent.KEYCODE_ESCAPE,
          0,
          metaState,
          -1,
          0,
          0,
          0,
        )
      }
    return if (canceled) {
      KeyEvent.changeFlags(event, event.flags or KeyEvent.FLAG_CANCELED)
    } else {
      event
    }
  }

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

private fun BottomSheetView.unhandledKeyListenerRegistrationsForTest(): List<Any> {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
    val listeners =
      getTag(androidx.core.R.id.tag_unhandled_key_listeners) as? List<*> ?: return emptyList()
    return listeners.filterNotNull()
  }

  val listenerInfo =
    View::class.java.getDeclaredField("mListenerInfo").let {
      it.isAccessible = true
      it.get(this)
    } ?: return emptyList()
  val listeners =
    listenerInfo.javaClass.getDeclaredField("mUnhandledKeyListeners").let {
      it.isAccessible = true
      it.get(listenerInfo) as? List<*>
    } ?: return emptyList()
  return listeners.filterNotNull()
}

private fun OnBackPressedDispatcher.callbacksForTest(): List<OnBackPressedCallback> =
  OnBackPressedDispatcher::class.java.getDeclaredField("onBackPressedCallbacks").let {
    it.isAccessible = true
    @Suppress("UNCHECKED_CAST") (it.get(this) as Collection<OnBackPressedCallback>).toList()
  }

private fun OnBackPressedDispatcher.callbackCountForTest(): Int = callbacksForTest().size

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

private class MutableContentHeightView(context: Context) : ViewGroup(context) {
  private val marker = View(context)
  var contentHeight = 0
    set(value) {
      field = value
      marker.layout(0, value, 1, value + 1)
      requestLayout()
    }

  val markerTop: Int
    get() = marker.top

  init {
    addView(marker)
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(
      MeasureSpec.getSize(widthMeasureSpec),
      MeasureSpec.getSize(heightMeasureSpec),
    )
    marker.measure(
      MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY),
    )
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    marker.layout(0, contentHeight, 1, contentHeight + 1)
  }
}

private class EscapeConsumingView(
  context: Context,
  private val consumesEscape: Boolean = true,
) : View(context) {
  var escapeEventCount = 0
  var ordinaryEventCount = 0

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
      escapeEventCount++
      return consumesEscape
    }
    ordinaryEventCount++
    return true
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
