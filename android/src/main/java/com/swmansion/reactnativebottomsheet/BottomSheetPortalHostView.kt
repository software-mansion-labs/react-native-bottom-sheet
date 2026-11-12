package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.views.view.ReactViewGroup

/**
 * The container `BottomSheetProvider` renders its portals into. Its siblings are the screen behind
 * the sheets, so while a sheet in it is modal it hides from accessibility services what the top
 * sheet covers, as a dialog's separate window would: those siblings, and the portals below the top
 * sheet's. TalkBack then neither swipes past the sheet into them nor finds them by touch.
 *
 * The sheets stay in the app's window, so the covered views are marked
 * `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS` and get their own value back once uncovered, as
 * the iOS host does with `accessibilityElementsHidden`. A view Fabric mounts or reorders meanwhile
 * is handled on the next layout pass.
 */
class BottomSheetPortalHostView(context: Context) : ReactViewGroup(context) {
  private val modalSheets = mutableSetOf<View>()
  // Each hidden view with the value it had before.
  private val hiddenViews = mutableMapOf<View, Int>()
  private var isObservingLayout = false
  private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateHiddenViews() }

  init {
    // It spans the screen above the app, so only its sheets may take touches. Set here because a
    // `ViewGroupManager` doesn't apply the `pointerEvents` prop.
    pointerEvents = PointerEvents.BOX_NONE
  }

  /**
   * Registers whether [sheet], a portal sheet inside this host, currently blocks the screen behind
   * it. The host hides what the top registered sheet covers.
   */
  fun setSheetModal(sheet: View, isModal: Boolean) {
    val changed = if (isModal) modalSheets.add(sheet) else modalSheets.remove(sheet)
    if (changed) updateHiddenViews()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    updateHiddenViews()
  }

  override fun onDetachedFromWindow() {
    updateHiddenViews(isAttached = false)
    super.onDetachedFromWindow()
  }

  private fun updateHiddenViews(isAttached: Boolean = isAttachedToWindow) {
    val isolating = isAttached && modalSheets.isNotEmpty()
    val covered = mutableSetOf<View>()
    if (isolating) {
      // The screen behind the sheets.
      (parent as? ViewGroup)?.let { siblings ->
        for (index in 0 until siblings.childCount) {
          siblings.getChildAt(index).takeIf { it !== this }?.let(covered::add)
        }
      }
      // The portals below the top modal sheet's. Portals are ordered by when their sheet opened, so
      // the last one holding a modal sheet is on top.
      val topPortal = topModalPortal()
      for (index in 0 until childCount) {
        getChildAt(index).takeIf { it !== topPortal }?.let(covered::add)
      }
    }
    hiddenViews.keys.filter { it !in covered }.forEach(::restore)
    covered.forEach(::hide)
    observeLayout(isolating)
  }

  /** The portal, a direct child of this host, holding the topmost modal sheet. */
  private fun topModalPortal(): View? =
    modalSheets
      .mapNotNull { sheet ->
        generateSequence(sheet) { it.parent as? View }.firstOrNull { it.parent === this }
      }
      .maxByOrNull(::indexOfChild)

  private fun hide(view: View) {
    if (view in hiddenViews) return
    val previous = view.importantForAccessibility
    if (previous == IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) return
    hiddenViews[view] = previous
    view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  }

  private fun restore(view: View) {
    val previous = hiddenViews.remove(view) ?: return
    // Leave a value set from JS meanwhile in place.
    if (view.importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
      view.importantForAccessibility = previous
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
