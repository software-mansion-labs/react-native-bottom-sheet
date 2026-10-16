package com.swmansion.reactnativebottomsheet.accessibility

import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.swmansion.reactnativebottomsheet.presentation.PortalAccessibilityPolicy
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationCoordinator
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.WeakHashMap

/** One mutation ledger per window consumes the complete supplied presentation policy. */
@UiThread
internal object PortalAccessibilityIsolationCoordinator {
  internal interface Lease {
    fun release()
  }

  private class WindowState(windowRoot: View) {
    private val windowRoot = WeakReference(windowRoot)
    private val reconciler = PortalAccessibilityReconciler(windowRoot)
    private val mountCallbacks = WeakHashMap<ViewGroup, MountReconciliationCallback>()

    fun reconcile(
      policy: PortalAccessibilityPolicy,
      isCurrent: () -> Boolean,
    ) {
      if (!isCurrent()) return
      val retainedByRoot = IdentityHashMap<ViewGroup, MutableList<View>>()
      policy.retainedPresentations.forEach { retained ->
        retainedByRoot.getOrPut(retained.reactRoot) { mutableListOf() }.add(retained.anchor)
      }
      // The authority still supplies legacy RN-root scopes until Android P1 is
      // resolved. This consumer accepts boundaries without inferring their size.
      val scopes = retainedByRoot.map { (root, anchors) -> PortalAccessibilityScope(root, anchors) }
      val activeRoots = policy.activePresentations.map { it.reactRoot }.toSet()
      mountCallbacks.keys
        .toList()
        .filter { it !in activeRoots }
        .forEach {
          mountCallbacks.remove(it)?.remove()
        }
      val windowRoot = windowRoot.get()
      activeRoots.forEach { root ->
        if (windowRoot == null) return@forEach
        if (mountCallbacks.containsKey(root)) return@forEach
        val callbacks = ReactNativeMountReconciliationAdapter.resolve(root) ?: return@forEach
        val callback = MountReconciliationCallback(windowRoot, root)
        callback.observation =
          ReactNativeMountReconciliationAdapter.shared.observe(callbacks, callback)
        mountCallbacks[root] = callback
        callback.observation?.requestReconciliation()
      }
      reconciler.reconcile(scopes, isCurrent)
    }
  }

  private class MountReconciliationCallback(
    windowRoot: View,
    root: ViewGroup,
  ) : () -> Unit {
    private val windowRoot = WeakReference(windowRoot)
    private val root = WeakReference(root)
    var observation: ReactNativeMountReconciliationAdapter.Observation? = null

    override fun invoke() {
      val windowRoot = windowRoot.get()
      if (windowRoot == null || root.get() == null) {
        remove()
        return
      }
      PortalPresentationCoordinator.reconcile(
        windowRoot,
        forceAccessibilityPolicyReconciliation = true,
      )
    }

    fun remove() {
      observation?.remove()
      observation = null
    }
  }

  private val windows = WeakHashMap<View, WindowState>()
  // Only queued during synchronous reconciliation; no Active/order state is retained.
  private val pendingPolicies = IdentityHashMap<View, PortalAccessibilityPolicy>()
  private var reconciling = false
  private var leaseCount = 0
  private var policyObservation: PortalPresentationCoordinator.PolicyObservation? = null

  fun acquire(): Lease {
    leaseCount++
    if (leaseCount == 1) {
      policyObservation =
        PortalPresentationCoordinator.observeAccessibilityPolicy(::onPolicyChanged)
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
  ) {
    pendingPolicies[windowRoot] =
      policy.copy(
        retainedPresentations = policy.retainedPresentations.toList(),
        activePresentations = policy.activePresentations.toList(),
      )
    if (reconciling) return
    reconciling = true
    try {
      while (pendingPolicies.isNotEmpty()) {
        val (root, currentPolicy) = pendingPolicies.entries.first().let { it.key to it.value }
        pendingPolicies.remove(root)
        val state = windows.getOrPut(root) { WindowState(root) }
        state.reconcile(currentPolicy) { !pendingPolicies.containsKey(root) }
        if (currentPolicy.activePresentations.isEmpty() && !pendingPolicies.containsKey(root))
          windows.remove(root)
      }
    } finally {
      reconciling = false
      pendingPolicies.clear()
    }
  }
}
