package com.swmansion.reactnativebottomsheet.accessibility

import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationCoordinator
import com.swmansion.reactnativebottomsheet.presentation.PortalRetainedPresentation
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

/** Owns root-scoped masking while one or more BottomSheetViews can publish portal policy. */
@UiThread
internal object PortalAccessibilityIsolationCoordinator {
  internal interface Lease {
    fun release()
  }

  private class RootState(
    windowRoot: View,
    root: ViewGroup,
  ) {
    private val windowRoot = WeakReference(windowRoot)
    val reconciler = PortalAccessibilityReconciler(root)
    private val root = WeakReference(root)
    private var mountCallback: MountReconciliationCallback? = null

    fun belongsTo(windowRoot: View): Boolean = this.windowRoot.get() === windowRoot

    fun observeMounts() {
      if (mountCallback != null) return
      val root = root.get() ?: return
      val windowRoot = windowRoot.get() ?: return
      val callbacks = ReactNativeMountReconciliationAdapter.resolve(root) ?: return
      val callback = MountReconciliationCallback(windowRoot, root)
      callback.observation =
        ReactNativeMountReconciliationAdapter.shared.observe(callbacks, callback)
      mountCallback = callback
    }

    fun release() {
      mountCallback?.remove()
      mountCallback = null
      reconciler.reconcile(emptyList())
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

  private val roots = WeakHashMap<ViewGroup, RootState>()
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
          roots.values.toList().forEach(RootState::release)
          roots.clear()
        }
      }
    }
  }

  private fun onPolicyChanged(
    windowRoot: View,
    retainedPresentations: List<PortalRetainedPresentation>,
  ) {
    val retainedByRoot = IdentityHashMap<ViewGroup, MutableList<View>>()
    retainedPresentations.forEach { retained ->
      retainedByRoot.getOrPut(retained.reactRoot) { mutableListOf() }.add(retained.anchor)
    }
    val desiredRoots = identitySet<ViewGroup>()
    retainedByRoot.forEach { (root, anchors) ->
      val state = roots.getOrPut(root) { RootState(windowRoot, root) }
      if (state.reconciler.reconcile(anchors)) {
        state.observeMounts()
        desiredRoots.add(root)
      } else {
        state.release()
        roots.remove(root)
      }
    }
    roots.entries
      .filter { (root, state) -> state.belongsTo(windowRoot) && root !in desiredRoots }
      .map { it.key }
      .forEach { root ->
        roots.remove(root)?.release()
      }
  }

  private fun <T> identitySet(): MutableSet<T> =
    Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
}
