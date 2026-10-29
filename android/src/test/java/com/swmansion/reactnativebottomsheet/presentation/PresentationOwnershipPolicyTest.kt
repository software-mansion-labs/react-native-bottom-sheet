package com.swmansion.reactnativebottomsheet.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationOwnershipPolicyTest {
  private enum class Sheet {
    A,
    B,
    C,
  }

  @Test
  fun `unique visual Top is retained even when another presentation registered later`() {
    val ownership =
      resolvePresentationOwnership(listOf(Sheet.B, Sheet.A)) { first, second ->
        when (first to second) {
          Sheet.B to Sheet.A -> NativePortalOrder.ABOVE
          Sheet.A to Sheet.B -> NativePortalOrder.BELOW
          else -> NativePortalOrder.UNKNOWN
        }
      }

    assertEquals(PresentationOwnershipDecision(Sheet.B, null, listOf(Sheet.B)), ownership)
  }

  @Test
  fun `unknown Top retains every Active presentation with a separate Close fallback owner`() {
    val ownership =
      resolvePresentationOwnership(listOf(Sheet.A, Sheet.B)) { _, _ -> NativePortalOrder.UNKNOWN }

    assertEquals(
      PresentationOwnershipDecision(null, Sheet.B, listOf(Sheet.A, Sheet.B)),
      ownership,
    )
  }

  @Test
  fun `no Active presentation leaves no ownership or retained presentation`() {
    val ownership =
      resolvePresentationOwnership<Sheet>(emptyList()) { _, _ -> NativePortalOrder.UNKNOWN }

    assertEquals(PresentationOwnershipDecision<Sheet>(null, null, emptyList()), ownership)
  }

  @Test
  fun `one Active presentation has a unique Top without pairwise order evidence`() {
    val ownership =
      resolvePresentationOwnership(listOf(Sheet.A)) { _, _ -> NativePortalOrder.UNKNOWN }

    assertEquals(PresentationOwnershipDecision(Sheet.A, null, listOf(Sheet.A)), ownership)
  }

  @Test
  fun `equal native anchors do not establish Top between distinct presentations`() {
    val ownership =
      resolvePresentationOwnership(listOf(Sheet.A, Sheet.B)) { _, _ -> NativePortalOrder.EQUAL }

    assertEquals(
      PresentationOwnershipDecision(null, Sheet.B, listOf(Sheet.A, Sheet.B)),
      ownership,
    )
  }

  @Test
  fun `unknown order among lower presentations does not prevent a proved Top`() {
    val ownership =
      resolvePresentationOwnership(listOf(Sheet.B, Sheet.A, Sheet.C)) { first, second ->
        when (first to second) {
          Sheet.B to Sheet.A,
          Sheet.B to Sheet.C -> NativePortalOrder.ABOVE
          Sheet.A to Sheet.B,
          Sheet.C to Sheet.B -> NativePortalOrder.BELOW
          else -> NativePortalOrder.UNKNOWN
        }
      }

    assertEquals(PresentationOwnershipDecision(Sheet.B, null, listOf(Sheet.B)), ownership)
  }

  @Test
  fun `unresolved Top retains even an Active presentation known to be below the contenders`() {
    val ownership =
      resolvePresentationOwnership(listOf(Sheet.A, Sheet.B, Sheet.C)) { first, second ->
        when (first to second) {
          Sheet.B to Sheet.A,
          Sheet.C to Sheet.A -> NativePortalOrder.ABOVE
          Sheet.A to Sheet.B,
          Sheet.A to Sheet.C -> NativePortalOrder.BELOW
          else -> NativePortalOrder.UNKNOWN
        }
      }

    assertEquals(
      PresentationOwnershipDecision(null, Sheet.C, listOf(Sheet.A, Sheet.B, Sheet.C)),
      ownership,
    )
  }
}
