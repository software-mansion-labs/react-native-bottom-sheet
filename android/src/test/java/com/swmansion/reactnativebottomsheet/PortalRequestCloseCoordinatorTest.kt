package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class PortalRequestCloseCoordinatorTest {
  @Test
  fun `newest open membership owns requests while unresolved and zero targets are skipped`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestTarget(isOpen = true)
      val unresolvedUpper = TestTarget(isOpen = false)
      val zeroUpper = TestTarget(isOpen = false)
      val lowerRegistration = PortalRequestCloseCoordinator.register(root, lower)
      val unresolvedRegistration = PortalRequestCloseCoordinator.register(root, unresolvedUpper)
      val zeroRegistration = PortalRequestCloseCoordinator.register(root, zeroUpper)

      try {
        assertTrue(lower.isOwner)
        assertFalse(unresolvedUpper.isOwner)
        assertFalse(zeroUpper.isOwner)
        assertHandledEscape(root)
        assertEquals(1, lower.requestCount)

        zeroUpper.isOpen = true
        zeroRegistration.targetChanged()
        assertFalse(lower.isOwner)
        assertTrue(zeroUpper.isOwner)
        assertHandledEscape(root)
        assertEquals(1, lower.requestCount)
        assertEquals(1, zeroUpper.requestCount)

        zeroUpper.isOpen = false
        zeroRegistration.targetChanged()
        assertTrue(lower.isOwner)
        assertHandledEscape(root)
        assertEquals(2, lower.requestCount)
      } finally {
        zeroRegistration.remove()
        unresolvedRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  @Test
  fun `an open owner without an active handler blocks lower memberships`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestTarget(isOpen = true, isEligible = true)
      val upper = TestTarget(isOpen = true, isEligible = false)
      val lowerRegistration = PortalRequestCloseCoordinator.register(root, lower)
      val upperRegistration = PortalRequestCloseCoordinator.register(root, upper)

      try {
        assertTrue(upper.isOwner)
        assertEquals(
          PortalEscapeDispatchResult.OWNER_UNHANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeDown(100L)),
        )
        assertEquals(
          PortalEscapeDispatchResult.OWNER_UNHANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeUp(100L)),
        )
        assertEquals(0, lower.requestCount)
        assertEquals(0, upper.requestCount)

        upper.isEligible = true
        upperRegistration.eligibilityChanged()
        assertHandledEscape(root, 110L)
        assertEquals(0, lower.requestCount)
        assertEquals(1, upper.requestCount)
      } finally {
        upperRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  @Test
  fun `ownership is isolated per root and target updates never reorder memberships`() {
    withActivity { activity ->
      val firstRoot = FrameLayout(activity)
      val secondRoot = FrameLayout(activity)
      val firstProviderLower = TestTarget(isOpen = true)
      val secondProviderUpper = TestTarget(isOpen = true)
      val isolated = TestTarget(isOpen = true)
      val lowerRegistration = PortalRequestCloseCoordinator.register(firstRoot, firstProviderLower)
      val upperRegistration = PortalRequestCloseCoordinator.register(firstRoot, secondProviderUpper)
      val isolatedRegistration = PortalRequestCloseCoordinator.register(secondRoot, isolated)

      try {
        secondProviderUpper.isOpen = false
        upperRegistration.targetChanged()
        secondProviderUpper.isOpen = true
        upperRegistration.targetChanged()

        assertTrue(secondProviderUpper.isOwner)
        assertFalse(firstProviderLower.isOwner)
        assertTrue(isolated.isOwner)
        assertHandledEscape(firstRoot, 120L)
        assertHandledEscape(secondRoot, 130L)
        assertEquals(1, secondProviderUpper.requestCount)
        assertEquals(1, isolated.requestCount)
      } finally {
        isolatedRegistration.remove()
        upperRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  @Test
  fun `remove and root migration create a new membership position`() {
    withActivity { activity ->
      val firstRoot = FrameLayout(activity)
      val secondRoot = FrameLayout(activity)
      val first = TestTarget(isOpen = true)
      val migrating = TestTarget(isOpen = true)
      val firstRegistration = PortalRequestCloseCoordinator.register(firstRoot, first)
      var migratingRegistration = PortalRequestCloseCoordinator.register(firstRoot, migrating)

      try {
        assertTrue(migrating.isOwner)
        migratingRegistration.remove()
        assertFalse(migrating.isOwner)
        assertTrue(first.isOwner)

        migratingRegistration = PortalRequestCloseCoordinator.register(secondRoot, migrating)
        assertTrue(first.isOwner)
        assertTrue(migrating.isOwner)

        val laterInSecondRoot = TestTarget(isOpen = true)
        val laterRegistration =
          PortalRequestCloseCoordinator.register(secondRoot, laterInSecondRoot)
        try {
          assertTrue(laterInSecondRoot.isOwner)
          migratingRegistration.remove()
          migratingRegistration = PortalRequestCloseCoordinator.register(secondRoot, migrating)
          assertTrue(migrating.isOwner)
          assertFalse(laterInSecondRoot.isOwner)
        } finally {
          laterRegistration.remove()
        }
      } finally {
        migratingRegistration.remove()
        firstRegistration.remove()
      }
    }
  }

  @Test
  fun `captured Escape remains pinned and eligibility loss is terminal for that press`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestTarget(isOpen = true)
      val upper = TestTarget(isOpen = false)
      val lowerRegistration = PortalRequestCloseCoordinator.register(root, lower)
      val upperRegistration = PortalRequestCloseCoordinator.register(root, upper)

      try {
        assertEquals(
          PortalEscapeDispatchResult.HANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeDown(140L)),
        )
        upper.isOpen = true
        upperRegistration.targetChanged()
        assertEquals(
          PortalEscapeDispatchResult.HANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeUp(140L)),
        )
        assertEquals(0, lower.requestCount)
        assertEquals(0, upper.requestCount)

        assertEquals(
          PortalEscapeDispatchResult.HANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeDown(150L)),
        )
        upper.isEligible = false
        upperRegistration.eligibilityChanged()
        upper.isEligible = true
        upperRegistration.eligibilityChanged()
        assertEquals(
          PortalEscapeDispatchResult.HANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeUp(150L)),
        )
        assertEquals(0, upper.requestCount)

        assertHandledEscape(root, 160L)
        assertEquals(1, upper.requestCount)
      } finally {
        upperRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  private fun assertHandledEscape(root: FrameLayout, downTime: Long = 90L) {
    assertEquals(
      PortalEscapeDispatchResult.HANDLED,
      PortalRequestCloseCoordinator.dispatchEscape(root, escapeDown(downTime)),
    )
    assertEquals(
      PortalEscapeDispatchResult.HANDLED,
      PortalRequestCloseCoordinator.dispatchEscape(root, escapeUp(downTime)),
    )
  }

  private fun escapeDown(downTime: Long) =
    KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0)

  private fun escapeUp(downTime: Long) =
    KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0)

  private fun withActivity(block: (Activity) -> Unit) {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      block(controller.get())
    } finally {
      controller.close()
    }
  }
}

private class TestTarget(
  var isOpen: Boolean,
  var isEligible: Boolean = true,
) : PortalRequestCloseTarget {
  var isOwner = false
  var requestCount = 0

  override val isPortalRequestCloseTargetOpen: Boolean
    get() = isOpen

  override val isPortalRequestCloseEligible: Boolean
    get() = isEligible && isOwner

  override fun onPortalRequestCloseOwnershipChanged(isOwner: Boolean) {
    this.isOwner = isOwner
  }

  override fun emitPortalRequestCloseIfEligible(): Boolean {
    if (!isPortalRequestCloseEligible) return false
    requestCount++
    return true
  }
}
