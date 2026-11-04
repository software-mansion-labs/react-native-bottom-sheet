package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.uimanager.ReactCompoundView
import com.facebook.react.uimanager.ReactPointerEventsView

/*
 * React Native picks a JS touch's target by walking the view tree against rectangular bounds, before
 * any view sees the touch. Left alone, a press in a corner the card clips away targets whatever is
 * hidden there, and never what shows through the corner.
 *
 * The walk has no shape hook, and `pointerEvents` is read without the touch's position. It does pass
 * the position to `ReactCompoundView.reactTagForTouch`, though, and it visits a view's children from
 * the top down. So the host keeps this sensor as its top child: the walk tells the sensor where the
 * touch is, the sensor declines to be the target, and the walk moves on to the sheet container,
 * which now knows whether to step aside.
 */

/** Learns where a touch is during React Native's target walk, without ever being the target. */
internal class BottomSheetCardTouchSensor(
  context: Context,
  private val onTouchTargeting: (x: Float, y: Float) -> Unit,
) : View(context), ReactCompoundView, ReactPointerEventsView {
  override val pointerEvents: PointerEvents = PointerEvents.BOX_NONE

  init {
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
  }

  /** The walk ignores a `box-none` view that returns its own tag, and carries on. */
  override fun reactTagForTouch(touchX: Float, touchY: Float): Int {
    onTouchTargeting(touchX, touchY)
    return id
  }
}

/** The sheet container, which can leave the walk for a touch off the card. */
internal class BottomSheetCardContainer(context: Context) :
  FrameLayout(context), ReactPointerEventsView {
  var skipsTouchTargeting = false

  override val pointerEvents: PointerEvents
    get() = if (skipsTouchTargeting) PointerEvents.NONE else PointerEvents.AUTO
}
