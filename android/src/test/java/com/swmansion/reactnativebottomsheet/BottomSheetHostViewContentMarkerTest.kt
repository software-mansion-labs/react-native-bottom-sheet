package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.R
import com.facebook.react.bridge.ReadableNativeMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.mapbuffer.ReadableMapBuffer
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.StateWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val HOST_WIDTH = 1080
private const val HOST_HEIGHT = 1920
private const val MARKER_TOP = 400

// The content wrapper is a Fabric view: Fabric positions its children directly
// through View.layout() and the wrapper's own onLayout never moves them, so a
// child keeps whatever frame the test gives it across host layout passes.
private class FabricLikeViewGroup(context: Context) : ViewGroup(context) {
  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(HOST_WIDTH, HOST_HEIGHT)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestArgumentsShadow::class])
class BottomSheetHostViewContentMarkerTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `content detent resolves from the marker when a z-ordered sibling mounts after it`() {
    // Fabric mounts children sorted by zIndex, so a content child with a
    // positive zIndex lands after the zero-index marker in the wrapper.
    val wrapper = FabricLikeViewGroup(activityContext())
    wrapper.addView(marker(wrapper.context))
    wrapper.addView(childAt(wrapper.context, top = 0, height = 120))

    withContentSheet(wrapper) { sheet, state ->
      assertTrue(sheet.isCloseRequestTargetResolvedAndOpen)
      assertEquals(contentOffsetFor(sheet, MARKER_TOP), state.lastContentOffsetY(), 0.001)
    }
  }

  @Test
  fun `content detent stays unresolved without a tagged marker`() {
    // An untagged last child is content, not the marker, even when it is the
    // only child: the marker is identified by its native ID, never by position.
    val wrapper = FabricLikeViewGroup(activityContext())
    wrapper.addView(childAt(wrapper.context, top = MARKER_TOP, height = 0))

    withContentSheet(wrapper) { sheet, _ -> assertFalse(sheet.isCloseRequestTargetResolvedAndOpen) }
  }

  private fun marker(context: Context): View =
    childAt(context, top = MARKER_TOP, height = 0).apply {
      setTag(R.id.view_tag_native_id, BottomSheetHostView.CONTENT_HEIGHT_MARKER_NATIVE_ID)
    }

  private fun childAt(context: Context, top: Int, height: Int): View =
    View(context).apply { layout(0, top, HOST_WIDTH, top + height) }

  private fun contentOffsetFor(sheet: BottomSheetHostView, contentHeightPx: Int): Double {
    val density = sheet.resources.displayMetrics.density
    return ((HOST_HEIGHT - contentHeightPx) / density).toDouble()
  }

  private var activityContext: Context? = null

  private fun activityContext(): Context =
    activityContext
      ?: Robolectric.buildActivity(Activity::class.java).setup().get().also {
        activityContext = it
      }

  private fun withContentSheet(
    wrapper: ViewGroup,
    block: (BottomSheetHostView, RecordingContentStateWrapper) -> Unit,
  ) {
    val activity = activityContext() as Activity
    val sheet = BottomSheetHostView(activity)
    val state = RecordingContentStateWrapper()
    try {
      sheet.animateIn = false
      sheet.setDetents(
        listOf(
          mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
          mapOf("value" to 0.0, "kind" to "content", "programmatic" to false),
        )
      )
      sheet.setIndex(1)
      sheet.addSheetChild(wrapper, 0)
      sheet.stateWrapper = state
      activity.setContentView(sheet, ViewGroup.LayoutParams(HOST_WIDTH, HOST_HEIGHT))
      sheet.measure(
        View.MeasureSpec.makeMeasureSpec(HOST_WIDTH, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(HOST_HEIGHT, View.MeasureSpec.EXACTLY),
      )
      sheet.layout(0, 0, HOST_WIDTH, HOST_HEIGHT)
      shadowOf(Looper.getMainLooper()).idle()
      block(sheet, state)
    } finally {
      sheet.destroy()
      shadowOf(Looper.getMainLooper()).idle()
    }
  }
}

private class RecordingContentStateWrapper : StateWrapper {
  private val contentOffsets = mutableListOf<Double>()
  override val stateData: ReadableNativeMap? = null
  override val stateDataMapBuffer: ReadableMapBuffer? = null

  fun lastContentOffsetY(): Double = contentOffsets.last()

  override fun updateState(map: WritableMap) {
    contentOffsets.add(map.getDouble("contentOffsetY"))
  }

  override fun destroyState() = Unit
}
