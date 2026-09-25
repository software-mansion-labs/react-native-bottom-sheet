package com.swmansion.reactnativebottomsheet

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortalModalAccessibilityInstrumentedTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun portalExcludesApplicationThroughClosingAndRestoresItOnlyAfterSettle() {
    val closeSettled = CountDownLatch(1)
    lateinit var root: InstrumentedReactRoot
    lateinit var background: View
    lateinit var sheetContent: View
    lateinit var dismiss: View
    lateinit var sheet: BottomSheetView
    var sheetCreated = false

    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      try {
        scenario.onActivity { activity ->
          root = InstrumentedReactRoot(activity)
          background =
            View(activity).apply {
              importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
              contentDescription = "Application control"
            }
          sheetContent =
            View(activity).apply {
              importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
              contentDescription = "Sheet content"
            }
          sheet =
            BottomSheetView(activity).apply {
              listener =
                object : BottomSheetViewListener {
                  override fun onIndexChange(index: Int) = Unit

                  override fun onSettle(index: Int) {
                    if (index == 0) closeSettled.countDown()
                  }

                  override fun onPositionChange(position: Double, index: Double) = Unit

                  override fun onCloseRequest() = Unit
                }
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
          sheetCreated = true
          val portalWrapper = FrameLayout(activity)
          portalWrapper.addView(sheet, matchParent())
          root.addView(background, matchParent())
          root.addView(portalWrapper, matchParent())
          activity.setContentView(root)
          dismiss = (sheet.getChildAt(0) as ViewGroup).getChildAt(0)
        }

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        scenario.onActivity {
          val activeTree = accessibleTree(root)
          assertFalse(activeTree.contains(background))
          assertTrue(activeTree.contains(sheetContent))
          assertTrue(activeTree.contains(dismiss))

          sheet.setIndex(0)
          val closingTree = accessibleTree(root)
          assertFalse(closingTree.contains(background))
          assertTrue(closingTree.contains(sheetContent))
        }

        assertTrue("close animation did not settle", closeSettled.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        scenario.onActivity {
          assertTrue(accessibleTree(root).contains(background))
        }
      } finally {
        scenario.onActivity {
          if (sheetCreated) sheet.destroy()
        }
      }
    }
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

  private fun matchParent() =
    ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT,
    )
}
