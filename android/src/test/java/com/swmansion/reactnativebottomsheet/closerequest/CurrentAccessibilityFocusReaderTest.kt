package com.swmansion.reactnativebottomsheet.closerequest

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CurrentAccessibilityFocusReaderTest {
  @Test
  fun `provider query failure is unresolved rather than escaping into Back handling`() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    try {
      val context = activity.get()
      shadowOf(context.getSystemService(AccessibilityManager::class.java)).setEnabled(true)
      val host =
        object : View(context) {
          override fun getAccessibilityNodeProvider() =
            object : AccessibilityNodeProvider() {
              override fun findFocus(focus: Int): AccessibilityNodeInfo =
                throw IllegalStateException("Virtual subtree changed during the query")
            }
        }
      context.setContentView(FrameLayout(context).apply { addView(host) })

      assertEquals(
        NativeAccessibilityFocus.Unresolved,
        readCurrentAccessibilityFocus(host.rootView),
      )
    } finally {
      activity.close()
    }
  }

  @Test
  fun `provider detaching its host during the query cannot supply a current window origin`() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    try {
      val context = activity.get()
      shadowOf(context.getSystemService(AccessibilityManager::class.java)).setEnabled(true)
      val host =
        object : View(context) {
          override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider {
            val owner = this
            return object : AccessibilityNodeProvider() {
              override fun findFocus(focus: Int): AccessibilityNodeInfo {
                (owner.parent as? ViewGroup)?.removeView(owner)
                return AccessibilityNodeInfo.obtain(owner).apply { isAccessibilityFocused = true }
              }
            }
          }
        }
      val content = FrameLayout(context).apply { addView(host) }
      context.setContentView(content)

      assertEquals(
        NativeAccessibilityFocus.Unresolved,
        readCurrentAccessibilityFocus(content.rootView),
      )
    } finally {
      activity.close()
    }
  }

  @Test
  fun `current virtual focus maps to its attached native host`() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    try {
      val context = activity.get()
      shadowOf(context.getSystemService(AccessibilityManager::class.java)).setEnabled(true)
      val host =
        object : View(context) {
          override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider {
            val owner = this
            return object : AccessibilityNodeProvider() {
              override fun findFocus(focus: Int): AccessibilityNodeInfo? =
                if (focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                  AccessibilityNodeInfo.obtain(owner).apply { isAccessibilityFocused = true }
                else null
            }
          }
        }
      context.setContentView(FrameLayout(context).apply { addView(host) })

      assertEquals(
        NativeAccessibilityFocus.Found(host),
        readCurrentAccessibilityFocus(host.rootView),
      )
    } finally {
      activity.close()
    }
  }

  @Test
  fun `null provider query is unresolved while keyboard focus remains available`() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    try {
      val context = activity.get()
      shadowOf(context.getSystemService(AccessibilityManager::class.java)).setEnabled(true)
      val keyboard = View(context).apply { isFocusableInTouchMode = true }
      val virtualHost =
        object : View(context) {
          override fun getAccessibilityNodeProvider() = object : AccessibilityNodeProvider() {}
        }
      val content =
        FrameLayout(context).apply {
          addView(keyboard)
          addView(virtualHost)
        }
      context.setContentView(content)
      assertTrue(keyboard.requestFocus())
      assertEquals(keyboard, content.rootView.findFocus())

      assertEquals(
        NativeAccessibilityFocus.Unresolved,
        readCurrentAccessibilityFocus(content.rootView),
      )
    } finally {
      activity.close()
    }
  }
}
