package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.views.view.ReactViewGroup

/**
 * The container `BottomSheetProvider` renders its portals into. Its siblings are the screen behind
 * the sheets, so while a sheet in it is modal it hides them from accessibility services, as a
 * dialog's separate window would: TalkBack neither swipes past the sheet into that screen nor finds
 * it by touch.
 *
 * Android has no counterpart to iOS's `accessibilityViewIsModal`, so the siblings are marked
 * `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS` and get their own value back once no sheet is
 * modal. A sibling Fabric mounts meanwhile is hidden on the next layout pass.
 */
class BottomSheetPortalHostView(context: Context) : ReactViewGroup(context) {
  private val modalSheets = mutableSetOf<View>()
  // Each hidden sibling with the value it had before.
  private val hiddenSiblings = mutableMapOf<View, Int>()
  private var isObservingLayout = false
  private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateSiblings() }

  init {
    // It spans the screen above the app, so only its sheets may take touches. Set here because a
    // `ViewGroupManager` doesn't apply the `pointerEvents` prop.
    pointerEvents = PointerEvents.BOX_NONE
  }

  /**
   * Registers whether [sheet], a portal sheet inside this host, currently blocks the screen behind
   * it. The host hides its siblings while any registered sheet does.
   */
  fun setSheetModal(sheet: View, isModal: Boolean) {
    val changed = if (isModal) modalSheets.add(sheet) else modalSheets.remove(sheet)
    if (changed) updateSiblings()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    updateSiblings()
  }

  override fun onDetachedFromWindow() {
    updateSiblings(isAttached = false)
    super.onDetachedFromWindow()
  }

  private fun updateSiblings(isAttached: Boolean = isAttachedToWindow) {
    val siblings = if (isAttached && modalSheets.isNotEmpty()) parent as? ViewGroup else null
    // Restore what is no longer a sibling, or everything once nothing is modal.
    hiddenSiblings.keys.filter { siblings == null || it.parent !== siblings }.forEach(::restore)
    siblings?.let {
      for (index in 0 until it.childCount) {
        val sibling = it.getChildAt(index)
        if (sibling !== this) hide(sibling)
      }
    }
    observeLayout(siblings != null)
  }

  private fun hide(sibling: View) {
    if (sibling in hiddenSiblings) return
    val previous = sibling.importantForAccessibility
    if (previous == IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) return
    hiddenSiblings[sibling] = previous
    sibling.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  }

  private fun restore(sibling: View) {
    val previous = hiddenSiblings.remove(sibling) ?: return
    // Leave a value set from JS meanwhile in place.
    if (sibling.importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
      sibling.importantForAccessibility = previous
    }
  }

  private fun observeLayout(observe: Boolean) {
    if (observe == isObservingLayout) return
    isObservingLayout = observe
    if (observe) {
      viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    } else {
      viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
    }
  }
}
