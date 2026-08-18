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
 * unfinished sequence. Repeats preserve the initial action, and an up always finishes the current
 * sequence without relying on event identity or down time. A request-close action can only degrade
 * to consume, so eligibility returning during the same press never reactivates emission.
 */
internal class EscapeRequestCloseDispatcher {
  enum class Action {
    REQUEST_CLOSE,
    CONSUME,
    UNCLAIMED,
  }

  private var sequenceAction: Action? = null

  val hasCapturedPress: Boolean
    get() = sequenceAction == Action.REQUEST_CLOSE || sequenceAction == Action.CONSUME

  fun dispatch(
    event: KeyEvent,
    resolveInitialAction: () -> Action,
    emitRequestCloseIfEligible: () -> Boolean,
  ): Boolean {
    if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) return false

    return when (event.action) {
      KeyEvent.ACTION_DOWN -> {
        if (event.repeatCount == 0) {
          sequenceAction = if (event.hasNoModifiers()) resolveInitialAction() else Action.UNCLAIMED
        }
        hasCapturedPress
      }
      KeyEvent.ACTION_UP -> {
        val completedAction = sequenceAction
        sequenceAction = null
        if (
          completedAction == Action.REQUEST_CLOSE && !event.isCanceled && event.hasNoModifiers()
        ) {
          emitRequestCloseIfEligible()
        }
        completedAction == Action.REQUEST_CLOSE || completedAction == Action.CONSUME
      }
      else -> false
    }
  }

  fun degradeCapturedRequestClose() {
    if (sequenceAction == Action.REQUEST_CLOSE) {
      sequenceAction = Action.CONSUME
    }
  }

  fun clear() {
    sequenceAction = null
  }
}
