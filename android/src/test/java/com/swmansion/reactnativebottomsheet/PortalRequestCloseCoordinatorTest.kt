package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.ref.WeakReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalRequestCloseCoordinatorTest {
  @Test
  fun `newest owner candidate wins while non-candidates are skipped`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestTarget()
      val unresolvedUpper = TestTarget()
      val zeroUpper = TestTarget()
      val lowerRegistration = register(root, lower, candidate = true)
      val unresolvedRegistration = register(root, unresolvedUpper, candidate = false)
      val zeroRegistration = register(root, zeroUpper, candidate = false)

      try {
        assertTrue(lower.handlingEnabled)
        assertFalse(unresolvedUpper.handlingEnabled)
        assertFalse(zeroUpper.handlingEnabled)
        assertHandledEscape(root)
        assertEquals(1, lower.requestCount)

        zeroRegistration.update(portalState(candidate = true))
        assertFalse(lower.handlingEnabled)
        assertTrue(zeroUpper.handlingEnabled)
        assertHandledEscape(root, 100L)
        assertEquals(1, lower.requestCount)
        assertEquals(1, zeroUpper.requestCount)

        zeroRegistration.update(portalState(candidate = false))
        assertTrue(lower.handlingEnabled)
        assertHandledEscape(root, 110L)
        assertEquals(2, lower.requestCount)
      } finally {
        zeroRegistration.remove()
        unresolvedRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  @Test
  fun `owner without a handler blocks lower and handler toggles do not reorder it`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestTarget()
      val upper = TestTarget()
      val lowerRegistration = register(root, lower, candidate = true)
      val upperRegistration = register(root, upper, candidate = true, canEmit = false)

      try {
        assertFalse(lower.handlingEnabled)
        assertFalse(upper.handlingEnabled)
        assertEquals(
          PortalEscapeDispatchResult.OWNER_UNHANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeDown(120L)),
        )
        assertEquals(
          PortalEscapeDispatchResult.OWNER_UNHANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeUp(120L)),
        )

        upperRegistration.update(portalState(candidate = true, canEmit = true))
        assertTrue(upper.handlingEnabled)
        assertHandledEscape(root, 130L)
        assertEquals(0, lower.requestCount)
        assertEquals(1, upper.requestCount)

        upperRegistration.update(portalState(candidate = true, canEmit = false))
        upperRegistration.update(portalState(candidate = true, canEmit = true))
        assertTrue(upper.handlingEnabled)
        assertFalse(lower.handlingEnabled)
      } finally {
        upperRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  @Test
  fun `inactive upper passes new requests lower and resume restores its position`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestTarget()
      val upper = TestTarget()
      val lowerRegistration = register(root, lower, candidate = true)
      val upperRegistration = register(root, upper, candidate = true)

      try {
        upperRegistration.update(portalState(candidate = false))
        assertTrue(lower.handlingEnabled)
        assertHandledEscape(root, 140L)
        assertEquals(1, lower.requestCount)

        upperRegistration.update(portalState(candidate = true))
        assertTrue(upper.handlingEnabled)
        assertFalse(lower.handlingEnabled)
        assertHandledEscape(root, 150L)
        assertEquals(1, upper.requestCount)

        upperRegistration.update(portalState(candidate = false))
        assertTrue(lower.handlingEnabled)
      } finally {
        upperRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  @Test
  fun `identical updates are no-ops and updates after remove are ignored`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val target = TestTarget()
      val initialState = portalState(candidate = true)
      val registration = PortalRequestCloseCoordinator.register(root, target, initialState)

      assertEquals(listOf(true), target.handlingChanges)
      registration.update(initialState)
      assertEquals(listOf(true), target.handlingChanges)

      registration.remove()
      assertEquals(listOf(true, false), target.handlingChanges)
      registration.update(initialState)
      registration.remove()
      assertEquals(listOf(true, false), target.handlingChanges)
    }
  }

  @Test
  fun `initial enabled result is synchronous while initial disabled is not redundantly sent`() {
    withActivity { activity ->
      val enabledRoot = FrameLayout(activity)
      val enabled = TestTarget()
      val enabledRegistration = register(enabledRoot, enabled, candidate = true)
      val disabledRoot = FrameLayout(activity)
      val disabled = TestTarget()
      val disabledRegistration = register(disabledRoot, disabled, candidate = false)

      try {
        assertEquals(listOf(true), enabled.handlingChanges)
        assertTrue(enabled.handlingEnabled)
        assertTrue(disabled.handlingChanges.isEmpty())
        assertFalse(disabled.handlingEnabled)
      } finally {
        disabledRegistration.remove()
        enabledRegistration.remove()
      }
    }
  }

  @Test
  fun `owner transitions disable before enabling and remove disables before promoting lower`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val transitions = mutableListOf<String>()
      val lower = TestTarget("lower", transitions)
      val upper = TestTarget("upper", transitions)
      val lowerRegistration = register(root, lower, candidate = true)
      val upperRegistration = register(root, upper, candidate = false)

      try {
        transitions.clear()
        upperRegistration.update(portalState(candidate = true))
        assertEquals(listOf("lower:false", "upper:true"), transitions)

        transitions.clear()
        upperRegistration.remove()
        assertEquals(listOf("upper:false", "lower:true"), transitions)
      } finally {
        upperRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  @Test
  fun `roots are isolated and migration creates a new membership position`() {
    withActivity { activity ->
      val firstRoot = FrameLayout(activity)
      val secondRoot = FrameLayout(activity)
      val firstLower = TestTarget()
      val migrating = TestTarget()
      val isolated = TestTarget()
      val firstRegistration = register(firstRoot, firstLower, candidate = true)
      var migratingRegistration = register(firstRoot, migrating, candidate = true)
      val isolatedRegistration = register(secondRoot, isolated, candidate = true)

      try {
        assertTrue(migrating.handlingEnabled)
        assertTrue(isolated.handlingEnabled)

        migratingRegistration.remove()
        assertTrue(firstLower.handlingEnabled)
        migratingRegistration = register(secondRoot, migrating, candidate = true)
        assertTrue(migrating.handlingEnabled)
        assertFalse(isolated.handlingEnabled)
        assertTrue(firstLower.handlingEnabled)
        assertEquals(listOf(true, false, true), migrating.handlingChanges)
      } finally {
        migratingRegistration.remove()
        isolatedRegistration.remove()
        firstRegistration.remove()
      }
    }
  }

  @Test
  fun `dead weak target is cleaned and lower becomes owner`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestTarget()
      val lowerRegistration = register(root, lower, candidate = true)
      val upperRegistration = registerTransientTarget(root)

      try {
        clearRegisteredTargetReference(upperRegistration)
        assertHandledEscape(root, 160L)
        assertTrue(lower.handlingEnabled)
        assertEquals(1, lower.requestCount)
      } finally {
        upperRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  @Test
  fun `ownership or emission loss is terminal for a captured Escape press`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestTarget()
      val upper = TestTarget()
      val lowerRegistration = register(root, lower, candidate = true)
      val upperRegistration = register(root, upper, candidate = true)

      try {
        assertEquals(
          PortalEscapeDispatchResult.HANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeDown(170L)),
        )
        upperRegistration.update(portalState(candidate = false))
        upperRegistration.update(portalState(candidate = true))
        assertEquals(
          PortalEscapeDispatchResult.HANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeUp(170L)),
        )
        assertEquals(0, lower.requestCount)
        assertEquals(0, upper.requestCount)

        assertEquals(
          PortalEscapeDispatchResult.HANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeDown(180L)),
        )
        upperRegistration.update(portalState(candidate = true, canEmit = false))
        upperRegistration.update(portalState(candidate = true, canEmit = true))
        assertEquals(
          PortalEscapeDispatchResult.HANDLED,
          PortalRequestCloseCoordinator.dispatchEscape(root, escapeUp(180L)),
        )
        assertEquals(0, upper.requestCount)

        assertHandledEscape(root, 190L)
        assertEquals(1, upper.requestCount)
      } finally {
        upperRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  @Test
  fun `state rejects emission without owner candidacy`() {
    assertThrows(IllegalArgumentException::class.java) {
      PortalRequestCloseState(isOwnerCandidate = false, canEmitIfOwner = true)
    }
  }

  private fun register(
    root: FrameLayout,
    target: TestTarget,
    candidate: Boolean,
    canEmit: Boolean = candidate,
  ): PortalRequestCloseCoordinator.Registration =
    PortalRequestCloseCoordinator.register(root, target, portalState(candidate, canEmit))

  private fun registerTransientTarget(
    root: FrameLayout
  ): PortalRequestCloseCoordinator.Registration =
    PortalRequestCloseCoordinator.register(root, TestTarget(), portalState(candidate = true))

  /** Clears the coordinator's weak reference deterministically instead of relying on a GC cycle. */
  private fun clearRegisteredTargetReference(
    registration: PortalRequestCloseCoordinator.Registration
  ) {
    val entryField =
      registration.javaClass.declaredFields.first { field ->
        field.type.name.endsWith("PortalRequestCloseCoordinator\u0024Entry")
      }
    entryField.isAccessible = true
    val entry = entryField.get(registration)
    val targetField = entry.javaClass.getDeclaredField("target")
    targetField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (targetField.get(entry) as WeakReference<PortalRequestCloseTarget>).clear()
  }

  private fun portalState(
    candidate: Boolean,
    canEmit: Boolean = candidate,
  ) =
    PortalRequestCloseState(
      isOwnerCandidate = candidate,
      canEmitIfOwner = canEmit,
    )

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
  private val name: String? = null,
  private val sharedTransitions: MutableList<String>? = null,
) : PortalRequestCloseTarget {
  var handlingEnabled = false
  var locallyEligible = true
  var requestCount = 0
  val handlingChanges = mutableListOf<Boolean>()

  override fun onPortalRequestCloseHandlingChanged(enabled: Boolean) {
    handlingEnabled = enabled
    handlingChanges.add(enabled)
    if (name != null) sharedTransitions?.add("$name:$enabled")
  }

  override fun emitPortalRequestCloseIfEligible(): Boolean {
    if (!handlingEnabled || !locallyEligible) return false
    requestCount++
    return true
  }
}
