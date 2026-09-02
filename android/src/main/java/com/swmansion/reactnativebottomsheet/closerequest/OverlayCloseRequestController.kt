package com.swmansion.reactnativebottomsheet.closerequest

import android.content.DialogInterface
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.lifecycle.Lifecycle

/**
 * Atomically computes overlay-dialog Back/Escape routing, touchability, focusability, and alpha
 * from the same state snapshot.
 */
internal class OverlayCloseRequestController(private val emitCloseRequest: () -> Boolean) {
  private var inputState = CloseRequestInputState.INACTIVE
  private var usesOverlayDialog = false
  private var isSheetInteractive = false
  private var dialog: ComponentDialog? = null
  private var disposed = false
  private val escapeDispatcher = EscapeCloseRequestDispatcher()

  private var backCallback: CloseRequestBackCallback? = null

  fun bind(dialog: ComponentDialog) {
    if (disposed) return
    if (this.dialog !== dialog) {
      unbindInternal()
      this.dialog = dialog
    }
    if (backCallback == null) {
      val callback =
        CloseRequestBackCallback(
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
    state: CloseRequestInputState,
    usesOverlayDialog: Boolean,
    isSheetInteractive: Boolean,
  ) {
    if (disposed) return
    inputState = state
    this.usesOverlayDialog = usesOverlayDialog
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
        emitCloseRequestIfEligible = ::emitCloseRequestIfEligible,
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
    if (action != CloseRequestInputAction.EMIT_CLOSE_REQUEST) {
      escapeDispatcher.degradeCapturedCloseRequest()
    }
    backCallback?.updateState(
      canReceiveBack = canReceiveBack(),
      currentAction = action,
    )
    updateWindowInputFlags()
  }

  private fun effectiveInputState(): CloseRequestInputState {
    val currentDialog = dialog
    val isLifecycleActive =
      currentDialog?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    return inputState.copy(
      isAttached = usesOverlayDialog && inputState.isAttached && currentDialog?.isShowing == true,
      isLifecycleActive = inputState.isLifecycleActive && isLifecycleActive,
    )
  }

  private fun currentAction(): CloseRequestInputAction =
    resolveCloseRequestInputAction(effectiveInputState())

  private fun canReceiveBack(): Boolean {
    val state = effectiveInputState()
    return state.isAttached && state.isLifecycleActive && state.isPresentationActive
  }

  private fun emitCloseRequestIfEligible(): Boolean {
    if (disposed || currentAction() != CloseRequestInputAction.EMIT_CLOSE_REQUEST) return false
    return emitCloseRequest()
  }

  private fun executeBackAction(action: CloseRequestInputAction) {
    when (action) {
      CloseRequestInputAction.EMIT_CLOSE_REQUEST -> emitCloseRequestIfEligible()
      CloseRequestInputAction.CONSUME -> Unit
      CloseRequestInputAction.PASS_THROUGH ->
        (dialog?.context?.findActivity() as? ComponentActivity)
          ?.onBackPressedDispatcher
          ?.onBackPressed()
    }
  }

  private fun updateWindowInputFlags() {
    val window = dialog?.window ?: return
    val touchable = isSheetInteractive
    val ownsCloseInput = currentAction() != CloseRequestInputAction.PASS_THROUGH
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
}
