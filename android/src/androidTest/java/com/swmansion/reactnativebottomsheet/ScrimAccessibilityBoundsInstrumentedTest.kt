// BridgeReactContext is required by the legacy-architecture test fixture.
@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
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
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrimAccessibilityBoundsInstrumentedTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun dismissControlGeometrySurvivesPortalOverlayReparentAndDisappearsOnClosing() {
    val openSettled = CountDownLatch(1)
    lateinit var reactContext: BridgeReactContext
    lateinit var sheet: BottomSheetView
    lateinit var host: ViewGroup
    lateinit var dismiss: View
    var sheetCreated = false

    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      try {
        scenario.onActivity { activity ->
          reactContext = BridgeReactContext(activity.applicationContext)
          reactContext.onHostResume(activity)
          val themedContext = ThemedReactContext(reactContext, activity, "test", 1)
          sheet =
            BottomSheetView(themedContext).apply {
              listener = SettledListener(openSettled)
              eventDispatcher = GeometryNoOpEventDispatcher
              animateIn = true
              modal = true
              setScrimOpacities(listOf(0f, 1f))
              setDetents(
                listOf(
                  mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
                  mapOf("value" to 160.0, "kind" to "points", "programmatic" to false),
                )
              )
              setIndex(1)
            }
          sheetCreated = true
          activity.setInstrumentedReactContentView(sheet)
          host = sheet.getChildAt(0) as ViewGroup
          dismiss = host.getChildAt(1)
        }

        assertTrue("opening did not settle", openSettled.await(5, TimeUnit.SECONDS))
        awaitStableGeometry(scenario, host, dismiss, "portal after animateIn")
        lateinit var portalRoot: View
        scenario.onActivity { portalRoot = dismiss.rootView }

        scenario.onActivity {
          sheet.setNativeOverlay(true)
          assertFreshGeometryOrHidden(host, dismiss)
          assertNotSame(portalRoot, dismiss.rootView)
        }
        awaitStableGeometry(scenario, host, dismiss, "nativeOverlay")

        scenario.onActivity {
          sheet.setNativeOverlay(false)
          assertFreshGeometryOrHidden(host, dismiss)
          assertSame(portalRoot, dismiss.rootView)
        }
        awaitStableGeometry(scenario, host, dismiss, "portal after overlay")

        scenario.onActivity {
          sheet.setIndex(0)
          assertEquals(View.INVISIBLE, dismiss.visibility)
          assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, dismiss.importantForAccessibility)
          assertFalse(dismiss.isClickable)
        }
        awaitNodeAbsent(dismiss.contentDescription.toString())
      } finally {
        scenario.onActivity {
          if (sheetCreated) {
            sheet.destroy()
            reactContext.onHostDestroy()
          }
        }
      }
    }
  }

  private fun awaitStableGeometry(
    scenario: ActivityScenario<ComponentActivity>,
    host: ViewGroup,
    dismiss: View,
    state: String,
  ) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val description = dismiss.contentDescription.toString()
    val deadline = SystemClock.uptimeMillis() + NODE_TIMEOUT_MS
    var lastExpected = Rect()
    var lastView = Rect()
    var lastNode: Rect? = null
    var lastViewWindowId = -1
    var lastNodeWindowId = -1

    do {
      instrumentation.waitForIdleSync()
      scenario.onActivity {
        lastExpected = expectedBounds(host)
        lastView = realViewBoundsOnScreen(dismiss)
        lastViewWindowId = dismiss.createAccessibilityNodeInfo().windowId
      }
      val node = instrumentation.uiAutomation.rootInActiveWindow?.let { findNode(it, description) }
      lastNode = node?.let { Rect().also(it::getBoundsInScreen) }
      lastNodeWindowId = node?.windowId ?: -1
      if (
        lastNode == lastExpected && lastView == lastExpected && lastNodeWindowId == lastViewWindowId
      ) {
        return
      }
      SystemClock.sleep(NODE_POLL_INTERVAL_MS)
    } while (SystemClock.uptimeMillis() < deadline)

    fail(
      "$state geometry did not stabilize: expected=$lastExpected, view=$lastView, node=$lastNode, " +
        "viewWindow=$lastViewWindowId, nodeWindow=$lastNodeWindowId"
    )
  }

  private fun assertFreshGeometryOrHidden(host: ViewGroup, dismiss: View) {
    if (dismiss.visibility != View.VISIBLE) return
    assertEquals(expectedBounds(host), realViewBoundsOnScreen(dismiss))
  }

  // View.getBoundsOnScreen(Rect, boolean), used internally by ViewRoot focus drawing, is hidden
  // from the public SDK. This is its unclipped public-API equivalent: map the View's full local
  // rectangle through the same global transform chain instead of reading a clipped visible rect.
  private fun realViewBoundsOnScreen(view: View): Rect {
    val matrix = Matrix()
    view.transformMatrixToGlobal(matrix)
    val bounds = RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
    matrix.mapRect(bounds)
    return Rect(
      bounds.left.roundToInt(),
      bounds.top.roundToInt(),
      bounds.right.roundToInt(),
      bounds.bottom.roundToInt(),
    )
  }

  private fun expectedBounds(host: ViewGroup): Rect {
    val sheetContainer = host.getChildAt(host.childCount - 1)
    val hostLocation = IntArray(2).also(host::getLocationOnScreen)
    val sheetLocation = IntArray(2).also(sheetContainer::getLocationOnScreen)
    return Rect(
      hostLocation[0],
      hostLocation[1],
      hostLocation[0] + host.width,
      sheetLocation[1],
    )
  }

  private fun awaitNodeAbsent(description: String) {
    val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val deadline = SystemClock.uptimeMillis() + NODE_TIMEOUT_MS
    do {
      uiAutomation.rootInActiveWindow?.let { root ->
        if (findNode(root, description) == null) return
      }
      SystemClock.sleep(NODE_POLL_INTERVAL_MS)
    } while (SystemClock.uptimeMillis() < deadline)

    fail("Accessibility node '$description' remained after closing started")
  }

  private fun findNode(root: AccessibilityNodeInfo, description: String): AccessibilityNodeInfo? {
    val pending = ArrayDeque<AccessibilityNodeInfo>()
    pending.add(root)
    while (pending.isNotEmpty()) {
      val node = pending.removeFirst()
      if (node.contentDescription?.toString() == description) return node
      for (index in 0 until node.childCount) {
        node.getChild(index)?.let(pending::addLast)
      }
    }
    return null
  }

  private companion object {
    const val NODE_TIMEOUT_MS = 5_000L
    const val NODE_POLL_INTERVAL_MS = 50L
  }
}

private class SettledListener(private val openSettled: CountDownLatch) : BottomSheetViewListener {
  override fun onIndexChange(index: Int) = Unit

  override fun onSettle(index: Int) {
    if (index == 1) openSettled.countDown()
  }

  override fun onPositionChange(position: Double, index: Double) = Unit

  override fun onCloseRequest() = Unit
}

private object GeometryNoOpEventDispatcher : EventDispatcher {
  override fun dispatchEvent(event: Event<*>) = Unit

  override fun dispatchAllEvents() = Unit

  override fun addListener(listener: EventDispatcherListener) = Unit

  override fun removeListener(listener: EventDispatcherListener) = Unit

  override fun addBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  override fun removeBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  @Suppress("OVERRIDE_DEPRECATION") override fun onCatalystInstanceDestroyed() = Unit
}
