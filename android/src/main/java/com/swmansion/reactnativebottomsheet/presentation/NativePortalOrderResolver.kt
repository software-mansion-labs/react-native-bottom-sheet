package com.swmansion.reactnativebottomsheet.presentation

import android.view.ViewGroup
import android.widget.FrameLayout
import com.facebook.react.ReactRootView
import com.facebook.react.runtime.ReactSurfaceView
import com.facebook.react.views.view.ReactViewGroup

internal enum class NativePortalOrder {
  ABOVE,
  BELOW,
  EQUAL,
  UNKNOWN,
}

/**
 * Native drawing order is not always observable. Registration and accessibility traversal order are
 * not evidence of which portal is visually Top.
 */
internal object NativePortalOrderResolver {
  fun compare(
    first: PortalPresentationContext,
    second: PortalPresentationContext,
  ): NativePortalOrder {
    if (
      first.windowPath.last().resolvePortalPresentationContext() != first ||
        second.windowPath.last().resolvePortalPresentationContext() != second
    )
      return NativePortalOrder.UNKNOWN
    if (first.windowRoot !== second.windowRoot || first.windowToken !== second.windowToken)
      return NativePortalOrder.UNKNOWN
    if (
      (first.windowPath + second.windowPath).any {
        it.animation?.hasEnded() == false || (it as? ViewGroup)?.layoutTransition?.isRunning == true
      }
    )
      return NativePortalOrder.UNKNOWN
    val commonLength = first.windowPath.zip(second.windowPath).takeWhile { (a, b) -> a === b }.size
    val pathsAreIdentical =
      commonLength == first.windowPath.size && commonLength == second.windowPath.size
    if (pathsAreIdentical) return NativePortalOrder.EQUAL
    if (commonLength == first.windowPath.size) return NativePortalOrder.BELOW
    if (commonLength == second.windowPath.size) return NativePortalOrder.ABOVE
    val parent =
      first.windowPath[commonLength - 1] as? ViewGroup ?: return NativePortalOrder.UNKNOWN
    // Android's custom-order enable flag is protected, even where the one-argument
    // getChildDrawingOrder hook is public. Restrict proof to known drawing contracts;
    // arbitrary subclasses may override dispatchDraw or leave their custom hook disabled.
    if (
      parent.javaClass != FrameLayout::class.java &&
        parent.javaClass != ReactViewGroup::class.java &&
        parent.javaClass != ReactRootView::class.java &&
        parent.javaClass != ReactSurfaceView::class.java
    ) {
      return NativePortalOrder.UNKNOWN
    }
    val firstZ = first.windowPath[commonLength].z
    val secondZ = second.windowPath[commonLength].z
    if (!firstZ.isFinite() || !secondZ.isFinite()) return NativePortalOrder.UNKNOWN
    if (firstZ > secondZ) return NativePortalOrder.ABOVE
    if (firstZ < secondZ) return NativePortalOrder.BELOW
    val order =
      (0 until parent.childCount).map {
        // Older RN versions expose Paper zIndex here; Fabric supplies ordered native children.
        if (parent is ReactViewGroup) parent.getZIndexMappedChildIndex(it) else it
      }
    if (order.toSet().size != parent.childCount || order.any { it !in 0 until parent.childCount }) {
      return NativePortalOrder.UNKNOWN
    }
    return if (
      order.indexOf(parent.indexOfChild(first.windowPath[commonLength])) >
        order.indexOf(parent.indexOfChild(second.windowPath[commonLength]))
    ) {
      NativePortalOrder.ABOVE
    } else {
      NativePortalOrder.BELOW
    }
  }
}
