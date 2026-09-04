package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Dismissal is asserted through onIndexChange alone: the closing spring runs on a
// Choreographer that Robolectric stops driving once another view test class has run.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BottomSheetViewScrimDismissTest {
  private var nextTouchDownTime = 1_000L

  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `scrim tap dismisses an open sheet`() {
    withOpenModalSheet { sheet, listener ->
      tapScrim(sheet)

      assertEquals(listOf(0), listener.indexChanges)
    }
  }

  @Test
  fun `further scrim taps during the close snap emit no extra index change`() {
    withOpenModalSheet { sheet, listener ->
      tapScrim(sheet)
      tapScrim(sheet)
      tapScrim(sheet)

      assertEquals(listOf(0), listener.indexChanges)
    }
  }

  private fun withOpenModalSheet(block: (BottomSheetView, RecordingBottomSheetListener) -> Unit) {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    val listener = RecordingBottomSheetListener()
    val sheet = openModalSheet(activity, listener)
    try {
      block(sheet, listener)
    } finally {
      sheet.destroy()
      shadowOf(Looper.getMainLooper()).idle()
      controller.close()
    }
  }

  private fun openModalSheet(activity: Activity, listener: RecordingBottomSheetListener) =
    BottomSheetView(activity)
      .apply {
        this.listener = listener
        animateIn = false
        modal = true
        setScrimColor(Color.BLACK)
        setScrimOpacities(listOf(0f, 1f))
        setDetents(
          listOf(
            mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
            mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
          )
        )
        setIndex(1)
      }
      .also {
        activity.setContentView(it)
        layoutView(it)
      }

  /** Presses and releases above the sheet top, where the scrim is the touch target. */
  private fun tapScrim(sheet: BottomSheetView) {
    val downTime = nextTouchDownTime
    nextTouchDownTime += 100L
    val x = 10f
    val y = 100f
    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
    val up = MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_UP, x, y, 0)
    sheet.dispatchTouchEvent(down)
    sheet.dispatchTouchEvent(up)
    down.recycle()
    up.recycle()
  }

  private fun layoutView(view: View) {
    val width = 1080
    val height = 1920
    view.measure(
      View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, width, height)
    shadowOf(Looper.getMainLooper()).idle()
  }
}

private class RecordingBottomSheetListener : BottomSheetViewListener {
  val indexChanges = mutableListOf<Int>()

  override fun onIndexChange(index: Int) {
    indexChanges.add(index)
  }

  override fun onSettle(index: Int) = Unit

  override fun onPositionChange(position: Double, index: Double) = Unit

  override fun onCloseRequest() = Unit
}
