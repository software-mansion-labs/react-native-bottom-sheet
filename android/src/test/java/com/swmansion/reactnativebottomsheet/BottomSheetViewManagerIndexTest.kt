package com.swmansion.reactnativebottomsheet

import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.ReactStylesDiffMap
import java.util.AbstractMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Fabric applies an update's props in the order of its folly::dynamic map, which is not the
// order they were written in JS, so these updates are fed to the manager in an explicit order.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BottomSheetViewManagerIndexTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `index targeting a detent added in the same update is applied in either prop order`() {
    for (indexFirst in listOf(true, false)) {
      withSheet(detents(0.0, 300.0), index = 0) { manager, sheet, _ ->
        val grow = listOf("detents" to detents(0.0, 300.0, 600.0), "index" to 2.0)
        manager.updateProperties(sheet, props(if (indexFirst) grow.reversed() else grow))

        assertTrue("indexFirst=$indexFirst", host(sheet).isCloseRequestTargetResolvedAndOpen)
      }
    }
  }

  @Test
  fun `a later update without index does not replay the previous index`() {
    withSheet(detents(0.0, 300.0), index = 1) { manager, sheet, listener ->
      manager.updateProperties(
        sheet,
        props(listOf("index" to 2.0, "detents" to detents(0.0, 300.0, 600.0))),
      )
      tapScrim(sheet)
      assertEquals(listOf(0), listener.indexChanges)

      manager.updateProperties(sheet, props(listOf("animateContentHeight" to false)))

      assertFalse(host(sheet).isCloseRequestTargetResolvedAndOpen)
    }
  }

  private fun withSheet(
    initialDetents: JavaOnlyArray,
    index: Int,
    block: (BottomSheetViewManager, BottomSheetView, IndexRecordingListener) -> Unit,
  ) {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val manager = BottomSheetViewManager()
    val listener = IndexRecordingListener()
    val sheet = BottomSheetView(controller.get()).apply { this.listener = listener }
    try {
      manager.setScrimColor(sheet, Color.BLACK)
      manager.updateProperties(
        sheet,
        props(
          listOf(
            "modal" to true,
            "animateIn" to false,
            "detents" to initialDetents,
            "index" to index.toDouble(),
          )
        ),
      )
      controller.get().setContentView(sheet)
      layout(sheet)
      block(manager, sheet, listener)
    } finally {
      sheet.destroy()
      shadowOf(Looper.getMainLooper()).idle()
      controller.close()
    }
  }

  private fun host(sheet: BottomSheetView) = sheet.getChildAt(0) as BottomSheetHostView

  private fun detents(vararg values: Double) =
    JavaOnlyArray.from(
      values.map { JavaOnlyMap.of("value", it, "kind", "points", "programmatic", false) }
    )

  private fun props(entries: List<Pair<String, Any>>) = ReactStylesDiffMap(OrderedProps(entries))

  private fun tapScrim(sheet: BottomSheetView) {
    val down = MotionEvent.obtain(1_000L, 1_000L, MotionEvent.ACTION_DOWN, 10f, 100f, 0)
    val up = MotionEvent.obtain(1_000L, 1_010L, MotionEvent.ACTION_UP, 10f, 100f, 0)
    sheet.dispatchTouchEvent(down)
    sheet.dispatchTouchEvent(up)
    down.recycle()
    up.recycle()
  }

  private fun layout(view: View) {
    view.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, 1080, 1920)
    shadowOf(Looper.getMainLooper()).idle()
  }
}

private class OrderedProps(private val entries: List<Pair<String, Any>>) :
  ReadableMap by JavaOnlyMap.of(*entries.flatMap { it.toList() }.toTypedArray()) {
  override val entryIterator: Iterator<Map.Entry<String, Any?>>
    get() = entries.map { AbstractMap.SimpleEntry<String, Any?>(it.first, it.second) }.iterator()
}

private class IndexRecordingListener : BottomSheetViewListener {
  val indexChanges = mutableListOf<Int>()

  override fun onIndexChange(index: Int) {
    indexChanges.add(index)
  }

  override fun onSettle(index: Int) = Unit

  override fun onPositionChange(position: Double, index: Double) = Unit

  override fun onCloseRequest() = Unit
}
