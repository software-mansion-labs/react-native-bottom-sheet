package com.swmansion.reactnativebottomsheet

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** The two copies of a card's shadow must meet exactly, or they paint a darker row between them. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BottomSheetCardShadowViewTest {
  private fun shadowView(): BottomSheetCardShadowView {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    return BottomSheetCardShadowView(activity).apply { layout(0, 0, 400, 800) }
  }

  @Test
  fun `halves placed at a whole-pixel split meet in the parent`() {
    val top = shadowView()
    val bottom = shadowView()
    val split = 160f
    val offset = -479f
    top.place(800f, 0f, Float.NEGATIVE_INFINITY, split)
    bottom.place(800f, offset, split, Float.POSITIVE_INFINITY)

    val topEnd = top.clipBounds!!.bottom + top.translationY
    val bottomStart = bottom.clipBounds!!.top + bottom.translationY
    assertEquals(split, topEnd, 0f)
    assertEquals(split, bottomStart, 0f)
  }

  @Test
  fun `edge band covers the blur, spread, offset and corners`() {
    val view = shadowView()
    view.setCornerRadii(floatArrayOf(20f, 10f, 0f, 0f))
    view.setShadows(listOf(CardShadow(0f, -6f, 24f, 2f, 0)))
    assertEquals(36f + 2f + 6f + 20f + 1f, view.edgeBand, 0f)
    view.setShadows(emptyList())
    assertEquals(21f, view.edgeBand, 0f)
  }
}
