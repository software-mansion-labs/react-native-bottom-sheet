package com.swmansion.reactnativebottomsheet.closerequest

import android.view.KeyEvent
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback

/**
 * Result of routing Back/Escape input:
 * - [EMIT_CLOSE_REQUEST] consumes the input and emits the close request callback.
 * - [CONSUME] maintains the modal boundary without emitting the callback.
 * - [PASS_THROUGH] passes handling beyond the sheet.
 */
internal enum class CloseRequestInputAction {
  EMIT_CLOSE_REQUEST,
  CONSUME,
  PASS_THROUGH,
}

/**
 * Snapshot used to decide how the sheet handles Back/Escape input.
 *
 * [isTargetResolvedAndOpen] determines whether a close request may be emitted. In contrast,
 * [isPresentationActive] also remains true while a visible sheet animates to a closed target, so
 * the modal boundary is maintained until settle.
 */
internal data class CloseRequestInputState(
  val isAttached: Boolean,
  val isLifecycleActive: Boolean,
  val isModal: Boolean,
  val hasCloseRequestHandler: Boolean,
  val isPresentationActive: Boolean,
  val isTargetResolvedAndOpen: Boolean,
) {
  companion object {
    val INACTIVE =
      CloseRequestInputState(
        isAttached = false,
        isLifecycleActive = false,
        isModal = false,
        hasCloseRequestHandler = false,
        isPresentationActive = false,
        isTargetResolvedAndOpen = false,
      )
  }
}

internal fun resolveCloseRequestInputAction(
  state: CloseRequestInputState,
): CloseRequestInputAction {
  if (
    !state.isAttached ||
      !state.isLifecycleActive ||
      !state.isModal ||
      !state.hasCloseRequestHandler ||
      !state.isPresentationActive
  ) {
    return CloseRequestInputAction.PASS_THROUGH
  }
  return if (state.isTargetResolvedAndOpen) {
    CloseRequestInputAction.EMIT_CLOSE_REQUEST
  } else {
    CloseRequestInputAction.CONSUME
  }
}

/** Pins one complete Back action at gesture/key start and never retargets its commit. */
internal class CloseRequestBackCallback(
  private val resolveAction: () -> CloseRequestInputAction,
  private val executeAction: (CloseRequestInputAction) -> Unit,
  private val onCaptureStateChanged: () -> Unit = {},
  private val onCaptureCancelled: () -> Unit = {},
) : OnBackPressedCallback(false) {
  private var canReceiveBack = false
  private var pinnedAction: CloseRequestInputAction? = null
  private var hasKeyPress = false
  private var disposed = false

  var isBackInProgress = false
    private set

  fun updateState(
    canReceiveBack: Boolean,
    currentAction: CloseRequestInputAction,
  ) {
    if (disposed) return
    this.canReceiveBack = canReceiveBack
    if (
      isBackInProgress &&
        pinnedAction == CloseRequestInputAction.EMIT_CLOSE_REQUEST &&
        currentAction != CloseRequestInputAction.EMIT_CLOSE_REQUEST
    ) {
      pinnedAction = CloseRequestInputAction.CONSUME
    }
    isEnabled = canReceiveBack || isBackInProgress
  }

  override fun handleOnBackStarted(backEvent: BackEventCompat) {
    if (disposed) return
    hasKeyPress = false
    isBackInProgress = true
    pinnedAction = resolveAction()
    isEnabled = canReceiveBack || isBackInProgress
    onCaptureStateChanged()
  }

  override fun handleOnBackCancelled() {
    if (disposed) return
    onCaptureCancelled()
    clearPinnedAction()
  }

  fun dispatchKey(event: KeyEvent): Boolean {
    if (disposed || event.keyCode != KeyEvent.KEYCODE_BACK) return false
    return when (event.action) {
      KeyEvent.ACTION_DOWN -> {
        if (event.repeatCount == 0) {
          if (isBackInProgress) handleOnBackCancelled()
          if (!isEnabled || !event.hasNoModifiers()) return false
          val action = resolveAction()
          if (action == CloseRequestInputAction.PASS_THROUGH) {
            onCaptureCancelled()
            return false
          }
          hasKeyPress = true
          isBackInProgress = true
          pinnedAction = action
          onCaptureStateChanged()
        }
        hasKeyPress
      }
      KeyEvent.ACTION_UP -> {
        if (!hasKeyPress) return false
        if (event.isCanceled || !event.hasNoModifiers()) handleOnBackCancelled()
        else handleOnBackPressed()
        true
      }
      else -> false
    }
  }

  override fun handleOnBackPressed() {
    if (disposed) return
    val action = if (isBackInProgress) pinnedAction else resolveAction()
    // Clear before executing. Pass-through can synchronously enter another dispatcher whose
    // callback changes props, presentation mode, attachment, or the lifetime of this view.
    clearPinnedAction(notify = false)
    try {
      action?.let(executeAction)
    } finally {
      // An ended proxy may dispose on notification; deliver its captured target before cleanup.
      if (!disposed) onCaptureStateChanged()
    }
  }

  private fun clearPinnedAction(notify: Boolean = true) {
    hasKeyPress = false
    isBackInProgress = false
    pinnedAction = null
    isEnabled = canReceiveBack
    if (notify) onCaptureStateChanged()
  }

  fun dispose() {
    onCaptureCancelled()
    disposed = true
    hasKeyPress = false
    canReceiveBack = false
    isBackInProgress = false
    pinnedAction = null
    isEnabled = false
    onCaptureStateChanged()
  }
}

/**
 * Tracks the current Escape sequence from its initial down through its terminal up. Android may
 * start a new sequence without delivering the preceding up, so every initial down replaces the
 * unfinished sequence. Repeats preserve the initial action, and an up always finishes the current
 * sequence without relying on event identity or down time. A close request action can only degrade
 * to consume, so eligibility returning during the same press never reactivates emission.
 */
internal class EscapeCloseRequestDispatcher {
  private var sequenceAction: CloseRequestInputAction? = null

  val hasCapturedPress: Boolean
    get() =
      sequenceAction == CloseRequestInputAction.EMIT_CLOSE_REQUEST ||
        sequenceAction == CloseRequestInputAction.CONSUME

  fun dispatch(
    event: KeyEvent,
    resolveInitialAction: () -> CloseRequestInputAction,
    emitCloseRequestIfEligible: () -> Boolean,
  ): Boolean {
    if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) return false

    return when (event.action) {
      KeyEvent.ACTION_DOWN -> {
        if (event.repeatCount == 0) {
          sequenceAction =
            if (event.hasNoModifiers()) {
              resolveInitialAction()
            } else {
              CloseRequestInputAction.PASS_THROUGH
            }
        }
        hasCapturedPress
      }
      KeyEvent.ACTION_UP -> {
        val completedAction = sequenceAction
        sequenceAction = null
        if (
          completedAction == CloseRequestInputAction.EMIT_CLOSE_REQUEST &&
            !event.isCanceled &&
            event.hasNoModifiers()
        ) {
          emitCloseRequestIfEligible()
        }
        completedAction == CloseRequestInputAction.EMIT_CLOSE_REQUEST ||
          completedAction == CloseRequestInputAction.CONSUME
      }
      else -> false
    }
  }

  fun degradeCapturedCloseRequest() {
    if (sequenceAction == CloseRequestInputAction.EMIT_CLOSE_REQUEST) {
      sequenceAction = CloseRequestInputAction.CONSUME
    }
  }

  fun clear() {
    sequenceAction = null
  }
}
