@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)
@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet.accessibility

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.facebook.react.ReactRootView
import com.facebook.react.bridge.UIManager
import com.facebook.react.bridge.UIManagerListener
import com.facebook.react.uimanager.ReactRoot
import com.facebook.react.uimanager.UIBlock
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.UIManagerModule
import com.facebook.react.uimanager.common.UIManagerType
import com.facebook.react.uimanager.common.ViewUtil
import com.swmansion.reactnativebottomsheet.BottomSheetView
import java.util.IdentityHashMap

/** Small compatibility boundary around React Native's unstable UIManager listener surface. */
internal interface ReactNativeMountCallbacks {
  val identity: Any
  val isFabric: Boolean

  fun addListener(listener: UIManagerListener)

  fun removeListener(listener: UIManagerListener)

  fun enqueueAfterPaperMount(block: () -> Unit)
}

internal class ReactNativeMountReconciliationAdapter(
  private val postToUiThread: (Runnable) -> Unit = {
    Handler(Looper.getMainLooper()).post(it)
    Unit
  },
  private val acquirePublicationSource: (Any) -> FabricPublicationLease? = {
    FabricPortalPublication.acquire(it)
  },
) {
  @UiThread
  internal interface Observation {
    /** Presentation lifecycle changes use the same coalesced queue as mount events. */
    fun requestReconciliation()

    /**
     * Source cuts for all supplied Active owners, retaining Top or all Active per local context.
     * Compose and consume in the same UI work; this does not discover Active membership.
     */
    fun readPortalIsolationCuts(
      windowRoot: View,
      presentations: Collection<BottomSheetView>,
    ): Map<Int, PortalAccessibilityCut>?

    fun remove()
  }

  private class ReconciliationCallback(val key: Any, val reconcile: () -> Unit) {
    var active = true
  }

  private inner class ManagerState(val boundary: ReactNativeMountCallbacks) {
    val callbacks = mutableListOf<ReconciliationCallback>()
    var reconciliationRequested = false
    var publicationSource: FabricPublicationLease? = null
    var active = true
    val listener =
      object : UIManagerListener {
        override fun willDispatchViewUpdates(uiManager: UIManager) {
          if (boundary.isFabric || !active) return
          boundary.enqueueAfterPaperMount {
            scheduleReconciliation()
          }
        }

        override fun willMountItems(uiManager: UIManager) = Unit

        override fun didMountItems(uiManager: UIManager) {
          if (boundary.isFabric) {
            // A mount event requests a current read; it does not certify successful execution.
            scheduleReconciliation()
          }
        }

        override fun didDispatchMountItems(uiManager: UIManager) = Unit

        override fun didScheduleMountItems(uiManager: UIManager) = Unit
      }

    fun scheduleReconciliation() {
      reconciliationRequested = true
      scheduleWork()
    }

    fun scheduleWork() {
      if (active) queueManager(this)
    }
  }

  private val managers = IdentityHashMap<Any, ManagerState>()
  private val pendingManagers = linkedSetOf<ManagerState>()
  private var workPosted = false

  private fun queueManager(state: ManagerState) {
    pendingManagers.add(state)
    if (workPosted) return
    workPosted = true
    postToUiThread(
      Runnable {
        workPosted = false
        val batch = pendingManagers.toList()
        pendingManagers.clear()
        // Install every pending source before any window attempts an all-manager read.
        batch.forEach {
          if (it.active && it.boundary.isFabric && it.publicationSource == null) {
            it.publicationSource = acquirePublicationSource(it.boundary.identity)
            it.publicationSource?.bootstrap()
          }
        }
        val callbacks = batch.flatMap {
          if (!it.active || !it.reconciliationRequested) emptyList()
          else {
            it.reconciliationRequested = false
            it.callbacks.toList()
          }
        }
        val reconciled = IdentityHashMap<Any, Boolean>()
        callbacks.forEach {
          if (it.active && !reconciled.containsKey(it.key)) {
            it.reconcile()
            // A callback that removed itself did not finish its shared lifetime's cleanup.
            if (it.active) reconciled[it.key] = true
          }
        }
      },
    )
  }

  /** Fresh UI input reuses the installed source; unavailable bootstrap is unresolved. */
  @UiThread
  fun readPortalInputContexts(
    boundary: ReactNativeMountCallbacks,
    surfaceId: Int,
    presentations: Collection<BottomSheetView>,
    windowRoot: View,
    origin: View?,
  ): FabricPortalInputContexts? {
    val state =
      managers[boundary.identity]?.takeIf { it.active && it.boundary.isFabric } ?: return null
    val manager = state.boundary.identity as? UIManager ?: return null
    val source = state.publicationSource ?: return null
    return source.attachedInputContextsFromRegistry(
      manager,
      surfaceId,
      presentations,
      windowRoot,
      origin,
    )
  }

  /** Reconciliation runs in queued UI work and must read current facts when invoked. */
  fun observe(
    boundary: ReactNativeMountCallbacks,
    reconciliationKey: Any? = null,
    reconcile: () -> Unit,
  ): Observation {
    val state =
      managers.getOrPut(boundary.identity) {
        ManagerState(boundary).also { boundary.addListener(it.listener) }
      }
    val callback = ReconciliationCallback(reconciliationKey ?: reconcile, reconcile)
    state.callbacks.add(callback)
    if (boundary.isFabric) state.scheduleWork()
    return object : Observation {
      private var active = true

      override fun requestReconciliation() {
        if (active) state.scheduleReconciliation()
      }

      override fun readPortalIsolationCuts(
        windowRoot: View,
        presentations: Collection<BottomSheetView>,
      ): Map<Int, PortalAccessibilityCut>? {
        if (!active || !state.active) return null
        if (presentations.isEmpty()) return emptyMap()
        val source = state.publicationSource ?: return null
        val manager = state.boundary.identity as? UIManager ?: return null
        return source.attachedIsolationCutsFromRegistry(
          manager,
          presentations,
          windowRoot,
        )
      }

      override fun remove() {
        if (!active) return
        active = false
        callback.active = false
        state.callbacks.remove(callback)
        if (state.callbacks.isEmpty()) {
          state.active = false
          managers.remove(state.boundary.identity)
          state.boundary.removeListener(state.listener)
          state.publicationSource?.let { source ->
            // Native bootstrap may call back into observation removal.
            // Reset the hybrid only after its native stack has unwound.
            postToUiThread(Runnable { source.release() })
          }
          state.publicationSource = null
        }
      }
    }
  }

  companion object {
    val shared = ReactNativeMountReconciliationAdapter()

    fun resolve(root: ViewGroup): ReactNativeMountCallbacks? {
      val reactContext =
        (root as? ReactRootView)?.currentReactContext
          ?: runCatching { UIManagerHelper.getReactContext(root) }.getOrNull()
          ?: return null
      val managerType = (root as? ReactRoot)?.getUIManagerType() ?: ViewUtil.getUIManagerType(root)
      val manager = UIManagerHelper.getUIManager(reactContext, managerType) ?: return null
      if (managerType != UIManagerType.FABRIC && manager !is UIManagerModule) return null
      return UIManagerMountCallbacks(manager, managerType == UIManagerType.FABRIC)
    }
  }
}

private class UIManagerMountCallbacks(
  private val manager: UIManager,
  override val isFabric: Boolean,
) : ReactNativeMountCallbacks {
  override val identity: Any = manager

  override fun addListener(listener: UIManagerListener) {
    manager.addUIManagerEventListener(listener)
  }

  override fun removeListener(listener: UIManagerListener) {
    manager.removeUIManagerEventListener(listener)
  }

  override fun enqueueAfterPaperMount(block: () -> Unit) {
    (manager as UIManagerModule).addUIBlock(UIBlock { block() })
  }
}
