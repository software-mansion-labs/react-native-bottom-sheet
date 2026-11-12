package com.swmansion.reactnativebottomsheet.host

import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
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
 */
internal class ScrollIndicators(private val state: SheetState) {
  private val fits = WeakHashMap<View, FittedThumb>()

  fun register(view: View) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || fits.containsKey(view)) return
    val thumb = view.verticalScrollbarThumbDrawable ?: return
    val fitted = thumb as? FittedThumb ?: FittedThumb(thumb, view)
    view.verticalScrollbarThumbDrawable = fitted
    fits[view] = fitted
    refit(view, fitted)
  }

  fun refit() {
    if (fits.isEmpty()) return
    for ((view, thumb) in fits.entries.toList()) {
      refit(view, thumb)
    }
  }

  private fun refit(view: View, thumb: FittedThumb) {
    val hidden = max(0, (bottomInHost(view) - state.hostView.height).roundToInt())
    if (hidden == thumb.hidden) return
    thumb.hidden = hidden
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
}

/**
 * A scroll bar thumb drawn on the visible part of the track. UIKit-like: the track ends where the
 * view leaves the host, the thumb's length is the visible share of the content, and its position
 * keeps the scroll fraction Android computed for the full track.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private class FittedThumb(thumb: Drawable, private val view: View) : DrawableWrapper(thumb) {
  var hidden = 0

  override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
    val trackTop = view.scrollY + view.paddingTop
    val trackLength = view.height - view.paddingTop - view.paddingBottom
    val visibleLength = trackLength - hidden
    if (hidden <= 0 || visibleLength <= 0 || trackLength <= 0) {
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
    val fittedTop = trackTop + ((visibleLength - fittedLength) * fraction).roundToInt()
    super.setBounds(left, fittedTop, right, fittedTop + fittedLength)
  }
}
