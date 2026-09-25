// BridgeReactContext is required by the legacy-architecture test fixture.
@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.events.BatchEventDispatchedListener
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.EventDispatcher
import com.facebook.react.uimanager.events.EventDispatcherListener
import com.facebook.react.views.view.ReactViewGroup
import com.swmansion.reactnativebottomsheet.presentation.TestReactRoot
import java.util.Collections
import java.util.IdentityHashMap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BottomSheetViewPortalAccessibilityTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `Active portal excludes background while sheet content and real Dismiss remain reachable`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val sheet = BottomSheetView(activity.get())
    try {
      val root = TestReactRoot(activity.get())
      val background =
        View(activity.get()).apply {
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
          contentDescription = "Application control"
        }
      val portalWrapper = FrameLayout(activity.get())
      val sheetContent =
        View(activity.get()).apply {
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
          contentDescription = "Sheet content"
        }
      sheet.apply {
        animateIn = false
        modal = true
        setDetents(
          listOf(
            mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
            mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
          )
        )
        setIndex(1)
        addSheetChild(sheetContent, 0)
      }
      portalWrapper.addView(sheet, matchParent())
      root.addView(background, matchParent())
      root.addView(portalWrapper, matchParent())
      activity.get().setContentView(root)
      layout(root)
      val host = sheet.getChildAt(0) as ViewGroup
      val dismiss = host.getChildAt(0)

      val activeTree = accessibleTree(root)
      assertFalse(activeTree.contains(background))
      assertTrue(activeTree.contains(sheetContent))
      assertTrue(activeTree.contains(dismiss))

      sheet.onHostDestroy()
      assertTrue(accessibleTree(root).contains(background))
      sheet.onHostResume()
      assertFalse(accessibleTree(root).contains(background))

      portalWrapper.removeView(sheet)
      assertTrue(accessibleTree(root).contains(background))
      portalWrapper.addView(sheet, matchParent())
      layout(root)
      assertFalse(accessibleTree(root).contains(background))

      sheet.modal = false

      assertTrue(accessibleTree(root).contains(background))
    } finally {
      sheet.destroy()
      activity.close()
    }
  }

  @Test
  fun `programmatic-only portal withdraws for native overlay and isolates again after inline attach`() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val reactContext = BridgeReactContext(activity.get().applicationContext)
    reactContext.onHostResume(activity.get())
    val themedContext = ThemedReactContext(reactContext, activity.get(), "test", 1)
    val sheet = BottomSheetView(themedContext)
    try {
      val root = TestReactRoot(activity.get())
      val background =
        View(activity.get()).apply {
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
          contentDescription = "Application control"
        }
      val sheetContent =
        View(activity.get()).apply {
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
          contentDescription = "Sheet content"
        }
      sheet.apply {
        eventDispatcher = NoOpPortalAccessibilityEventDispatcher
        animateIn = false
        modal = true
        setHasCloseRequestHandler(false)
        setDetents(
          listOf(
            mapOf("value" to 0.0, "kind" to "points", "programmatic" to true),
            mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
          )
        )
        setIndex(1)
        addSheetChild(sheetContent, 0)
      }
      root.addView(background, matchParent())
      root.addView(sheet, matchParent())
      activity.get().setContentView(root)
      layout(root)
      sheet.onHostResume()
      val dismiss = (sheet.getChildAt(0) as ViewGroup).getChildAt(0)

      assertFalse(accessibleTree(root).contains(background))
      assertTrue(accessibleTree(root).contains(sheetContent))
      assertFalse(accessibleTree(root).contains(dismiss))

      sheet.setNativeOverlay(true)
      shadowOf(Looper.getMainLooper()).idle()
      assertTrue(accessibleTree(root).contains(background))

      sheet.setNativeOverlay(false)
      layout(root)
      assertFalse(accessibleTree(root).contains(background))
      assertTrue(accessibleTree(root).contains(sheetContent))
    } finally {
      sheet.destroy()
      reactContext.onHostDestroy()
      activity.close()
    }
  }

  @Test
  fun `opening an upper sibling portal removes the lower portal from the accessibility tree`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val lowerSheet = BottomSheetView(activity.get())
    val upperSheet = BottomSheetView(activity.get())
    try {
      val root = TestReactRoot(activity.get())
      val portalHost = ReactViewGroup(activity.get())
      val background =
        View(activity.get()).apply {
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
          contentDescription = "Application control"
        }
      val lowerWrapper = ReactViewGroup(activity.get())
      val upperWrapper = ReactViewGroup(activity.get())
      val lowerContent =
        View(activity.get()).apply {
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
          contentDescription = "Lower focus target"
        }
      val upperContent =
        View(activity.get()).apply {
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
          contentDescription = "Upper focus target"
        }
      lowerSheet.configurePortal(index = 1, content = lowerContent)
      upperSheet.configurePortal(index = 0, content = upperContent)
      lowerWrapper.addView(lowerSheet, matchParent())
      upperWrapper.addView(upperSheet, matchParent())
      portalHost.addView(lowerWrapper, matchParent())
      portalHost.addView(upperWrapper, matchParent())
      root.addView(background, matchParent())
      root.addView(portalHost, matchParent())
      activity.get().setContentView(root)
      layout(root)
      layout(lowerSheet)
      layout(upperSheet)

      val lowerTree = accessibleTree(root)
      assertFalse(lowerTree.contains(background))
      assertTrue(lowerTree.contains(lowerContent))

      upperSheet.setIndex(1)
      shadowOf(Looper.getMainLooper()).idle()

      val upperTree = accessibleTree(root)
      assertFalse(upperTree.contains(background))
      assertFalse(upperTree.contains(lowerContent))
      assertTrue(upperTree.contains(upperContent))
    } finally {
      upperSheet.destroy()
      lowerSheet.destroy()
      activity.close()
    }
  }

  private fun BottomSheetView.configurePortal(index: Int, content: View) {
    animateIn = false
    modal = true
    setDetents(
      listOf(
        mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
        mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
      )
    )
    setIndex(index)
    addSheetChild(content, 0)
  }

  private fun accessibleTree(root: ViewGroup): Set<View> {
    val result = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
    fun visit(parent: ViewGroup) {
      val children = arrayListOf<View>()
      parent.addChildrenForAccessibility(children)
      children.forEach { child ->
        if (result.add(child) && child is ViewGroup) visit(child)
      }
    }
    visit(root)
    return result
  }

  private fun layout(view: View) {
    view.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, 1080, 1920)
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun matchParent() =
    ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT,
    )
}

private object NoOpPortalAccessibilityEventDispatcher : EventDispatcher {
  override fun dispatchEvent(event: Event<*>) = Unit

  override fun dispatchAllEvents() = Unit

  override fun addListener(listener: EventDispatcherListener) = Unit

  override fun removeListener(listener: EventDispatcherListener) = Unit

  override fun addBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  override fun removeBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  @Suppress("OVERRIDE_DEPRECATION") override fun onCatalystInstanceDestroyed() = Unit
}
