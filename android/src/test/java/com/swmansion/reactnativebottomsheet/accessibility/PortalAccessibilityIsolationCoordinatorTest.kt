package com.swmansion.reactnativebottomsheet.accessibility

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationCoordinator
import com.swmansion.reactnativebottomsheet.presentation.TestReactRoot
import java.lang.ref.WeakReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalAccessibilityIsolationCoordinatorTest {
  @Test
  fun `nested Top stays isolated through closing and transfers directly on settle`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val lease = PortalAccessibilityIsolationCoordinator.acquire()
    try {
      val root = TestReactRoot(activity.get())
      val background = View(activity.get()).also(root::addView)
      val lowerPortal = FrameLayout(activity.get()).also(root::addView)
      var transferredWithoutBackgroundRestore = false
      val lowerContent =
        RestoreObservingView(activity.get()) {
            transferredWithoutBackgroundRestore =
              background.importantForAccessibility ==
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
          }
          .also(lowerPortal::addView)
      val nestedProvider = FrameLayout(activity.get()).also(lowerPortal::addView)
      val upperPortal = View(activity.get()).also(nestedProvider::addView)
      activity.get().setContentView(root)
      val lowerRegistration =
        requireNotNull(PortalPresentationCoordinator.register(lowerPortal, true) {})
      val upperRegistration =
        requireNotNull(PortalPresentationCoordinator.register(upperPortal, true) {})

      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        background.importantForAccessibility,
      )
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        lowerContent.importantForAccessibility,
      )
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, upperPortal.importantForAccessibility)

      // A visible closing Top remains Active until its host reports settle.
      upperRegistration.update(true)
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        lowerContent.importantForAccessibility,
      )
      upperRegistration.update(false)

      assertTrue(transferredWithoutBackgroundRestore)
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, lowerContent.importantForAccessibility)
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        background.importantForAccessibility,
      )

      upperRegistration.remove()
      lowerRegistration.remove()
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, background.importantForAccessibility)
    } finally {
      lease.release()
      activity.close()
    }
  }

  @Test
  fun `shared presentation policy isolates only the owner React root and restores on release`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val lease = PortalAccessibilityIsolationCoordinator.acquire()
    try {
      val window = FrameLayout(activity.get())
      val root = TestReactRoot(activity.get())
      val background = View(activity.get()).also(root::addView)
      val owner = View(activity.get()).also(root::addView)
      val otherRoot = TestReactRoot(activity.get())
      val otherRootContent = View(activity.get()).also(otherRoot::addView)
      window.addView(root)
      window.addView(otherRoot)
      activity.get().setContentView(window)

      val registration = requireNotNull(PortalPresentationCoordinator.register(owner, true) {})

      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        background.importantForAccessibility,
      )
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, owner.importantForAccessibility)
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
        otherRootContent.importantForAccessibility,
      )

      registration.remove()
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, background.importantForAccessibility)
    } finally {
      lease.release()
      activity.close()
    }
  }

  @Test
  fun `unique Top hides the lower portal while unknown Top retains every Active portal`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val lease = PortalAccessibilityIsolationCoordinator.acquire()
    try {
      val window = FrameLayout(activity.get())
      val orderedRoot = TestReactRoot(activity.get())
      val orderedParent = FrameLayout(activity.get()).also(orderedRoot::addView)
      val background = View(activity.get()).also(orderedParent::addView)
      val lower = FrameLayout(activity.get()).also(orderedParent::addView)
      val upper = FrameLayout(activity.get()).also(orderedParent::addView)
      window.addView(orderedRoot)
      activity.get().setContentView(window)
      val lowerRegistration = requireNotNull(PortalPresentationCoordinator.register(lower, true) {})
      val upperRegistration = requireNotNull(PortalPresentationCoordinator.register(upper, true) {})

      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        background.importantForAccessibility,
      )
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        lower.importantForAccessibility,
      )
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, upper.importantForAccessibility)

      upperRegistration.remove()
      lowerRegistration.remove()
      val unknownRoot = TestReactRoot(activity.get())
      val unknownBackground = View(activity.get()).also(unknownRoot::addView)
      val first = View(activity.get()).also(unknownRoot::addView)
      val second = View(activity.get()).also(unknownRoot::addView)
      window.addView(unknownRoot)
      val firstRegistration = requireNotNull(PortalPresentationCoordinator.register(first, true) {})
      val secondRegistration =
        requireNotNull(PortalPresentationCoordinator.register(second, true) {})

      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        unknownBackground.importantForAccessibility,
      )
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, first.importantForAccessibility)
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, second.importantForAccessibility)

      secondRegistration.remove()
      firstRegistration.remove()
    } finally {
      lease.release()
      activity.close()
    }
  }

  @Test
  fun `isolation state does not retain a detached React root`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val lease = PortalAccessibilityIsolationCoordinator.acquire()
    val (registration, root) = registerAndDetachRoot(activity.get())
    try {
      repeat(20) {
        if (root.get() != null) {
          System.gc()
          System.runFinalization()
        }
      }

      assertNull("the isolation coordinator must not retain the React root", root.get())
    } finally {
      registration.remove()
      lease.release()
      activity.close()
    }
  }

  private fun registerAndDetachRoot(
    activity: Activity
  ): Pair<PortalPresentationCoordinator.Registration, WeakReference<TestReactRoot>> {
    val window = FrameLayout(activity)
    val root = TestReactRoot(activity)
    val background = View(activity).also(root::addView)
    val owner = View(activity).also(root::addView)
    window.addView(root)
    activity.setContentView(window)
    val registration = requireNotNull(PortalPresentationCoordinator.register(owner, true) {})
    assertEquals(
      View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
      background.importantForAccessibility,
    )
    window.removeView(root)
    return registration to WeakReference(root)
  }

  private class RestoreObservingView(
    activity: Activity,
    private val onRestore: () -> Unit,
  ) : View(activity) {
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
