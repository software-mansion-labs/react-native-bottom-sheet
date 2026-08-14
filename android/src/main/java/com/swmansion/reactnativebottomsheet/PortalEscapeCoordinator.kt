package com.swmansion.reactnativebottomsheet

import android.view.KeyEvent
import android.view.View
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal fun interface PortalEscapeTarget {
  fun dispatchEscape(event: KeyEvent): Boolean
}

/**
 * Routes Escape between portal sheets that share the exact same Android root view.
 *
 * Registration order is the only ordering signal available across independent portal providers, so
 * dispatch mirrors [androidx.activity.OnBackPressedDispatcher] and asks the newest live target
 * first. Targets remain responsible for eligibility and for owning a captured key sequence.
 */
internal object PortalEscapeCoordinator {
  internal interface Registration {
    fun remove()
  }

  private class Entry(target: PortalEscapeTarget) {
    val target = WeakReference(target)
    var isActive = true
  }

  private val entriesByRoot = WeakHashMap<View, MutableList<Entry>>()

  fun register(root: View, target: PortalEscapeTarget): Registration {
    removeInactiveEntries(root)
    val entry = Entry(target)
    entriesByRoot.getOrPut(root, ::mutableListOf).add(entry)
    val rootReference = WeakReference(root)

    return object : Registration {
      override fun remove() {
        if (!entry.isActive) return
        entry.isActive = false
        rootReference.get()?.let(::removeInactiveEntries)
      }
    }
  }

  fun dispatch(root: View, event: KeyEvent): Boolean {
    removeInactiveEntries(root)
    val entries = entriesByRoot[root] ?: return false
    val snapshot = entries.toList()

    for (index in snapshot.indices.reversed()) {
      val entry = snapshot[index]
      if (!entry.isActive) continue
      val target = entry.target.get() ?: continue
      if (target.dispatchEscape(event)) return true
    }

    removeInactiveEntries(root)
    return false
  }

  private fun removeInactiveEntries(root: View) {
    val entries = entriesByRoot[root] ?: return
    entries.removeAll { !it.isActive || it.target.get() == null }
    if (entries.isEmpty()) {
      entriesByRoot.remove(root)
    }
  }
}
