package com.swmansion.reactnativebottomsheet

import android.view.KeyEvent
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback

internal enum class RequestCloseInputAction {
  REQUEST_CLOSE,
  CONSUME,
  PASS_THROUGH,
}

internal data class RequestCloseInputState(
  val isAttached: Boolean,
  val isActive: Boolean,
  val isModal: Boolean,
  val hasHandler: Boolean,
  val isPresentationActive: Boolean,
  val isTargetOpen: Boolean,
)

internal fun resolveRequestCloseInputAction(
  state: RequestCloseInputState
): RequestCloseInputAction {
  if (
    !state.isAttached ||
      !state.isActive ||
      !state.isModal ||
      !state.hasHandler ||
      !state.isPresentationActive
  ) {
    return RequestCloseInputAction.PASS_THROUGH
  }
  return if (state.isTargetOpen) {
    RequestCloseInputAction.REQUEST_CLOSE
  } else {
    RequestCloseInputAction.CONSUME
  }
}

/** Pins one complete Back action at predictive-gesture start and never retargets its commit. */
internal class RequestCloseBackCallback(
  private val resolveAction: () -> RequestCloseInputAction,
  private val executeAction: (RequestCloseInputAction) -> Unit,
  private val onPredictiveBackStateChanged: () -> Unit = {},
) : OnBackPressedCallback(false) {
  private var canReceiveBack = false
  private var pinnedAction: RequestCloseInputAction? = null
  private var disposed = false

  var isPredictiveBackInProgress = false
    private set

  fun updateState(
    canReceiveBack: Boolean,
    currentAction: RequestCloseInputAction,
  ) {
    if (disposed) return
    this.canReceiveBack = canReceiveBack
    if (
      isPredictiveBackInProgress &&
        pinnedAction == RequestCloseInputAction.REQUEST_CLOSE &&
        currentAction != RequestCloseInputAction.REQUEST_CLOSE
    ) {
      pinnedAction = RequestCloseInputAction.CONSUME
    }
    isEnabled = canReceiveBack || isPredictiveBackInProgress
  }

  override fun handleOnBackStarted(backEvent: BackEventCompat) {
    if (disposed) return
    isPredictiveBackInProgress = true
    pinnedAction = resolveAction()
    isEnabled = canReceiveBack || isPredictiveBackInProgress
    onPredictiveBackStateChanged()
  }

  override fun handleOnBackCancelled() {
    if (disposed) return
    clearPinnedAction()
  }

  override fun handleOnBackPressed() {
    if (disposed) return
    val action = if (isPredictiveBackInProgress) pinnedAction else resolveAction()
    // Clear before executing. Pass-through can synchronously enter another dispatcher whose
    // callback changes props, presentation mode, attachment, or the lifetime of this view.
    clearPinnedAction()
    action?.let(executeAction)
  }

  private fun clearPinnedAction() {
    isPredictiveBackInProgress = false
    pinnedAction = null
    isEnabled = canReceiveBack
    onPredictiveBackStateChanged()
  }

  fun dispose() {
    disposed = true
    canReceiveBack = false
    isPredictiveBackInProgress = false
    pinnedAction = null
    isEnabled = false
    onPredictiveBackStateChanged()
  }
}

/**
 * Tracks the current Escape sequence from its initial down through its terminal up. Android may
 * start a new sequence without delivering the preceding up, so every initial down replaces the
 * unfinished sequence. Repeats preserve the initial action, and an up always finishes the current
 * sequence without relying on event identity or down time. A request-close action can only degrade
 * to consume, so eligibility returning during the same press never reactivates emission.
 */
internal class EscapeRequestCloseDispatcher {
  private var sequenceAction: RequestCloseInputAction? = null

  val hasCapturedPress: Boolean
    get() =
      sequenceAction == RequestCloseInputAction.REQUEST_CLOSE ||
        sequenceAction == RequestCloseInputAction.CONSUME

  fun dispatch(
    event: KeyEvent,
    resolveInitialAction: () -> RequestCloseInputAction,
    emitRequestCloseIfEligible: () -> Boolean,
  ): Boolean {
    if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) return false

    return when (event.action) {
      KeyEvent.ACTION_DOWN -> {
        if (event.repeatCount == 0) {
          sequenceAction =
            if (event.hasNoModifiers()) {
              resolveInitialAction()
            } else {
              RequestCloseInputAction.PASS_THROUGH
            }
        }
        hasCapturedPress
      }
      KeyEvent.ACTION_UP -> {
        val completedAction = sequenceAction
        sequenceAction = null
        if (
          completedAction == RequestCloseInputAction.REQUEST_CLOSE &&
            !event.isCanceled &&
            event.hasNoModifiers()
        ) {
          emitRequestCloseIfEligible()
        }
        completedAction == RequestCloseInputAction.REQUEST_CLOSE ||
          completedAction == RequestCloseInputAction.CONSUME
      }
      else -> false
    }
  }

  fun degradeCapturedRequestClose() {
    if (sequenceAction == RequestCloseInputAction.REQUEST_CLOSE) {
      sequenceAction = RequestCloseInputAction.CONSUME
    }
  }

  fun clear() {
    sequenceAction = null
  }
}
