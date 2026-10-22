@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)

package com.swmansion.reactnativebottomsheet.accessibility

import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.facebook.react.uimanager.common.UIManagerType
import com.facebook.react.uimanager.common.ViewUtil
import com.swmansion.reactnativebottomsheet.BottomSheetView
import com.swmansion.reactnativebottomsheet.presentation.PortalAccessibilityPolicy
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationCoordinator
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.WeakHashMap

/** One mutation ledger per window consumes the complete supplied presentation policy. */
@UiThread
internal class PortalAccessibilityIsolationCoordinator(
  private val mountAdapter: ReactNativeMountReconciliationAdapter =
    ReactNativeMountReconciliationAdapter.shared
) {
  internal interface Lease {
    fun release()
  }

  private inner class WindowState(windowRoot: View) {
    private val windowRoot = WeakReference(windowRoot)
    private val reconciler = PortalAccessibilityReconciler(windowRoot)
    private val mountCallbacks = WeakHashMap<ViewGroup, MountReconciliationCallback>()

    fun reconcile(
      policy: PortalAccessibilityPolicy,
      fromQueuedWork: Boolean,
      isCurrent: () -> Boolean,
    ) {
      if (!isCurrent()) return
      val activeRoots = policy.activePresentations.map { it.reactRoot }.toSet()
      mountCallbacks.keys
        .toList()
        .filter { it !in activeRoots }
        .forEach {
          mountCallbacks.remove(it)?.remove()
        }
      // Keep the previous accessible interface during motion. The host notifies
      // again on settle; queued mount work then reads current facts once.
      if (
        policy.activePresentations.any {
          (it.anchor as? BottomSheetView)?.isAccessibilityTransitionRunning == true
        }
      )
        return
      val windowRoot = windowRoot.get()
      activeRoots.forEach { root ->
        if (windowRoot == null) return@forEach
        if (mountCallbacks.containsKey(root)) return@forEach
        val callbacks = ReactNativeMountReconciliationAdapter.resolve(root) ?: return@forEach
        val callback = MountReconciliationCallback(windowRoot, root, callbacks.identity)
        callback.observation = mountAdapter.observe(callbacks, callback)
        mountCallbacks[root] = callback
        callback.observation?.requestReconciliation()
      }
      val fabric = policy.activePresentations.filter { usesFabric(it.anchor) }
      if (fabric.isNotEmpty() && !fromQueuedWork) {
        fabric.forEach { mountCallbacks[it.reactRoot]?.observation?.requestReconciliation() }
        return
      }
      val byManager =
        IdentityHashMap<
          Any,
          Pair<ReactNativeMountReconciliationAdapter.Observation, MutableList<BottomSheetView>>,
        >()
      for (active in fabric) {
        val owner = active.anchor as? BottomSheetView ?: return
        val callback = mountCallbacks[active.reactRoot] ?: return
        val observation = callback.observation ?: return
        byManager
          .getOrPut(callback.managerIdentity) { observation to mutableListOf() }
          .second
          .add(owner)
      }
      val cuts = mutableListOf<PortalAccessibilityCut>()
      for ((observation, owners) in byManager.values) {
        val currentWindow = windowRoot ?: return
        cuts.addAll((observation.readPortalIsolationCuts(currentWindow, owners) ?: return).values)
      }
      // Keep the existing non-Fabric compatibility path; failed Fabric reads never widen to it.
      val retainedByRoot = IdentityHashMap<ViewGroup, MutableList<View>>()
      policy.retainedPresentations
        .filterNot { usesFabric(it.anchor) }
        .forEach { retained ->
          retainedByRoot.getOrPut(retained.reactRoot) { mutableListOf() }.add(retained.anchor)
        }
      val scopes = retainedByRoot.map { (root, anchors) ->
        PortalAccessibilityScope(root, anchors)
      }
      if (reconciler.reconcile(scopes, cuts, isCurrent) && isCurrent()) {
        // Establish background masks before exposing a newly settled portal.
        policy.activePresentations.forEach {
          (it.anchor as? BottomSheetView)?.commitAccessibilityPresentation()
        }
      }
    }
  }

  private inner class MountReconciliationCallback(
    windowRoot: View,
    root: ViewGroup,
    val managerIdentity: Any,
  ) : () -> Unit {
    private val windowRoot = WeakReference(windowRoot)
    private val root = WeakReference(root)
    var observation: ReactNativeMountReconciliationAdapter.Observation? = null

    override fun invoke() {
      if (observation == null) return
      val windowRoot = windowRoot.get()
      if (windowRoot == null || root.get() == null) {
        remove()
        return
      }
      onPolicyChanged(
        windowRoot,
        PortalPresentationCoordinator.currentAccessibilityPolicy(windowRoot),
        fromQueuedWork = true,
      )
    }

    fun remove() {
      observation?.remove()
      observation = null
    }
  }

  private val windows = WeakHashMap<View, WindowState>()

  private data class PendingPolicy(
    val policy: PortalAccessibilityPolicy,
    val fromQueuedWork: Boolean,
  )

  // Only queued during synchronous reconciliation; no Active/order state is retained.
  private val pendingPolicies = IdentityHashMap<View, PendingPolicy>()
  private var reconciling = false
  private var leaseCount = 0
  private var policyObservation: PortalPresentationCoordinator.PolicyObservation? = null

  fun acquire(): Lease {
    leaseCount++
    if (leaseCount == 1) {
      policyObservation = PortalPresentationCoordinator.observeAccessibilityPolicy { root, policy ->
        onPolicyChanged(root, policy)
      }
    }
    return object : Lease {
      private var active = true

      override fun release() {
        if (!active) return
        active = false
        leaseCount--
        if (leaseCount == 0) {
          policyObservation?.remove()
          policyObservation = null
          for (root in windows.keys.toList()) {
            if (leaseCount != 0) break
            onPolicyChanged(root, PortalAccessibilityPolicy(emptyList(), emptyList()))
          }
        }
      }
    }
  }

  private fun onPolicyChanged(
    windowRoot: View,
    policy: PortalAccessibilityPolicy,
    fromQueuedWork: Boolean = false,
  ) {
    pendingPolicies[windowRoot] =
      PendingPolicy(
        policy.copy(
          retainedPresentations = policy.retainedPresentations.toList(),
          activePresentations = policy.activePresentations.toList(),
        ),
        fromQueuedWork,
      )
    if (reconciling) return
    reconciling = true
    try {
      while (pendingPolicies.isNotEmpty()) {
        val (root, pending) = pendingPolicies.entries.first().let { it.key to it.value }
        val currentPolicy = pending.policy
        pendingPolicies.remove(root)
        val state = windows.getOrPut(root) { WindowState(root) }
        state.reconcile(currentPolicy, pending.fromQueuedWork) {
          !pendingPolicies.containsKey(root)
        }
        if (currentPolicy.activePresentations.isEmpty() && !pendingPolicies.containsKey(root))
          windows.remove(root)
      }
    } finally {
      reconciling = false
      pendingPolicies.clear()
    }
  }

  private fun usesFabric(anchor: View): Boolean =
    ViewUtil.getUIManagerType(anchor) == UIManagerType.FABRIC

  companion object {
    private val shared = PortalAccessibilityIsolationCoordinator()

    fun acquire(): Lease = shared.acquire()
  }
}
