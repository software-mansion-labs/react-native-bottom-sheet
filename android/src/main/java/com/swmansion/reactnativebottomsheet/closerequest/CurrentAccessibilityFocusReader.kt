package com.swmansion.reactnativebottomsheet.closerequest

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo

/** A current native origin; an inconclusive provider read does not establish absence. */
internal sealed interface NativeAccessibilityFocus {
  data class Found(val view: View) : NativeAccessibilityFocus

  data object Absent : NativeAccessibilityFocus

  data object Unresolved : NativeAccessibilityFocus
}

/** Called for fresh close input only; no focus history, delegates or periodic observation. */
@Suppress("DEPRECATION") // Recycle query results for the supported pre-33 SDKs.
internal fun readCurrentAccessibilityFocus(windowRoot: View): NativeAccessibilityFocus {
  try {
    if (!windowRoot.isAttachedToWindow || windowRoot.rootView !== windowRoot)
      return NativeAccessibilityFocus.Unresolved
    val token = windowRoot.windowToken ?: return NativeAccessibilityFocus.Unresolved
    val manager =
      windowRoot.context.getSystemService(AccessibilityManager::class.java)
        ?: return NativeAccessibilityFocus.Unresolved
    if (!manager.isEnabled) return NativeAccessibilityFocus.Absent
    val pending = ArrayDeque<View>()
    pending.add(windowRoot)
    var focused: View? = null
    while (pending.isNotEmpty()) {
      val view = pending.removeLast()
      if (!view.isAttachedToWindow || view.rootView !== windowRoot || view.windowToken !== token)
        continue
      val virtualFocus =
        view.accessibilityNodeProvider?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.let {
          val hasFocus = it.isAccessibilityFocused
          it.recycle()
          hasFocus
        } == true
      if (view.isAccessibilityFocused || virtualFocus) {
        if (focused != null && focused !== view) return NativeAccessibilityFocus.Unresolved
        focused = view
      }
      if (view is ViewGroup) {
        for (index in 0 until view.childCount) pending.add(view.getChildAt(index))
      }
    }
    return focused
      ?.takeIf { it.isAttachedToWindow && it.rootView === windowRoot && it.windowToken === token }
      ?.let(NativeAccessibilityFocus::Found) ?: NativeAccessibilityFocus.Unresolved
  } catch (_: RuntimeException) {
    // A foreign provider can fail while its virtual subtree is changing.
    return NativeAccessibilityFocus.Unresolved
  }
}
