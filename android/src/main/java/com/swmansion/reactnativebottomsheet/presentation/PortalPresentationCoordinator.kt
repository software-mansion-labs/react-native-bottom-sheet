package com.swmansion.reactnativebottomsheet.presentation

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.UiThread
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal enum class PortalPresentationAssignment {
  NONE,
  TOP,
  CLOSE_FALLBACK,
}

internal data class PortalRetainedPresentation(val reactRoot: ViewGroup, val anchor: View)

internal fun interface PortalAccessibilityPolicyObserver {
  fun onPolicyChanged(
    windowRoot: View,
    retainedPresentations: List<PortalRetainedPresentation>,
  )
}

/** One neutral membership and native-order authority, with weak anchors, roots and observers. */
@UiThread
internal object PortalPresentationCoordinator {
  internal interface Registration {
    /** False means the hierarchy or observer expired; the controller must resolve it again. */
    fun update(isActive: Boolean): Boolean

    fun remove()
  }

  internal interface PolicyObservation {
    fun remove()
  }

  private class Entry(
    anchor: View,
    context: PortalPresentationContext,
    var isActive: Boolean,
    observer: (PortalPresentationAssignment) -> Unit,
  ) {
    val anchor = WeakReference(anchor)
    val reactRoot = WeakReference(context.reactRoot)
    val windowRoot = WeakReference(context.windowRoot)
    val windowToken = WeakReference(context.windowToken)
    val observer = WeakReference(observer)
    var isRegistered = true
    var assignment = PortalPresentationAssignment.NONE

    fun assign(value: PortalPresentationAssignment) {
      if (assignment == value) return
      assignment = value
      observer.get()?.invoke(value)
    }
  }

  private class RegistrationImpl(private val entry: Entry, root: View) : Registration {
    private val root = WeakReference(root)

    override fun update(isActive: Boolean): Boolean {
      if (!entry.isRegistered) return false
      entry.isActive = isActive
      root.get()?.let { reconcile(it, forceAccessibilityPolicyReconciliation = true) }
      return entry.isRegistered
    }

    override fun remove() {
      if (!entry.isRegistered) return
      entry.isRegistered = false
      entry.assign(PortalPresentationAssignment.NONE)
      root.get()?.let { reconcile(it, forceAccessibilityPolicyReconciliation = true) }
    }
  }

  private class AccessibilityPolicySnapshot(paths: List<List<View>>) {
    private val paths = paths.map { path -> path.map(::WeakReference) }

    fun matches(current: List<List<View>>): Boolean =
      paths.size == current.size &&
        paths.zip(current).all { (previous, next) ->
          previous.size == next.size && previous.zip(next).all { (old, new) -> old.get() === new }
        }
  }

  private class WindowState(root: View) : ViewTreeObserver.OnPreDrawListener {
    val entries = mutableListOf<Entry>()
    var accessibilityPolicySnapshot = AccessibilityPolicySnapshot(emptyList())
    private val root = WeakReference(root)
    private var observer: WeakReference<ViewTreeObserver>? = null

    fun observeDrawing(view: View) {
      val current = view.viewTreeObserver
      if (observer?.get() === current) return
      stopObserving()
      if (current.isAlive) {
        current.addOnPreDrawListener(this)
        observer = WeakReference(current)
      }
    }

    override fun onPreDraw(): Boolean {
      // This runs before every draw while registrations exist, so keep reconciliation cheap.
      // Z and native child order may change without layout, requiring this refresh before the
      // new order is displayed; do not derive Active from drawing.
      val view = root.get()
      if (view != null) reconcile(view) else stopObserving()
      return true
    }

    fun stopObserving() {
      observer?.get()?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
      observer = null
    }
  }

  private val windows = WeakHashMap<View, WindowState>()
  private val accessibilityPolicyObservers = mutableListOf<PortalAccessibilityPolicyObserver>()

  fun observeAccessibilityPolicy(observer: PortalAccessibilityPolicyObserver): PolicyObservation {
    accessibilityPolicyObservers.add(observer)
    windows.keys.toList().forEach {
      reconcile(it, forceAccessibilityPolicyReconciliation = true)
    }
    return object : PolicyObservation {
      private var observing = true

      override fun remove() {
        if (!observing) return
        observing = false
        accessibilityPolicyObservers.remove(observer)
      }
    }
  }

  fun register(
    anchor: View,
    isActive: Boolean,
    observer: (PortalPresentationAssignment) -> Unit,
  ): Registration? {
    val context = anchor.resolvePortalPresentationContext() ?: return null
    val entry = Entry(anchor, context, isActive, observer)
    windows.getOrPut(context.windowRoot) { WindowState(context.windowRoot) }.entries.add(entry)
    reconcile(context.windowRoot, forceAccessibilityPolicyReconciliation = true)
    return RegistrationImpl(entry, context.windowRoot)
  }

  fun reconcile(
    root: View,
    forceAccessibilityPolicyReconciliation: Boolean = false,
  ) {
    val window = windows[root] ?: return
    val entries = window.entries
    val contexts = mutableMapOf<Entry, PortalPresentationContext>()
    entries.toList().forEach { entry ->
      val context = entry.anchor.get()?.resolvePortalPresentationContext()
      if (
        !entry.isRegistered ||
          entry.observer.get() == null ||
          context == null ||
          context.reactRoot !== entry.reactRoot.get() ||
          context.windowRoot !== root ||
          context.windowRoot !== entry.windowRoot.get() ||
          context.windowToken !== entry.windowToken.get()
      ) {
        entry.isRegistered = false
        entry.assign(PortalPresentationAssignment.NONE)
        entries.remove(entry)
      } else if (entry.isActive) {
        contexts[entry] = context
      }
    }
    if (entries.isEmpty()) {
      publishAccessibilityPolicy(
        root,
        window,
        emptyList(),
        emptyList(),
        forceAccessibilityPolicyReconciliation,
      )
      window.stopObserving()
      windows.remove(root)
      return
    }
    window.observeDrawing(root)
    val top =
      contexts.keys.singleOrNull { candidate ->
        contexts.all { (other, context) ->
          candidate === other ||
            NativePortalOrderResolver.compare(contexts.getValue(candidate), context) ==
              NativePortalOrder.ABOVE
        }
      }
    val closeOwner = top ?: entries.lastOrNull { it in contexts }
    // Registration order is only a Close fallback, never evidence for Top.
    // Withdraw the previous assignment synchronously before enabling its successor.
    entries.filter { it !== closeOwner }.forEach { it.assign(PortalPresentationAssignment.NONE) }
    closeOwner?.assign(
      if (top != null) PortalPresentationAssignment.TOP
      else PortalPresentationAssignment.CLOSE_FALLBACK
    )
    val retainedEntries = top?.let(::listOf) ?: contexts.keys.toList()
    val retainedContexts = retainedEntries.mapNotNull(contexts::get)
    publishAccessibilityPolicy(
      root,
      window,
      retainedEntries.zip(retainedContexts).mapNotNull { (entry, context) ->
        val anchor = entry.anchor.get() ?: return@mapNotNull null
        PortalRetainedPresentation(context.reactRoot, anchor)
      },
      retainedContexts.map(PortalPresentationContext::path),
      forceAccessibilityPolicyReconciliation,
    )
  }

  private fun publishAccessibilityPolicy(
    root: View,
    window: WindowState,
    retainedPresentations: List<PortalRetainedPresentation>,
    retainedPaths: List<List<View>>,
    force: Boolean,
  ) {
    if (!force && window.accessibilityPolicySnapshot.matches(retainedPaths)) return
    window.accessibilityPolicySnapshot = AccessibilityPolicySnapshot(retainedPaths)
    accessibilityPolicyObservers.toList().forEach {
      it.onPolicyChanged(root, retainedPresentations)
    }
  }
}
