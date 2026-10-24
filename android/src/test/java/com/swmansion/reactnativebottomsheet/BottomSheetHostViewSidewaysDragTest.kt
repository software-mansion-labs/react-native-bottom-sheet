package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val WIDTH_PX = 1080
private const val HEIGHT_PX = 1920

// A vertical ScrollView intercepts any move past the touch slop regardless of
// its sideways component, so the sheet, which intercepts first, must claim the
// same moves inside one or the scrollable takes a shallow diagonal the sheet
// owns below its largest detent.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestArgumentsShadow::class])
class BottomSheetHostViewSidewaysDragTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `a shallow diagonal drag inside a vertical scrollable is intercepted for the sheet`() {
    withSheet { sheet, content ->
      content.addView(tallScrollView(sheet.context))
      layout(sheet, content)

      assertTrue(sheet.interceptsDrag(dx = 120f, dy = 60f))
    }
  }

  @Test
  fun `a vertical drag inside a vertical scrollable is intercepted for the sheet`() {
    withSheet { sheet, content ->
      content.addView(tallScrollView(sheet.context))
      layout(sheet, content)

      assertTrue(sheet.interceptsDrag(dx = 0f, dy = 60f))
    }
  }

  @Test
  fun `a shallow diagonal drag through a horizontal scroller is left to it`() {
    withSheet { sheet, content ->
      val list = tallScrollView(sheet.context)
      // A wide carousel row at the top of the list, under the touch.
      (list.getChildAt(0) as ViewGroup).addView(
        HorizontalScrollView(sheet.context).apply {
          // A scroll view measures its child unbounded, which a plain View
          // answers with its minimum size.
          addView(
            View(context).apply {
              minimumWidth = WIDTH_PX * 3
              minimumHeight = 300
            },
            ViewGroup.LayoutParams(WIDTH_PX * 3, 300),
          )
        },
        0,
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600),
      )
      content.addView(list)
      layout(sheet, content)

      assertFalse(sheet.interceptsDrag(dx = 120f, dy = 60f))
    }
  }

  @Test
  fun `a shallow diagonal drag in a list inside a horizontal pager is left to the pager`() {
    withSheet { sheet, content ->
      content.addView(
        HorizontalScrollView(sheet.context).apply {
          addView(
            LinearLayout(context).apply {
              orientation = LinearLayout.HORIZONTAL
              addView(
                tallScrollView(context),
                ViewGroup.LayoutParams(WIDTH_PX, ViewGroup.LayoutParams.MATCH_PARENT),
              )
              addView(
                View(context),
                ViewGroup.LayoutParams(WIDTH_PX, ViewGroup.LayoutParams.MATCH_PARENT),
              )
            },
          )
        },
      )
      layout(sheet, content)

      assertFalse(sheet.interceptsDrag(dx = 120f, dy = 60f))
    }
  }

  @Test
  fun `a shallow diagonal drag outside a scrollable is not intercepted`() {
    withSheet { sheet, content ->
      content.addView(View(sheet.context))
      layout(sheet, content)

      assertFalse(sheet.interceptsDrag(dx = 120f, dy = 60f))
    }
  }

  // Presses on the sheet 100dp above the bottom edge, then moves once by (dx, dy)
  // px and reports whether the sheet intercepts that move.
  private fun BottomSheetHostView.interceptsDrag(dx: Float, dy: Float): Boolean {
    val x = WIDTH_PX / 2f
    val y = HEIGHT_PX - 100f * resources.displayMetrics.density
    val downTime = 1_000L
    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
    val move =
      MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, x + dx, y + dy, 0)
    try {
      onInterceptTouchEvent(down)
      return onInterceptTouchEvent(move)
    } finally {
      down.recycle()
      move.recycle()
    }
  }

  private fun tallScrollView(context: Context) =
    ScrollView(context).apply {
      addView(
        LinearLayout(context).apply {
          orientation = LinearLayout.VERTICAL
          addView(View(context), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 5000))
        },
        ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
      )
    }

  private fun withSheet(block: (BottomSheetHostView, FrameLayout) -> Unit) {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val activity = controller.get()
    val sheet = BottomSheetHostView(activity)
    try {
      sheet.animateIn = false
      sheet.scrollableExpandNegotiation = 1
      sheet.scrollableCollapseNegotiation = 1
      sheet.setDetents(listOf(points(0.0), points(300.0), points(600.0)))
      sheet.setIndex(1)
      // Stands in for the Fabric-measured content wrapper; the host places it.
      val content = FrameLayout(activity)
      sheet.addSheetChild(content, 0)
      activity.setContentView(sheet, ViewGroup.LayoutParams(WIDTH_PX, HEIGHT_PX))
      layout(sheet, content)
      block(sheet, content)
    } finally {
      sheet.destroy()
      controller.close()
    }
  }

  private fun layout(sheet: BottomSheetHostView, content: FrameLayout) {
    val density = sheet.resources.displayMetrics.density
    content.measure(
      View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec((600 * density).toInt(), View.MeasureSpec.EXACTLY),
    )
    sheet.measure(
      View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY),
    )
    sheet.layout(0, 0, WIDTH_PX, HEIGHT_PX)
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun points(value: Double): Map<String, Any> = mapOf("value" to value, "kind" to "points")
}
