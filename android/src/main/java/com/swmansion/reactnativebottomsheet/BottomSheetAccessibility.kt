package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.uimanager.ReactPointerEventsView

/*
 * The host draws the scrim on its own canvas and owns every touch on it, so the scrim has no place
 * in the accessibility tree. This view gives it one: a "Dismiss" button over the part of the scrim
 * above the sheet, present only while activating it would dismiss the sheet.
 *
 * It is a real child rather than a virtual node. An `ExploreByTouchHelper` on the host replaces the
 * host's node provider, which hides the sheet content's real nodes from TalkBack, and its key
 * forwarding can swallow Enter meant for a focused control in the sheet.
 */

/** The scrim's "Dismiss" button for accessibility services. It draws nothing and takes no touch. */
internal class ScrimDismissView(
  context: Context,
  private val performDismiss: () -> Boolean,
  traversalAfter: View,
) : View(context), ReactPointerEventsView {
  // React Native's touch-target walk skips it, so a JS touch on the scrim still reaches the host.
  override val pointerEvents: PointerEvents = PointerEvents.NONE

  private var isExposed = false

  init {
    // Laid out over the whole host and scaled down to the area above the sheet, so following the
    // sheet is a RenderNode property change rather than a layout pass on every frame. Accessibility
    // bounds and hover hit testing both apply the scale.
    pivotX = 0f
    pivotY = 0f
    scaleY = 0f
    visibility = INVISIBLE
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    // Out of keyboard focus, as on the web: it draws no focus indicator, and Escape is the
    // keyboard's dismissal path. Explicit, since a clickable view is focusable by default.
    isFocusable = false
    ViewCompat.setAccessibilityDelegate(this, ScrimDismissAccessibilityDelegate(traversalAfter))
  }

  /**
   * Follows the sheet's top edge, and exposes the button while dismissal is available. Scaled
   * against the host's height, which this view is laid out to, so it is right before this view's
   * own layout.
   */
  fun update(isDismissAvailable: Boolean, sheetTop: Float, hostHeight: Int) {
    val scale = if (hostHeight > 0) (sheetTop / hostHeight).coerceIn(0f, 1f) else 0f
    val exposed = isDismissAvailable && scale * hostHeight >= 1f
    // Only an exposed button follows the sheet; a hidden one keeps its last scale until shown.
    if (exposed && scaleY != scale) scaleY = scale
    if (exposed == isExposed) return
    isExposed = exposed
    visibility = if (exposed) VISIBLE else INVISIBLE
    importantForAccessibility =
      if (exposed) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO
    // Clickable, so accessibility services can activate it.
    isClickable = exposed
    if (!exposed) isPressed = false
  }

  // The host intercepts scrim touches before they reach its children; this only declines the rest.
  override fun onTouchEvent(event: MotionEvent): Boolean = false

  override fun performClick(): Boolean {
    if (!isExposed) return false
    super.performClick()
    return performDismiss()
  }

  override fun getAccessibilityClassName(): CharSequence = Button::class.java.name
}

private class ScrimDismissAccessibilityDelegate(private val traversalAfter: View) :
  AccessibilityDelegateCompat() {
  override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
    super.onInitializeAccessibilityNodeInfo(host, info)
    if (host.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_YES) return
    info.isDismissable = true
    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_DISMISS)
    // Reached after the sheet's content, which drawing order alone would not guarantee.
    info.setTraversalAfter(traversalAfter)
  }

  override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
    if (action == AccessibilityNodeInfoCompat.ACTION_DISMISS) return host.performClick()
    return super.performAccessibilityAction(host, action, args)
  }
}

/**
 * Surfaces `ACTION_DISMISS` on the sheet container, so TalkBack's dismiss action works while focus
 * is inside the sheet content.
 */
internal class SheetDismissAccessibilityDelegate(
  private val isDismissAvailable: () -> Boolean,
  private val performDismiss: () -> Boolean,
) : AccessibilityDelegateCompat() {
  override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
    super.onInitializeAccessibilityNodeInfo(host, info)
    if (isDismissAvailable()) {
      info.isDismissable = true
      info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_DISMISS)
    }
  }

  override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
    if (
      action == AccessibilityNodeInfoCompat.ACTION_DISMISS &&
        isDismissAvailable() &&
        performDismiss()
    ) {
      return true
    }
    return super.performAccessibilityAction(host, action, args)
  }
}
