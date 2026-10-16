package com.swmansion.reactnativebottomsheet.closerequest

import android.view.KeyEvent
import android.view.View
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal interface PortalCloseRequestParticipant {
  fun onAssignedActionChanged(action: CloseRequestInputAction)

  fun emitCloseRequestIfEligible(): Boolean
}

/** Applies input policy to the shared presentation assignment; owns no membership or ordering. */
internal object PortalCloseRequestCoordinator {
  private class InputState {
    var owner: WeakReference<PortalCloseRequestParticipant>? = null
    var action = CloseRequestInputAction.PASS_THROUGH
    val escapeDispatcher = EscapeCloseRequestDispatcher()
    var capturedOwner: WeakReference<PortalCloseRequestParticipant>? = null
  }

  private val inputStatesByWindowRoot = WeakHashMap<View, InputState>()

  fun assign(
    windowRoot: View,
    owner: PortalCloseRequestParticipant,
    action: CloseRequestInputAction,
  ) {
    val state = inputStatesByWindowRoot.getOrPut(windowRoot, ::InputState)
    val previous = state.owner?.get()
    if (previous !== owner) {
      state.escapeDispatcher.degradeCapturedCloseRequest()
      previous?.onAssignedActionChanged(CloseRequestInputAction.PASS_THROUGH)
      state.owner = WeakReference(owner)
    }
    if (action != CloseRequestInputAction.EMIT_CLOSE_REQUEST) {
      state.escapeDispatcher.degradeCapturedCloseRequest()
    }
    if (previous !== owner || state.action != action) {
      state.action = action
      owner.onAssignedActionChanged(action)
    }
  }

  fun release(windowRoot: View, owner: PortalCloseRequestParticipant) {
    val state = inputStatesByWindowRoot[windowRoot] ?: return
    if (state.owner?.get() !== owner) return
    state.owner = null
    state.action = CloseRequestInputAction.PASS_THROUGH
    state.escapeDispatcher.degradeCapturedCloseRequest()
    owner.onAssignedActionChanged(CloseRequestInputAction.PASS_THROUGH)
    // Keep a captured Escape through transfers, so its terminal up cannot target a new owner.
    if (!state.escapeDispatcher.hasCapturedPress) inputStatesByWindowRoot.remove(windowRoot)
  }

  fun dispatchEscape(windowRoot: View, event: KeyEvent): Boolean {
    val state = inputStatesByWindowRoot[windowRoot] ?: return false
    val owner = state.owner?.get()
    if (owner == null) state.escapeDispatcher.degradeCapturedCloseRequest()
    val handled =
      state.escapeDispatcher.dispatch(
        event = event,
        resolveInitialAction = {
          state.capturedOwner = owner?.let(::WeakReference)
          if (owner != null) state.action else CloseRequestInputAction.PASS_THROUGH
        },
        emitCloseRequestIfEligible = {
          val captured = state.capturedOwner?.get()
          captured != null &&
            captured === state.owner?.get() &&
            state.action == CloseRequestInputAction.EMIT_CLOSE_REQUEST &&
            captured.emitCloseRequestIfEligible()
        },
      )
    if (!state.escapeDispatcher.hasCapturedPress) {
      state.capturedOwner = null
      if (state.owner?.get() == null) inputStatesByWindowRoot.remove(windowRoot)
    }
    return handled
  }
}
