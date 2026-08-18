package com.swmansion.reactnativebottomsheet

import android.view.KeyEvent
import android.view.View
import androidx.annotation.VisibleForTesting
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal data class PortalRequestCloseState(
  val isOwnerCandidate: Boolean,
  val canEmitIfOwner: Boolean,
) {
  init {
    require(!canEmitIfOwner || isOwnerCandidate)
  }
}

internal interface PortalRequestCloseTarget {
  fun onPortalRequestCloseHandlingChanged(enabled: Boolean)

  fun emitPortalRequestCloseIfEligible(): Boolean
}

internal enum class PortalEscapeDispatchResult {
  NO_OWNER,
  OWNER_UNHANDLED,
  HANDLED,
}

/**
 * Coordinates portal close-request ownership across every provider mounted in one Android root.
 *
 * Membership order is structural: the newest registered portal is highest. State updates never move
 * an entry. The highest current owner candidate blocks every lower portal, even when it has no
 * request-close handler and therefore cannot emit a request itself.
 */
internal object PortalRequestCloseCoordinator {
  internal interface Registration {
    fun update(state: PortalRequestCloseState)

    fun remove()
  }

  private class Entry(
    target: PortalRequestCloseTarget,
    initialState: PortalRequestCloseState,
  ) {
    val target = WeakReference(target)
    var state = initialState
    var isRegistered = true
    var handlingEnabled = false
  }

  private class RootState {
    val entries = mutableListOf<Entry>()
    val escapeDispatcher = EscapeRequestCloseDispatcher()
    var owner: Entry? = null
    var capturedEscapeOwner: WeakReference<Entry>? = null
  }

  private class RegistrationImpl(
    private val entry: Entry,
    root: View,
  ) : Registration {
    private val rootReference = WeakReference(root)

    override fun update(state: PortalRequestCloseState) {
      if (!entry.isRegistered || entry.state == state) return
      entry.state = state
      val currentRoot = rootReference.get() ?: return
      val currentRootState = statesByRoot[currentRoot] ?: return
      updateHandling(currentRoot, currentRootState)
    }

    override fun remove() {
      if (!entry.isRegistered) return
      entry.isRegistered = false

      val currentRoot = rootReference.get()
      val currentRootState = currentRoot?.let(statesByRoot::get)
      currentRootState?.let(::degradeCapturedEscapeIfNeeded)

      if (entry.handlingEnabled) {
        entry.handlingEnabled = false
        entry.target.get()?.onPortalRequestCloseHandlingChanged(false)
      }

      if (currentRoot == null || currentRootState == null) return
      currentRootState.entries.remove(entry)
      updateHandling(currentRoot, currentRootState)
    }

    fun clearTargetReference() {
      entry.target.clear()
    }
  }

  private val statesByRoot = WeakHashMap<View, RootState>()

  fun register(
    root: View,
    target: PortalRequestCloseTarget,
    initialState: PortalRequestCloseState,
  ): Registration {
    statesByRoot[root]?.let { updateHandling(root, it) }
    val rootState = statesByRoot.getOrPut(root, ::RootState)
    val entry = Entry(target, initialState)
    rootState.entries.add(entry)
    updateHandling(root, rootState)
    return RegistrationImpl(entry, root)
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal fun clearTargetReferenceForTest(registration: Registration) {
    require(registration is RegistrationImpl)
    registration.clearTargetReference()
  }

  fun dispatchEscape(root: View, event: KeyEvent): PortalEscapeDispatchResult {
    val rootState = statesByRoot[root] ?: return PortalEscapeDispatchResult.NO_OWNER
    updateHandling(root, rootState)
    if (statesByRoot[root] !== rootState) return PortalEscapeDispatchResult.NO_OWNER

    val ownerAtDispatch = rootState.owner
    val handled =
      rootState.escapeDispatcher.dispatch(
        event = event,
        resolveInitialAction = {
          rootState.capturedEscapeOwner = ownerAtDispatch?.let(::WeakReference)
          if (ownerAtDispatch?.isRegistered == true && ownerAtDispatch.state.canEmitIfOwner) {
            EscapeRequestCloseDispatcher.Action.REQUEST_CLOSE
          } else {
            EscapeRequestCloseDispatcher.Action.UNCLAIMED
          }
        },
        emitRequestCloseIfEligible = {
          val capturedOwner = rootState.capturedEscapeOwner?.get()
          if (
            capturedOwner != null &&
              capturedOwner.isRegistered &&
              rootState.owner === capturedOwner &&
              capturedOwner.state.canEmitIfOwner
          ) {
            capturedOwner.target.get()?.emitPortalRequestCloseIfEligible() == true
          } else {
            false
          }
        },
      )

    if (!rootState.escapeDispatcher.hasCapturedPress) {
      rootState.capturedEscapeOwner = null
    }

    return when {
      handled -> PortalEscapeDispatchResult.HANDLED
      ownerAtDispatch != null -> PortalEscapeDispatchResult.OWNER_UNHANDLED
      else -> PortalEscapeDispatchResult.NO_OWNER
    }
  }

  /** Applies every transition in one direction: snapshot -> owner -> enabled transports. */
  private fun updateHandling(root: View, rootState: RootState) {
    removeDeadEntries(rootState)
    if (rootState.entries.isEmpty()) {
      clearRootState(root, rootState)
      return
    }

    val nextOwner =
      rootState.entries.asReversed().firstOrNull { entry ->
        entry.isRegistered && entry.state.isOwnerCandidate
      }
    rootState.owner = nextOwner
    degradeCapturedEscapeIfNeeded(rootState)

    // Disable stale owners before enabling the new one so two portal Back callbacks are never
    // transiently active in the same root.
    rootState.entries.forEach { entry ->
      val nextHandlingEnabled = entry === nextOwner && entry.state.canEmitIfOwner
      if (entry.handlingEnabled && !nextHandlingEnabled) {
        entry.handlingEnabled = false
        entry.target.get()?.onPortalRequestCloseHandlingChanged(false)
      }
    }
    rootState.entries.forEach { entry ->
      val nextHandlingEnabled = entry === nextOwner && entry.state.canEmitIfOwner
      if (!entry.handlingEnabled && nextHandlingEnabled) {
        entry.handlingEnabled = true
        entry.target.get()?.onPortalRequestCloseHandlingChanged(true)
      }
    }
  }

  private fun degradeCapturedEscapeIfNeeded(rootState: RootState) {
    if (!rootState.escapeDispatcher.hasCapturedPress) return
    val capturedOwner = rootState.capturedEscapeOwner?.get()
    if (
      capturedOwner == null ||
        !capturedOwner.isRegistered ||
        rootState.owner !== capturedOwner ||
        !capturedOwner.state.canEmitIfOwner
    ) {
      rootState.escapeDispatcher.degradeCapturedRequestClose()
    }
  }

  private fun removeDeadEntries(rootState: RootState) {
    rootState.entries.removeAll { entry ->
      val isDead = !entry.isRegistered || entry.target.get() == null
      if (isDead) {
        entry.isRegistered = false
        entry.handlingEnabled = false
      }
      isDead
    }
  }

  private fun clearRootState(root: View, rootState: RootState) {
    rootState.escapeDispatcher.clear()
    rootState.capturedEscapeOwner = null
    rootState.owner = null
    if (statesByRoot[root] === rootState) {
      statesByRoot.remove(root)
    }
  }
}
