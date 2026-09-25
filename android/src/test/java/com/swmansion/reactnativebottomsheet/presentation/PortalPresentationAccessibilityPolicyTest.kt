package com.swmansion.reactnativebottomsheet.presentation

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalPresentationAccessibilityPolicyTest {
  @Test
  fun `accessibility policy retains only unique Top and all Active anchors when Top is unknown`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val window = FrameLayout(activity.get())
      val orderedRoot = TestReactRoot(activity.get())
      val orderedParent = FrameLayout(activity.get()).also(orderedRoot::addView)
      val lower = View(activity.get()).also(orderedParent::addView)
      val upper = View(activity.get()).also(orderedParent::addView)
      val unknownRoot = TestReactRoot(activity.get())
      val unknownFirst = View(activity.get()).also(unknownRoot::addView)
      val unknownSecond = View(activity.get()).also(unknownRoot::addView)
      window.addView(orderedRoot)
      window.addView(unknownRoot)
      activity.get().setContentView(window)
      val retained = mutableMapOf<View, Set<View>>()
      val observation =
        PortalPresentationCoordinator.observeAccessibilityPolicy { windowRoot, policy ->
          retained[windowRoot] = policy.map { it.anchor }.toSet()
        }
      val lowerRegistration = requireNotNull(PortalPresentationCoordinator.register(lower, true) {})
      val upperRegistration = requireNotNull(PortalPresentationCoordinator.register(upper, true) {})

      assertEquals(setOf(upper), retained[lower.rootView])

      lowerRegistration.remove()
      upperRegistration.remove()
      val firstRegistration =
        requireNotNull(PortalPresentationCoordinator.register(unknownFirst, true) {})
      val secondRegistration =
        requireNotNull(PortalPresentationCoordinator.register(unknownSecond, true) {})

      assertEquals(setOf(unknownFirst, unknownSecond), retained[unknownFirst.rootView])

      secondRegistration.remove()
      firstRegistration.remove()
      observation.remove()
    } finally {
      activity.close()
    }
  }

  @Test
  fun `inactive upper presentation is excluded until it becomes Active`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = TestReactRoot(activity.get())
      val parent = FrameLayout(activity.get()).also(root::addView)
      val lower = View(activity.get()).also(parent::addView)
      val upper = View(activity.get()).also(parent::addView)
      activity.get().setContentView(root)
      var retained = emptySet<View>()
      val observation = PortalPresentationCoordinator.observeAccessibilityPolicy { _, policy ->
        retained = policy.map { it.anchor }.toSet()
      }
      val lowerRegistration = requireNotNull(PortalPresentationCoordinator.register(lower, true) {})
      val upperRegistration =
        requireNotNull(PortalPresentationCoordinator.register(upper, false) {})
      assertEquals(setOf(lower), retained)

      upperRegistration.update(true)
      assertEquals(setOf(upper), retained)

      upperRegistration.remove()
      lowerRegistration.remove()
      observation.remove()
    } finally {
      activity.close()
    }
  }
}
