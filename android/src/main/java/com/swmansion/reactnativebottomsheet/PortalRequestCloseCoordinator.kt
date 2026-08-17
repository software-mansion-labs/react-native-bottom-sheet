package com.swmansion.reactnativebottomsheet

import android.view.KeyEvent
import android.view.View
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal interface PortalRequestCloseTarget {
  /** Whether this structurally attached portal currently targets a resolved positive detent. */
  val isPortalRequestCloseTargetOpen: Boolean

  /** Whether the current owner can emit a request at this instant. */
  val isPortalRequestCloseEligible: Boolean

  fun onPortalRequestCloseOwnershipChanged(isOwner: Boolean)

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
 * Membership order is structural: the newest attached portal is highest, and target, handler, and
 * lifecycle updates never move it. Ownership skips unresolved and zero-height targets, but an open
 * owner without an active handler still blocks every lower portal from handling the same request.
 */
internal object PortalRequestCloseCoordinator {
  internal interface Registration {
    fun targetChanged()

    fun eligibilityChanged()

    fun remove()
  }

  private class Entry(target: PortalRequestCloseTarget) {
    val target = WeakReference(target)
    var isActive = true
    var ownershipKnown = false
    var isOwner = false
  }

  private class RootState {
    val entries = mutableListOf<Entry>()
    val escapeDispatcher = EscapeRequestCloseDispatcher()
    var owner: Entry? = null
    var capturedEscapeOwner: WeakReference<Entry>? = null
    var capturedEscapeRemainsEligible = false
  }

  private val statesByRoot = WeakHashMap<View, RootState>()

  fun register(root: View, target: PortalRequestCloseTarget): Registration {
    statesByRoot[root]?.let { removeInactiveEntries(root, it) }
    val state = statesByRoot.getOrPut(root, ::RootState)
    val entry = Entry(target)
    state.entries.add(entry)
    updateOwnership(root, state)
    val rootReference = WeakReference(root)

    return object : Registration {
      override fun targetChanged() {
        if (!entry.isActive) return
        val currentRoot = rootReference.get() ?: return
        val currentState = statesByRoot[currentRoot] ?: return
        updateOwnership(currentRoot, currentState)
      }

      override fun eligibilityChanged() {
        if (!entry.isActive) return
        val currentRoot = rootReference.get() ?: return
        val currentState = statesByRoot[currentRoot] ?: return
        invalidateCapturedEscapeIfNeeded(currentState)
      }

      override fun remove() {
        if (!entry.isActive) return
        entry.isActive = false
        if (entry.isOwner) {
          entry.isOwner = false
          entry.target.get()?.onPortalRequestCloseOwnershipChanged(false)
        }
        val currentRoot = rootReference.get() ?: return
        val currentState = statesByRoot[currentRoot] ?: return
        updateOwnership(currentRoot, currentState)
      }
    }
  }

  fun dispatchEscape(root: View, event: KeyEvent): PortalEscapeDispatchResult {
    val state = statesByRoot[root] ?: return PortalEscapeDispatchResult.NO_OWNER
    updateOwnership(root, state)
    val ownerAtDispatch = state.owner
    val handled =
      state.escapeDispatcher.dispatch(
        event = event,
        shouldCapturePress = {
          state.capturedEscapeOwner = ownerAtDispatch?.let(::WeakReference)
          state.capturedEscapeRemainsEligible =
            ownerAtDispatch?.target?.get()?.isPortalRequestCloseEligible == true
          state.capturedEscapeRemainsEligible
        },
        emitRequestCloseIfEligible = {
          val capturedOwner = state.capturedEscapeOwner?.get()
          if (
            state.capturedEscapeRemainsEligible &&
              capturedOwner != null &&
              capturedOwner.isActive &&
              state.owner === capturedOwner
          ) {
            capturedOwner.target.get()?.emitPortalRequestCloseIfEligible() == true
          } else {
            false
          }
        },
      )

    if (!state.escapeDispatcher.hasCapturedPress) {
      state.capturedEscapeOwner = null
      state.capturedEscapeRemainsEligible = false
    }

    return when {
      handled -> PortalEscapeDispatchResult.HANDLED
      ownerAtDispatch != null -> PortalEscapeDispatchResult.OWNER_UNHANDLED
      else -> PortalEscapeDispatchResult.NO_OWNER
    }
  }

  private fun updateOwnership(root: View, state: RootState) {
    removeInactiveEntries(root, state)
    if (statesByRoot[root] !== state) return

    val nextOwner =
      state.entries.asReversed().firstOrNull { entry ->
        entry.isActive && entry.target.get()?.isPortalRequestCloseTargetOpen == true
      }
    state.owner = nextOwner

    val snapshot = state.entries.toList()
    snapshot.forEach { entry ->
      val target = entry.target.get() ?: return@forEach
      val ownsRequestClose = entry === nextOwner
      if (!entry.ownershipKnown || entry.isOwner != ownsRequestClose) {
        entry.ownershipKnown = true
        entry.isOwner = ownsRequestClose
        target.onPortalRequestCloseOwnershipChanged(ownsRequestClose)
      }
    }
    invalidateCapturedEscapeIfNeeded(state)
  }

  private fun invalidateCapturedEscapeIfNeeded(state: RootState) {
    if (!state.escapeDispatcher.hasCapturedPress || !state.capturedEscapeRemainsEligible) return
    val capturedOwner = state.capturedEscapeOwner?.get()
    if (
      capturedOwner == null ||
        !capturedOwner.isActive ||
        state.owner !== capturedOwner ||
        capturedOwner.target.get()?.isPortalRequestCloseEligible != true
    ) {
      state.capturedEscapeRemainsEligible = false
    }
  }

  private fun removeInactiveEntries(root: View, state: RootState) {
    state.entries.removeAll { !it.isActive || it.target.get() == null }
    if (state.entries.isEmpty()) {
      state.escapeDispatcher.clear()
      state.capturedEscapeOwner = null
      state.capturedEscapeRemainsEligible = false
      state.owner = null
      statesByRoot.remove(root)
    }
  }
}
