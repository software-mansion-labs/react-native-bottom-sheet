package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class BottomSheetAccessibilityTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `an open dismissible sheet exposes the scrim as a Dismiss button above the sheet`() {
    withModalSheet { sheet, _ ->
      val button = scrimDismissView(sheet)
      val node = button.createAccessibilityNodeInfo()
      val host = button.parent as View
      val bounds = Rect()
      val hostBounds = Rect()
      node.getBoundsInScreen(bounds)
      host.getGlobalVisibleRect(hostBounds)
      val sheetTop = host.height - (300 * sheet.resources.displayMetrics.density).toInt()

      // A direct child of the host, not routed into the sheet with React children.
      assertTrue(host is BottomSheetHostView)
      assertEquals(
        Rect(hostBounds.left, hostBounds.top, hostBounds.right, hostBounds.top + sheetTop),
        bounds,
      )

      assertEquals(View.VISIBLE, button.visibility)
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, button.importantForAccessibility)
      assertEquals(Button::class.java.name, node.className)
      assertEquals("Dismiss", node.contentDescription)
      assertTrue(node.isClickable)
      assertTrue(node.isDismissable)
      assertTrue(node.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS))
    }
  }

  @Test
  fun `activating the Dismiss button dismisses the sheet`() {
    withModalSheet { sheet, listener ->
      assertTrue(
        scrimDismissView(sheet)
          .performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null),
      )

      assertEquals(listOf(0), listener.indexChanges)
    }
  }

  @Test
  fun `the Dismiss button's dismiss action dismisses the sheet`() {
    withModalSheet { sheet, listener ->
      assertTrue(
        scrimDismissView(sheet)
          .performAccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS, null),
      )

      assertEquals(listOf(0), listener.indexChanges)
    }
  }

  @Test
  fun `the sheet container's dismiss action dismisses the sheet from focus in its content`() {
    withModalSheet { sheet, listener ->
      val container = sheetContainer(sheet)
      val node = container.createAccessibilityNodeInfo()

      assertTrue(node.isDismissable)
      assertTrue(container.performAccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS, null))
      assertEquals(listOf(0), listener.indexChanges)
    }
  }

  @Test
  fun `the Dismiss button is withdrawn once a dismissal is running`() {
    withModalSheet { sheet, listener ->
      val button = scrimDismissView(sheet)
      button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)

      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, button.importantForAccessibility)
      assertFalse(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
      assertFalse(
        sheetContainer(sheet)
          .performAccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS, null),
      )
      assertEquals(listOf(0), listener.indexChanges)
    }
  }

  @Test
  fun `a programmatic-only closed detent exposes no dismissal`() {
    withModalSheet(closedProgrammatic = true) { sheet, listener ->
      val button = scrimDismissView(sheet)

      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, button.importantForAccessibility)
      assertEquals(View.INVISIBLE, button.visibility)
      assertFalse(sheetContainer(sheet).createAccessibilityNodeInfo().isDismissable)
      assertFalse(
        sheetContainer(sheet)
          .performAccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS, null),
      )
      assertEquals(emptyList<Int>(), listener.indexChanges)
    }
  }

  @Test
  fun `an invisible scrim exposes no dismissal`() {
    withModalSheet(openScrimOpacity = 0.0) { sheet, listener ->
      val button = scrimDismissView(sheet)

      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, button.importantForAccessibility)
      assertFalse(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
      assertEquals(emptyList<Int>(), listener.indexChanges)
    }
  }

  @Test
  fun `a sheet that fills the host leaves no Dismiss button`() {
    withModalSheet(openDetent = mapOf("value" to 1.0, "kind" to "percentage")) { sheet, _ ->
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO,
        scrimDismissView(sheet).importantForAccessibility,
      )
    }
  }

  @Test
  fun `the Dismiss button stays out of keyboard focus`() {
    withModalSheet { sheet, _ ->
      val button = scrimDismissView(sheet)

      assertTrue(button.isClickable)
      assertFalse(button.isFocusable)
    }
  }

  @Test
  fun `a sheet made modal while settled open exposes the Dismiss button at once`() {
    withModalSheet { sheet, _ ->
      sheet.modal = false
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO,
        scrimDismissView(sheet).importantForAccessibility,
      )

      // No frame or layout follows: the setter alone must refresh the button.
      sheet.modal = true

      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_YES,
        scrimDismissView(sheet).importantForAccessibility,
      )
    }
  }

  @Test
  fun `the Dismiss button announces the supplied label`() {
    withModalSheet { sheet, _ ->
      sheet.setScrimAccessibilityLabel("Schließen")

      assertEquals(
        "Schließen",
        scrimDismissView(sheet).createAccessibilityNodeInfo().contentDescription,
      )
    }
  }

  private fun withModalSheet(
    closedProgrammatic: Boolean = false,
    openScrimOpacity: Double = 1.0,
    openDetent: Map<String, Any> = mapOf("value" to 300.0, "kind" to "points"),
    block: (BottomSheetView, RecordingAccessibilityListener) -> Unit,
  ) {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    val listener = RecordingAccessibilityListener()
    val sheet = openModalSheet(activity, listener, closedProgrammatic, openScrimOpacity, openDetent)
    try {
      block(sheet, listener)
    } finally {
      sheet.destroy()
      shadowOf(Looper.getMainLooper()).idle()
      controller.close()
    }
  }

  private fun openModalSheet(
    activity: Activity,
    listener: RecordingAccessibilityListener,
    closedProgrammatic: Boolean,
    openScrimOpacity: Double,
    openDetent: Map<String, Any>,
  ) =
    BottomSheetView(activity)
      .apply {
        this.listener = listener
        animateIn = false
        modal = true
        setScrimColor(Color.BLACK)
        setScrimAccessibilityLabel("Dismiss")
        setDetents(
          listOf(
            mapOf(
              "value" to 0.0,
              "kind" to "points",
              "programmatic" to closedProgrammatic,
              "scrimOpacity" to 0.0,
            ),
            openDetent + mapOf("programmatic" to false, "scrimOpacity" to openScrimOpacity),
          ),
        )
        setIndex(1)
        // Content, so the sheet container has a descendant to hold focus.
        addSheetChild(View(activity), 0)
      }
      .also {
        activity.setContentView(it)
        layoutView(it)
      }

  private fun scrimDismissView(sheet: BottomSheetView): ScrimDismissView =
    checkNotNull(findView(sheet) { it is ScrimDismissView }) as ScrimDismissView

  /** The sheet container, two levels above the content: content, clip container, container. */
  private fun sheetContainer(sheet: BottomSheetView): View {
    val content = sheet.getSheetChildAt(0)!!
    return (content.parent as View).parent as View
  }

  private fun findView(root: View, predicate: (View) -> Boolean): View? {
    if (predicate(root)) return root
    if (root !is ViewGroup) return null
    for (i in 0 until root.childCount) {
      findView(root.getChildAt(i), predicate)?.let {
        return it
      }
    }
    return null
  }

  private fun layoutView(view: View) {
    view.measure(
      View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, WIDTH, HEIGHT)
    shadowOf(Looper.getMainLooper()).idle()
  }

  private companion object {
    const val WIDTH = 1080
    const val HEIGHT = 1920
  }
}

private class RecordingAccessibilityListener : BottomSheetViewListener {
  val indexChanges = mutableListOf<Int>()

  override fun onIndexChange(index: Int) {
    indexChanges.add(index)
  }

  override fun onSettle(index: Int) = Unit

  override fun onPositionChange(position: Double, index: Double) = Unit

  override fun onCloseRequest() = Unit
}
