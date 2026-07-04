package com.swmansion.reactnativebottomsheet

import android.content.Context
import com.facebook.react.bridge.Arguments
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.views.view.ReactViewGroup

/**
 * Wrapper around the sheet content. The host identifies it by type and, in native-overlay mode,
 * owns its Yoga geometry: [updateFrameState] pushes the overlay's measured width and the natively
 * computed detent cap into the wrapper's shadow state, and the component descriptor forces the
 * node's size from it. Inline sheets never push, leaving the JS-provided flex styles in effect.
 */
class BottomSheetContentWrapperView(context: Context) : ReactViewGroup(context) {

  var stateWrapper: StateWrapper? = null

  private val density = context.resources.displayMetrics.density
  private var lastFrameWidthPx = Int.MIN_VALUE
  private var lastFrameHeightPx = Float.NaN

  fun updateFrameState(widthPx: Int, heightPx: Float) {
    if (widthPx == lastFrameWidthPx && heightPx == lastFrameHeightPx) return
    val sw = stateWrapper ?: return
    lastFrameWidthPx = widthPx
    lastFrameHeightPx = heightPx
    val map = Arguments.createMap()
    map.putDouble("frameWidth", (widthPx / density).toDouble())
    map.putDouble("frameHeight", (heightPx / density).toDouble())
    sw.updateState(map)
  }

  /** Pushes a zero size, restoring the JS-provided styles (used when the overlay is dismissed). */
  fun clearFrameState() {
    updateFrameState(0, 0f)
  }
}
