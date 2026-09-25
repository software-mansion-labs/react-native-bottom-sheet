@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)
@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet.accessibility

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.facebook.react.bridge.UIManager
import com.facebook.react.bridge.UIManagerListener
import com.facebook.react.uimanager.ReactRoot
import com.facebook.react.uimanager.UIBlock
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.UIManagerModule
import com.facebook.react.uimanager.common.UIManagerType
import com.facebook.react.uimanager.common.ViewUtil
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
  }
) {
  internal interface Observation {
    fun remove()
  }

  private inner class ManagerState(val boundary: ReactNativeMountCallbacks) {
    val callbacks = mutableListOf<() -> Unit>()
    var reconciliationPosted = false
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
          if (boundary.isFabric) scheduleReconciliation()
        }

        override fun didDispatchMountItems(uiManager: UIManager) = Unit

        override fun didScheduleMountItems(uiManager: UIManager) = Unit
      }

    fun scheduleReconciliation() {
      if (!active || reconciliationPosted) return
      reconciliationPosted = true
      postToUiThread(
        Runnable {
          reconciliationPosted = false
          if (active) callbacks.toList().forEach { it() }
        }
      )
    }
  }

  private val managers = IdentityHashMap<Any, ManagerState>()

  fun observe(
    boundary: ReactNativeMountCallbacks,
    reconcile: () -> Unit,
  ): Observation {
    val state =
      managers.getOrPut(boundary.identity) {
        ManagerState(boundary).also { boundary.addListener(it.listener) }
      }
    state.callbacks.add(reconcile)
    return object : Observation {
      private var active = true

      override fun remove() {
        if (!active) return
        active = false
        state.callbacks.remove(reconcile)
        if (state.callbacks.isEmpty()) {
          state.active = false
          managers.remove(state.boundary.identity)
          state.boundary.removeListener(state.listener)
        }
      }
    }
  }

  companion object {
    val shared = ReactNativeMountReconciliationAdapter()

    fun resolve(root: ViewGroup): ReactNativeMountCallbacks? {
      val reactContext =
        runCatching { UIManagerHelper.getReactContext(root) }.getOrNull() ?: return null
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
