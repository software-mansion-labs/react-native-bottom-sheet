package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner

/** The independently resolved Android owners used by a portal's close-request input handling. */
internal data class PortalRequestCloseHost(
  val dispatcherOwner: OnBackPressedDispatcherOwner?,
  val lifecycleOwner: LifecycleOwner?,
  val rootView: View,
)

/**
 * Resolves owners only from an attached portal hierarchy. The nearest view-tree owners take
 * precedence over the React host Activity. Invalid Activity owners are rejected as a whole, while a
 * destroyed custom lifecycle owner is intentionally retained instead of falling back to a broader
 * Activity lifecycle.
 */
internal fun View.resolvePortalRequestCloseHost(
  currentActivity: Activity?
): PortalRequestCloseHost? {
  if (!isAttachedToWindow) return null

  val validatedCurrentActivity = currentActivity?.takeIf(::isValidActivityOwner)
  val viewTreeDispatcherOwner = findViewTreeOnBackPressedDispatcherOwner()
  val dispatcherOwner =
    when (viewTreeDispatcherOwner) {
      is Activity -> viewTreeDispatcherOwner.takeIf(::isValidActivityOwner)
      else -> viewTreeDispatcherOwner
    } ?: (validatedCurrentActivity as? OnBackPressedDispatcherOwner)

  val viewTreeLifecycleOwner = findViewTreeLifecycleOwner()
  val validatedViewTreeLifecycleOwner =
    when (viewTreeLifecycleOwner) {
      is Activity -> viewTreeLifecycleOwner.takeIf(::isValidActivityOwner)
      else -> viewTreeLifecycleOwner
    }
  val lifecycleOwner =
    when {
      dispatcherOwner !is Activity &&
        dispatcherOwner != null &&
        (viewTreeLifecycleOwner is Activity || viewTreeLifecycleOwner is ComponentDialog) ->
        dispatcherOwner
      validatedViewTreeLifecycleOwner != null -> validatedViewTreeLifecycleOwner
      else -> dispatcherOwner
    }

  return PortalRequestCloseHost(dispatcherOwner, lifecycleOwner, rootView)
}

private fun View.isValidActivityOwner(activity: Activity): Boolean =
  !activity.isFinishing && !activity.isDestroyed && belongsToWindow(activity.window)

private fun View.belongsToWindow(window: Window): Boolean {
  val decorView = window.decorView
  if (rootView === decorView.rootView) return true

  val portalToken = windowToken ?: return false
  val decorToken = decorView.windowToken ?: return false
  return portalToken == decorToken
}

internal tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
