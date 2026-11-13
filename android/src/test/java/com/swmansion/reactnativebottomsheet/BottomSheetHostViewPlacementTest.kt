package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.modules.i18nmanager.I18nUtil
import com.facebook.react.uimanager.TouchTargetHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * `maxWidth` caps the card's width and `placement` (sent as a physical bias: 0 left, 0.5 center, 1
 * right) puts it across the host. The side insets stay the least gap, and the content follows the
 * card from where Yoga laid it out.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestArgumentsShadow::class])
class BottomSheetHostViewPlacementTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `a centered card has its middle on the host's`() {
    withSheet(listOf(points(0.0), points(300.0)), maxWidth = 600.0, bias = 0.5) { sheet ->
      val d = sheet.density
      val left = (WIDTH_PX - 600 * d) / 2f
      assertCardSpan(sheet, left, left + 600 * d)
    }
  }

  @Test
  fun `a leading card sits at the left edge`() {
    withSheet(listOf(points(0.0), points(300.0)), maxWidth = 600.0, bias = 0.0) { sheet ->
      assertCardSpan(sheet, 0f, 600 * sheet.density)
    }
  }

  @Test
  fun `a trailing card sits at the right edge`() {
    withSheet(listOf(points(0.0), points(300.0)), maxWidth = 600.0, bias = 1.0) { sheet ->
      assertCardSpan(sheet, WIDTH_PX - 600 * sheet.density, WIDTH_PX.toFloat())
    }
  }

  @Test
  fun `a placed card keeps its side insets`() {
    val detents = listOf(points(0.0), points(300.0, left = 24.0, right = 16.0))
    withSheet(detents, maxWidth = 600.0, bias = 0.0) { sheet ->
      val d = sheet.density
      assertCardSpan(sheet, 24 * d, 624 * d)
    }
    withSheet(detents, maxWidth = 600.0, bias = 1.0) { sheet ->
      val d = sheet.density
      assertCardSpan(sheet, WIDTH_PX - 616 * sheet.density, WIDTH_PX - 16 * d)
    }
  }

  @Test
  fun `a centered card moves off center only as far as an inset requires`() {
    // Centered, it would start inside the left inset.
    withSheet(
      listOf(points(0.0), points(300.0, left = WIDTH_DP * 0.4)),
      maxWidth = WIDTH_DP * 0.5,
      bias = 0.5,
    ) { sheet ->
      assertCardSpan(sheet, WIDTH_PX * 0.4f, WIDTH_PX * 0.9f)
    }
  }

  @Test
  fun `a maximum width wider than the host leaves the card full width`() {
    withSheet(listOf(points(0.0), points(300.0)), maxWidth = WIDTH_DP * 2, bias = 1.0) { sheet ->
      assertCardSpan(sheet, 0f, WIDTH_PX.toFloat())
      assertEquals(1f, sheet.getSheetChildAt(0)!!.scaleX, 0.001f)
    }
  }

  @Test
  fun `the content is unscaled and on the card at the widest card`() {
    withSheet(
      listOf(points(0.0), points(300.0, widest = true)),
      maxWidth = 600.0,
      bias = 0.5,
      contentWidthDp = 600.0,
    ) { sheet ->
      val d = sheet.density
      val content = sheet.getSheetChildAt(0)!!
      assertEquals(1f, content.scaleX, 0.001f)
      assertEquals((WIDTH_PX - 600 * d) / 2f, content.left + content.translationX, 0.5f)
    }
  }

  @Test
  fun `the content scales to a card the insets make narrower than the maximum width`() {
    // The widest card is capped at maxWidth; the other one is narrower still.
    val maxWidth = WIDTH_DP - 20
    val detents = listOf(points(100.0, left = 40.0, right = 40.0), points(300.0, widest = true))
    withSheet(detents, index = 0, maxWidth = maxWidth, bias = 0.5, contentWidthDp = maxWidth) {
      sheet ->
      val d = sheet.density
      val content = sheet.getSheetChildAt(0)!!
      val narrow = WIDTH_PX - 80 * d
      assertEquals(narrow / (maxWidth.toFloat() * d), content.scaleX, 0.001f)
      assertEquals(40 * d, content.left + content.translationX, 0.5f)
      assertCardSpan(sheet, 40 * d, WIDTH_PX - 40 * d)
    }
  }

  @Test
  fun `in a right-to-left layout the content starts at the right, as Yoga lays it out`() {
    val context = RuntimeEnvironment.getApplication()
    val flags = context.applicationInfo.flags
    // React Native lays out right to left only in an app that declares RTL support.
    context.applicationInfo.flags = flags or ApplicationInfo.FLAG_SUPPORTS_RTL
    I18nUtil.instance.forceRTL(context, true)
    try {
      withSheet(
        listOf(points(0.0), points(300.0, left = 8.0, right = 24.0, widest = true)),
        maxWidth = 400.0,
        bias = 1.0,
        contentWidthDp = 400.0,
      ) { sheet ->
        val d = sheet.density
        val content = sheet.getSheetChildAt(0)!!
        // Yoga puts the capped box at the start inset, which is the right one.
        assertEquals((WIDTH_PX - 24 * d - 400 * d).toInt(), content.left)
        // The card, placed at the right (leading in RTL), carries it along.
        assertCardSpan(sheet, WIDTH_PX - 424 * d, WIDTH_PX - 24 * d)
        assertEquals(WIDTH_PX - 424 * d, content.left + content.translationX, 0.5f)
      }
    } finally {
      I18nUtil.instance.forceRTL(context, false)
      context.applicationInfo.flags = flags
    }
  }

  @Test
  fun `React Native targets the card but not the space beside it`() {
    withSheet(listOf(points(0.0), points(300.0)), maxWidth = 600.0, bias = 0.5) { sheet ->
      val d = sheet.density
      val parent = FrameLayout(sheet.context).apply { id = PARENT_TAG }
      (sheet.parent as ViewGroup).removeView(sheet)
      sheet.id = SHEET_TAG
      parent.addView(sheet, ViewGroup.LayoutParams(WIDTH_PX, HEIGHT_PX))
      parent.layout(0, 0, WIDTH_PX, HEIGHT_PX)
      val middleY = HEIGHT_PX - 150 * d
      val left = (WIDTH_PX - 600 * d) / 2f
      val target = { x: Float -> TouchTargetHelper.findTargetTagForTouch(x, middleY, parent) }

      assertEquals(SHEET_TAG, target(WIDTH_PX / 2f))
      assertEquals("left of the card", PARENT_TAG, target(left - 8 * d))
      assertEquals("right of the card", PARENT_TAG, target(left + 608 * d))
    }
  }

  @Test
  fun `a maximum width applies at every detent`() {
    val detents = listOf(points(0.0), points(200.0), points(400.0, left = 16.0, right = 16.0))
    withSheet(detents, index = 1, maxWidth = 500.0, bias = 0.0) { sheet ->
      assertCardSpan(sheet, 0f, 500 * sheet.density)
    }
    withSheet(detents, index = 2, maxWidth = 500.0, bias = 0.0) { sheet ->
      val d = sheet.density
      assertCardSpan(sheet, 16 * d, 516 * d)
    }
  }

  @Test
  fun `side insets that fill the host's measured width are rejected`() {
    val half = WIDTH_DP / 2
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        withSheet(
          listOf(points(0.0), points(300.0, left = half, right = half)),
          maxWidth = 0.0,
          bias = 0.5,
        ) {}
      }
    assertTrue(error.message!!.contains("leaves no room for the sheet"))
  }

  @Test
  fun `side insets just inside the host's measured width leave a sliver of card`() {
    val half = WIDTH_DP / 2
    withSheet(
      listOf(points(0.0), points(300.0, left = half - 2, right = half)),
      maxWidth = 0.0,
      bias = 0.5,
    ) { sheet ->
      assertCardSpan(sheet, WIDTH_PX / 2f - 2 * sheet.density, WIDTH_PX / 2f)
    }
  }

  /** Checks the card's edges, in px, half a pixel inside and out, at the card's middle height. */
  private fun assertCardSpan(sheet: BottomSheetHostView, left: Float, right: Float) {
    val middle = 50 * sheet.density
    if (left >= 1f) assertFalse("left of $left", sheet.clip.cardContains(left - 1f, middle))
    assertTrue("right of $left", sheet.clip.cardContains(left + 1f, middle))
    assertTrue("left of $right", sheet.clip.cardContains(right - 1f, middle))
    if (right <= WIDTH_PX - 1f) {
      assertFalse("right of $right", sheet.clip.cardContains(right + 1f, middle))
    }
  }

  private val BottomSheetHostView.container: ViewGroup
    get() = getChildAt(0) as ViewGroup

  private val BottomSheetHostView.clip: BottomSheetCardClipLayout
    get() = container.getChildAt(container.childCount - 1) as BottomSheetCardClipLayout

  private val BottomSheetHostView.density: Float
    get() = resources.displayMetrics.density

  private val WIDTH_DP: Double
    get() =
      WIDTH_PX / RuntimeEnvironment.getApplication().resources.displayMetrics.density.toDouble()

  private fun points(
    value: Double,
    left: Double = 0.0,
    right: Double = 0.0,
    widest: Boolean = false,
  ): Map<String, Any> =
    mapOf(
      "value" to value,
      "kind" to "points",
      "usesStatusBarTopInset" to false,
      "insetTop" to 0.0,
      "insetLeft" to left,
      "insetRight" to right,
      "insetBottom" to 0.0,
      "widestCard" to widest,
    )

  private fun withSheet(
    detents: List<Map<String, Any>>,
    index: Int = 1,
    maxWidth: Double,
    bias: Double,
    contentWidthDp: Double? = null,
    block: (BottomSheetHostView) -> Unit,
  ) {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val sheet = BottomSheetHostView(controller.get())
    try {
      sheet.animateIn = false
      val density = sheet.resources.displayMetrics.density
      // As Yoga would size it: the widest card's width, or a stand-in when that doesn't matter.
      val contentWidthPx = contentWidthDp?.let { (it * density).toInt() } ?: 100
      sheet.addSheetChild(
        View(controller.get()).apply {
          measure(
            View.MeasureSpec.makeMeasureSpec(contentWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
          )
        },
        0,
      )
      sheet.setDetents(
        detents.map { it + mapOf("maxWidth" to maxWidth, "placementBias" to bias) },
      )
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
