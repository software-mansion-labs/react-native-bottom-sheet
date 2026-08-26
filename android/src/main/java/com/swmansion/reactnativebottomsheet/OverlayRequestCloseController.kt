package com.swmansion.reactnativebottomsheet

import android.content.DialogInterface
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.lifecycle.Lifecycle

/**
 * Owns request-close input routing for the overlay dialog and coordinates the dialog window input
 * state required for safe routing and pass-through.
 *
 * Touchability follows the sheet's interaction state. Focusability also depends on close-input
 * ownership and any Back or Escape sequence already in progress, while input-related alpha keeps
 * the window visible whenever the sheet is interactive or owns close input. Keeping these decisions
 * here lets the controller reconcile the window flags and alpha atomically. [BottomSheetView]
 * establishes a safe initial window state while configuring the dialog; this controller owns its
 * dynamic input state once the dialog is bound and shown.
 */
internal class OverlayRequestCloseController(private val emitRequestClose: () -> Boolean) {
  private var inputState = INACTIVE_INPUT_STATE
  private var isOverlayModeEnabled = false
  private var isSheetInteractive = false
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
          executeAction = ::executeBackAction,
          onPredictiveBackStateChanged = ::updateWindowInputFlags,
        )
      backCallback = callback
      dialog.onBackPressedDispatcher.addCallback(callback)
    }
    dialog.setOnKeyListener(DialogInterface.OnKeyListener { _, _, event -> dispatchEscape(event) })
    reconcileInputHandling()
  }

  fun unbind() {
    unbindInternal()
  }

  fun update(
    state: RequestCloseInputState,
    isOverlayModeEnabled: Boolean,
    isSheetInteractive: Boolean,
  ) {
    if (disposed) return
    inputState = state
    this.isOverlayModeEnabled = isOverlayModeEnabled
    this.isSheetInteractive = isSheetInteractive
    reconcileInputHandling()
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

  private fun reconcileInputHandling() {
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

  private fun effectiveInputState(): RequestCloseInputState {
    val currentDialog = dialog
    val isLifecycleActive =
      currentDialog?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    return inputState.copy(
      isAttached =
        isOverlayModeEnabled && inputState.isAttached && currentDialog?.isShowing == true,
      isLifecycleActive = inputState.isLifecycleActive && isLifecycleActive,
    )
  }

  private fun currentAction(): RequestCloseInputAction =
    resolveRequestCloseInputAction(effectiveInputState())

  /** Whether the dialog can be the platform recipient for a new Back sequence. */
  private fun canReceiveBack(): Boolean {
    val state = effectiveInputState()
    return state.isAttached && state.isLifecycleActive && state.isPresentationActive
  }

  private fun emitRequestCloseIfEligible(): Boolean {
    if (disposed || currentAction() != RequestCloseInputAction.REQUEST_CLOSE) return false
    return emitRequestClose()
  }

  private fun executeBackAction(action: RequestCloseInputAction) {
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
    val touchable = isSheetInteractive
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
        isLifecycleActive = false,
        isModal = false,
        hasRequestCloseHandler = false,
        isPresentationActive = false,
        isTargetResolvedAndOpen = false,
      )
  }
}
