package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.uimanager.ReactPointerEventsView
import com.facebook.react.uimanager.TouchTargetHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Runs React Native's own target walk over a card, as a JS touch does: a press in a corner the card
 * clips away must find what shows through it, not the content hidden there.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestArgumentsShadow::class])
class BottomSheetCardTouchTargetingTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  private fun target(root: ViewGroup, x: Float, y: Float) =
    TouchTargetHelper.findTargetTagForTouch(x, y, root)

  private fun withCard(block: (root: ViewGroup, sheet: BottomSheetHostView, top: Float) -> Unit) {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val activity = controller.get()
    val density = activity.resources.displayMetrics.density
    val root = FrameLayout(activity).apply { id = ROOT_ID }
    val behind = View(activity).apply { id = BEHIND_ID }
    val sheet = BottomSheetHostView(activity)
    val content = View(activity).apply { id = CONTENT_ID }
    try {
      sheet.animateIn = false
      sheet.setDetents(listOf(mapOf("value" to 300.0, "kind" to "points")))
      sheet.setIndex(0)
      sheet.setSurfaceCornerRadii(listOf(RADIUS_DP, RADIUS_DP, 0f, 0f))
      root.addView(behind)
      root.addView(sheet)
      activity.setContentView(root, ViewGroup.LayoutParams(WIDTH_PX, HEIGHT_PX))
      sheet.addSheetChild(content, 0)
      root.measure(exactly(WIDTH_PX), exactly(HEIGHT_PX))
      root.layout(0, 0, WIDTH_PX, HEIGHT_PX)
      behind.layout(0, 0, WIDTH_PX, HEIGHT_PX)
      content.measure(exactly(WIDTH_PX), exactly(HEIGHT_PX))
      sheet.measure(exactly(WIDTH_PX), exactly(HEIGHT_PX))
      sheet.layout(0, 0, WIDTH_PX, HEIGHT_PX)
      shadowOf(Looper.getMainLooper()).idle()
      block(root, sheet, HEIGHT_PX - 300f * density)
    } finally {
      sheet.destroy()
      controller.close()
    }
  }

  private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

  @Test
  fun `a press in a clipped corner finds what shows through it`() {
    withCard { root, _, top ->
      assertEquals(BEHIND_ID, target(root, 2f, top + 2f))
      assertEquals(BEHIND_ID, target(root, WIDTH_PX - 2f, top + 2f))
    }
  }

  @Test
  fun `a press on the card finds the sheet's content`() {
    withCard { root, _, top ->
      assertEquals(CONTENT_ID, target(root, WIDTH_PX / 2f, top + 2f))
      assertEquals(CONTENT_ID, target(root, WIDTH_PX / 2f, top + 200f))
    }
  }

  @Test
  fun `a press above the card finds what is behind the sheet`() {
    withCard { root, _, top -> assertEquals(BEHIND_ID, target(root, 100f, top - 50f)) }
  }

  @Test
  fun `a press on the card right after a clipped corner finds the content`() {
    withCard { root, _, top ->
      assertEquals(BEHIND_ID, target(root, 2f, top + 2f))
      assertEquals(CONTENT_ID, target(root, WIDTH_PX / 2f, top + 2f))
    }
  }

  @Test
  fun `the skip ends with the touch's dispatch`() {
    withCard { root, sheet, top ->
      target(root, 2f, top + 2f)
      val container = sheet.getChildAt(0) as ReactPointerEventsView
      assertEquals(PointerEvents.NONE, container.pointerEvents)
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(PointerEvents.AUTO, container.pointerEvents)
    }
  }

  private companion object {
    const val ROOT_ID = 1
    const val BEHIND_ID = 11
    const val CONTENT_ID = 22
    const val RADIUS_DP = 48f
    const val WIDTH_PX = 1080
    const val HEIGHT_PX = 1920
  }
}
