package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.content.Context
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The content region — the Yoga box the sheet's in-flow children are laid out in — must be bounded
 * by the tallest detent the sheet can reach, not by the detent cap alone. Otherwise a scrollable
 * under a fixed tallest detent is laid out against the full cap and its last rows sit below the
 * screen edge. The region is reported as `contentRegionInset`, the gap between the sheet's height
 * and the region, in dp.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestArgumentsShadow::class])
class BottomSheetHostViewContentRegionTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `fixed tallest detent bounds the content region`() {
    withSheet(listOf(points(0.0), points(300.0))) { sheet ->
      assertEquals(maxOf(0.0, HEIGHT_PX / sheet.density - 300.0), publishedInset(sheet), 0.001)
    }
  }

  @Test
  fun `percentage tallest detent bounds the content region to that share of the cap`() {
    withSheet(listOf(points(0.0), percentage(0.5))) { sheet ->
      assertEquals(HEIGHT_PX / sheet.density * 0.5, publishedInset(sheet), 0.001)
    }
  }

  @Test
  fun `content tallest detent keeps the whole cap as the content region`() {
    withSheet(listOf(points(0.0), content())) { sheet ->
      assertEquals(0.0, publishedInset(sheet), 0.001)
    }
  }

  @Test
  fun `fixed detent above a content detent bounds the content region`() {
    withSheet(listOf(points(0.0), content(), points(300.0))) { sheet ->
      assertEquals(maxOf(0.0, HEIGHT_PX / sheet.density - 300.0), publishedInset(sheet), 0.001)
    }
  }

  @Test
  fun `fixed tallest detent above the cap leaves the whole cap as the content region`() {
    withSheet(listOf(points(0.0), points(HEIGHT_PX / 2.0 * 3))) { sheet ->
      assertEquals(0.0, publishedInset(sheet), 0.001)
    }
  }

  @Test
  fun `changing the tallest detent republishes the content region`() {
    withSheet(listOf(points(0.0), points(300.0))) { sheet ->
      val wrapper = InsetRecordingStateWrapper()
      sheet.stateWrapper = wrapper
      wrapper.insets.clear()

      sheet.setDetents(listOf(points(0.0), points(600.0)))
      shadowOf(Looper.getMainLooper()).idle()

      assertEquals(HEIGHT_PX / sheet.density - 600.0, wrapper.insets.last(), 0.001)
    }
  }

  // A detents change that alters the content region leaves the content's measured height stale
  // until Fabric lays the content out against the new region. Resolving `'content'` from the stale
  // value can trip the ascending-order validation (a fixed 500 above a filling scrollable that was
  // measured in a 300 region), so the measurement must be treated as pending until the new region
  // is applied.
  @Test
  fun `growing the content region via detents defers the stale content measurement`() {
    withSheet(listOf(points(0.0), points(300.0))) { sheet ->
      val listener = RecordingSheetListener()
      sheet.listener = listener
      val content = RegionContentView(sheet.context)
      sheet.addSheetChild(content, 0)
      // Fabric measured the filling content against the 300dp region: the marker sits at its end.
      content.applyFabricMeasure(WIDTH_PX, (300 * sheet.density).toInt(), fillsRegion = true)
      layout(sheet)
      sheet.stateWrapper = InsetRecordingStateWrapper()
      sheet.commitContentRegion(300.0)

      // Must not throw: the stale 300dp measurement is below the new 500dp detent.
      sheet.setDetents(listOf(points(0.0), points(500.0), content()))

      // Fabric commits the new inset and re-measures the content against the full cap; the host
      // then places the content at its new size.
      content.applyFabricMeasure(WIDTH_PX, HEIGHT_PX, fillsRegion = true)
      sheet.commitContentRegion(HEIGHT_PX / sheet.density)
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(HEIGHT_PX, content.height)

      sheet.setIndex(2)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
      assertEquals(2, listener.settles.last())
      assertEquals(HEIGHT_PX / sheet.density, listener.positions.last(), 0.5)
    }
  }

  @Test
  fun `a detents change while detached still defers the stale content measurement`() {
    withSheet(listOf(points(0.0), points(300.0))) { sheet ->
      val content = RegionContentView(sheet.context)
      sheet.addSheetChild(content, 0)
      content.applyFabricMeasure(WIDTH_PX, (300 * sheet.density).toInt(), fillsRegion = true)
      layout(sheet)
      sheet.stateWrapper = InsetRecordingStateWrapper()
      sheet.commitContentRegion(300.0)

      val parent = sheet.parent as ViewGroup
      parent.removeView(sheet)
      // Must not throw even though no geometry can be pushed while detached.
      sheet.setDetents(listOf(points(0.0), points(500.0), content()))
      parent.addView(sheet, ViewGroup.LayoutParams(WIDTH_PX, HEIGHT_PX))
      layout(sheet)
    }
  }

  @Test
  fun `reverting the detents before Fabric commits releases the content measurement`() {
    withSheet(listOf(points(0.0), points(300.0))) { sheet ->
      val listener = RecordingSheetListener()
      sheet.listener = listener
      val content = RegionContentView(sheet.context)
      sheet.addSheetChild(content, 0)
      content.applyFabricMeasure(WIDTH_PX, (300 * sheet.density).toInt(), fillsRegion = true)
      layout(sheet)
      sheet.stateWrapper = InsetRecordingStateWrapper()
      sheet.commitContentRegion(300.0)

      sheet.setDetents(listOf(points(0.0), points(500.0), content()))
      // Back to the committed 300dp region before Fabric applied the change: the content is still
      // laid out against it, so its measurement is valid again and the content detent resolves to
      // it (equal to the following fixed detent, which the ordering check allows).
      sheet.setDetents(listOf(points(0.0), content(), points(300.0)))
      sheet.setIndex(1)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
      assertEquals(1, listener.settles.last())
      assertEquals(300.0, listener.positions.last(), 0.5)
    }
  }

  @Test
  fun `content detent re-resolves once Fabric applies the changed content region`() {
    withSheet(listOf(points(0.0), content(), points(560.0))) { sheet ->
      val listener = RecordingSheetListener()
      sheet.listener = listener
      val content = RegionContentView(sheet.context)
      sheet.addSheetChild(content, 0)
      // Intrinsic 300dp content: its marker does not move when the region changes.
      content.markerTopPx = (300 * sheet.density).toInt()
      content.applyFabricMeasure(WIDTH_PX, (560 * sheet.density).toInt(), fillsRegion = false)
      layout(sheet)
      sheet.stateWrapper = InsetRecordingStateWrapper()
      sheet.commitContentRegion(560.0)
      sheet.setIndex(1)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
      assertEquals(300.0, listener.positions.last(), 0.5)

      sheet.setDetents(listOf(points(0.0), content(), points(600.0)))
      content.applyFabricMeasure(WIDTH_PX, (600 * sheet.density).toInt(), fillsRegion = false)
      sheet.commitContentRegion(600.0)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
      assertEquals((600 * sheet.density).toInt(), content.height)
      assertEquals(300.0, listener.positions.last(), 0.5)

      sheet.setIndex(2)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
      assertEquals(2, listener.settles.last())
      assertEquals(600.0, listener.positions.last(), 0.5)
    }
  }

  private fun publishedInset(sheet: BottomSheetHostView): Double {
    val wrapper = InsetRecordingStateWrapper()
    sheet.stateWrapper = wrapper
    return wrapper.insets.single()
  }

  private val BottomSheetHostView.density: Double
    get() = resources.displayMetrics.density.toDouble()

  // Stands in for Fabric committing the pushed state back and completing the mount batch: the
  // content is now laid out against a region of [regionDp], i.e. a content-region inset of the
  // remaining height.
  private fun BottomSheetHostView.commitContentRegion(regionDp: Double) {
    onContentRegionStateCommitted(HEIGHT_PX / density - regionDp)
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun points(value: Double): Map<String, Any> = mapOf("value" to value, "kind" to "points")

  private fun percentage(ratio: Double): Map<String, Any> =
    mapOf("value" to ratio, "kind" to "percentage")

  private fun content(): Map<String, Any> = mapOf("value" to 0.0, "kind" to "content")

  private fun withSheet(detents: List<Map<String, Any>>, block: (BottomSheetHostView) -> Unit) {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val sheet = BottomSheetHostView(controller.get())
    try {
      sheet.animateIn = false
      sheet.setDetents(detents)
      sheet.setIndex(0)
      controller.get().setContentView(sheet, ViewGroup.LayoutParams(WIDTH_PX, HEIGHT_PX))
      layout(sheet)
      block(sheet)
    } finally {
      sheet.destroy()
      controller.close()
    }
  }

  private fun layout(sheet: BottomSheetHostView) {
    sheet.measure(
      View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY),
    )
    sheet.layout(0, 0, WIDTH_PX, HEIGHT_PX)
    shadowOf(Looper.getMainLooper()).idle()
  }

  private companion object {
    const val WIDTH_PX = 1080
    const val HEIGHT_PX = 1920
  }
}

private class RecordingSheetListener : BottomSheetViewListener {
  val settles = mutableListOf<Int>()
  val positions = mutableListOf<Double>()

  override fun onIndexChange(index: Int) = Unit

  override fun onSettle(index: Int) {
    settles.add(index)
  }

  override fun onPositionChange(position: Double, index: Double) {
    positions.add(position)
  }

  override fun onCloseRequest() = Unit
}

/**
 * Stands in for the Fabric-measured content wrapper. The sheet owns its children's placement
 * (`needsCustomLayoutForChildren`), so Fabric only measures the wrapper: [applyFabricMeasure] does
 * the same, and the host's layout pass places it. Its last child is the marker the host measures
 * content by: filling content keeps the marker at the region's end; intrinsic content pins it at
 * [markerTopPx].
 */
private class RegionContentView(context: Context) : ViewGroup(context) {
  private val marker = View(context)
  var markerTopPx = 0

  init {
    addView(marker)
  }

  fun applyFabricMeasure(widthPx: Int, heightPx: Int, fillsRegion: Boolean) {
    if (fillsRegion) markerTopPx = heightPx
    measure(
      MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY),
    )
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(
      MeasureSpec.getSize(widthMeasureSpec),
      MeasureSpec.getSize(heightMeasureSpec),
    )
    marker.measure(
      MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY),
    )
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    marker.layout(0, markerTopPx, 1, markerTopPx + 1)
  }
}

private class InsetRecordingStateWrapper : StateWrapper {
  val insets = mutableListOf<Double>()
  override val stateData: ReadableNativeMap? = null
  override val stateDataMapBuffer: ReadableMapBuffer? = null

  override fun updateState(map: WritableMap) {
    insets.add(map.getDouble("contentRegionInset"))
  }

  override fun destroyState() = Unit
}
