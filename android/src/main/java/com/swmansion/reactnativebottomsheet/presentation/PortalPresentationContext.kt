package com.swmansion.reactnativebottomsheet.presentation

import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import com.facebook.react.uimanager.RootViewUtil

/** A current hierarchy snapshot, never retained by the shared registry. */
internal data class PortalPresentationContext(
  val reactRoot: ViewGroup,
  val windowRoot: View,
  val windowToken: IBinder,
  val path: List<View>,
  val windowPath: List<View>,
)

/** The nearest RN event root is the boundary, including RN content hosted in a Dialog. */
internal fun View.resolvePortalPresentationContext(): PortalPresentationContext? {
  if (!isAttachedToWindow) return null
  val token = windowToken ?: return null
  // RootViewUtil asserts when a hierarchy without an RN root reaches ViewRootImpl.
  val reactRoot =
    try {
      RootViewUtil.getRootView(this) as? ViewGroup
    } catch (_: AssertionError) {
      null
    } ?: return null
  if (!reactRoot.isAttachedToWindow || reactRoot.windowToken !== token) return null
  val windowRoot = rootView
  val ancestors = mutableListOf<View>()
  var current: View = this
  while (true) {
    ancestors.add(current)
    if (current === windowRoot) break
    val parent = current.parent as? ViewGroup ?: return null
    if (parent.indexOfChild(current) < 0) return null
    current = parent
  }
  val windowPath = ancestors.asReversed()
  val rootIndex = windowPath.indexOf(reactRoot)
  if (rootIndex < 0) return null
  return PortalPresentationContext(
    reactRoot,
    windowRoot,
    token,
    windowPath.drop(rootIndex),
    windowPath,
  )
}
