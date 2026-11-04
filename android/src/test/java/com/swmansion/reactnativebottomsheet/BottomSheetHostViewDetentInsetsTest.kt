package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.TouchTargetHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
      val middle = 150 * d
      assertFalse(sheet.clip.cardContains(15 * d, middle))
      assertTrue(sheet.clip.cardContains(17 * d, middle))
      assertTrue(sheet.clip.cardContains(WIDTH_PX - 9 * d, middle))
      assertFalse(sheet.clip.cardContains(WIDTH_PX - 7 * d, middle))
      // The card's bottom in the container: its height, clear of the gap.
      assertTrue(sheet.clip.cardContains(WIDTH_PX / 2f, 299 * d))
      assertFalse(sheet.clip.cardContains(WIDTH_PX / 2f, 301 * d))
    }
  }

  @Test
  fun `a full height detent fits between its top and bottom insets`() {
    withSheet(listOf(points(0.0), percentage(1.0, top = 24.0, bottom = 32.0))) { sheet ->
      val d = sheet.density
      assertEquals(24 * d, sheet.container.translationY, 0.5f)
      assertTrue(sheet.clip.cardContains(WIDTH_PX / 2f, HEIGHT_PX - 57 * d))
      assertFalse(sheet.clip.cardContains(WIDTH_PX / 2f, HEIGHT_PX - 55 * d))
    }
  }

  @Test
  fun `a full height detent reaches the top with a top inset of zero`() {
    withSheet(listOf(points(0.0), percentage(1.0, top = 0.0, bottom = 32.0))) { sheet ->
      assertEquals(0f, sheet.container.translationY, 0.5f)
    }
  }

  @Test
  fun `an unset top inset is the status bar's overlap`() {
    // Nothing overlaps an unattached host, so the detent reaches its top.
    withSheet(listOf(points(0.0), percentage(1.0, top = null))) { sheet ->
      assertEquals(0f, sheet.container.translationY, 0.5f)
    }
  }

  @Test
  fun `only a sheet that clips rejects touches off the card`() {
    withSheet(listOf(points(0.0), points(300.0))) { sheet ->
      assertFalse(sheet.clip.clipsContent)
    }
    withSheet(listOf(points(0.0), points(300.0, left = 8.0))) { sheet ->
      assertTrue(sheet.clip.clipsContent)
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
  fun `the content is laid out at the widest card's left inset`() {
    val detents = listOf(points(100.0, left = 24.0), points(300.0, left = 8.0, widest = true))
    withSheet(detents, index = 1) { sheet ->
      assertEquals((8 * sheet.density).toInt(), sheet.getSheetChildAt(0)!!.left)
    }
  }

  @Test
  fun `the content scales to the card and pins to its left edge`() {
    val detents =
      listOf(
        points(100.0, left = 40.0, right = 40.0),
        points(300.0, left = 8.0, right = 8.0, widest = true),
      )
    withSheet(detents, index = 0) { sheet ->
      val d = sheet.density
      val content = sheet.getSheetChildAt(0)!!
      val expected = (WIDTH_PX - 80 * d) / (WIDTH_PX - 16 * d)
      assertEquals(expected, content.scaleX, 0.001f)
      assertEquals(expected, content.scaleY, 0.001f)
      assertEquals(40 * d, content.left + content.translationX, 0.5f)
    }
  }

  @Test
  fun `the content is unscaled at the widest card`() {
    val detents =
      listOf(
        points(100.0, left = 40.0, right = 40.0),
        points(300.0, left = 8.0, right = 8.0, widest = true),
      )
    withSheet(detents, index = 1) { sheet ->
      val content = sheet.getSheetChildAt(0)!!
      assertEquals(1f, content.scaleX, 0.001f)
      assertEquals(8 * sheet.density, content.left + content.translationX, 0.5f)
    }
  }

  @Test
  fun `the content follows the detent flagged as widest`() {
    // Native doesn't pick the widest card itself: JS flags the one it lays the content out against.
    val detents =
      listOf(points(100.0, left = 8.0), points(300.0, left = 24.0, right = 24.0, widest = true))
    withSheet(detents, index = 1) { sheet ->
      val content = sheet.getSheetChildAt(0)!!
      assertEquals((24 * sheet.density).toInt(), content.left)
      assertEquals(1f, content.scaleX, 0.001f)
    }
  }

  @Test
  fun `React Native targets the card but not the gaps around it`() {
    val detents = listOf(points(0.0), points(300.0, left = 16.0, right = 16.0, bottom = 32.0))
    withSheet(detents, shadows = true) { sheet ->
      val d = sheet.density
      val parent = FrameLayout(sheet.context).apply { id = PARENT_TAG }
      (sheet.parent as ViewGroup).removeView(sheet)
      sheet.id = SHEET_TAG
      parent.addView(sheet, ViewGroup.LayoutParams(WIDTH_PX, HEIGHT_PX))
      parent.layout(0, 0, WIDTH_PX, HEIGHT_PX)
      val cardTop = HEIGHT_PX - 332 * d
      val target = { x: Float, y: Float ->
        TouchTargetHelper.findTargetTagForTouch(x, y, parent)
      }

      assertEquals(SHEET_TAG, target(WIDTH_PX / 2f, cardTop + 150 * d))
      assertEquals("bottom gap", PARENT_TAG, target(WIDTH_PX / 2f, HEIGHT_PX - 16 * d))
      assertEquals("left gap", PARENT_TAG, target(8 * d, cardTop + 150 * d))
      assertEquals("right gap", PARENT_TAG, target(WIDTH_PX - 8 * d, cardTop + 150 * d))
      assertEquals("above the card", PARENT_TAG, target(WIDTH_PX / 2f, cardTop - 16 * d))
    }
  }

  @Test
  fun `React Native targets the card's rounded shape but not its clipped corners`() {
    val detents = listOf(points(0.0), points(300.0, left = 16.0, right = 16.0, bottom = 32.0))
    withSheet(detents, shadows = true, radius = 28f) { sheet ->
      val d = sheet.density
      val parent = FrameLayout(sheet.context).apply { id = PARENT_TAG }
      (sheet.parent as ViewGroup).removeView(sheet)
      sheet.id = SHEET_TAG
      parent.addView(sheet, ViewGroup.LayoutParams(WIDTH_PX, HEIGHT_PX))
      parent.layout(0, 0, WIDTH_PX, HEIGHT_PX)
      val cardBottom = HEIGHT_PX - 32 * d
      val target = { x: Float, y: Float ->
        TouchTargetHelper.findTargetTagForTouch(x, y, parent)
      }

      assertEquals("clipped corner", PARENT_TAG, target(18 * d, cardBottom - 2 * d))
      assertEquals("on the curve's inside", SHEET_TAG, target(32 * d, cardBottom - 16 * d))
      assertEquals(
        "clipped corner again",
        PARENT_TAG,
        target(WIDTH_PX - 18 * d, cardBottom - 2 * d),
      )
      assertEquals("middle of the card", SHEET_TAG, target(WIDTH_PX / 2f, cardBottom - 100 * d))
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
    widest: Boolean = false,
  ) = detent(value, "points", top, left, right, bottom, widest)

  private fun percentage(ratio: Double, top: Double? = 0.0, bottom: Double = 0.0) =
    detent(ratio, "percentage", top, 0.0, 0.0, bottom, widest = false)

  private fun detent(
    value: Double,
    kind: String,
    top: Double?,
    left: Double,
    right: Double,
    bottom: Double,
    widest: Boolean,
  ): Map<String, Any> =
    mapOf(
      "value" to value,
      "kind" to kind,
      "usesStatusBarTopInset" to (top == null),
      "insetTop" to (top ?: 0.0),
      "insetLeft" to left,
      "insetRight" to right,
      "insetBottom" to bottom,
      "widestCard" to widest,
    )

  private fun withSheet(
    detents: List<Map<String, Any>>,
    index: Int = 1,
    shadows: Boolean = false,
    radius: Float = 0f,
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
      sheet.setSurfaceCornerRadii(List(4) { radius })
      if (shadows) sheet.setSurfaceShadows(listOf(CardShadow(0f, 4f, 24f, 0f, Color.BLACK)))
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
    const val PARENT_TAG = 1
    const val SHEET_TAG = 2
  }
}
