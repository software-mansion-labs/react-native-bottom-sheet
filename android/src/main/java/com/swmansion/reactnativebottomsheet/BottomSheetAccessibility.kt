package com.swmansion.reactnativebottomsheet

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

internal class ScrimAccessibilityHelper(
  private val host: View,
  private val isDismissAvailable: () -> Boolean,
  private val scrimBottom: () -> Float,
  private val performDismiss: () -> Boolean,
) : ExploreByTouchHelper(host) {
  private var wasVisible = false

  fun updateVisibility() {
    val visible = isVirtualScrimVisible()
    if (visible == wasVisible) return

    wasVisible = visible
    invalidateRoot()
  }

  override fun getVirtualViewAt(x: Float, y: Float): Int {
    val bottom = scrimBottomInParent()
    return if (isDismissAvailable() && x >= 0f && x < host.width && y >= 0f && y < bottom) {
      SCRIM_VIRTUAL_VIEW_ID
    } else {
      INVALID_ID
    }
  }

  override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
    if (isVirtualScrimVisible()) virtualViewIds.add(SCRIM_VIRTUAL_VIEW_ID)
  }

  override fun onPopulateNodeForVirtualView(
    virtualViewId: Int,
    node: AccessibilityNodeInfoCompat,
  ) {
    node.className = "android.widget.Button"
    node.contentDescription = host.context.getString(R.string.bottom_sheet_dismiss)
    node.isClickable = true
    node.isDismissable = true
    node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
    node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_DISMISS)
    setBoundsInScreenFromBoundsInParent(
      node,
      Rect(0, 0, host.width, scrimBottomInParent()),
    )
  }

  override fun onPerformActionForVirtualView(
    virtualViewId: Int,
    action: Int,
    arguments: Bundle?,
  ): Boolean =
    virtualViewId == SCRIM_VIRTUAL_VIEW_ID &&
      isDismissAvailable() &&
      when (action) {
        AccessibilityNodeInfoCompat.ACTION_CLICK,
        AccessibilityNodeInfoCompat.ACTION_DISMISS -> performDismiss()
        else -> false
      }

  private fun isVirtualScrimVisible(): Boolean =
    isDismissAvailable() && host.width > 0 && scrimBottomInParent() > 0

  private fun scrimBottomInParent(): Int = scrimBottom().toInt().coerceIn(0, host.height)

  private companion object {
    // Virtual IDs are local to this helper; zero identifies its only virtual child.
    const val SCRIM_VIRTUAL_VIEW_ID = 0
  }
}

internal class SheetDismissAccessibilityDelegate(
  private val isDismissAvailable: () -> Boolean,
  private val performDismiss: () -> Boolean,
) : AccessibilityDelegateCompat() {
  override fun onInitializeAccessibilityNodeInfo(
    host: View,
    info: AccessibilityNodeInfoCompat,
  ) {
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
