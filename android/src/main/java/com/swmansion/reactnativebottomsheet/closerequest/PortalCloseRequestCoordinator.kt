package com.swmansion.reactnativebottomsheet.closerequest

import android.view.KeyEvent
import android.view.View
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal data class PortalCloseRequestState(
  val isRoutingOwnerCandidate: Boolean,
  val actionIfRoutingOwner: CloseRequestInputAction,
) {
  init {
    require(isRoutingOwnerCandidate || actionIfRoutingOwner == CloseRequestInputAction.PASS_THROUGH)
  }
}

internal interface PortalCloseRequestParticipant {
  fun onAssignedActionChanged(action: CloseRequestInputAction)

  fun emitCloseRequestIfEligible(): Boolean
}

/**
 * Coordinates portal close request ownership across every provider mounted in one Android root.
 *
 * Membership order is structural: the newest registered portal is highest. State updates never move
 * an entry. The highest current routing owner candidate blocks every lower portal, even when it has
 * no `onCloseRequest` handler and therefore cannot emit a request itself.
 */
internal object PortalCloseRequestCoordinator {
  internal interface Registration {
    fun update(state: PortalCloseRequestState)

    fun remove()
  }

  private class Entry(
    participant: PortalCloseRequestParticipant,
    initialState: PortalCloseRequestState,
  ) {
    val participant = WeakReference(participant)
    var state = initialState
    var isRegistered = true
    var assignedAction = CloseRequestInputAction.PASS_THROUGH
  }

  private class RootState {
    val entries = mutableListOf<Entry>()
    val escapeDispatcher = EscapeCloseRequestDispatcher()
    var routingOwner: Entry? = null
    var capturedEscapeRoutingOwner: WeakReference<Entry>? = null
  }

  private class RegistrationImpl(
    private val entry: Entry,
    root: View,
  ) : Registration {
    private val rootReference = WeakReference(root)

    override fun update(state: PortalCloseRequestState) {
      if (!entry.isRegistered || entry.state == state) return
      entry.state = state
      val currentRoot = rootReference.get() ?: return
      val currentRootState = statesByRoot[currentRoot] ?: return
      reconcileRoutingOwnership(currentRoot, currentRootState)
    }

    override fun remove() {
      if (!entry.isRegistered) return
      entry.isRegistered = false

      val currentRoot = rootReference.get()
      val currentRootState = currentRoot?.let(statesByRoot::get)
      currentRootState?.let(::degradeCapturedEscapeIfNeeded)

      if (entry.assignedAction != CloseRequestInputAction.PASS_THROUGH) {
        entry.assignedAction = CloseRequestInputAction.PASS_THROUGH
        entry.participant.get()?.onAssignedActionChanged(CloseRequestInputAction.PASS_THROUGH)
      }

      if (currentRoot == null || currentRootState == null) return
      currentRootState.entries.remove(entry)
      reconcileRoutingOwnership(currentRoot, currentRootState)
    }
  }

  private val statesByRoot = WeakHashMap<View, RootState>()

  fun register(
    root: View,
    participant: PortalCloseRequestParticipant,
    initialState: PortalCloseRequestState,
  ): Registration {
    statesByRoot[root]?.let { reconcileRoutingOwnership(root, it) }
    val rootState = statesByRoot.getOrPut(root, ::RootState)
    val entry = Entry(participant, initialState)
    rootState.entries.add(entry)
    reconcileRoutingOwnership(root, rootState)
    return RegistrationImpl(entry, root)
  }

  fun dispatchEscape(root: View, event: KeyEvent): Boolean {
    val rootState = statesByRoot[root] ?: return false
    reconcileRoutingOwnership(root, rootState)
    if (statesByRoot[root] !== rootState) return false

    val routingOwnerAtDispatch = rootState.routingOwner
    val handled =
      rootState.escapeDispatcher.dispatch(
        event = event,
        resolveInitialAction = {
          rootState.capturedEscapeRoutingOwner = routingOwnerAtDispatch?.let(::WeakReference)
          routingOwnerAtDispatch?.takeIf { it.isRegistered }?.state?.actionIfRoutingOwner
            ?: CloseRequestInputAction.PASS_THROUGH
        },
        emitCloseRequestIfEligible = {
          val capturedEscapeRoutingOwner = rootState.capturedEscapeRoutingOwner?.get()
          if (
            capturedEscapeRoutingOwner != null &&
              capturedEscapeRoutingOwner.isRegistered &&
              rootState.routingOwner === capturedEscapeRoutingOwner &&
              capturedEscapeRoutingOwner.state.actionIfRoutingOwner ==
                CloseRequestInputAction.EMIT_CLOSE_REQUEST
          ) {
            capturedEscapeRoutingOwner.participant.get()?.emitCloseRequestIfEligible() == true
          } else {
            false
          }
        },
      )

    if (!rootState.escapeDispatcher.hasCapturedPress) {
      rootState.capturedEscapeRoutingOwner = null
    }

    return handled
  }

  private fun reconcileRoutingOwnership(root: View, rootState: RootState) {
    removeStaleEntries(rootState)
    if (rootState.entries.isEmpty()) {
      clearRootState(root, rootState)
      return
    }

    val nextRoutingOwner =
      rootState.entries.asReversed().firstOrNull { entry ->
        entry.isRegistered && entry.state.isRoutingOwnerCandidate
      }
    rootState.routingOwner = nextRoutingOwner
    degradeCapturedEscapeIfNeeded(rootState)

    // Put stale owners into pass-through before assigning the new routing owner action so two
    // portal OnBackPressedCallbacks are never transiently eligible in the same root.
    rootState.entries.forEach { entry ->
      if (
        entry !== nextRoutingOwner && entry.assignedAction != CloseRequestInputAction.PASS_THROUGH
      ) {
        entry.assignedAction = CloseRequestInputAction.PASS_THROUGH
        entry.participant.get()?.onAssignedActionChanged(CloseRequestInputAction.PASS_THROUGH)
      }
    }

    nextRoutingOwner?.let { entry ->
      val nextAction = entry.state.actionIfRoutingOwner
      if (entry.assignedAction != nextAction) {
        entry.assignedAction = nextAction
        entry.participant.get()?.onAssignedActionChanged(nextAction)
      }
    }
  }

  private fun degradeCapturedEscapeIfNeeded(rootState: RootState) {
    if (!rootState.escapeDispatcher.hasCapturedPress) return
    val capturedEscapeRoutingOwner = rootState.capturedEscapeRoutingOwner?.get()
    if (
      capturedEscapeRoutingOwner == null ||
        !capturedEscapeRoutingOwner.isRegistered ||
        rootState.routingOwner !== capturedEscapeRoutingOwner ||
        capturedEscapeRoutingOwner.state.actionIfRoutingOwner !=
          CloseRequestInputAction.EMIT_CLOSE_REQUEST
    ) {
      rootState.escapeDispatcher.degradeCapturedCloseRequest()
    }
  }

  private fun removeStaleEntries(rootState: RootState) {
    rootState.entries.removeAll { entry ->
      val isDead = !entry.isRegistered || entry.participant.get() == null
      if (isDead) {
        entry.isRegistered = false
        entry.assignedAction = CloseRequestInputAction.PASS_THROUGH
      }
      isDead
    }
  }

  private fun clearRootState(root: View, rootState: RootState) {
    rootState.escapeDispatcher.clear()
    rootState.capturedEscapeRoutingOwner = null
    rootState.routingOwner = null
    if (statesByRoot[root] === rootState) {
      statesByRoot.remove(root)
    }
  }
}
