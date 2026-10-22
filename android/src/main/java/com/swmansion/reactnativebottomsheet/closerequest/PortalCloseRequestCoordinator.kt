package com.swmansion.reactnativebottomsheet.closerequest

import android.view.KeyEvent
import android.view.View
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal interface PortalCloseRequestParticipant {
  val inputAnchor: View

  fun currentInputAction(): CloseRequestInputAction

  fun onInputPolicyChanged()

  fun emitCloseRequestIfEligible(): Boolean
}

/** Applies input policy to the shared presentation assignment; owns no membership or ordering. */
internal object PortalCloseRequestCoordinator {
  private class InputState {
    var owner: WeakReference<PortalCloseRequestParticipant>? = null
    var action = CloseRequestInputAction.PASS_THROUGH
    val escapeDispatcher = EscapeCloseRequestDispatcher()
    var escapeTarget: CapturedTarget? = null
    val participants = WeakHashMap<View, WeakReference<PortalCloseRequestParticipant>>()
    val participantActions = WeakHashMap<PortalCloseRequestParticipant, CloseRequestInputAction>()
    val backTargets = WeakHashMap<PortalCloseRequestParticipant, CapturedTarget>()
    var captureValidationPosted = false
  }

  private class CapturedTarget(
    owner: PortalCloseRequestParticipant,
    context: PortalInputContextKey?,
  ) {
    val owner = WeakReference(owner)
    val contextRoot = context?.root?.let(::WeakReference)
    val contextTag = context?.tag
    var canEmit = true

    fun isCurrent(windowRoot: View, state: InputState): Boolean {
      val current = owner.get() ?: return false
      if (!canEmit || state.participants[current.inputAnchor]?.get() !== current) return false
      val tag = contextTag ?: return state.owner?.get() === current
      val root = contextRoot?.get() ?: return false
      return PortalInputContextResolver.ownerInContext(
        windowRoot,
        PortalInputContextKey(root, tag),
      ) === current.inputAnchor
    }
  }

  private val inputStatesByWindowRoot = WeakHashMap<View, InputState>()

  fun updateParticipant(windowRoot: View, participant: PortalCloseRequestParticipant) {
    val state = inputStatesByWindowRoot.getOrPut(windowRoot, ::InputState)
    state.participants[participant.inputAnchor] = WeakReference(participant)
    val action = participant.currentInputAction()
    val previousAction = state.participantActions.put(participant, action)
    if (action != CloseRequestInputAction.EMIT_CLOSE_REQUEST) {
      state.degradeOwner(participant)
    }
    if (previousAction != action) scheduleCaptureValidation(windowRoot, state)
    state.owner?.get()?.onInputPolicyChanged()
  }

  fun hasInputHandler(windowRoot: View): Boolean =
    inputStatesByWindowRoot[windowRoot]?.participants?.values?.any {
      it.get()?.currentInputAction()?.let { action ->
        action != CloseRequestInputAction.PASS_THROUGH
      } == true
    } == true

  fun removeParticipant(windowRoot: View, participant: PortalCloseRequestParticipant) {
    val state = inputStatesByWindowRoot[windowRoot] ?: return
    state.participants.remove(participant.inputAnchor)
    state.participantActions.remove(participant)
    state.degradeOwner(participant)
    release(windowRoot, participant)
    scheduleCaptureValidation(windowRoot, state)
    state.owner?.get()?.onInputPolicyChanged()
  }

  private fun InputState.degradeOwner(participant: PortalCloseRequestParticipant) {
    backTargets.values.filter { it.owner.get() === participant }.forEach { it.canEmit = false }
    escapeTarget
      ?.takeIf { it.owner.get() === participant }
      ?.let {
        it.canEmit = false
        escapeDispatcher.degradeCapturedCloseRequest()
      }
  }

  private fun scheduleCaptureValidation(windowRoot: View, state: InputState) {
    if (
      state.captureValidationPosted ||
        (state.backTargets.values.none { it.canEmit } && state.escapeTarget?.canEmit != true)
    )
      return
    // Membership changes can precede the authority update in the same synchronous UI batch.
    // Read the current context after that batch, only while an input sequence is captured.
    state.captureValidationPosted = windowRoot.post {
      state.captureValidationPosted = false
      if (inputStatesByWindowRoot[windowRoot] !== state) return@post
      state.backTargets.values.toList().forEach {
        if (it.canEmit && !it.isCurrent(windowRoot, state)) it.canEmit = false
      }
      state.escapeTarget?.let {
        if (it.canEmit && !it.isCurrent(windowRoot, state)) {
          it.canEmit = false
          state.escapeDispatcher.degradeCapturedCloseRequest()
        }
      }
    }
  }

  fun resolveBackAction(
    windowRoot: View,
    proxy: PortalCloseRequestParticipant,
  ): CloseRequestInputAction {
    val state = inputStatesByWindowRoot[windowRoot] ?: return CloseRequestInputAction.PASS_THROUGH
    state.backTargets.remove(proxy)
    val target =
      PortalInputContextResolver.select(windowRoot, state.owner?.get()?.inputAnchor)
        ?: return CloseRequestInputAction.PASS_THROUGH
    val owner =
      state.participants[target.anchor]?.get() ?: return CloseRequestInputAction.PASS_THROUGH
    state.backTargets[proxy] = CapturedTarget(owner, target.context)
    return owner.currentInputAction()
  }

  fun executeBackAction(
    windowRoot: View,
    proxy: PortalCloseRequestParticipant,
    action: CloseRequestInputAction,
  ) {
    val state = inputStatesByWindowRoot[windowRoot] ?: return
    val captured = state.backTargets.remove(proxy) ?: return
    if (
      action == CloseRequestInputAction.EMIT_CLOSE_REQUEST && captured.isCurrent(windowRoot, state)
    ) {
      captured.owner.get()?.emitCloseRequestIfEligible()
    }
  }

  fun capturedBackAction(
    windowRoot: View,
    proxy: PortalCloseRequestParticipant,
  ): CloseRequestInputAction? {
    val captured = inputStatesByWindowRoot[windowRoot]?.backTargets?.get(proxy) ?: return null
    return if (
      captured.canEmit &&
        captured.owner.get()?.currentInputAction() == CloseRequestInputAction.EMIT_CLOSE_REQUEST
    )
      CloseRequestInputAction.EMIT_CLOSE_REQUEST
    else CloseRequestInputAction.CONSUME
  }

  fun cancelBackCapture(windowRoot: View, proxy: PortalCloseRequestParticipant) {
    inputStatesByWindowRoot[windowRoot]?.backTargets?.remove(proxy)
  }

  private fun InputState.degradeWindowFallback() {
    if (escapeTarget?.contextTag == null) escapeDispatcher.degradeCapturedCloseRequest()
    backTargets.values.filter { it.contextTag == null }.forEach { it.canEmit = false }
  }

  fun assign(
    windowRoot: View,
    owner: PortalCloseRequestParticipant,
    action: CloseRequestInputAction,
  ) {
    val state = inputStatesByWindowRoot.getOrPut(windowRoot, ::InputState)
    val previous = state.owner?.get()
    if (previous !== owner) {
      state.degradeWindowFallback()
      previous?.onInputPolicyChanged()
      state.owner = WeakReference(owner)
    }
    if (action != CloseRequestInputAction.EMIT_CLOSE_REQUEST) {
      state.degradeWindowFallback()
    }
    if (previous !== owner || state.action != action) {
      state.action = action
      owner.onInputPolicyChanged()
    }
  }

  fun release(windowRoot: View, owner: PortalCloseRequestParticipant) {
    val state = inputStatesByWindowRoot[windowRoot] ?: return
    if (state.owner?.get() !== owner) return
    state.owner = null
    state.action = CloseRequestInputAction.PASS_THROUGH
    state.degradeWindowFallback()
    owner.onInputPolicyChanged()
    // Keep a captured Escape through transfers, so its terminal up cannot target a new owner.
    if (!state.escapeDispatcher.hasCapturedPress && state.participants.isEmpty())
      inputStatesByWindowRoot.remove(windowRoot)
  }

  fun dispatchEscape(windowRoot: View, event: KeyEvent): Boolean {
    val state = inputStatesByWindowRoot[windowRoot] ?: return false
    val owner = state.owner?.get()
    if (owner == null) state.degradeWindowFallback()
    val handled =
      state.escapeDispatcher.dispatch(
        event = event,
        resolveInitialAction = {
          val target = PortalInputContextResolver.select(windowRoot, owner?.inputAnchor)
          val selected = target?.let { state.participants[it.anchor]?.get() }
          state.escapeTarget =
            if (selected != null) CapturedTarget(selected, target.context) else null
          selected?.currentInputAction() ?: CloseRequestInputAction.PASS_THROUGH
        },
        emitCloseRequestIfEligible = {
          val captured = state.escapeTarget
          captured != null &&
            captured.isCurrent(windowRoot, state) &&
            captured.owner.get()?.emitCloseRequestIfEligible() == true
        },
      )
    if (!state.escapeDispatcher.hasCapturedPress) {
      state.escapeTarget = null
      if (state.owner?.get() == null && state.participants.isEmpty())
        inputStatesByWindowRoot.remove(windowRoot)
    }
    return handled
  }
}
