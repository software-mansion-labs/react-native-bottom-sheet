package com.swmansion.reactnativebottomsheet.host

import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fits the touched scrollables' scroll bars to the part of them on screen. The content region is
 * laid out against the tallest detent, so at a lower detent a scrollable extends past the bottom of
 * the host, and Android sizes and places its scroll bar for that whole height: at the preview the
 * bar is as long as the visible list and runs off the screen. As on iOS, the track is shortened by
 * the part below the host, and refitted on every frame the sheet moves, so a visible bar shrinks as
 * the sheet collapses and grows as it expands.
 *
 * Android has no scroll bar insets. The thumb drawable is wrapped instead, which needs Android 10;
 * below it the bar keeps its full-height size.
 *
 * Only the sheet a scrollable is in fits its bar. A scrollable that leaves the sheet draws its full
 * bar at once and gets its original thumb back on the next refit or when the sheet detaches;
 * touching it in another sheet hands the wrapper to that sheet. The scrollables are held weakly, so
 * a removed one can be collected.
 */
internal class ScrollIndicators(private val state: SheetState) {
  private val views: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

  fun register(view: View) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val current = view.verticalScrollbarThumbDrawable ?: return
    val fitted =
      current as? FittedThumb
        ?: FittedThumb(current, view).also { view.verticalScrollbarThumbDrawable = it }
    fitted.owner = this
    views.add(view)
    refit(view, fitted)
  }

  fun refit() {
    if (views.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    for (view in views.toList()) {
      val fitted = view.verticalScrollbarThumbDrawable as? FittedThumb
      when {
        // Another sheet took it over, or the app replaced the thumb.
        fitted == null || fitted.owner !== this -> views.remove(view)
        !contains(view) -> release(view, fitted)
        else -> refit(view, fitted)
      }
    }
  }

  /** Gives every scrollable this sheet fits its original thumb back. */
  fun releaseAll() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    for (view in views.toList()) {
      val fitted = view.verticalScrollbarThumbDrawable as? FittedThumb
      if (fitted != null && fitted.owner === this) release(view, fitted)
    }
    views.clear()
  }

  /** Whether the view is still inside this sheet's host. */
  fun contains(view: View): Boolean {
    var parent = view.parent
    while (parent != null) {
      if (parent === state.hostView) return true
      parent = parent.parent
    }
    return false
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun release(view: View, fitted: FittedThumb) {
    views.remove(view)
    fitted.owner = null
    view.verticalScrollbarThumbDrawable = fitted.drawable
    view.invalidate()
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun refit(view: View, thumb: FittedThumb) {
    val hidden = max(0, (bottomInHost(view) - state.hostView.height).roundToInt())
    val inverted = isInverted(view)
    if (hidden == thumb.hidden && inverted == thumb.inverted) return
    thumb.hidden = hidden
    thumb.inverted = inverted
    // The bar is only drawn with the view; moving the sheet doesn't redraw it.
    view.invalidate()
  }

  /** The view's bottom edge in the host's coordinates, with the sheet's current translation. */
  private fun bottomInHost(view: View): Float {
    var y = view.top + view.translationY + view.height
    var parent = view.parent as? View
    while (parent != null && parent !== state.hostView) {
      y += parent.top + parent.translationY - parent.scrollY
      parent = parent.parent as? View
    }
    return y
  }

  /**
   * Whether the view draws upside down, like an inverted list, so its local top is at the bottom.
   */
  private fun isInverted(view: View): Boolean {
    val values = FloatArray(9)
    var inverted = false
    var current: View? = view
    while (current != null && current !== state.hostView) {
      if (!current.matrix.isIdentity) {
        current.matrix.getValues(values)
        if (values[Matrix.MSCALE_Y] < 0) inverted = !inverted
      }
      current = current.parent as? View
    }
    return inverted
  }
}

/**
 * A scroll bar thumb drawn on the visible part of the track. UIKit-like: the track ends where the
 * view leaves the host, the thumb's length is the visible share of the content, and its position
 * keeps the scroll fraction Android computed for the full track. In an inverted view the hidden
 * part is at the local top, so the track starts below it.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private class FittedThumb(thumb: Drawable, view: View) : DrawableWrapper(thumb) {
  private val view = WeakReference(view)
  var owner: ScrollIndicators? = null
  var hidden = 0
  var inverted = false

  override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
    val view = view.get()
    // A view that has left its sheet draws the full bar until the thumb is given back.
    if (view == null || hidden <= 0 || owner?.contains(view) != true) {
      super.setBounds(left, top, right, bottom)
      return
    }
    val trackTop = view.scrollY + view.paddingTop
    val trackLength = view.height - view.paddingTop - view.paddingBottom
    val visibleLength = trackLength - hidden
    if (visibleLength <= 0 || trackLength <= 0) {
      super.setBounds(left, top, right, bottom)
      return
    }
    val thumbLength = bottom - top
    val travel = trackLength - thumbLength
    val fraction = if (travel > 0) ((top - trackTop).toFloat() / travel).coerceIn(0f, 1f) else 0f
    // The full-height thumb is the view's share of the content; at the visible height that share
    // shrinks with the visible part, and the track it sits on shrinks too.
    val share = thumbLength.toFloat() / trackLength * (view.height - hidden) / view.height
    val minLength = 2 * (right - left)
    val fittedLength =
      max(minLength, (visibleLength * share).roundToInt()).coerceAtMost(visibleLength)
    val visibleTop = if (inverted) trackTop + hidden else trackTop
    val fittedTop = visibleTop + ((visibleLength - fittedLength) * fraction).roundToInt()
    super.setBounds(left, fittedTop, right, fittedTop + fittedLength)
  }
}
