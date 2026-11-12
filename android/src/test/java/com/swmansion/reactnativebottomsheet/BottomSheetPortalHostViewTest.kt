package com.swmansion.reactnativebottomsheet

import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
import android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
import android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
import android.widget.FrameLayout
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BottomSheetPortalHostViewTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `an open portal sheet hides the screen behind it until it is destroyed`() {
    withPortal { activity, screen, host ->
      val sheet = openModalSheet(activity)
      host.addView(sheet)
      // Fabric lays out a React view's children, so the test does it here.
      layoutView(sheet)

      assertEquals(
        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        screen.importantForAccessibility,
      )
      assertEquals(IMPORTANT_FOR_ACCESSIBILITY_AUTO, host.importantForAccessibility)

      sheet.destroy()

      assertEquals(IMPORTANT_FOR_ACCESSIBILITY_AUTO, screen.importantForAccessibility)
    }
  }

  @Test
  fun `restores each sibling's own value`() {
    withPortal { _, screen, host ->
      screen.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
      val sheet = View(host.context)

      host.setSheetModal(sheet, true)
      assertEquals(
        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        screen.importantForAccessibility,
      )

      host.setSheetModal(sheet, false)
      assertEquals(IMPORTANT_FOR_ACCESSIBILITY_NO, screen.importantForAccessibility)
    }
  }

  @Test
  fun `stays isolated while any sheet is modal`() {
    withPortal { _, screen, host ->
      val lower = View(host.context)
      val upper = View(host.context)

      host.setSheetModal(lower, true)
      host.setSheetModal(upper, true)
      host.setSheetModal(upper, false)
      assertEquals(
        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        screen.importantForAccessibility,
      )

      host.setSheetModal(lower, false)
      assertEquals(IMPORTANT_FOR_ACCESSIBILITY_AUTO, screen.importantForAccessibility)
    }
  }

  @Test
  fun `hides the portals below the top modal sheet until it closes`() {
    withPortal { _, screen, host ->
      val lowerPortal = FrameLayout(host.context)
      val upperPortal = FrameLayout(host.context)
      val lowerSheet = View(host.context)
      val upperSheet = View(host.context)
      lowerPortal.addView(lowerSheet)
      upperPortal.addView(upperSheet)
      host.addView(lowerPortal)
      host.addView(upperPortal)

      host.setSheetModal(lowerSheet, true)
      assertEquals(IMPORTANT_FOR_ACCESSIBILITY_AUTO, lowerPortal.importantForAccessibility)
      assertEquals(
        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        upperPortal.importantForAccessibility,
      )

      host.setSheetModal(upperSheet, true)
      assertEquals(
        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        lowerPortal.importantForAccessibility,
      )
      assertEquals(IMPORTANT_FOR_ACCESSIBILITY_AUTO, upperPortal.importantForAccessibility)
      assertEquals(
        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        screen.importantForAccessibility,
      )

      host.setSheetModal(upperSheet, false)
      assertEquals(IMPORTANT_FOR_ACCESSIBILITY_AUTO, lowerPortal.importantForAccessibility)
      assertEquals(
        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        screen.importantForAccessibility,
      )

      host.setSheetModal(lowerSheet, false)
      assertEquals(IMPORTANT_FOR_ACCESSIBILITY_AUTO, upperPortal.importantForAccessibility)
      assertEquals(IMPORTANT_FOR_ACCESSIBILITY_AUTO, screen.importantForAccessibility)
    }
  }

  @Test
  fun `hides a sibling mounted while isolated and restores one removed meanwhile`() {
    withPortal { _, screen, host ->
      val root = host.parent as FrameLayout
      host.setSheetModal(View(host.context), true)

      val mounted = View(host.context)
      root.addView(mounted, 0)
      layoutView(root)
      assertEquals(
        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        mounted.importantForAccessibility,
      )

      root.removeView(screen)
      layoutView(root)
      assertEquals(IMPORTANT_FOR_ACCESSIBILITY_AUTO, screen.importantForAccessibility)
    }
  }

  @Test
  fun `detaching the host restores its siblings`() {
    withPortal { _, screen, host ->
      host.setSheetModal(View(host.context), true)

      (host.parent as FrameLayout).removeView(host)

      assertEquals(IMPORTANT_FOR_ACCESSIBILITY_AUTO, screen.importantForAccessibility)
    }
  }

  /** A window with a screen and, above it, the portal host as siblings. */
  private fun withPortal(
    block: (ComponentActivity, View, BottomSheetPortalHostView) -> Unit,
  ) {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    val root = FrameLayout(activity)
    val screen = FrameLayout(activity)
    val host = BottomSheetPortalHostView(activity)
    root.addView(screen)
    root.addView(host)
    activity.setContentView(root)
    layoutView(root)
    try {
      block(activity, screen, host)
    } finally {
      shadowOf(Looper.getMainLooper()).idle()
      controller.close()
    }
  }

  private fun openModalSheet(activity: ComponentActivity) =
    BottomSheetView(activity).apply {
      animateIn = false
      modal = true
      setScrimColor(Color.BLACK)
      setDetents(
        listOf(
          mapOf("value" to 0.0, "kind" to "points", "programmatic" to false, "scrimOpacity" to 0.0),
          mapOf(
            "value" to 300.0,
            "kind" to "points",
            "programmatic" to false,
            "scrimOpacity" to 1.0,
          ),
        ),
      )
      setIndex(1)
    }

  private fun layoutView(view: View) {
    val width = 1080
    val height = 1920
    view.measure(
      View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, width, height)
    view.viewTreeObserver.dispatchOnGlobalLayout()
    shadowOf(Looper.getMainLooper()).idle()
  }
}
