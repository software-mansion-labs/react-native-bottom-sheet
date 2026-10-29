package com.swmansion.reactnativebottomsheet.accessibility

import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap

/** Current native facts supplied by presentation ownership; no boundary discovery here. */
internal data class PortalAccessibilityScope(
  val boundary: ViewGroup,
  val retainedAnchors: Collection<View>,
)

/** Native branch proposals and the whole owners retained by this source. */
internal data class PortalAccessibilityCut(
  val branches: Collection<View>,
  val retainedOwners: Collection<View>,
)

/** Applies supplied native context cuts through one ledger; it does not discover boundaries. */
internal class PortalAccessibilityReconciler(windowAnchor: View) {
  private val windowAnchor = WeakReference(windowAnchor)
  private val maskLedger = PortalAccessibilityMaskLedger()

  fun reconcile(
    scopes: Collection<PortalAccessibilityScope>,
    sourceCuts: Collection<PortalAccessibilityCut> = emptyList(),
    isCurrent: () -> Boolean = { true },
  ): Boolean {
    if (scopes.isEmpty()) return reconcileCuts(sourceCuts, isCurrent)
    val window = windowAnchor.get()?.rootView ?: return false
    val token = window.windowToken ?: return false
    if (!window.isAttachedToWindow) return false
    val desiredMasks = identitySet()
    val retainedOwners = identitySet()
    val anchorsByScope = IdentityHashMap<ViewGroup, MutableSet<View>>()
    scopes.forEach { scope ->
      if (scope.retainedAnchors.isEmpty()) return false
      if (
        !scope.boundary.isAttachedToWindow ||
          scope.boundary.rootView !== window ||
          scope.boundary.windowToken !== token
      )
        return false
      anchorsByScope.getOrPut(scope.boundary) { identitySet() }.addAll(scope.retainedAnchors)
    }
    anchorsByScope.forEach { (boundary, anchors) ->
      val retainedChildren = IdentityHashMap<ViewGroup, MutableSet<View>>()
      anchors.forEach { anchor ->
        if (
          !anchor.isAttachedToWindow || anchor.rootView !== window || anchor.windowToken !== token
        )
          return false
        val path = pathFromRoot(anchor, boundary) ?: return false
        retainedOwners.add(anchor)
        for ((parent, child) in path.zipWithNext()) {
          if (parent in anchors) break // This context already retains the whole ancestor.
          val group = parent as? ViewGroup ?: continue
          retainedChildren.getOrPut(group) { identitySet() }.add(child)
        }
      }
      retainedChildren.forEach { (parent, retained) ->
        for (index in 0 until parent.childCount) {
          parent.getChildAt(index).takeIf { it !in retained }?.let(desiredMasks::add)
        }
      }
    }
    return reconcileCuts(
      sourceCuts + PortalAccessibilityCut(desiredMasks, retainedOwners),
      isCurrent,
    )
  }

  /**
   * Source validation belongs to the caller. Consume the complete cut collection in the current UI
   * work; an empty collection releases our masks, and a rejected proposal changes none of them.
   */
  fun reconcileCuts(
    cuts: Collection<PortalAccessibilityCut>,
    isCurrent: () -> Boolean = { true },
  ): Boolean {
    if (cuts.isEmpty()) {
      if (!isCurrent()) return false
      maskLedger.reconcile(emptyList())
      return true
    }
    val window = windowAnchor.get()?.rootView ?: return false
    val token = window.windowToken ?: return false
    val branches = identitySet()
    val retainedOwners = identitySet()
    cuts.forEach { cut ->
      if (cut.retainedOwners.isEmpty()) return false
      branches.addAll(cut.branches)
      retainedOwners.addAll(cut.retainedOwners)
    }
    val composed = composeMasks(branches, retainedOwners)
    if (!isCurrent()) return false
    // Validate the complete proposal after the caller's current-source check, before any writes.
    listOf(branches, retainedOwners, composed).flatten().forEach { view ->
      if (
        !view.isAttachedToWindow ||
          view.rootView !== window ||
          view.windowToken !== token ||
          pathFromRoot(view, window) == null
      )
        return false
    }
    maskLedger.reconcile(composed)
    return true
  }

  private fun composeMasks(branches: Collection<View>, retainedOwners: Set<View>): Set<View> {
    val result = identitySet()
    branches.forEach { branch ->
      val retainedPath = identitySet()
      retainedOwners.forEach { owner ->
        pathFromRoot(owner, branch)?.let(retainedPath::addAll)
      }
      val pending = ArrayDeque<View>()
      pending.add(branch)
      while (pending.isNotEmpty()) {
        val view = pending.removeLast()
        if (view !in retainedPath) {
          result.add(view)
        } else if (view !in retainedOwners && view is ViewGroup) {
          for (index in 0 until view.childCount) pending.add(view.getChildAt(index))
        }
      }
    }
    return result
  }

  private fun pathFromRoot(anchor: View, root: View): List<View>? {
    val reversed = mutableListOf<View>()
    var current = anchor
    while (true) {
      if (reversed.any { it === current }) return null
      reversed.add(current)
      if (current === root) return reversed.asReversed()
      val parent = current.parent as? ViewGroup ?: return null
      val index = parent.indexOfChild(current)
      if (index < 0 || parent.getChildAt(index) !== current) return null
      current = parent
    }
  }

  private fun identitySet(): MutableSet<View> =
    Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
}
