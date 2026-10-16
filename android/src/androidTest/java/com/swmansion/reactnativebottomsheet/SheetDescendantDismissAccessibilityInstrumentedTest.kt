package com.swmansion.reactnativebottomsheet

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SheetDescendantDismissAccessibilityInstrumentedTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun focusedSheetDescendantCanDismissThroughItsAccessibilityAncestor() {
    val indexChanges = CopyOnWriteArrayList<Int>()
    val closeRequestCount = AtomicInteger()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val uiAutomation = instrumentation.uiAutomation
    val originalUiAutomationFlags = uiAutomation.serviceInfo.flags
    lateinit var host: BottomSheetHostView

    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      try {
        scenario.onActivity { activity ->
          host =
            BottomSheetHostView(activity).apply {
              listener = RecordingAccessibilityListener(indexChanges, closeRequestCount)
              animateIn = false
              modal = true
              setDetents(
                listOf(
                  mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
                  mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
                )
              )
              setIndex(1)
            }
          val focusedChild =
            Button(activity).apply {
              contentDescription = FOCUSED_CHILD_DESCRIPTION
              importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
              measure(
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(96, View.MeasureSpec.EXACTLY),
              )
            }
          host.addSheetChild(focusedChild, 0)
          activity.setContentView(host)
        }

        instrumentation.waitForIdleSync()
        uiAutomation.requestTouchExploration()
        awaitTouchExplorationEnabled()
        val child = awaitNodeWithContentDescription(FOCUSED_CHILD_DESCRIPTION)
        assertTrue(
          "UiAutomation must be able to place accessibility focus on the sheet descendant: " +
            child.debugState(),
          child.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS),
        )

        val focusedNode = awaitAccessibilityFocus(FOCUSED_CHILD_DESCRIPTION)
        assertFalse(
          "The focused descendant must not appear to own an action implemented only by its parent",
          focusedNode.hasAction(AccessibilityNodeInfo.ACTION_DISMISS),
        )
        assertFalse(
          "Dispatching ACTION_DISMISS to the focused descendant itself must not be mistaken for " +
            "TalkBack's ancestor lookup",
          focusedNode.performAction(AccessibilityNodeInfo.ACTION_DISMISS),
        )
        instrumentation.waitForIdleSync()
        assertTrue(indexChanges.isEmpty())
        assertEquals(0, closeRequestCount.get())

        val dismissOwner = focusedNode.firstAncestorWithAction(AccessibilityNodeInfo.ACTION_DISMISS)
        assertNotNull(
          "ACTION_DISMISS must be discoverable on an accessibility ancestor of focused content",
          dismissOwner,
        )
        assertTrue(
          "The action owner must be important for accessibility, not visible only because of " +
            "UiAutomation fetch flags",
          dismissOwner!!.isImportantForAccessibility,
        )
        assertTrue(
          "Performing the discovered ancestor action must commit accessible dismissal",
          dismissOwner.performAction(AccessibilityNodeInfo.ACTION_DISMISS),
        )
        instrumentation.waitForIdleSync()

        assertEquals(listOf(0), indexChanges)
        assertEquals(0, closeRequestCount.get())
      } finally {
        uiAutomation.restoreFlags(originalUiAutomationFlags)
        scenario.onActivity { host.destroy() }
      }
    }
  }

  private fun UiAutomation.requestTouchExploration() {
    val updatedServiceInfo = serviceInfo
    updatedServiceInfo.flags =
      updatedServiceInfo.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
    serviceInfo = updatedServiceInfo
  }

  private fun UiAutomation.restoreFlags(flags: Int) {
    val restoredServiceInfo = serviceInfo
    restoredServiceInfo.flags = flags
    serviceInfo = restoredServiceInfo
  }

  private fun awaitTouchExplorationEnabled() {
    val accessibilityManager =
      InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getSystemService(AccessibilityManager::class.java)
    val deadline = SystemClock.uptimeMillis() + NODE_TIMEOUT_MS
    while (
      !accessibilityManager.isTouchExplorationEnabled && SystemClock.uptimeMillis() < deadline
    ) {
      SystemClock.sleep(NODE_POLL_INTERVAL_MS)
    }
    assertTrue(
      "UiAutomation did not enable touch exploration; serviceFlags=" +
        InstrumentationRegistry.getInstrumentation().uiAutomation.serviceInfo.flags,
      accessibilityManager.isTouchExplorationEnabled,
    )
  }

  private fun awaitNodeWithContentDescription(description: String): AccessibilityNodeInfo {
    return awaitNode("No accessibility node with content description '$description'") { root ->
      findNode(root, description)
    }
  }

  private fun awaitAccessibilityFocus(description: String): AccessibilityNodeInfo {
    return awaitNode("Accessibility focus did not move to '$description'") { root ->
      root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.takeIf {
        it.contentDescription?.toString() == description
      }
    }
  }

  private fun awaitNode(
    failureMessage: String,
    find: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?,
  ): AccessibilityNodeInfo {
    val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val deadline = SystemClock.uptimeMillis() + NODE_TIMEOUT_MS
    do {
      uiAutomation.rootInActiveWindow?.let { root ->
        find(root)?.let {
          return it
        }
      }
      SystemClock.sleep(NODE_POLL_INTERVAL_MS)
    } while (SystemClock.uptimeMillis() < deadline)

    fail(failureMessage)
    throw AssertionError("unreachable")
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

  private fun AccessibilityNodeInfo.hasAction(actionId: Int): Boolean = actionList.any {
    it.id == actionId
  }

  private fun AccessibilityNodeInfo.debugState(): String {
    val bounds = Rect().also(::getBoundsInScreen)
    return "visible=$isVisibleToUser enabled=$isEnabled focusable=$isFocusable " +
      "accessibilityFocused=$isAccessibilityFocused important=$isImportantForAccessibility " +
      "bounds=$bounds actions=${actionList.map { it.id }}"
  }

  private fun AccessibilityNodeInfo.firstAncestorWithAction(actionId: Int): AccessibilityNodeInfo? {
    var ancestor = parent
    while (ancestor != null) {
      if (ancestor.hasAction(actionId)) return ancestor
      ancestor = ancestor.parent
    }
    return null
  }

  private class RecordingAccessibilityListener(
    private val indexChanges: MutableList<Int>,
    private val closeRequestCount: AtomicInteger,
  ) : BottomSheetViewListener {
    override fun onIndexChange(index: Int) {
      indexChanges.add(index)
    }

    override fun onSettle(index: Int) = Unit

    override fun onPositionChange(position: Double, index: Double) = Unit

    override fun onCloseRequest() {
      closeRequestCount.incrementAndGet()
    }
  }

  private companion object {
    const val FOCUSED_CHILD_DESCRIPTION = "Focused sheet action"
    const val NODE_TIMEOUT_MS = 5_000L
    const val NODE_POLL_INTERVAL_MS = 50L
  }
}
