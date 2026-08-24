package com.swmansion.reactnativebottomsheet

import android.content.DialogInterface
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.lifecycle.Lifecycle

/** Owns the dialog transport for Back and Escape close requests. */
internal class OverlayRequestCloseController(private val emitRequestClose: () -> Boolean) {
  private var inputState = INACTIVE_INPUT_STATE
  private var enabled = false
  private var interactive = false
  private var dialog: ComponentDialog? = null
  private var disposed = false
  private val escapeDispatcher = EscapeRequestCloseDispatcher()

  private var backCallback: RequestCloseBackCallback? = null

  fun bind(dialog: ComponentDialog) {
    if (disposed) return
    if (this.dialog !== dialog) {
      unbindInternal()
      this.dialog = dialog
    }
    if (backCallback == null) {
      val callback =
        RequestCloseBackCallback(
          resolveAction = ::currentAction,
          executeAction = ::executeAction,
          onPredictiveBackStateChanged = ::updateWindowInputFlags,
        )
      backCallback = callback
      dialog.onBackPressedDispatcher.addCallback(callback)
    }
    dialog.setOnKeyListener(DialogInterface.OnKeyListener { _, _, event -> dispatchEscape(event) })
    refreshHandling()
  }

  fun unbind() {
    unbindInternal()
  }

  fun update(
    state: RequestCloseInputState,
    enabled: Boolean,
    interactive: Boolean,
  ) {
    if (disposed) return
    inputState = state
    this.enabled = enabled
    this.interactive = interactive
    refreshHandling()
  }

  private fun dispatchEscape(event: KeyEvent): Boolean {
    if (disposed || dialog == null) return false
    val hadCapturedPress = escapeDispatcher.hasCapturedPress
    val handled =
      escapeDispatcher.dispatch(
        event = event,
        resolveInitialAction = ::currentAction,
        emitRequestCloseIfEligible = ::emitRequestCloseIfEligible,
      )

    if (hadCapturedPress && !escapeDispatcher.hasCapturedPress) {
      updateWindowInputFlags()
    }
    return handled
  }

  fun dispose() {
    if (disposed) return
    disposed = true
    unbindInternal()
  }

  private fun refreshHandling() {
    if (disposed) return
    val action = currentAction()
    if (action != RequestCloseInputAction.REQUEST_CLOSE) {
      escapeDispatcher.degradeCapturedRequestClose()
    }
    backCallback?.updateState(
      canReceiveBack = canReceiveBack(),
      currentAction = action,
    )
    updateWindowInputFlags()
  }

  private fun transportState(): RequestCloseInputState {
    val currentDialog = dialog
    val lifecycleActive =
      currentDialog?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    return inputState.copy(
      isAttached = enabled && inputState.isAttached && currentDialog?.isShowing == true,
      isActive = inputState.isActive && lifecycleActive,
    )
  }

  private fun currentAction(): RequestCloseInputAction =
    resolveRequestCloseInputAction(transportState())

  /** Whether the dialog can be the platform recipient for a new Back sequence. */
  private fun canReceiveBack(): Boolean {
    val state = transportState()
    return state.isAttached && state.isActive && state.isPresentationActive
  }

  private fun emitRequestCloseIfEligible(): Boolean {
    if (disposed || currentAction() != RequestCloseInputAction.REQUEST_CLOSE) return false
    return emitRequestClose()
  }

  private fun executeAction(action: RequestCloseInputAction) {
    when (action) {
      RequestCloseInputAction.REQUEST_CLOSE -> emitRequestCloseIfEligible()
      RequestCloseInputAction.CONSUME -> Unit
      RequestCloseInputAction.PASS_THROUGH ->
        (dialog?.context?.findActivity() as? ComponentActivity)
          ?.onBackPressedDispatcher
          ?.onBackPressed()
    }
  }

  /**
   * Touchability follows the sheet interaction state. Focusability additionally follows close input
   * ownership and any input sequence already in progress.
   */
  private fun updateWindowInputFlags() {
    val window = dialog?.window ?: return
    val touchable = interactive
    val ownsCloseInput = currentAction() != RequestCloseInputAction.PASS_THROUGH
    val focusable =
      touchable ||
        ownsCloseInput ||
        escapeDispatcher.hasCapturedPress ||
        backCallback?.isPredictiveBackInProgress == true

    if (touchable) {
      window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    } else {
      window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    val isCurrentlyFocusable =
      window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
    if (focusable != isCurrentlyFocusable) {
      if (focusable) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
      } else {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
      }
    }

    window.attributes =
      window.attributes.apply { alpha = if (touchable || ownsCloseInput) 1f else 0f }
  }

  private fun unbindInternal() {
    val boundDialog = dialog
    dialog = null
    boundDialog?.setOnKeyListener(null)
    val callback = backCallback
    backCallback = null
    callback?.dispose()
    callback?.remove()
    escapeDispatcher.clear()
  }

  private companion object {
    val INACTIVE_INPUT_STATE =
      RequestCloseInputState(
        isAttached = false,
        isActive = false,
        isModal = false,
        hasHandler = false,
        isPresentationActive = false,
        isTargetOpen = false,
      )
  }
}
