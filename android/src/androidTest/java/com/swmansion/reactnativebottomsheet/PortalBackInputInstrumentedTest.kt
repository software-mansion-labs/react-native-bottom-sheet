package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.ReactActivity
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.RootView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortalBackInputInstrumentedTest {
  @Before
  fun initializeRecordingHost() {
    ReactNativeFeatureFlagsForTests.setUp()
    RecordingReactActivityDelegate.backEntryCount.set(0)
    RecordingReactActivityDelegate.defaultBackLater = false
  }

  @After
  fun resetRecordingHost() {
    RecordingReactActivityDelegate.defaultBackLater = false
  }

  @Test
  fun systemBackRoutesPortalAndAsyncHostDefaultWithoutReentry() =
    exerciseNativeBack(RecordingReactActivity::class.java)

  @Test
  fun optedOutWindowKeepsLegacyKeyBackAndAsyncHostDefaultWorking() =
    exerciseNativeBack(LegacyRecordingReactActivity::class.java)

  @Test
  fun aHostCallbackEnabledAfterMountCannotBypassThePortalCloseRequest() {
    val focused = CountDownLatch(1)
    val laidOut = CountDownLatch(1)
    val requested = CountDownLatch(1)
    val requests = AtomicInteger()
    val hostBacks = AtomicInteger()
    lateinit var sheet: BottomSheetView
    lateinit var hostCallback: OnBackPressedCallback
    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        hostCallback =
          object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
              hostBacks.incrementAndGet()
            }
          }
        activity.onBackPressedDispatcher.addCallback(hostCallback)
        val root = PortalBackTestRoot(activity)
        activity.setContentView(root)
        sheet =
          mountedPortal(activity, root, laidOut) {
            requests.incrementAndGet()
            requested.countDown()
          }
        val decor = activity.window.decorView
        decor.viewTreeObserver.addOnWindowFocusChangeListener { if (it) focused.countDown() }
        if (decor.hasWindowFocus()) focused.countDown()
      }
      try {
        assertTrue("Testhost did not receive focus", focused.await(5, TimeUnit.SECONDS))
        assertTrue("Portal did not receive layout", laidOut.await(5, TimeUnit.SECONDS))
        scenario.onActivity {
          assertTrue(sheet.requestFocus())
          hostCallback.isEnabled = true
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertTrue(
          "Late host callback bypassed portal; host=${hostBacks.get()}",
          requested.await(5, TimeUnit.SECONDS),
        )
        assertEquals(1, requests.get())
        assertEquals(0, hostBacks.get())
      } finally {
        scenario.onActivity { sheet.destroy() }
      }
    }
  }

  private fun exerciseNativeBack(activityClass: Class<out ReactActivity>) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val focused = CountDownLatch(1)
    val laidOut = CountDownLatch(1)
    val hostDefault = CountDownLatch(1)
    val requested = Semaphore(0)
    val requests = AtomicInteger()
    val defaults = AtomicInteger()
    lateinit var sheet: BottomSheetView
    lateinit var outside: View
    ActivityScenario.launch(activityClass).use { scenario ->
      scenario.onActivity { activity ->
        RecordingReactActivityDelegate.defaultBackLater = true
        activity.onBackPressedDispatcher.addCallback(
          object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
              defaults.incrementAndGet()
              hostDefault.countDown()
            }
          }
        )
        val root = PortalBackTestRoot(activity)
        outside =
          View(activity).apply {
            isFocusableInTouchMode = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Host toolbar"
          }
        activity.setContentView(
          LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(root, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(outside, LinearLayout.LayoutParams(-1, 150))
          }
        )
        sheet =
          mountedPortal(activity, root, laidOut) {
            requests.incrementAndGet()
            requested.release()
          }
        val decor = activity.window.decorView
        decor.viewTreeObserver.addOnWindowFocusChangeListener { if (it) focused.countDown() }
        if (decor.hasWindowFocus()) focused.countDown()
      }
      try {
        assertTrue("Testhost did not receive focus", focused.await(5, TimeUnit.SECONDS))
        assertTrue("Portal did not receive layout", laidOut.await(5, TimeUnit.SECONDS))
        scenario.onActivity { assertTrue(sheet.requestFocus()) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertTrue("Local Back did not request close", requested.tryAcquire(5, TimeUnit.SECONDS))
        scenario.onActivity { activity ->
          assertTrue(outside.requestFocus())
          if (activity.getSystemService(AccessibilityManager::class.java).isEnabled) {
            assertTrue(
              outside.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null,
              )
            )
          }
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertTrue("RN async default did not reach host", hostDefault.await(5, TimeUnit.SECONDS))
        assertEquals(1, requests.get())
        assertEquals(1, defaults.get())
        assertEquals(1, RecordingReactActivityDelegate.backEntryCount.get())
        scenario.onActivity {
          outside.performAccessibilityAction(
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
            null,
          )
          assertTrue(sheet.requestFocus())
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertTrue(
          "Fresh Back after RN default did not request close",
          requested.tryAcquire(5, TimeUnit.SECONDS),
        )
        assertEquals(2, requests.get())
        assertEquals(1, defaults.get())
        assertEquals(1, RecordingReactActivityDelegate.backEntryCount.get())
      } finally {
        scenario.onActivity { sheet.destroy() }
      }
    }
  }

  private fun mountedPortal(
    context: Context,
    root: FrameLayout,
    laidOut: CountDownLatch,
    onRequest: () -> Unit,
  ) =
    BottomSheetView(context)
      .apply {
        listener =
          object : BottomSheetViewListener {
            override fun onIndexChange(index: Int) = Unit

            override fun onSettle(index: Int) = Unit

            override fun onPositionChange(position: Double, index: Double) = Unit

            override fun onCloseRequest() = onRequest()
          }
        isFocusableInTouchMode = true
        animateIn = false
        modal = true
        setHasCloseRequestHandler(true)
        setDetents(listOf(mapOf("value" to 0.0), mapOf("value" to 300.0)))
        setIndex(1)
        addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
          if (right > left && bottom > top) laidOut.countDown()
        }
      }
      .also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }

  @Test
  fun portalMountedWhileKeyboardIsVisibleLetsImeHandleBackThenHandlesFreshBack() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val focused = CountDownLatch(1)
    val shown = CountDownLatch(1)
    val hidden = CountDownLatch(1)
    val laidOut = CountDownLatch(1)
    val requested = CountDownLatch(1)
    val requests = AtomicInteger()
    lateinit var editor: EditText
    lateinit var root: FrameLayout
    var sheet: BottomSheetView? = null
    ActivityScenario.launch(RecordingReactActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        root = PortalBackTestRoot(activity)
        editor = EditText(activity).apply { hint = "Host editor" }
        activity.setContentView(
          LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(root, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(editor, LinearLayout.LayoutParams(-1, 150))
          }
        )
        val decor = activity.window.decorView
        decor.viewTreeObserver.addOnWindowFocusChangeListener { if (it) focused.countDown() }
        if (decor.hasWindowFocus()) focused.countDown()
        var wasShown = false
        ViewCompat.setOnApplyWindowInsetsListener(decor) { _, insets ->
          if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
            wasShown = true
            shown.countDown()
          } else if (wasShown) hidden.countDown()
          insets
        }
      }
      try {
        assertTrue("Testhost did not receive focus", focused.await(5, TimeUnit.SECONDS))
        scenario.onActivity { activity ->
          assertTrue(editor.requestFocus())
          activity
            .getSystemService(InputMethodManager::class.java)
            .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        assertTrue("IME did not become visible", shown.await(5, TimeUnit.SECONDS))
        scenario.onActivity { activity ->
          sheet =
            mountedPortal(activity, root, laidOut) {
              requests.incrementAndGet()
              requested.countDown()
            }
        }
        assertTrue("Portal did not receive layout", laidOut.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertTrue(
          "Back must hide IME before routing the portal",
          hidden.await(5, TimeUnit.SECONDS),
        )
        assertEquals(0, requests.get())
        assertEquals(0, RecordingReactActivityDelegate.backEntryCount.get())
        scenario.onActivity { assertTrue(requireNotNull(sheet).requestFocus()) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertTrue(
          "Fresh Back after IME must reach the portal",
          requested.await(5, TimeUnit.SECONDS),
        )
        assertEquals(1, requests.get())
        assertEquals(0, RecordingReactActivityDelegate.backEntryCount.get())
      } finally {
        scenario.onActivity { activity ->
          sheet?.destroy()
          root.removeAllViews()
          activity
            .getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(editor.windowToken, 0)
        }
      }
    }
  }
}

private class PortalBackTestRoot(context: Context) : FrameLayout(context), RootView {
  override fun onChildStartedNativeGesture(childView: View?, ev: MotionEvent) = Unit

  override fun onChildEndedNativeGesture(childView: View, ev: MotionEvent) = Unit

  override fun handleException(t: Throwable) = throw t
}
