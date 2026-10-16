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
  fun `losing a lower Active anchor republishes policy even when the retained Top is unchanged`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    var observation: PortalPresentationCoordinator.PolicyObservation? = null
    val registrations = mutableListOf<PortalPresentationCoordinator.Registration>()
    try {
      val window = FrameLayout(activity.get())
      val rootA = TestReactRoot(activity.get()).also(window::addView)
      val rootB = TestReactRoot(activity.get()).also(window::addView)
      val a = View(activity.get()).also(rootA::addView)
      val b = View(activity.get()).also(rootB::addView)
      activity.get().setContentView(window)
      var active = emptySet<View>()
      var retained = emptySet<View>()
      observation = PortalPresentationCoordinator.observeAccessibilityPolicy { _, policy ->
        active = policy.activePresentations.map { it.anchor }.toSet()
        retained = policy.retainedPresentations.map { it.anchor }.toSet()
      }
      registrations.add(requireNotNull(PortalPresentationCoordinator.register(a, true) {}))
      registrations.add(requireNotNull(PortalPresentationCoordinator.register(b, true) {}))
      assertEquals(setOf(a, b), active)
      assertEquals(setOf(b), retained)

      rootA.removeView(a)
      rootB.viewTreeObserver.dispatchOnPreDraw()
      assertEquals(setOf(b), active)
      assertEquals(setOf(b), retained)
    } finally {
      registrations.forEach { it.remove() }
      observation?.remove()
      activity.close()
    }
  }

  @Test
  fun `policy includes lower Active presentations separately from the retained Top through settle`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    var observation: PortalPresentationCoordinator.PolicyObservation? = null
    val registrations = mutableListOf<PortalPresentationCoordinator.Registration>()
    try {
      val window = FrameLayout(activity.get())
      val rootA = TestReactRoot(activity.get()).also(window::addView)
      val rootB = TestReactRoot(activity.get()).also(window::addView)
      val a = View(activity.get()).also(rootA::addView)
      val b = View(activity.get()).also(rootB::addView)
      activity.get().setContentView(window)
      var active = emptySet<View>()
      var retained = emptySet<View>()
      observation = PortalPresentationCoordinator.observeAccessibilityPolicy { _, policy ->
        active = policy.activePresentations.map { it.anchor }.toSet()
        retained = policy.retainedPresentations.map { it.anchor }.toSet()
      }
      val registrationA = requireNotNull(PortalPresentationCoordinator.register(a, true) {})
      registrations.add(registrationA)
      val registrationB = requireNotNull(PortalPresentationCoordinator.register(b, false) {})
      registrations.add(registrationB)
      assertEquals(setOf(a), active)

      registrationB.update(true)
      assertEquals(setOf(b), retained)
      assertEquals(setOf(a, b), active)
      registrationB.update(true) // Host still reports Active during visible closing.
      assertEquals(setOf(a, b), active)
      registrationB.update(false) // Closing settled.
      assertEquals(setOf(a), active)
      assertEquals(setOf(a), retained)
      registrationA.update(false)
      assertEquals(emptySet<View>(), active)
      assertEquals(emptySet<View>(), retained)
    } finally {
      registrations.forEach { it.remove() }
      observation?.remove()
      activity.close()
    }
  }

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
          retained[windowRoot] = policy.retainedPresentations.map { it.anchor }.toSet()
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
        retained = policy.retainedPresentations.map { it.anchor }.toSet()
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
