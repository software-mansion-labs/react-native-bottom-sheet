package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A detent's value is the card's height and its position adds the bottom inset, so insets keep
 * `100%` inside the space left, the card clear of the gaps, and the content's width fixed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestArgumentsShadow::class])
class BottomSheetHostViewDetentInsetsTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `a detent rests at its height plus its bottom inset`() {
    withSheet(listOf(points(0.0), points(300.0, left = 16.0, right = 8.0, bottom = 32.0))) { sheet
      ->
      val d = sheet.density
      assertEquals(HEIGHT_PX - 332 * d, sheet.container.translationY, 0.5f)
      assertEquals(-16 * d, sheet.clip.hitSlopRect.left.toFloat(), 1f)
      assertEquals(-8 * d, sheet.clip.hitSlopRect.right.toFloat(), 1f)
      // The card's bottom in the container: its height, clear of the gap.
      assertEquals(300 * d - HEIGHT_PX, sheet.clip.hitSlopRect.bottom.toFloat(), 1f)
    }
  }

  @Test
  fun `a full height detent fits between its top and bottom insets`() {
    withSheet(listOf(points(0.0), percentage(1.0, top = 24.0, bottom = 32.0))) { sheet ->
      val d = sheet.density
      assertEquals(24 * d, sheet.container.translationY, 0.5f)
      assertEquals(-56 * d, sheet.clip.hitSlopRect.bottom.toFloat(), 1f)
    }
  }

  @Test
  fun `percentages resolve against the space the insets leave`() {
    withSheet(listOf(points(0.0), percentage(0.5, top = 20.0, bottom = 40.0))) { sheet ->
      val d = sheet.density
      val height = (HEIGHT_PX - 60 * d) * 0.5f
      assertEquals(HEIGHT_PX - height - 40 * d, sheet.container.translationY, 0.5f)
    }
  }

  @Test
  fun `a fixed detent above the space left is capped to it`() {
    withSheet(listOf(points(0.0), points(HEIGHT_PX * 2.0, top = 24.0, bottom = 32.0))) { sheet ->
      assertEquals(24 * sheet.density, sheet.container.translationY, 0.5f)
    }
  }

  @Test
  fun `a closed detent stays fully off screen`() {
    withSheet(listOf(points(0.0), points(300.0, bottom = 32.0)), index = 0) { sheet ->
      assertEquals(HEIGHT_PX.toFloat(), sheet.container.translationY, 0.5f)
    }
  }

  @Test
  fun `detents ascend by position, not by card height`() {
    // A shorter card is fine while its top rises: 320 to 342.
    withSheet(listOf(points(320.0), points(310.0, bottom = 32.0))) {}
    // A taller card is not while its top drops: 332 to 320.
    assertThrows(IllegalArgumentException::class.java) {
      withSheet(listOf(points(300.0, bottom = 32.0), points(320.0))) {}
    }
  }

  @Test
  fun `the content is placed at the largest left inset`() {
    val detents = listOf(points(100.0, left = 8.0), points(300.0, left = 24.0), points(400.0))
    withSheet(detents, index = 2) { sheet ->
      assertEquals((24 * sheet.density).toInt(), sheet.getSheetChildAt(0)!!.left)
    }
  }

  private val BottomSheetHostView.container: ViewGroup
    get() = getChildAt(0) as ViewGroup

  private val BottomSheetHostView.clip: BottomSheetCardClipLayout
    get() = container.getChildAt(container.childCount - 1) as BottomSheetCardClipLayout

  private val BottomSheetHostView.density: Float
    get() = resources.displayMetrics.density

  private fun points(
    value: Double,
    top: Double = 0.0,
    left: Double = 0.0,
    right: Double = 0.0,
    bottom: Double = 0.0,
  ) = detent(value, "points", top, left, right, bottom)

  private fun percentage(ratio: Double, top: Double = 0.0, bottom: Double = 0.0) =
    detent(ratio, "percentage", top, 0.0, 0.0, bottom)

  private fun detent(
    value: Double,
    kind: String,
    top: Double,
    left: Double,
    right: Double,
    bottom: Double,
  ): Map<String, Any> =
    mapOf(
      "value" to value,
      "kind" to kind,
      "insetTop" to top,
      "insetLeft" to left,
      "insetRight" to right,
      "insetBottom" to bottom,
    )

  private fun withSheet(
    detents: List<Map<String, Any>>,
    index: Int = 1,
    block: (BottomSheetHostView) -> Unit,
  ) {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val sheet = BottomSheetHostView(controller.get())
    try {
      sheet.animateIn = false
      sheet.addSheetChild(
        View(controller.get()).apply {
          measure(
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
          )
        },
        0,
      )
      sheet.setDetents(detents)
      sheet.setIndex(index)
      controller.get().setContentView(sheet, ViewGroup.LayoutParams(WIDTH_PX, HEIGHT_PX))
      sheet.measure(
        View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY),
      )
      sheet.layout(0, 0, WIDTH_PX, HEIGHT_PX)
      shadowOf(Looper.getMainLooper()).idle()
      block(sheet)
    } finally {
      sheet.destroy()
      controller.close()
    }
  }

  private companion object {
    const val WIDTH_PX = 1080
    const val HEIGHT_PX = 1920
  }
}
