// BridgeReactContext is required by the legacy-architecture test fixture.
@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.events.BatchEventDispatchedListener
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.EventDispatcher
import com.facebook.react.uimanager.events.EventDispatcherListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EscapeRoutingInstrumentedTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun eligiblePortalInterceptsFullEscapeSequenceBeforeFocusedDescendantAndEmitsOnce() {
    val requestCount = AtomicInteger()
    val childEventCount = AtomicInteger()

    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val sheet = openSheet(activity, requestCount)
        val child = EscapeRecordingView(activity, childEventCount, consumesEscape = true)
        child.isFocusableInTouchMode = true
        sheet.addView(child, ViewGroup.LayoutParams(1, 1))
        activity.setContentView(sheet)
        assertTrue(child.requestFocus())
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)
      instrumentation.waitForIdleSync()

      assertEquals(0, childEventCount.get())
      assertEquals(1, requestCount.get())
    }
  }

  @Test
  fun unhandledEscapeOutsidePortalUsesWindowFallback() {
    val requestCount = AtomicInteger()
    val siblingEventCount = AtomicInteger()

    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val root = FrameLayout(activity)
        val sheet = openSheet(activity, requestCount)
        val sibling = EscapeRecordingView(activity, siblingEventCount, consumesEscape = false)
        sibling.isFocusableInTouchMode = true
        root.addView(
          sheet,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
          ),
        )
        root.addView(sibling, FrameLayout.LayoutParams(100, 100))
        activity.setContentView(root)
        assertTrue(sibling.requestFocus())
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)
      instrumentation.waitForIdleSync()

      assertEquals(1, siblingEventCount.get())
      assertEquals(1, requestCount.get())
    }
  }

  @Test
  fun escapeFocusedInLowerPortalMovesFromZeroContentUpperToPositiveContentUpper() {
    val lowerRequestCount = AtomicInteger()
    val upperRequestCount = AtomicInteger()
    val lowerChildEventCount = AtomicInteger()
    lateinit var upperContent: MutableContentHeightView
    lateinit var upperSheet: BottomSheetView

    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val root = FrameLayout(activity)
        val lowerSheet = openSheet(activity, lowerRequestCount)
        val lowerChild =
          EscapeRecordingView(activity, lowerChildEventCount, consumesEscape = true).apply {
            isFocusableInTouchMode = true
          }
        lowerSheet.addView(lowerChild, ViewGroup.LayoutParams(1, 1))
        root.addView(
          lowerSheet,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
          ),
        )
        activity.setContentView(root)
        assertTrue(lowerChild.requestFocus())

        upperContent = MutableContentHeightView(activity)
        upperSheet = openContentSheet(activity, upperRequestCount, upperContent)
        root.addView(
          upperSheet,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
          ),
        )

        assertTrue(lowerChild.isFocused)
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)
      instrumentation.waitForIdleSync()

      assertEquals(0, lowerChildEventCount.get())
      assertEquals(1, lowerRequestCount.get())
      assertEquals(0, upperRequestCount.get())

      scenario.onActivity { activity ->
        upperContent.contentHeight = 300
        upperContent.requestLayout()
        upperSheet.setDetents(contentDetents())
        activity.window.decorView.requestLayout()
      }
      instrumentation.waitForIdleSync()
      instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)
      instrumentation.waitForIdleSync()

      scenario.onActivity { activity ->
        assertTrue(activity.currentFocus is EscapeRecordingView)
      }
      assertEquals(0, lowerChildEventCount.get())
      assertEquals(1, lowerRequestCount.get())
      assertEquals(1, upperRequestCount.get())
    }
  }

  @Test
  fun nativeOverlayWithoutHandlerRoutesTheFullEscapeSequenceToFocusedDescendant() {
    val requestCount = AtomicInteger()
    val childEventCount = AtomicInteger()
    lateinit var reactContext: BridgeReactContext
    lateinit var sheet: BottomSheetView
    lateinit var child: EscapeRecordingView

    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        reactContext = BridgeReactContext(activity.applicationContext)
        reactContext.onHostResume(activity)
        val themedContext = ThemedReactContext(reactContext, activity, "test", 1)
        sheet =
          BottomSheetView(themedContext).apply {
            listener = CountingCloseRequestListener(requestCount)
            eventDispatcher = NoOpInstrumentedEventDispatcher
            animateIn = false
            modal = true
            setHasCloseRequestHandler(false)
            setScrimColor(Color.BLACK)
            setScrimOpacities(listOf(0f, 1f))
            setDetents(
              listOf(
                mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
                mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
              )
            )
            setIndex(1)
          }
        child = EscapeRecordingView(activity, childEventCount, consumesEscape = true)
        child.isFocusableInTouchMode = true
        child.layoutParams = FrameLayout.LayoutParams(1, 1)
        // Fabric normally supplies measured child bounds; this direct native fixture must do so.
        child.measure(
          View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY),
        )
        sheet.addSheetChild(child, 0)
        activity.setContentView(sheet)
        sheet.setNativeOverlay(true)
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      awaitFocusedWindow(scenario, child)
      instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)
      instrumentation.waitForIdleSync()

      assertEquals(2, childEventCount.get())
      assertEquals(0, requestCount.get())

      scenario.onActivity {
        sheet.destroy()
        reactContext.onHostDestroy()
      }
    }
  }

  private fun awaitFocusedWindow(
    scenario: ActivityScenario<ComponentActivity>,
    child: View,
  ) {
    val focusReady = CountDownLatch(1)
    lateinit var focusObserver: ViewTreeObserver
    lateinit var windowFocusListener: ViewTreeObserver.OnWindowFocusChangeListener

    scenario.onActivity {
      val signalIfReady = {
        if (child.isFocused && child.hasWindowFocus()) {
          focusReady.countDown()
        }
      }
      child.onFocusChangeListener = View.OnFocusChangeListener { _, _ -> signalIfReady() }
      windowFocusListener = ViewTreeObserver.OnWindowFocusChangeListener { _ -> signalIfReady() }
      focusObserver = child.viewTreeObserver
      focusObserver.addOnWindowFocusChangeListener(windowFocusListener)
      val focused = child.requestFocus()
      val windowFlags = (child.rootView.layoutParams as? WindowManager.LayoutParams)?.flags
      assertTrue(
        "requestFocus failed: attached=${child.isAttachedToWindow}, shown=${child.isShown}, " +
          "focusable=${child.isFocusable}, touchMode=${child.isFocusableInTouchMode}, " +
          "size=${child.width}x${child.height}, windowFocus=${child.hasWindowFocus()}, " +
          "windowFlags=$windowFlags",
        focused,
      )
      signalIfReady()
    }

    assertTrue(
      "Timed out waiting for the overlay child to gain focus and window focus",
      focusReady.await(5, TimeUnit.SECONDS),
    )
    scenario.onActivity {
      child.onFocusChangeListener = null
      if (focusObserver.isAlive) {
        focusObserver.removeOnWindowFocusChangeListener(windowFocusListener)
      }
      assertTrue(child.isFocused)
      assertTrue(child.hasWindowFocus())
    }
  }

  private fun openContentSheet(
    context: Context,
    requestCount: AtomicInteger,
    content: MutableContentHeightView,
  ) =
    BottomSheetView(context).apply {
      listener = CountingCloseRequestListener(requestCount)
      animateIn = false
      animateContentHeight = false
      modal = true
      setHasCloseRequestHandler(true)
      addSheetChild(content, 0)
      setDetents(contentDetents())
      setIndex(1)
    }

  private fun contentDetents(): List<Map<String, Any>> =
    listOf(
      mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
      mapOf("value" to 0.0, "kind" to "content", "programmatic" to false),
    )

  private fun openSheet(
    context: Context,
    requestCount: AtomicInteger,
  ) =
    BottomSheetView(context).apply {
      listener = CountingCloseRequestListener(requestCount)
      animateIn = false
      modal = true
      setHasCloseRequestHandler(true)
      setDetents(
        listOf(
          mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
          mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
        )
      )
      setIndex(1)
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

private class EscapeRecordingView(
  context: Context,
  private val eventCount: AtomicInteger,
  private val consumesEscape: Boolean,
) : View(context) {
  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) return super.dispatchKeyEvent(event)
    eventCount.incrementAndGet()
    return consumesEscape
  }
}

private class CountingCloseRequestListener(private val requestCount: AtomicInteger) :
  BottomSheetViewListener {
  override fun onIndexChange(index: Int) = Unit

  override fun onSettle(index: Int) = Unit

  override fun onPositionChange(position: Double, index: Double) = Unit

  override fun onCloseRequest() {
    requestCount.incrementAndGet()
  }
}

private object NoOpInstrumentedEventDispatcher : EventDispatcher {
  override fun dispatchEvent(event: Event<*>) = Unit

  override fun dispatchAllEvents() = Unit

  override fun addListener(listener: EventDispatcherListener) = Unit

  override fun removeListener(listener: EventDispatcherListener) = Unit

  override fun addBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  override fun removeBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  @Suppress("OVERRIDE_DEPRECATION") override fun onCatalystInstanceDestroyed() = Unit
}
