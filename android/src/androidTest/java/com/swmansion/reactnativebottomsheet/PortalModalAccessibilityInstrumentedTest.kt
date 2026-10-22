package com.swmansion.reactnativebottomsheet

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swmansion.reactnativebottomsheet.accessibility.PortalAccessibilityCut
import com.swmansion.reactnativebottomsheet.accessibility.PortalAccessibilityReconciler
import java.util.Collections
import java.util.IdentityHashMap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortalModalAccessibilityInstrumentedTest {
  @Test
  fun projectedPortalMasksControlAndroidAccessibilityTraversalAndRestoreBackground() {
    lateinit var root: FrameLayout
    lateinit var background: View
    lateinit var outside: View
    lateinit var sheetContent: View
    lateinit var dismiss: View
    lateinit var owner: FrameLayout
    lateinit var reconciler: PortalAccessibilityReconciler

    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        root = FrameLayout(activity)
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
        dismiss =
          View(activity).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Dismiss"
          }
        outside =
          View(activity).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Outside toolbar"
          }
        owner =
          FrameLayout(activity).apply {
            addView(sheetContent, matchParent())
            addView(dismiss, matchParent())
          }
        val area =
          FrameLayout(activity).apply {
            addView(background, matchParent())
            addView(owner, matchParent())
          }
        root.addView(area, matchParent())
        root.addView(outside, matchParent())
        activity.setContentView(root)
        reconciler = PortalAccessibilityReconciler(root)
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      scenario.onActivity {
        // Source discovery is covered by Fabric/JNI tests; lifecycle retention by
        // controller tests. This seam verifies the actual Android traversal.
        val cut = PortalAccessibilityCut(listOf(background), listOf(owner))
        assertTrue(reconciler.reconcileCuts(listOf(cut)))
        val activeTree = accessibleTree(root)
        assertFalse(activeTree.contains(background))
        assertTrue(activeTree.contains(sheetContent))
        assertTrue(activeTree.contains(dismiss))
        assertTrue(activeTree.contains(outside))
        assertTrue(reconciler.reconcileCuts(emptyList()))
        assertTrue(accessibleTree(root).contains(background))
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
