package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val WIDTH_PX = 1080
private const val HEIGHT_PX = 1920
private const val TOUCH_STEP_MS = 16L

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestArgumentsShadow::class])
class BottomSheetHostViewSettleDurationTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `a long settle carries the release briefly past the largest detent, as on iOS`() {
    withSheet(listOf(0.0, 300.0, 600.0)) { sheet, listener ->
      sheet.settleDuration = 4f

      sheet.drag(-180f to 360L)
      // Robolectric doesn't advance SystemClock with the frames, so the 4s deadline never fires
      // here; idle long enough for the spring to come to rest on its own.
      idleFor(10_000L)

      assertEquals(listOf(2), listener.settles)
      assertTrue(listener.peakPosition > 610.0)
    }
  }

  // Presses 100dp above the bottom edge, then moves through (distance px, duration ms) segments
  // and lifts where the last one ends.
  private fun BottomSheetHostView.drag(vararg segments: Pair<Float, Long>) {
    val x = WIDTH_PX / 2f
    var y = HEIGHT_PX - 100f * resources.displayMetrics.density
    val downTime = 1_000L
    var time = downTime
    dispatch(downTime, time, MotionEvent.ACTION_DOWN, x, y)
    for ((distance, duration) in segments) {
      val steps = (duration / TOUCH_STEP_MS).toInt()
      repeat(steps) {
        y += distance / steps
        time += TOUCH_STEP_MS
        dispatch(downTime, time, MotionEvent.ACTION_MOVE, x, y)
      }
    }
    dispatch(downTime, time + TOUCH_STEP_MS, MotionEvent.ACTION_UP, x, y)
  }

  private fun BottomSheetHostView.dispatch(
    downTime: Long,
    time: Long,
    action: Int,
    x: Float,
    y: Float,
  ) {
    val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
    try {
      dispatchTouchEvent(event)
    } finally {
      event.recycle()
    }
  }

  private fun idleFor(millis: Long) {
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
  }

  private fun withSheet(
    detents: List<Double>,
    block: (BottomSheetHostView, SettleRecordingListener) -> Unit,
  ) {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val activity = controller.get()
    val sheet = BottomSheetHostView(activity)
    val listener = SettleRecordingListener()
    try {
      sheet.animateIn = false
      sheet.setDetents(detents.map { mapOf("value" to it, "kind" to "points") })
      sheet.setIndex(if (detents.size > 2) 1 else 0)
      val content = FrameLayout(activity)
      sheet.addSheetChild(content, 0)
      activity.setContentView(sheet, ViewGroup.LayoutParams(WIDTH_PX, HEIGHT_PX))
      layout(sheet, content)
      sheet.listener = listener
      listener.clear()
      block(sheet, listener)
    } finally {
      sheet.destroy()
      // Drain the cancelled spring's frame before Robolectric resets the looper, or a later test's
      // spring can wait forever for the discarded callback.
      shadowOf(Looper.getMainLooper()).idle()
      controller.close()
    }
  }

  private fun layout(sheet: BottomSheetHostView, content: FrameLayout) {
    content.measure(
      View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY),
    )
    sheet.measure(
      View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY),
    )
    sheet.layout(0, 0, WIDTH_PX, HEIGHT_PX)
    shadowOf(Looper.getMainLooper()).idle()
  }
}

private class SettleRecordingListener : BottomSheetViewListener {
  val indexChanges = mutableListOf<Int>()
  val settles = mutableListOf<Int>()
  var peakPosition = 0.0

  fun clear() {
    indexChanges.clear()
    settles.clear()
    peakPosition = 0.0
  }

  override fun onIndexChange(index: Int) {
    indexChanges.add(index)
  }

  override fun onSettle(index: Int) {
    settles.add(index)
  }

  override fun onPositionChange(position: Double, index: Double) {
    peakPosition = maxOf(peakPosition, position)
  }

  override fun onCloseRequest() = Unit
}
