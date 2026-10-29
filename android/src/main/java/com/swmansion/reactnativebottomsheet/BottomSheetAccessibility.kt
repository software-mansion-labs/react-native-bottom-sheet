package com.swmansion.reactnativebottomsheet

import android.os.Bundle
import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

internal class DismissAccessibilityDelegate(private val isDismissAvailable: () -> Boolean) :
  AccessibilityDelegateCompat() {
  override fun onInitializeAccessibilityNodeInfo(
    host: View,
    info: AccessibilityNodeInfoCompat,
  ) {
    super.onInitializeAccessibilityNodeInfo(host, info)
    if (!isDismissAvailable()) return

    info.className = "android.widget.Button"
    info.contentDescription = host.contentDescription
    info.isClickable = true
    info.isDismissable = true
    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_DISMISS)
  }

  override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
    if (
      isDismissAvailable() &&
        (action == AccessibilityNodeInfoCompat.ACTION_CLICK ||
          action == AccessibilityNodeInfoCompat.ACTION_DISMISS)
    ) {
      return host.performClick()
    }
    return super.performAccessibilityAction(host, action, args)
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
