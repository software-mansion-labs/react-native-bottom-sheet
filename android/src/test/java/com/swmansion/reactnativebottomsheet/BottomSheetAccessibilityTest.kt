package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BottomSheetAccessibilityTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `scrim helper exposes its only virtual child with screen bounds`() {
    val host = laidOutView(width = 100, height = 200)
    val helper =
      ScrimAccessibilityHelper(
        host = host,
        isDismissAvailable = { true },
        scrimBottom = { 80f },
        performDismiss = { true },
      )
    val provider = helper.getAccessibilityNodeProvider(host)!!

    val hostNode = provider.createAccessibilityNodeInfo(ExploreByTouchHelper.HOST_ID)!!
    val scrimNode = provider.createAccessibilityNodeInfo(SCRIM_VIRTUAL_VIEW_ID)!!
    val bounds = Rect()
    scrimNode.getBoundsInScreen(bounds)

    assertEquals(1, hostNode.childCount)
    assertEquals("android.widget.Button", scrimNode.className)
    assertEquals("Dismiss", scrimNode.contentDescription)
    assertTrue(scrimNode.isClickable)
    assertTrue(scrimNode.isDismissable)
    assertEquals(Rect(0, 0, 100, 80), bounds)
  }

  @Test
  fun `scrim helper hides its virtual child when dismissal or scrim area is unavailable`() {
    var dismissAvailable = false
    var scrimBottom = 80f
    val host = laidOutView(width = 100, height = 200)
    val helper =
      ScrimAccessibilityHelper(
        host = host,
        isDismissAvailable = { dismissAvailable },
        scrimBottom = { scrimBottom },
        performDismiss = { true },
      )
    val provider = helper.getAccessibilityNodeProvider(host)!!

    assertEquals(
      0,
      provider.createAccessibilityNodeInfo(ExploreByTouchHelper.HOST_ID)!!.childCount,
    )

    dismissAvailable = true
    scrimBottom = 0f

    assertEquals(
      0,
      provider.createAccessibilityNodeInfo(ExploreByTouchHelper.HOST_ID)!!.childCount,
    )
  }

  @Test
  fun `scrim helper routes click and dismiss actions only for its virtual child`() {
    var dismissCount = 0
    val host = laidOutView(width = 100, height = 200)
    val helper =
      ScrimAccessibilityHelper(
        host = host,
        isDismissAvailable = { true },
        scrimBottom = { 80f },
        performDismiss = {
          dismissCount++
          true
        },
      )
    val provider = helper.getAccessibilityNodeProvider(host)!!

    assertTrue(
      provider.performAction(
        SCRIM_VIRTUAL_VIEW_ID,
        AccessibilityNodeInfoCompat.ACTION_CLICK,
        null,
      ),
    )
    assertTrue(
      provider.performAction(
        SCRIM_VIRTUAL_VIEW_ID,
        AccessibilityNodeInfoCompat.ACTION_DISMISS,
        null,
      ),
    )
    assertFalse(provider.performAction(42, AccessibilityNodeInfoCompat.ACTION_CLICK, null))
    assertEquals(2, dismissCount)
  }

  @Test
  fun `sheet delegate exposes and performs dismiss only while available`() {
    var dismissAvailable = false
    var dismissCount = 0
    val host = View(context)
    val delegate =
      SheetDismissAccessibilityDelegate(
        isDismissAvailable = { dismissAvailable },
        performDismiss = {
          dismissCount++
          true
        },
      )

    val unavailableInfo = accessibilityNodeInfo()
    delegate.onInitializeAccessibilityNodeInfo(host, unavailableInfo)
    assertFalse(unavailableInfo.isDismissable)
    assertFalse(
      delegate.performAccessibilityAction(host, AccessibilityNodeInfo.ACTION_DISMISS, null),
    )

    dismissAvailable = true
    val availableInfo = accessibilityNodeInfo()
    delegate.onInitializeAccessibilityNodeInfo(host, availableInfo)
    assertTrue(availableInfo.isDismissable)
    assertTrue(
      delegate.performAccessibilityAction(host, AccessibilityNodeInfo.ACTION_DISMISS, null),
    )
    assertEquals(1, dismissCount)
  }

  private fun laidOutView(width: Int, height: Int): View =
    View(context).apply { layout(0, 0, width, height) }

  @Suppress("DEPRECATION")
  private fun accessibilityNodeInfo(): AccessibilityNodeInfoCompat =
    AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())

  private companion object {
    const val SCRIM_VIRTUAL_VIEW_ID = 0
  }
}
