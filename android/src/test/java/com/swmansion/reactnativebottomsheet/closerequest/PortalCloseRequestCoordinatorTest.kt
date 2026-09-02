package com.swmansion.reactnativebottomsheet.closerequest

import android.app.Activity
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class PortalCloseRequestCoordinatorTest {
  @Test
  fun `newest routing owner candidate wins while non-candidates are skipped`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestParticipant()
      val unresolvedUpper = TestParticipant()
      val zeroUpper = TestParticipant()
      val lowerRegistration = register(root, lower, routingOwnerCandidate = true)
      val unresolvedRegistration = register(root, unresolvedUpper, routingOwnerCandidate = false)
      val zeroRegistration = register(root, zeroUpper, routingOwnerCandidate = false)

      try {
        assertTrue(lower.isInputHandlingEnabled)
        assertFalse(unresolvedUpper.isInputHandlingEnabled)
        assertFalse(zeroUpper.isInputHandlingEnabled)
        assertHandledEscape(root)
        assertEquals(1, lower.requestCount)

        zeroRegistration.update(portalState(routingOwnerCandidate = true))
        assertFalse(lower.isInputHandlingEnabled)
        assertTrue(zeroUpper.isInputHandlingEnabled)
        assertHandledEscape(root, 100L)
        assertEquals(1, lower.requestCount)
        assertEquals(1, zeroUpper.requestCount)

        zeroRegistration.update(portalState(routingOwnerCandidate = false))
        assertTrue(lower.isInputHandlingEnabled)
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
  fun `routing owner without a handler blocks lower and handler toggles do not reorder it`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestParticipant()
      val upper = TestParticipant()
      val lowerRegistration = register(root, lower, routingOwnerCandidate = true)
      val upperRegistration = register(root, upper, routingOwnerCandidate = true, canEmit = false)

      try {
        assertFalse(lower.isInputHandlingEnabled)
        assertFalse(upper.isInputHandlingEnabled)
        assertFalse(PortalCloseRequestCoordinator.dispatchEscape(root, escapeDown(120L)))
        assertFalse(PortalCloseRequestCoordinator.dispatchEscape(root, escapeUp(120L)))

        upperRegistration.update(portalState(routingOwnerCandidate = true, canEmit = true))
        assertTrue(upper.isInputHandlingEnabled)
        assertHandledEscape(root, 130L)
        assertEquals(0, lower.requestCount)
        assertEquals(1, upper.requestCount)

        upperRegistration.update(portalState(routingOwnerCandidate = true, canEmit = false))
        upperRegistration.update(portalState(routingOwnerCandidate = true, canEmit = true))
        assertTrue(upper.isInputHandlingEnabled)
        assertFalse(lower.isInputHandlingEnabled)
      } finally {
        upperRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  @Test
  fun `consuming upper routing owner blocks lower without emitting`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestParticipant()
      val upper = TestParticipant()
      val lowerRegistration = register(root, lower, routingOwnerCandidate = true)
      val upperRegistration =
        PortalCloseRequestCoordinator.register(
          root,
          upper,
          portalState(routingOwnerCandidate = true, action = CloseRequestInputAction.CONSUME),
        )

      try {
        assertFalse(lower.isInputHandlingEnabled)
        assertEquals(CloseRequestInputAction.CONSUME, upper.action)
        assertHandledEscape(root, 135L)
        assertEquals(0, lower.requestCount)
        assertEquals(0, upper.requestCount)

        upperRegistration.update(portalState(routingOwnerCandidate = false))
        assertTrue(lower.isInputHandlingEnabled)
        assertHandledEscape(root, 136L)
        assertEquals(1, lower.requestCount)
      } finally {
        upperRegistration.remove()
        lowerRegistration.remove()
      }
    }
  }

  @Test
  fun `same routing owner changes action without passing through`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val participant = TestParticipant()
      val registration = register(root, participant, routingOwnerCandidate = true)

      try {
        registration.update(
          portalState(
            routingOwnerCandidate = true,
            action = CloseRequestInputAction.CONSUME,
          )
        )
        registration.update(
          portalState(
            routingOwnerCandidate = true,
            action = CloseRequestInputAction.EMIT_CLOSE_REQUEST,
          )
        )

        assertEquals(
          listOf(
            CloseRequestInputAction.EMIT_CLOSE_REQUEST,
            CloseRequestInputAction.CONSUME,
            CloseRequestInputAction.EMIT_CLOSE_REQUEST,
          ),
          participant.actionChanges,
        )
        assertEquals(listOf(true, true, true), participant.inputHandlingChanges)
        assertTrue(participant.isInputHandlingEnabled)
      } finally {
        registration.remove()
      }
    }
  }

  @Test
  fun `inactive upper passes new requests lower and resume restores its position`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestParticipant()
      val upper = TestParticipant()
      val lowerRegistration = register(root, lower, routingOwnerCandidate = true)
      val upperRegistration = register(root, upper, routingOwnerCandidate = true)

      try {
        upperRegistration.update(portalState(routingOwnerCandidate = false))
        assertTrue(lower.isInputHandlingEnabled)
        assertHandledEscape(root, 140L)
        assertEquals(1, lower.requestCount)

        upperRegistration.update(portalState(routingOwnerCandidate = true))
        assertTrue(upper.isInputHandlingEnabled)
        assertFalse(lower.isInputHandlingEnabled)
        assertHandledEscape(root, 150L)
        assertEquals(1, upper.requestCount)

        upperRegistration.update(portalState(routingOwnerCandidate = false))
        assertTrue(lower.isInputHandlingEnabled)
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
      val participant = TestParticipant()
      val initialState = portalState(routingOwnerCandidate = true)
      val registration = PortalCloseRequestCoordinator.register(root, participant, initialState)

      assertEquals(listOf(true), participant.inputHandlingChanges)
      registration.update(initialState)
      assertEquals(listOf(true), participant.inputHandlingChanges)

      registration.remove()
      assertEquals(listOf(true, false), participant.inputHandlingChanges)
      registration.update(initialState)
      registration.remove()
      assertEquals(listOf(true, false), participant.inputHandlingChanges)
    }
  }

  @Test
  fun `initial action assignment is synchronous and pass-through is not redundantly reported`() {
    withActivity { activity ->
      val closeRequestRoot = FrameLayout(activity)
      val closeRequestParticipant = TestParticipant()
      val closeRequestRegistration =
        register(closeRequestRoot, closeRequestParticipant, routingOwnerCandidate = true)
      val passThroughRoot = FrameLayout(activity)
      val passThroughParticipant = TestParticipant()
      val passThroughRegistration =
        register(passThroughRoot, passThroughParticipant, routingOwnerCandidate = false)

      try {
        assertEquals(listOf(true), closeRequestParticipant.inputHandlingChanges)
        assertTrue(closeRequestParticipant.isInputHandlingEnabled)
        assertTrue(passThroughParticipant.inputHandlingChanges.isEmpty())
        assertFalse(passThroughParticipant.isInputHandlingEnabled)
      } finally {
        passThroughRegistration.remove()
        closeRequestRegistration.remove()
      }
    }
  }

  @Test
  fun `routing owner transitions disable before enabling and remove disables before promoting lower`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val transitions = mutableListOf<String>()
      val lower = TestParticipant("lower", transitions)
      val upper = TestParticipant("upper", transitions)
      val lowerRegistration = register(root, lower, routingOwnerCandidate = true)
      val upperRegistration = register(root, upper, routingOwnerCandidate = false)

      try {
        transitions.clear()
        upperRegistration.update(portalState(routingOwnerCandidate = true))
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
      val firstLower = TestParticipant()
      val migrating = TestParticipant()
      val isolated = TestParticipant()
      val firstRegistration = register(firstRoot, firstLower, routingOwnerCandidate = true)
      var migratingRegistration = register(firstRoot, migrating, routingOwnerCandidate = true)
      val isolatedRegistration = register(secondRoot, isolated, routingOwnerCandidate = true)

      try {
        assertTrue(migrating.isInputHandlingEnabled)
        assertTrue(isolated.isInputHandlingEnabled)

        migratingRegistration.remove()
        assertTrue(firstLower.isInputHandlingEnabled)
        migratingRegistration = register(secondRoot, migrating, routingOwnerCandidate = true)
        assertTrue(migrating.isInputHandlingEnabled)
        assertFalse(isolated.isInputHandlingEnabled)
        assertTrue(firstLower.isInputHandlingEnabled)
        assertEquals(listOf(true, false, true), migrating.inputHandlingChanges)
      } finally {
        migratingRegistration.remove()
        isolatedRegistration.remove()
        firstRegistration.remove()
      }
    }
  }

  @Test
  fun `routing ownership or emission loss is terminal for a captured Escape press`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val lower = TestParticipant()
      val upper = TestParticipant()
      val lowerRegistration = register(root, lower, routingOwnerCandidate = true)
      val upperRegistration = register(root, upper, routingOwnerCandidate = true)

      try {
        assertTrue(PortalCloseRequestCoordinator.dispatchEscape(root, escapeDown(170L)))
        upperRegistration.update(portalState(routingOwnerCandidate = false))
        upperRegistration.update(portalState(routingOwnerCandidate = true))
        assertTrue(PortalCloseRequestCoordinator.dispatchEscape(root, escapeUp(170L)))
        assertEquals(0, lower.requestCount)
        assertEquals(0, upper.requestCount)

        assertTrue(PortalCloseRequestCoordinator.dispatchEscape(root, escapeDown(180L)))
        upperRegistration.update(portalState(routingOwnerCandidate = true, canEmit = false))
        upperRegistration.update(portalState(routingOwnerCandidate = true, canEmit = true))
        assertTrue(PortalCloseRequestCoordinator.dispatchEscape(root, escapeUp(180L)))
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
  fun `state rejects emission without routing owner candidacy`() {
    assertThrows(IllegalArgumentException::class.java) {
      PortalCloseRequestState(
        isRoutingOwnerCandidate = false,
        actionIfRoutingOwner = CloseRequestInputAction.EMIT_CLOSE_REQUEST,
      )
    }
  }

  private fun register(
    root: FrameLayout,
    participant: TestParticipant,
    routingOwnerCandidate: Boolean,
    canEmit: Boolean = routingOwnerCandidate,
  ): PortalCloseRequestCoordinator.Registration =
    PortalCloseRequestCoordinator.register(
      root,
      participant,
      portalState(routingOwnerCandidate, canEmit),
    )

  private fun portalState(
    routingOwnerCandidate: Boolean,
    canEmit: Boolean = routingOwnerCandidate,
    action: CloseRequestInputAction? = null,
  ) =
    PortalCloseRequestState(
      isRoutingOwnerCandidate = routingOwnerCandidate,
      actionIfRoutingOwner =
        action
          ?: if (routingOwnerCandidate && canEmit) {
            CloseRequestInputAction.EMIT_CLOSE_REQUEST
          } else {
            CloseRequestInputAction.PASS_THROUGH
          },
    )

  private fun assertHandledEscape(root: FrameLayout, downTime: Long = 90L) {
    assertTrue(PortalCloseRequestCoordinator.dispatchEscape(root, escapeDown(downTime)))
    assertTrue(PortalCloseRequestCoordinator.dispatchEscape(root, escapeUp(downTime)))
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

private class TestParticipant(
  private val name: String? = null,
  private val sharedTransitions: MutableList<String>? = null,
) : PortalCloseRequestParticipant {
  var action = CloseRequestInputAction.PASS_THROUGH
  var isInputHandlingEnabled = false
  var locallyEligible = true
  var requestCount = 0
  val actionChanges = mutableListOf<CloseRequestInputAction>()
  val inputHandlingChanges = mutableListOf<Boolean>()

  override fun onAssignedActionChanged(action: CloseRequestInputAction) {
    this.action = action
    actionChanges.add(action)
    val enabled = action != CloseRequestInputAction.PASS_THROUGH
    isInputHandlingEnabled = enabled
    inputHandlingChanges.add(enabled)
    if (name != null) sharedTransitions?.add("$name:$enabled")
  }

  override fun emitCloseRequestIfEligible(): Boolean {
    if (action != CloseRequestInputAction.EMIT_CLOSE_REQUEST || !locallyEligible) return false
    requestCount++
    return true
  }
}
