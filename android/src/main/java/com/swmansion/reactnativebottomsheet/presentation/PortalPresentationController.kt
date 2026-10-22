package com.swmansion.reactnativebottomsheet.presentation

import android.view.View

/** Owns one portal registration independently of its input and accessibility policies. */
internal class PortalPresentationController(
  private val view: View,
  private val onAssignmentChanged:
    (PortalPresentationContext?, PortalPresentationAssignment) -> Unit,
) {
  private var isPortal = false
  private var isActive = false
  private var disposed = false
  private var context: PortalPresentationContext? = null
  private var registration: PortalPresentationCoordinator.Registration? = null
  private var assignment = PortalPresentationAssignment.NONE
  private val observer: (PortalPresentationAssignment) -> Unit = {
    assignment = it
    onAssignmentChanged(context, it)
  }
  private val syncHierarchyRunnable = Runnable { syncHierarchy() }

  fun update(isPortal: Boolean, isActive: Boolean) {
    if (disposed) return
    this.isPortal = isPortal
    this.isActive = isActive
    syncHierarchy()
  }

  fun syncHierarchy() {
    if (disposed) return
    val resolved = view.takeIf { isPortal }?.resolvePortalPresentationContext()
    if (
      resolved == null ||
        resolved.reactRoot !== context?.reactRoot ||
        resolved.windowRoot !== context?.windowRoot ||
        resolved.windowToken !== context?.windowToken
    ) {
      removeRegistration()
    }
    context = resolved
    if (resolved != null) {
      if (registration?.update(isActive) != true) {
        registration = PortalPresentationCoordinator.register(view, isActive, observer)
      }
    }
    onAssignmentChanged(context, assignment)
  }

  fun scheduleHierarchySync() {
    if (disposed) return
    view.removeCallbacks(syncHierarchyRunnable)
    view.post(syncHierarchyRunnable)
  }

  fun clear() {
    isPortal = false
    view.removeCallbacks(syncHierarchyRunnable)
    removeRegistration()
  }

  fun dispose() {
    if (disposed) return
    clear()
    disposed = true
  }

  private fun removeRegistration() {
    val previous = registration
    registration = null
    previous?.remove()
    context = null
    assignment = PortalPresentationAssignment.NONE
    onAssignmentChanged(null, assignment)
  }
}
