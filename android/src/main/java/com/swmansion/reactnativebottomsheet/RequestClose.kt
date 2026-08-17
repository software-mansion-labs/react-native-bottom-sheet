package com.swmansion.reactnativebottomsheet

import android.view.KeyEvent

internal data class RequestCloseEligibility(
  val isAttached: Boolean,
  val isActive: Boolean,
  val isModal: Boolean,
  val isEnabled: Boolean,
  val isTargetOpen: Boolean,
)

internal fun isRequestCloseEligible(state: RequestCloseEligibility): Boolean =
  state.isAttached && state.isActive && state.isModal && state.isEnabled && state.isTargetOpen

/**
 * Tracks the current Escape sequence from its initial down through its terminal up. Android may
 * start a new sequence without delivering the preceding up, so every initial down replaces the
 * unfinished sequence. Repeats preserve the initial capture decision, and an up always finishes the
 * current sequence without relying on event identity or down time.
 */
internal class EscapeRequestCloseDispatcher {
  private enum class SequenceState {
    NONE,
    CAPTURED,
    UNCLAIMED,
  }

  private var sequenceState = SequenceState.NONE

  val hasCapturedPress: Boolean
    get() = sequenceState == SequenceState.CAPTURED

  fun dispatch(
    event: KeyEvent,
    shouldCapturePress: () -> Boolean,
    emitRequestCloseIfEligible: () -> Boolean,
  ): Boolean {
    if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) return false

    return when (event.action) {
      KeyEvent.ACTION_DOWN -> {
        if (event.repeatCount == 0) {
          sequenceState =
            if (event.hasNoModifiers() && shouldCapturePress()) {
              SequenceState.CAPTURED
            } else {
              SequenceState.UNCLAIMED
            }
        }
        sequenceState == SequenceState.CAPTURED
      }
      KeyEvent.ACTION_UP -> {
        val completedState = sequenceState
        sequenceState = SequenceState.NONE
        if (
          completedState == SequenceState.CAPTURED && !event.isCanceled && event.hasNoModifiers()
        ) {
          emitRequestCloseIfEligible()
        }
        completedState == SequenceState.CAPTURED
      }
      else -> false
    }
  }

  fun clear() {
    sequenceState = SequenceState.NONE
  }
}
