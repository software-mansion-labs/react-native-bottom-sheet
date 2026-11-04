package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.graphics.Outline
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Touches are routed to the card or the scrim by the card's rounded shape, so a touch in a corner
 * the card clips away must miss it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BottomSheetCardClipLayoutTest {
  private fun layout(radii: FloatArray): BottomSheetCardClipLayout {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    return BottomSheetCardClipLayout(activity).apply {
      setCornerRadii(radii)
      setCard(0f, 0f, 200f, 100f)
    }
  }

  @Test
  fun `unrounded card contains its whole rect`() {
    val card = layout(floatArrayOf(0f, 0f, 0f, 0f))
    assertTrue(card.cardContains(0f, 0f))
    assertTrue(card.cardContains(199f, 99f))
    assertFalse(card.cardContains(100f, 101f))
  }

  @Test
  fun `rounded corners exclude the wedge outside the arc`() {
    val card = layout(floatArrayOf(20f, 20f, 0f, 0f))
    assertFalse(card.cardContains(2f, 2f))
    assertFalse(card.cardContains(198f, 2f))
    // On the arc's inside, near the corner.
    assertTrue(card.cardContains(8f, 8f))
    assertTrue(card.cardContains(192f, 8f))
    // Sharp bottom corners.
    assertTrue(card.cardContains(0.5f, 99.5f))
    assertTrue(card.cardContains(199.5f, 99.5f))
  }

  @Test
  fun `each corner uses its own radius`() {
    val card = layout(floatArrayOf(0f, 10f, 40f, 0f))
    assertTrue(card.cardContains(1f, 1f))
    // 10 radius: (5, 5) from the top-right corner is inside, (40, 40) radius excludes it below.
    assertTrue(card.cardContains(195f, 5f))
    assertFalse(card.cardContains(195f, 95f))
  }

  @Test
  fun `radii are limited to half the card's shorter side`() {
    val card = layout(floatArrayOf(500f, 500f, 500f, 500f))
    // Limited to 50: a capsule, so the middle of the top edge is on the card.
    assertTrue(card.cardContains(100f, 1f))
    assertFalse(card.cardContains(2f, 2f))
  }

  /** An elevation shadow is cast from the outline, so it must carry the card's rounded corners. */
  @Test
  @Config(sdk = [29])
  fun `mixed radii that clip on the canvas still outline the rounded card`() {
    val card = layout(floatArrayOf(40f, 10f, 0f, 0f))
    val outline = Outline()
    card.outlineProvider.getOutline(card, outline)
    assertFalse(card.clipToOutline)
    assertFalse(outline.getRect(Rect()))
    assertFalse(outline.isEmpty)
  }

  @Test
  fun `outline limits a top-corner radius to what the card fits, like the hit test`() {
    val card = layout(floatArrayOf(80f, 80f, 0f, 0f))
    val outline = Outline()
    card.outlineProvider.getOutline(card, outline)
    assertEquals(50f, outline.radius, 0f)
    assertFalse(card.cardContains(10f, 10f))
  }

  @Test
  fun `touches in a clipped corner skip the card's children`() {
    val card = layout(floatArrayOf(20f, 20f, 0f, 0f))
    val time = SystemClock.uptimeMillis()
    val down = { x: Float, y: Float ->
      MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)
    }
    var childTouched = false
    val child = View(card.context)
    child.setOnTouchListener { _, _ ->
      childTouched = true
      true
    }
    card.addView(child)
    card.layout(0, 0, 200, 100)
    child.layout(0, 0, 200, 100)

    assertFalse(card.dispatchTouchEvent(down(2f, 2f)))
    assertFalse(childTouched)
    assertTrue(card.dispatchTouchEvent(down(100f, 50f)))
    assertTrue(childTouched)
  }
}
