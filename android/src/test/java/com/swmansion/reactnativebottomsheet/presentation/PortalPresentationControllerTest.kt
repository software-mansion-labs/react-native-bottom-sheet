package com.swmansion.reactnativebottomsheet.presentation

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swmansion.reactnativebottomsheet.accessibility.PortalAccessibilityIsolationCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalPresentationControllerTest {
  @Test
  fun `clear restores isolation cancels pending sync and dispose makes later updates inert`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val lease = PortalAccessibilityIsolationCoordinator.acquire()
    try {
      val root = TestReactRoot(activity.get())
      val background = View(activity.get()).also(root::addView)
      val anchor = View(activity.get()).also(root::addView)
      activity.get().setContentView(root)
      val controller = PortalPresentationController(anchor) { _, _ -> }
      controller.update(isPortal = true, isActive = true)
      assertMasked(background)

      controller.scheduleHierarchySync()
      controller.clear()
      assertRestored(background)
      shadowOf(Looper.getMainLooper()).idle()
      assertRestored(background)

      controller.update(isPortal = true, isActive = true)
      assertMasked(background)
      controller.dispose()
      controller.dispose()
      controller.update(isPortal = true, isActive = true)
      controller.scheduleHierarchySync()
      shadowOf(Looper.getMainLooper()).idle()
      assertRestored(background)
    } finally {
      lease.release()
      activity.close()
    }
  }

  @Test
  fun `same-root reparent masks the new path before restore and migration cleans the old root`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val lease = PortalAccessibilityIsolationCoordinator.acquire()
    try {
      val window = FrameLayout(activity.get())
      val firstRoot = TestReactRoot(activity.get())
      val firstBackground = View(activity.get()).also(firstRoot::addView)
      val oldBranch = FrameLayout(activity.get()).also(firstRoot::addView)
      var newPathRestoredSafely = false
      val newBranch =
        RestoreObservingLayout(activity.get()) {
            newPathRestoredSafely = firstBackground.isMasked() && oldBranch.isMasked()
          }
          .also(firstRoot::addView)
      val anchor = View(activity.get()).also(oldBranch::addView)
      val secondRoot = TestReactRoot(activity.get())
      val secondBackground = View(activity.get()).also(secondRoot::addView)
      val secondBranch = FrameLayout(activity.get()).also(secondRoot::addView)
      window.addView(firstRoot)
      window.addView(secondRoot)
      activity.get().setContentView(window)
      val assignments = mutableListOf<Pair<ViewGroup?, PortalPresentationAssignment>>()
      val controller =
        PortalPresentationController(anchor) { context, assignment ->
          assignments.add(context?.reactRoot to assignment)
        }
      controller.update(isPortal = true, isActive = true)

      oldBranch.removeView(anchor)
      newBranch.addView(anchor)
      controller.syncHierarchy()

      assertTrue(newPathRestoredSafely)
      assertMasked(firstBackground)
      assertMasked(oldBranch)
      assertRestored(newBranch)

      assignments.clear()
      newBranch.removeView(anchor)
      secondBranch.addView(anchor)
      controller.scheduleHierarchySync()
      shadowOf(Looper.getMainLooper()).idle()

      assertRestored(firstBackground)
      assertRestored(oldBranch)
      assertRestored(newBranch)
      assertMasked(secondBackground)
      assertRestored(secondBranch)
      val oldRelease = assignments.indexOf(firstRoot to PortalPresentationAssignment.NONE)
      val newClaim = assignments.indexOf(secondRoot to PortalPresentationAssignment.TOP)
      assertTrue(oldRelease >= 0 && newClaim > oldRelease)
      controller.dispose()
      assertRestored(secondBackground)
    } finally {
      lease.release()
      activity.close()
    }
  }

  @Test
  fun `only an attached portal registers and clear and dispose withdraw synchronously`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = TestReactRoot(activity.get())
      val anchor = View(activity.get())
      var context: PortalPresentationContext? = null
      var assignment = PortalPresentationAssignment.NONE
      val controller =
        PortalPresentationController(anchor) { resolved, selected ->
          context = resolved
          assignment = selected
        }
      controller.update(isPortal = true, isActive = true)
      assertNull(context)
      root.addView(anchor)
      activity.get().setContentView(root)
      controller.syncHierarchy()
      assertEquals(PortalPresentationAssignment.TOP, assignment)
      controller.update(isPortal = false, isActive = true)
      assertNull(context)
      assertEquals(PortalPresentationAssignment.NONE, assignment)
      controller.update(isPortal = true, isActive = true)
      controller.clear()
      assertNull(context)
      controller.update(isPortal = true, isActive = true)
      controller.dispose()
      controller.update(isPortal = true, isActive = true)
      assertNull(context)
      assertEquals(PortalPresentationAssignment.NONE, assignment)
    } finally {
      activity.close()
    }
  }

  private fun assertMasked(view: View) {
    assertEquals(
      View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
      view.importantForAccessibility,
    )
  }

  private fun assertRestored(view: View) {
    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, view.importantForAccessibility)
  }

  private fun View.isMasked(): Boolean =
    importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

  private class RestoreObservingLayout(
    context: android.content.Context,
    private val onRestore: () -> Unit,
  ) : FrameLayout(context) {
    override fun setImportantForAccessibility(mode: Int) {
      if (
        importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS &&
          mode != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      ) {
        onRestore()
      }
      super.setImportantForAccessibility(mode)
    }
  }
}
