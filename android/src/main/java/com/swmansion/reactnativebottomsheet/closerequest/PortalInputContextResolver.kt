package com.swmansion.reactnativebottomsheet.closerequest

import android.view.View
import android.view.ViewGroup
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.common.UIManagerType
import com.facebook.react.uimanager.common.ViewUtil
import com.swmansion.reactnativebottomsheet.BottomSheetView
import com.swmansion.reactnativebottomsheet.accessibility.ReactNativeMountReconciliationAdapter
import com.swmansion.reactnativebottomsheet.accessibility.currentWindowPath
import com.swmansion.reactnativebottomsheet.presentation.NativePortalOrder
import com.swmansion.reactnativebottomsheet.presentation.NativePortalOrderResolver
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationAnchor
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationCoordinator
import com.swmansion.reactnativebottomsheet.presentation.resolvePortalPresentationContext
import com.swmansion.reactnativebottomsheet.presentation.resolvePresentationOwnership

internal data class PortalInputContextKey(val root: ViewGroup, val tag: Int)

/** A null context marks the accepted existing-window fallback, not a widened local context. */
internal data class PortalInputTarget(val anchor: View, val context: PortalInputContextKey?)

/**
 * Fresh input reads the shared source; captured input revalidates its context without refocusing.
 */
internal object PortalInputContextResolver {
  fun select(windowRoot: View, fallback: View?): PortalInputTarget? {
    val focus = readCurrentAccessibilityFocus(windowRoot)
    if (focus == NativeAccessibilityFocus.Unresolved)
      return fallback?.let { PortalInputTarget(it, null) }
    val origin =
      when (focus) {
        is NativeAccessibilityFocus.Found -> focus.view
        NativeAccessibilityFocus.Absent -> windowRoot.findFocus()
        NativeAccessibilityFocus.Unresolved -> null
      } ?: return fallback?.let { PortalInputTarget(it, null) }
    val path =
      origin.currentWindowPath(windowRoot) ?: return fallback?.let { PortalInputTarget(it, null) }
    val active =
      PortalPresentationCoordinator.currentAccessibilityPolicy(windowRoot).activePresentations
    val roots = active.groupBy { it.reactRoot }.filterKeys { it in path }
    val groups = mutableMapOf<PortalInputContextKey, List<View>>()
    val candidates = mutableListOf<Pair<Int, PortalInputContextKey>>()
    for ((root, presentations) in roots) {
      val facts =
        contexts(windowRoot, root, presentations, origin)
          ?: return fallback?.let { PortalInputTarget(it, null) }
      for ((tag, owners) in facts.owners) groups[PortalInputContextKey(root, tag)] = owners
      facts.path.forEachIndexed { index, tag ->
        candidates.add((facts.depth + index) to PortalInputContextKey(root, tag))
      }
    }
    val ordered = candidates.sortedBy { it.first }
    if (ordered.zipWithNext().any { (a, b) -> a.first == b.first && a.second != b.second })
      return fallback?.let { PortalInputTarget(it, null) }
    val classified =
      CurrentInputFocus.Inside(
        ordered.map { (_, key) ->
          InputContextCandidate(key, !groups[key].isNullOrEmpty())
        }
      )
    val selected =
      selectActiveInputContext(
        if (focus == NativeAccessibilityFocus.Absent) CurrentInputFocus.Absent else classified,
        classified,
        hasForegroundWindowModalPresentation = false,
      )
    return when (selected) {
      is ActiveInputContext.Portal ->
        owner(groups[selected.identity].orEmpty())?.let { PortalInputTarget(it, selected.identity) }
      ActiveInputContext.Host -> null
      ActiveInputContext.WindowFallback,
      ActiveInputContext.WindowModal -> fallback?.let { PortalInputTarget(it, null) }
    }
  }

  fun ownerInContext(windowRoot: View, key: PortalInputContextKey): View? {
    val active =
      PortalPresentationCoordinator.currentAccessibilityPolicy(windowRoot)
        .activePresentations
        .filter { it.reactRoot === key.root }
    return contexts(windowRoot, key.root, active, null)?.owners?.get(key.tag)?.let(::owner)
  }

  private data class Contexts(val owners: Map<Int, List<View>>, val path: List<Int>, val depth: Int)

  private fun contexts(
    windowRoot: View,
    root: ViewGroup,
    active: List<PortalPresentationAnchor>,
    origin: View?,
  ): Contexts? {
    if (active.isEmpty()) return Contexts(emptyMap(), emptyList(), 0)
    val fabric = active.filter { ViewUtil.getUIManagerType(it.anchor) == UIManagerType.FABRIC }
    if (fabric.isEmpty()) {
      // Preserve the existing non-Fabric compatibility boundary.
      return Contexts(
        mapOf(root.id to active.map { it.anchor }),
        listOf(root.id),
        origin?.currentWindowPath(windowRoot)?.indexOf(root)?.takeIf { it >= 0 } ?: 0,
      )
    }
    if (fabric.size != active.size) return null
    val boundary =
      ReactNativeMountReconciliationAdapter.resolve(root)?.takeIf { it.isFabric } ?: return null
    val owners = active.map { it.anchor as? BottomSheetView ?: return null }
    val bySurface = owners.groupBy(UIManagerHelper::getSurfaceId)
    if (bySurface.size != 1) return null
    val (surface, presentations) = bySurface.entries.single()
    if (surface <= 0) return null
    val facts =
      ReactNativeMountReconciliationAdapter.shared.readPortalInputContexts(
        boundary,
        surface,
        presentations,
        windowRoot,
        origin,
      ) ?: return null
    return Contexts(facts.contextOwners, facts.focusContexts, facts.nativeDepth ?: 0)
  }

  private fun owner(active: List<View>): View? {
    val contexts = active.associateWith { it.resolvePortalPresentationContext() }
    val ownership =
      resolvePresentationOwnership(active) { first, second ->
        val a = contexts[first]
        val b = contexts[second]
        if (a == null || b == null) NativePortalOrder.UNKNOWN
        else NativePortalOrderResolver.compare(a, b)
      }
    return ownership.top ?: ownership.closeFallbackOwner
  }
}
