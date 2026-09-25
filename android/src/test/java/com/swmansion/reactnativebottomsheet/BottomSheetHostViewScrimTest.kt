package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BottomSheetHostViewScrimTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `scrim is a full size child below sheet content and mirrors visual props`() {
    withActivity { activity ->
      val host = configuredHost(activity)
      val sheetChild = View(activity)
      host.addSheetChild(sheetChild, 0)
      val color = Color.argb(128, 12, 34, 56)
      host.setScrimColor(color)
      host.setScrimOpacities(listOf(0f, 0.4f))
      activity.setContentView(host)
      layout(host)

      val scrim = scrim(host)
      val sheetContainer = sheetContainer(host)
      assertSame(scrim, host.getChildAt(0))
      assertSame(sheetContainer, host.getChildAt(1))
      assertSame(sheetChild, host.getSheetChildAt(0))
      assertEquals(1, host.sheetChildCount)
      assertEquals(HOST_WIDTH, scrim.width)
      assertEquals(HOST_HEIGHT, scrim.height)
      assertEquals(color, (scrim.background as ColorDrawable).color)
      assertEquals(0.4f, scrim.alpha, 0.001f)
      assertEquals(View.VISIBLE, scrim.visibility)

      host.setScrimOpacities(listOf(0f, 0.7f))
      assertEquals(0.7f, scrim.alpha, 0.001f)
      assertFalse(scrim.isLayoutRequested)
    }
  }

  @Test
  fun `accessibility tree contains both sheet content and dismiss scrim`() {
    withActivity { activity ->
      val host = configuredHost(activity)
      val sheetChild =
        View(activity).apply {
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
          contentDescription = "Sheet content"
        }
      host.addSheetChild(sheetChild, 0)
      activity.setContentView(host)
      layout(host)

      val hostAccessibleChildren = arrayListOf<View>()
      host.addChildrenForAccessibility(hostAccessibleChildren)
      val sheetAccessibleChildren = arrayListOf<View>()
      sheetContainer(host).addChildrenForAccessibility(sheetAccessibleChildren)

      assertEquals(2, hostAccessibleChildren.size)
      assertTrue(hostAccessibleChildren.contains(scrim(host)))
      assertTrue(hostAccessibleChildren.contains(sheetContainer(host)))
      assertTrue(sheetAccessibleChildren.contains(sheetChild))
    }
  }

  @Test
  fun `scrim exposes dismiss button semantics above the sheet in traversal order`() {
    withActivity { activity ->
      val host = configuredHost(activity)
      activity.setContentView(host)
      layout(host)
      val scrim = scrim(host)
      val node = scrim.createAccessibilityNodeInfo()
      val bounds = Rect()
      node.getBoundsInScreen(bounds)
      val expectedBottom =
        HOST_HEIGHT - (OPEN_DETENT_DP * activity.resources.displayMetrics.density).roundToInt()

      assertEquals("android.widget.Button", node.className)
      assertEquals("Dismiss", node.contentDescription)
      assertTrue(node.isClickable)
      assertTrue(node.isDismissable)
      assertTrue(node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
      assertTrue(node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_DISMISS })
      assertEquals(Rect(0, 0, HOST_WIDTH, expectedBottom), bounds)
      assertEquals(sheetContainer(host).id, scrim.accessibilityTraversalAfter)
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, scrim.importantForAccessibility)
      assertTrue(scrim.isFocusable)
      assertTrue(scrim.isClickable)
    }
  }

  @Test
  fun `accessibility click and dismiss each snap closed without a close request`() {
    val accessibilityActions =
      listOf(AccessibilityNodeInfo.ACTION_CLICK, AccessibilityNodeInfo.ACTION_DISMISS)
    accessibilityActions.forEach { action ->
      withActivity { activity ->
        val listener = RecordingListener()
        val host = configuredHost(activity, listener = listener)
        activity.setContentView(host)
        layout(host)

        val scrim = scrim(host)
        assertTrue(scrim.performAccessibilityAction(action, null))
        assertEquals(listOf(0), listener.indexChanges)
        assertEquals(0, listener.closeRequestCount)
        assertFalse(scrim.isClickable)
      }
    }
  }

  @Test
  fun `confirm keys on focused scrim each snap closed exactly once`() {
    val confirmKeys =
      listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER)
    confirmKeys.forEach { keyCode ->
      withActivity { activity ->
        val listener = RecordingListener()
        val host = configuredHost(activity, listener = listener)
        activity.setContentView(host)
        layout(host)
        val scrim = scrim(host)
        assertTrue(scrim.requestFocus())

        assertTrue(host.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)))
        assertTrue(host.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode)))

        assertEquals(listOf(0), listener.indexChanges)
        assertEquals(0, listener.closeRequestCount)
      }
    }
  }

  @Test
  fun `focused sheet content receives confirm keys regardless of scrim availability`() {
    listOf(false, true).forEach { modal ->
      withActivity { activity ->
        val host = configuredHost(activity, modal = modal)
        var downCount = 0
        val sheetChild =
          object : View(activity) {
              override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
                downCount++
                return true
              }
            }
            .apply { isFocusableInTouchMode = true }
        host.addSheetChild(sheetChild, 0)
        activity.setContentView(host)
        sheetChild.measure(
          View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        )
        layout(host)
        assertTrue(sheetChild.requestFocus())

        host.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))
        assertEquals("Control: A reaches content when modal=$modal", 1, downCount)
        listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER)
          .forEachIndexed { index, keyCode ->
            host.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            assertEquals("Confirm key must reach content when modal=$modal", index + 2, downCount)
          }
      }
    }
  }

  @Test
  fun `host dismisses a scrim tap while sheet content keeps its own touch`() {
    withActivity { activity ->
      val listener = RecordingListener()
      val host = configuredHost(activity, listener = listener)
      activity.setContentView(host)
      layout(host)

      dispatchTap(host, y = 100f)

      assertEquals(listOf(0), listener.indexChanges)
      assertEquals(0, listener.closeRequestCount)
    }

    withActivity { activity ->
      val listener = RecordingListener()
      var contentTouchCount = 0
      val content =
        object : View(activity) {
          override fun onTouchEvent(event: MotionEvent): Boolean {
            contentTouchCount++
            return true
          }
        }
      val host = configuredHost(activity, listener = listener)
      host.addSheetChild(content, 0)
      val openDetentPx = (OPEN_DETENT_DP * activity.resources.displayMetrics.density).roundToInt()
      content.measure(
        View.MeasureSpec.makeMeasureSpec(HOST_WIDTH, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(openDetentPx, View.MeasureSpec.EXACTLY),
      )
      activity.setContentView(host)
      layout(host)

      dispatchTap(host, y = HOST_HEIGHT - openDetentPx / 2f)

      assertEquals(2, contentTouchCount)
      assertTrue(listener.indexChanges.isEmpty())
      assertFalse(scrim(host).onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 100f)))
    }
  }

  @Test
  fun `scrim disables accessibility and activation when dismissal is unavailable`() {
    listOf(
        HostState(modal = false),
        HostState(index = 0),
        HostState(openDetent = 1.0, openDetentKind = "percentage"),
        HostState(closedDetentProgrammatic = true),
      )
      .forEach { state ->
        withActivity { activity ->
          val listener = RecordingListener()
          val host =
            configuredHost(
              activity,
              listener = listener,
              modal = state.modal,
              index = state.index,
              openDetent = state.openDetent,
              openDetentKind = state.openDetentKind,
              closedDetentProgrammatic = state.closedDetentProgrammatic,
            )
          activity.setContentView(host)
          layout(host)
          val scrim = scrim(host)

          assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, scrim.importantForAccessibility)
          assertFalse(scrim.isFocusable)
          assertFalse(scrim.isClickable)
          assertFalse(scrim.performClick())
          assertTrue(listener.indexChanges.isEmpty())
          assertEquals(0, listener.closeRequestCount)
          assertFalse(scrim.visibility == View.GONE)
        }
      }
  }

  @Test
  fun `disabling a modal scrim clears focus and pressed state without removing the view`() {
    withActivity { activity ->
      val host = configuredHost(activity)
      activity.setContentView(host)
      layout(host)
      val scrim = scrim(host)
      assertTrue(scrim.requestFocus())
      scrim.isPressed = true

      host.modal = false

      assertFalse(scrim.hasFocus())
      assertFalse(scrim.isPressed)
      assertFalse(scrim.isFocusable)
      assertFalse(scrim.isClickable)
      assertEquals(View.INVISIBLE, scrim.visibility)
      assertEquals(2, host.childCount)
    }
  }

  private fun configuredHost(
    activity: Activity,
    listener: RecordingListener? = null,
    modal: Boolean = true,
    index: Int = 1,
    openDetent: Double = OPEN_DETENT_DP.toDouble(),
    openDetentKind: String = "points",
    closedDetentProgrammatic: Boolean = false,
  ) =
    BottomSheetHostView(activity).apply {
      this.listener = listener
      animateIn = false
      this.modal = modal
      setDetents(
        listOf(
          mapOf(
            "value" to 0.0,
            "kind" to "points",
            "programmatic" to closedDetentProgrammatic,
          ),
          mapOf("value" to openDetent, "kind" to openDetentKind, "programmatic" to false),
        )
      )
      setIndex(index)
    }

  private fun layout(host: ViewGroup) {
    host.measure(
      View.MeasureSpec.makeMeasureSpec(HOST_WIDTH, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(HOST_HEIGHT, View.MeasureSpec.EXACTLY),
    )
    host.layout(0, 0, HOST_WIDTH, HOST_HEIGHT)
  }

  private fun dispatchTap(host: View, y: Float) {
    host.dispatchTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, y))
    host.dispatchTouchEvent(motionEvent(MotionEvent.ACTION_UP, y))
  }

  private fun motionEvent(action: Int, y: Float): MotionEvent =
    MotionEvent.obtain(1L, 2L, action, 100f, y, 0)

  private fun scrim(host: BottomSheetHostView): View = host.getChildAt(0)

  private fun sheetContainer(host: BottomSheetHostView): ViewGroup = host.getChildAt(1) as ViewGroup

  private inline fun withActivity(block: (Activity) -> Unit) {
    Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
      val activity = controller.get()
      try {
        block(activity)
      } finally {
        (activity.findViewById<View>(android.R.id.content) as? ViewGroup)?.let { content ->
          (content.getChildAt(0) as? BottomSheetHostView)?.destroy()
        }
      }
    }
  }

  private data class HostState(
    val modal: Boolean = true,
    val index: Int = 1,
    val openDetent: Double = OPEN_DETENT_DP.toDouble(),
    val openDetentKind: String = "points",
    val closedDetentProgrammatic: Boolean = false,
  )

  private class RecordingListener : BottomSheetViewListener {
    val indexChanges = mutableListOf<Int>()
    var closeRequestCount = 0

    override fun onIndexChange(index: Int) {
      indexChanges.add(index)
    }

    override fun onSettle(index: Int) = Unit

    override fun onPositionChange(position: Double, index: Double) = Unit

    override fun onCloseRequest() {
      closeRequestCount++
    }
  }

  private companion object {
    const val HOST_WIDTH = 600
    const val HOST_HEIGHT = 1000
    const val OPEN_DETENT_DP = 100
  }
}
