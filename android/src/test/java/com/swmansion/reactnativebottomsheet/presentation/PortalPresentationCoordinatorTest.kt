package com.swmansion.reactnativebottomsheet.presentation

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.ref.WeakReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalPresentationCoordinatorTest {
  @Test
  fun `one host window has one Top while other windows keep independent ownership`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val otherActivity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val windowContent = FrameLayout(activity.get())
      val firstRoot = TestReactRoot(activity.get())
      val secondRoot = TestReactRoot(activity.get())
      val otherRoot = TestReactRoot(otherActivity.get())
      val first = View(activity.get()).also(firstRoot::addView)
      val second = View(activity.get()).also(secondRoot::addView)
      val other = View(otherActivity.get()).also(otherRoot::addView)
      windowContent.addView(firstRoot)
      windowContent.addView(secondRoot)
      activity.get().setContentView(windowContent)
      otherActivity.get().setContentView(otherRoot)
      var firstAssignment = PortalPresentationAssignment.NONE
      var secondAssignment = PortalPresentationAssignment.NONE
      var otherAssignment = PortalPresentationAssignment.NONE
      val firstObserver: (PortalPresentationAssignment) -> Unit = { firstAssignment = it }
      val secondObserver: (PortalPresentationAssignment) -> Unit = { secondAssignment = it }
      val otherObserver: (PortalPresentationAssignment) -> Unit = { otherAssignment = it }
      val firstRegistration =
        requireNotNull(PortalPresentationCoordinator.register(first, true, firstObserver))
      val secondRegistration =
        requireNotNull(PortalPresentationCoordinator.register(second, true, secondObserver))
      val otherRegistration =
        requireNotNull(PortalPresentationCoordinator.register(other, true, otherObserver))
      assertEquals(PortalPresentationAssignment.NONE, firstAssignment)
      assertEquals(PortalPresentationAssignment.TOP, secondAssignment)
      assertEquals(PortalPresentationAssignment.TOP, otherAssignment)
      secondRegistration.remove()
      assertEquals(PortalPresentationAssignment.TOP, firstAssignment)
      assertEquals(PortalPresentationAssignment.TOP, otherAssignment)
      firstRegistration.remove()
      otherRegistration.remove()
    } finally {
      otherActivity.close()
      activity.close()
    }
  }

  @Test
  fun `expired observer and detached anchor release ownership on reconciliation`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = TestReactRoot(activity.get())
      val parent = FrameLayout(activity.get())
      val lower = View(activity.get())
      val upper = View(activity.get())
      parent.addView(lower)
      parent.addView(upper)
      root.addView(parent)
      activity.get().setContentView(root)
      val assignments = mutableListOf<PortalPresentationAssignment>()
      val observer: (PortalPresentationAssignment) -> Unit = { assignments.add(it) }
      val lowerRegistration =
        requireNotNull(PortalPresentationCoordinator.register(lower, true, observer))
      val (expiredRegistration, weakObserver) = registerAbandonedObserver(upper)
      repeat(20) {
        if (weakObserver.get() != null) {
          System.gc()
          System.runFinalization()
        }
      }
      assertNull("the shared coordinator must not retain the observer", weakObserver.get())
      root.viewTreeObserver.dispatchOnPreDraw()
      assertEquals(
        listOf(
          PortalPresentationAssignment.TOP,
          PortalPresentationAssignment.NONE,
          PortalPresentationAssignment.TOP,
        ),
        assignments,
      )
      assertFalse(expiredRegistration.update(true))
      parent.removeView(lower)
      root.viewTreeObserver.dispatchOnPreDraw()
      assertEquals(PortalPresentationAssignment.NONE, assignments.last())
      assertFalse(lowerRegistration.update(true))
    } finally {
      activity.close()
    }
  }

  @Test
  fun `drawing observation resumes after the last registration is removed`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = TestReactRoot(activity.get())
      val parent = FrameLayout(activity.get()).also(root::addView)
      val lower = View(activity.get()).also(parent::addView)
      val upper = View(activity.get()).also(parent::addView)
      activity.get().setContentView(root)
      val changes = mutableListOf<String>()
      val lowerObserver: (PortalPresentationAssignment) -> Unit = { changes.add("lower:$it") }
      val upperObserver: (PortalPresentationAssignment) -> Unit = { changes.add("upper:$it") }
      var lowerRegistration =
        requireNotNull(PortalPresentationCoordinator.register(lower, true, lowerObserver))
      lowerRegistration.remove()
      changes.clear()
      root.viewTreeObserver.dispatchOnPreDraw()
      assertEquals(emptyList<String>(), changes)

      lowerRegistration =
        requireNotNull(PortalPresentationCoordinator.register(lower, true, lowerObserver))
      val upperRegistration =
        requireNotNull(PortalPresentationCoordinator.register(upper, true, upperObserver))
      changes.clear()
      lower.elevation = 10f
      root.viewTreeObserver.dispatchOnPreDraw()
      assertEquals(listOf("upper:NONE", "lower:TOP"), changes)
      lowerRegistration.remove()
      upperRegistration.remove()
      changes.clear()
      lower.elevation = 0f
      root.viewTreeObserver.dispatchOnPreDraw()
      assertEquals(emptyList<String>(), changes)
    } finally {
      activity.close()
    }
  }

  @Test
  fun `equal anchors publish no Top and only the newest registration gets Close fallback`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = TestReactRoot(activity.get())
      val anchor = View(activity.get())
      root.addView(anchor)
      activity.get().setContentView(root)
      var first = PortalPresentationAssignment.NONE
      var second = PortalPresentationAssignment.NONE
      val firstObserver: (PortalPresentationAssignment) -> Unit = { first = it }
      val secondObserver: (PortalPresentationAssignment) -> Unit = { second = it }
      val firstRegistration =
        requireNotNull(PortalPresentationCoordinator.register(anchor, true, firstObserver))
      val secondRegistration =
        requireNotNull(PortalPresentationCoordinator.register(anchor, true, secondObserver))
      assertEquals(PortalPresentationAssignment.NONE, first)
      assertEquals(PortalPresentationAssignment.CLOSE_FALLBACK, second)
      secondRegistration.remove()
      assertEquals(PortalPresentationAssignment.TOP, first)
      firstRegistration.remove()
      firstRegistration.update(true)
      assertEquals(PortalPresentationAssignment.NONE, first)
    } finally {
      activity.close()
    }
  }

  private fun registerAbandonedObserver(
    anchor: View
  ): Pair<
    PortalPresentationCoordinator.Registration,
    WeakReference<(PortalPresentationAssignment) -> Unit>,
  > {
    val assignments = mutableListOf<PortalPresentationAssignment>()
    val observer: (PortalPresentationAssignment) -> Unit = { assignments.add(it) }
    return requireNotNull(PortalPresentationCoordinator.register(anchor, true, observer)) to
      WeakReference(observer)
  }

  @Test
  fun `unknown order has only a Close fallback and updates keep registration position`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      // An arbitrary RootView implementation does not expose its drawing contract.
      val root = TestReactRoot(activity.get())
      val first = View(activity.get())
      val second = View(activity.get())
      root.addView(first)
      root.addView(second)
      activity.get().setContentView(root)
      val changes = mutableListOf<String>()
      val firstObserver: (PortalPresentationAssignment) -> Unit = { changes.add("first:$it") }
      val secondObserver: (PortalPresentationAssignment) -> Unit = { changes.add("second:$it") }
      var firstRegistration =
        requireNotNull(PortalPresentationCoordinator.register(first, true, firstObserver))
      val secondRegistration =
        requireNotNull(PortalPresentationCoordinator.register(second, true, secondObserver))
      assertEquals(listOf("first:TOP", "first:NONE", "second:CLOSE_FALLBACK"), changes)
      changes.clear()
      firstRegistration.update(false)
      firstRegistration.update(true)
      assertEquals(listOf("second:TOP", "second:CLOSE_FALLBACK"), changes)

      changes.clear()
      firstRegistration.remove()
      firstRegistration =
        requireNotNull(PortalPresentationCoordinator.register(first, true, firstObserver))
      assertEquals(listOf("second:TOP", "second:NONE", "first:CLOSE_FALLBACK"), changes)
      firstRegistration.remove()
      secondRegistration.remove()
    } finally {
      activity.close()
    }
  }

  @Test
  fun `visual Top owns the assignment and transfers before enabling its successor`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = TestReactRoot(activity.get())
      val parent = FrameLayout(activity.get())
      val lower = View(activity.get())
      val upper = View(activity.get())
      parent.addView(lower)
      parent.addView(upper)
      root.addView(parent)
      activity.get().setContentView(root)
      val changes = mutableListOf<String>()
      val upperObserver: (PortalPresentationAssignment) -> Unit = { changes.add("upper:$it") }
      val lowerObserver: (PortalPresentationAssignment) -> Unit = { changes.add("lower:$it") }
      val upperRegistration =
        requireNotNull(PortalPresentationCoordinator.register(upper, true, upperObserver))
      val lowerRegistration =
        requireNotNull(PortalPresentationCoordinator.register(lower, true, lowerObserver))
      assertEquals(listOf("upper:TOP"), changes)

      changes.clear()
      upperRegistration.update(false)
      assertEquals(listOf("upper:NONE", "lower:TOP"), changes)
      lowerRegistration.remove()
      upperRegistration.remove()
    } finally {
      activity.close()
    }
  }
}
