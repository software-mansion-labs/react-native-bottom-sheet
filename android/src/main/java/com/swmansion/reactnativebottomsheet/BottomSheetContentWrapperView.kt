package com.swmansion.reactnativebottomsheet

import android.content.Context
import com.facebook.react.bridge.Arguments
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.views.view.ReactViewGroup

/**
 * Wrapper around the sheet content. The host identifies it by type and owns its Yoga geometry in
 * every mode: [updateFrameState] pushes the sheet's measured width and the natively computed detent
 * cap into the wrapper's shadow state, and the component descriptor forces the node's size from it.
 * The JS-provided styles apply only before the sheet's first native measure.
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
}
