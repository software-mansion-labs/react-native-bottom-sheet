package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.ReadableNativeMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.mapbuffer.ReadableMapBuffer
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.StateWrapper
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestArgumentsShadow::class])
class BottomSheetHostViewStateTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `committed wrapper replacements do not publish again for closed or open sheets`() {
    for (modal in listOf(false, true)) {
      for (index in listOf(0, 1)) {
        withSheet(index, modal) { sheet ->
          val first = RecordingStateWrapper()
          sheet.stateWrapper = first
          assertEquals(1, first.updates.size)

          repeat(10) {
            val committed = RecordingStateWrapper()
            sheet.stateWrapper = committed
            layout(sheet)
            assertTrue(committed.updates.isEmpty())
          }
        }
      }
    }
  }

  @Test
  fun `geometry and offset derived before wrapper attachment are flushed together`() {
    withSheet { sheet ->
      val wrapper = RecordingStateWrapper()
      sheet.stateWrapper = wrapper

      assertEquals(1, wrapper.updates.size)
      val state = wrapper.updates.single()
      val density = sheet.resources.displayMetrics.density
      assertEquals((1080 / density).toDouble(), state["frameWidth"])
      assertEquals((1920 / density).toDouble(), state["frameHeight"])
      assertEquals((1920 / density).toDouble(), state["contentOffsetY"])
      assertTrue(state.containsKey("contentRegionInset"))
    }
  }

  @Test
  fun `resize and movement still publish complete snapshots through the latest wrapper`() {
    withSheet { sheet ->
      val first = RecordingStateWrapper()
      sheet.stateWrapper = first
      val latest = RecordingStateWrapper()
      sheet.stateWrapper = latest

      layout(sheet, width = 900)
      assertTrue(latest.updates.isNotEmpty())
      val resized = latest.updates.last()
      val density = sheet.resources.displayMetrics.density
      assertEquals((900 / density).toDouble(), resized["frameWidth"])

      latest.updates.clear()
      sheet.setIndex(1)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
      assertTrue(latest.updates.isNotEmpty())
      assertTrue(
        latest.updates.last().getValue("contentOffsetY") < resized.getValue("contentOffsetY"),
      )
      latest.updates.forEach {
        assertEquals(resized["frameWidth"], it["frameWidth"])
        assertEquals(resized["frameHeight"], it["frameHeight"])
        assertEquals(resized["contentRegionInset"], it["contentRegionInset"])
      }
      assertEquals(1, first.updates.size)
    }
  }

  @Test
  fun `snapshots carry where the card puts the content`() {
    withSheet { sheet ->
      val wrapper = RecordingStateWrapper()
      sheet.stateWrapper = wrapper
      val density = sheet.resources.displayMetrics.density
      val floating = mapOf("value" to 300.0, "insetLeft" to 16.0, "insetRight" to 16.0)

      // Laid out against the full-width detent, so the floating card scales it down.
      sheet.setDetents(
        listOf(mapOf("value" to 0.0), floating, mapOf("value" to 1.0, "kind" to "percentage")),
      )
      sheet.setIndex(1)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
      layout(sheet)
      val scaled = wrapper.updates.last()
      assertEquals((1080 - 32 * density) / 1080.0, scaled.getValue("contentScale"), 0.001)
      assertEquals(16.0, scaled.getValue("contentOffsetX"), 0.01)

      // Laid out against the floating card itself: its margin is what places it.
      sheet.setDetents(listOf(mapOf("value" to 0.0), floating))
      shadowOf(Looper.getMainLooper()).idle()
      layout(sheet)
      val unscaled = wrapper.updates.last()
      assertEquals(1.0, unscaled.getValue("contentScale"), 0.001)
      assertEquals(0.0, unscaled.getValue("contentOffsetX"), 0.01)
    }
  }

  @Test
  fun `disconnecting the wrapper allows the current snapshot to be sent again`() {
    withSheet { sheet ->
      val first = RecordingStateWrapper()
      sheet.stateWrapper = first
      sheet.stateWrapper = null
      val reconnected = RecordingStateWrapper()
      sheet.stateWrapper = reconnected
      assertEquals(first.updates, reconnected.updates)
      assertEquals(1, reconnected.updates.size)
    }
  }

  @Test
  fun `destroy clears the sent snapshot for reuse`() {
    withSheet { sheet ->
      sheet.stateWrapper = RecordingStateWrapper()
      sheet.destroy()
      sheet.forceLayout()
      layout(sheet)
      val replacement = RecordingStateWrapper()
      sheet.stateWrapper = replacement
      assertEquals(1, replacement.updates.size)
    }
  }

  private fun layout(sheet: BottomSheetHostView, width: Int = 1080) {
    sheet.layoutParams =
      sheet.layoutParams.apply {
        this.width = width
        height = 1920
      }
    sheet.measure(
      View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
    )
    sheet.layout(0, 0, width, 1920)
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun withSheet(
    index: Int = 0,
    modal: Boolean = false,
    block: (BottomSheetHostView) -> Unit,
  ) {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val sheet = BottomSheetHostView(controller.get())
    try {
      sheet.animateIn = false
      sheet.modal = modal
      sheet.setDetents(listOf(mapOf("value" to 0.0), mapOf("value" to 300.0)))
      sheet.setIndex(index)
      controller.get().setContentView(sheet, ViewGroup.LayoutParams(1080, 1920))
      layout(sheet)
      block(sheet)
    } finally {
      sheet.destroy()
      controller.close()
    }
  }
}

private class RecordingStateWrapper : StateWrapper {
  val updates = mutableListOf<Map<String, Double>>()
  override val stateData: ReadableNativeMap? = null
  override val stateDataMapBuffer: ReadableMapBuffer? = null

  override fun updateState(map: WritableMap) {
    updates.add(
      listOf(
          "contentOffsetX",
          "contentOffsetY",
          "contentScale",
          "frameWidth",
          "frameHeight",
          "contentRegionInset",
        )
        .associateWith { map.getDouble(it) },
    )
  }

  override fun destroyState() = Unit
}
