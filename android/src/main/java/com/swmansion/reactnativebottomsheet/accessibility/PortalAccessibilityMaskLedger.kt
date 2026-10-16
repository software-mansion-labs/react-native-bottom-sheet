package com.swmansion.reactnativebottomsheet.accessibility

import android.view.View
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Owns mask mutations for the caller's complete desired target union, without choosing its scope.
 */
internal class PortalAccessibilityMaskLedger {
  private class MaskRecord(view: View, var restoreCandidate: Int) {
    val view = WeakReference(view)
    val coordinatorWrite = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  }

  private val maskRecords = mutableListOf<MaskRecord>()

  fun reconcile(desiredMasks: Collection<View>) {
    val desired = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
    desired.addAll(desiredMasks)
    desired.forEach { view ->
      val record = maskRecords.firstOrNull { it.view.get() === view }
      if (record == null) {
        // Same-value application writes are indistinguishable from ours. Preserve the last
        // distinguishable restore candidate, as required by ADR-0002.
        maskRecords.add(MaskRecord(view, view.importantForAccessibility))
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      } else if (view.importantForAccessibility != record.coordinatorWrite) {
        record.restoreCandidate = view.importantForAccessibility
        view.importantForAccessibility = record.coordinatorWrite
      }
    }
    // Establish every new mask before releasing any obsolete one during a transfer.
    val records = maskRecords.iterator()
    while (records.hasNext()) {
      val record = records.next()
      val view = record.view.get()
      if (view == null || view !in desired) {
        if (view?.importantForAccessibility == record.coordinatorWrite) {
          view.importantForAccessibility = record.restoreCandidate
        }
        records.remove()
      }
    }
  }
}
