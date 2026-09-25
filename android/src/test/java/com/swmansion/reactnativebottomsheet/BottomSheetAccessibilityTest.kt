package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
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
  fun `scrim delegate exposes button semantics and visible bounds`() {
    val scrim =
      laidOutView(width = 100, height = 200).apply {
        contentDescription = "Dismiss"
        isClickable = true
      }
    val delegate =
      ScrimAccessibilityDelegate(
        isDismissAvailable = { true },
        scrimBottom = { 80f },
      )
    ViewCompat.setAccessibilityDelegate(scrim, delegate)

    val scrimNode = scrim.createAccessibilityNodeInfo()
    val bounds = Rect()
    scrimNode.getBoundsInScreen(bounds)

    assertEquals("android.widget.Button", scrimNode.className)
    assertEquals("Dismiss", scrimNode.contentDescription)
    assertTrue(scrimNode.isClickable)
    assertTrue(scrimNode.isDismissable)
    assertEquals(Rect(0, 0, 100, 80), bounds)
  }

  @Test
  fun `scrim delegate does not expose or perform dismissal while unavailable`() {
    var dismissAvailable = false
    var clickCount = 0
    val scrim =
      laidOutView(width = 100, height = 200).apply {
        isClickable = true
        setOnClickListener { clickCount++ }
      }
    val delegate =
      ScrimAccessibilityDelegate(
        isDismissAvailable = { dismissAvailable },
        scrimBottom = { 80f },
      )
    ViewCompat.setAccessibilityDelegate(scrim, delegate)

    val unavailableInfo = scrim.createAccessibilityNodeInfo()
    assertFalse(unavailableInfo.isDismissable)
    assertFalse(scrim.performAccessibilityAction(AccessibilityNodeInfoCompat.ACTION_DISMISS, null))

    dismissAvailable = true
    assertTrue(scrim.performAccessibilityAction(AccessibilityNodeInfoCompat.ACTION_DISMISS, null))
    assertTrue(scrim.performAccessibilityAction(AccessibilityNodeInfoCompat.ACTION_CLICK, null))
    assertEquals(2, clickCount)
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
      delegate.performAccessibilityAction(host, AccessibilityNodeInfo.ACTION_DISMISS, null)
    )

    dismissAvailable = true
    val availableInfo = accessibilityNodeInfo()
    delegate.onInitializeAccessibilityNodeInfo(host, availableInfo)
    assertTrue(availableInfo.isDismissable)
    assertTrue(
      delegate.performAccessibilityAction(host, AccessibilityNodeInfo.ACTION_DISMISS, null)
    )
    assertEquals(1, dismissCount)
  }

  private fun laidOutView(width: Int, height: Int): View =
    View(context).apply { layout(0, 0, width, height) }

  @Suppress("DEPRECATION")
  private fun accessibilityNodeInfo(): AccessibilityNodeInfoCompat =
    AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())
}
