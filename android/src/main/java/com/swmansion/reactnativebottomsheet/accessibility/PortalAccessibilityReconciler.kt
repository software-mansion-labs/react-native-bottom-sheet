package com.swmansion.reactnativebottomsheet.accessibility

import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap

/** Applies the accessibility path cut for one nearest React Native root. */
internal class PortalAccessibilityReconciler(root: ViewGroup) {
  private class MaskRecord(view: View, var restoreCandidate: Int) {
    val view = WeakReference(view)
    val coordinatorWrite = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  }

  private val root = WeakReference(root)
  private val maskRecords = mutableListOf<MaskRecord>()

  fun reconcile(retainedAnchors: Collection<View>): Boolean {
    val root = root.get()
    val retainedChildren = IdentityHashMap<ViewGroup, MutableSet<View>>()
    retainedAnchors.forEach { anchor ->
      val path = root?.let { pathFromRoot(anchor, it) } ?: return@forEach
      path.zipWithNext().forEach { (parent, child) ->
        val group = parent as? ViewGroup ?: return@forEach
        retainedChildren.getOrPut(group) { identitySet() }.add(child)
      }
    }
    val desiredMasks = identitySet()

    retainedChildren.forEach { (parent, retained) ->
      for (index in 0 until parent.childCount) {
        parent.getChildAt(index).takeIf { it !in retained }?.let(desiredMasks::add)
      }
    }
    desiredMasks.forEach { view ->
      val record = maskRecords.firstOrNull { it.view.get() === view }
      if (record == null) {
        // If the application already owns the same NO_HIDE_DESCENDANTS scalar, that write is
        // indistinguishable from ours. The last distinguishable value remains the best restore
        // candidate, as required by ADR-0002's explicit same-value limitation.
        maskRecords.add(MaskRecord(view, view.importantForAccessibility))
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      } else if (view.importantForAccessibility != record.coordinatorWrite) {
        record.restoreCandidate = view.importantForAccessibility
        view.importantForAccessibility = record.coordinatorWrite
      }
    }
    val records = maskRecords.iterator()
    while (records.hasNext()) {
      val record = records.next()
      val view = record.view.get()
      if (view == null || view !in desiredMasks) {
        if (view?.importantForAccessibility == record.coordinatorWrite) {
          view.importantForAccessibility = record.restoreCandidate
        }
        records.remove()
      }
    }
    return retainedChildren.isNotEmpty()
  }

  private fun pathFromRoot(anchor: View, root: ViewGroup): List<View>? {
    val reversed = mutableListOf<View>()
    var current = anchor
    while (true) {
      reversed.add(current)
      if (current === root) return reversed.asReversed()
      val parent = current.parent as? ViewGroup ?: return null
      if (parent.indexOfChild(current) < 0) return null
      current = parent
    }
  }

  private fun identitySet(): MutableSet<View> =
    Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
}
