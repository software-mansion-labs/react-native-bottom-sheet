package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.dynamicanimation.animation.SpringAnimation
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w600dp-h1000dp-mdpi")
class BottomSheetHostViewScrimTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `host keeps full size visual and dismiss layers below sheet content`() {
    withActivity { activity ->
      val host = configuredHost(activity)
      val sheetChild = View(activity)
      host.addSheetChild(sheetChild, 0)
      val color = Color.argb(128, 12, 34, 56)
      host.setScrimColor(color)
      host.setScrimOpacities(listOf(0f, 0.4f))
      activity.setContentView(host)
      layout(host)

      val visualScrim = visualScrim(host)
      val dismiss = dismissAccessibilityView(host)
      val sheetContainer = sheetContainer(host)
      assertEquals(3, host.childCount)
      assertSame(visualScrim, host.getChildAt(0))
      assertSame(dismiss, host.getChildAt(1))
      assertSame(sheetContainer, host.getChildAt(2))
      assertSame(sheetChild, host.getSheetChildAt(0))
      assertEquals(1, host.sheetChildCount)
      assertEquals(HOST_WIDTH, visualScrim.width)
      assertEquals(HOST_HEIGHT, visualScrim.height)
      assertEquals(HOST_WIDTH, dismiss.width)
      assertEquals(HOST_HEIGHT, dismiss.height)
      assertEquals(color, (visualScrim.background as ColorDrawable).color)
      assertEquals(0.4f, visualScrim.alpha, 0.001f)
      assertEquals(View.VISIBLE, visualScrim.visibility)
      assertEquals(null, dismiss.background)
      assertEquals(1f, dismiss.alpha, 0f)
      assertEquals(0f, dismiss.pivotY, 0f)

      host.setScrimOpacities(listOf(0f, 0.7f))
      assertEquals(0.7f, visualScrim.alpha, 0.001f)
      assertEquals(1f, dismiss.alpha, 0f)
      assertFalse(visualScrim.isLayoutRequested)
      assertFalse(dismiss.isLayoutRequested)
    }
  }

  @Test
  fun `accessibility tree exposes sheet content and Dismiss with a traversal hint`() {
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
      assertFalse(hostAccessibleChildren.contains(visualScrim(host)))
      assertTrue(hostAccessibleChildren.contains(dismissAccessibilityView(host)))
      assertTrue(hostAccessibleChildren.contains(sheetContainer(host)))
      assertTrue(sheetAccessibleChildren.contains(sheetChild))
      // This verifies the traversal hint, not TalkBack's effective descendant order.
      assertEquals(
        sheetContainer(host).createAccessibilityNodeInfo(),
        dismissAccessibilityView(host).createAccessibilityNodeInfo().traversalAfter,
      )
      assertEquals(
        sheetContainer(host).id,
        dismissAccessibilityView(host).accessibilityTraversalAfter,
      )
    }
  }

  @Test
  fun `Dismiss default node and transformed view end at stable sheet top`() {
    withActivity { activity ->
      val host = configuredHost(activity)
      activity.setContentView(host)
      layout(host)
      val visualScrim = visualScrim(host)
      val dismiss = dismissAccessibilityView(host)
      val node = dismiss.createAccessibilityNodeInfo()
      val nodeBounds = Rect()
      val viewBounds = Rect()
      node.getBoundsInScreen(nodeBounds)
      dismiss.getGlobalVisibleRect(viewBounds)
      val expectedBottom =
        HOST_HEIGHT - (OPEN_DETENT_DP * activity.resources.displayMetrics.density).roundToInt()
      val expectedBounds = Rect(0, 0, HOST_WIDTH, expectedBottom)

      assertEquals("android.widget.Button", node.className)
      assertEquals("Dismiss", node.contentDescription)
      assertTrue(node.isClickable)
      assertTrue(node.isDismissable)
      assertTrue(node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
      assertTrue(node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_DISMISS })
      assertEquals(expectedBounds, nodeBounds)
      assertEquals(expectedBounds, viewBounds)
      assertEquals(expectedBottom.toFloat() / HOST_HEIGHT, dismiss.scaleY, 0.001f)
      assertEquals(sheetContainer(host).id, dismiss.accessibilityTraversalAfter)
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, dismiss.importantForAccessibility)
      assertTrue(dismiss.isFocusable)
      assertTrue(dismiss.isClickable)

      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, visualScrim.importantForAccessibility)
      assertEquals(null, visualScrim.contentDescription)
      assertFalse(visualScrim.isFocusable)
      assertFalse(visualScrim.isClickable)
      assertFalse(visualScrim.createAccessibilityNodeInfo().isDismissable)
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

        val dismiss = dismissAccessibilityView(host)
        assertTrue(dismiss.performAccessibilityAction(action, null))
        assertEquals(listOf(0), listener.indexChanges)
        assertEquals(0, listener.closeRequestCount)
        assertFalse(dismiss.isClickable)
      }
    }
  }

  @Test
  fun `confirm keys on focused Dismiss control each snap closed exactly once`() {
    val confirmKeys =
      listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER)
    confirmKeys.forEach { keyCode ->
      withActivity { activity ->
        val listener = RecordingListener()
        val host = configuredHost(activity, listener = listener)
        activity.setContentView(host)
        layout(host)
        val dismiss = dismissAccessibilityView(host)
        assertTrue(dismiss.requestFocus())

        assertTrue(host.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)))
        assertTrue(host.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode)))

        assertEquals(listOf(0), listener.indexChanges)
        assertEquals(0, listener.closeRequestCount)
      }
    }
  }

  @Test
  fun `focused sheet content receives confirm keys regardless of Dismiss control availability`() {
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
      assertFalse(visualScrim(host).onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 100f)))
      assertFalse(
        dismissAccessibilityView(host).onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 100f))
      )
    }
  }

  @Test
  fun `Dismiss requires positive stable geometry and direct dismissal`() {
    listOf(
        HostState(modal = false),
        HostState(index = 0),
        HostState(openDetent = 1.0, openDetentKind = "percentage"),
        HostState(openDetent = 2_000.0),
        HostState(closedDetentProgrammatic = true),
        HostState(scrimOpacity = 0f),
        HostState(includeClosedDetent = false, index = 0),
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
              scrimOpacity = state.scrimOpacity,
              includeClosedDetent = state.includeClosedDetent,
            )
          val dismissBeforeLayout = dismissAccessibilityView(host)
          assertEquals(0f, dismissBeforeLayout.scaleY, 0f)
          assertEquals(View.INVISIBLE, dismissBeforeLayout.visibility)
          assertEquals(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO,
            dismissBeforeLayout.importantForAccessibility,
          )
          activity.setContentView(host)
          layout(host)
          val dismiss = dismissAccessibilityView(host)

          assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, dismiss.importantForAccessibility)
          assertFalse(dismiss.isFocusable)
          assertFalse(dismiss.isClickable)
          assertFalse(dismiss.performClick())
          assertTrue(listener.indexChanges.isEmpty())
          assertEquals(0, listener.closeRequestCount)
          assertEquals(View.INVISIBLE, dismiss.visibility)
        }
      }
  }

  @Test
  fun `Dismiss becomes unavailable when host layout reaches zero size`() {
    withActivity { activity ->
      val host = configuredHost(activity)
      activity.setContentView(host)
      layout(host)
      val dismiss = dismissAccessibilityView(host)
      assertTrue(dismiss.isClickable)

      layout(host, width = 0, height = 0)

      assertEquals(0f, dismiss.scaleY, 0f)
      assertEquals(View.INVISIBLE, dismiss.visibility)
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, dismiss.importantForAccessibility)
      assertFalse(dismiss.isFocusable)
      assertFalse(dismiss.isClickable)
    }
  }

  @Test
  fun `direct drag keeps Dismiss control geometry synchronized without relayout`() {
    withActivity { activity ->
      val host = threeDetentHost(activity)
      activity.setContentView(host)
      layout(host)
      val dismiss = dismissAccessibilityView(host)
      assertEquals(0.9f, dismiss.scaleY, 0.001f)

      assertFalse(host.onInterceptTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 950f)))
      assertTrue(host.onInterceptTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, 850f)))
      assertTrue(host.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, 850f)))
      assertTrue(host.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, 800f)))

      assertEquals(850f, sheetContainer(host).translationY, 0.001f)
      assertEquals(0.85f, dismiss.scaleY, 0.001f)
      assertFalse(host.isLayoutRequested)
      assertFalse(dismiss.isLayoutRequested)
    }
  }

  @Test
  fun `nested movement keeps Dismiss control geometry synchronized`() {
    withActivity { activity ->
      val scrollView =
        object : ScrollView(activity) {
          override fun canScrollVertically(direction: Int): Boolean = true
        }
      scrollView.addView(View(activity), ViewGroup.LayoutParams(HOST_WIDTH, 600))
      val host = threeDetentHost(activity).apply { addSheetChild(scrollView, 0) }
      scrollView.measure(
        View.MeasureSpec.makeMeasureSpec(HOST_WIDTH, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
      )
      activity.setContentView(host)
      layout(host)
      val dismiss = dismissAccessibilityView(host)

      assertFalse(host.onInterceptTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 950f)))
      assertTrue(
        host.onStartNestedScroll(
          scrollView,
          scrollView,
          ViewCompat.SCROLL_AXIS_VERTICAL,
          ViewCompat.TYPE_TOUCH,
        )
      )
      host.onNestedScrollAccepted(
        scrollView,
        scrollView,
        ViewCompat.SCROLL_AXIS_VERTICAL,
        ViewCompat.TYPE_TOUCH,
      )
      val consumed = IntArray(2)
      host.onNestedPreScroll(scrollView, 0, 50, consumed, ViewCompat.TYPE_TOUCH)

      assertEquals(50, consumed[1])
      assertEquals(850f, sheetContainer(host).translationY, 0.001f)
      assertEquals(0.85f, dismiss.scaleY, 0.001f)
      assertFalse(dismiss.isLayoutRequested)
    }
  }

  @Test
  fun `detach hides Dismiss control until reattach layout publishes fresh geometry`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val host = configuredHost(activity)
      root.addView(host)
      activity.setContentView(root)
      layout(root)
      val dismiss = dismissAccessibilityView(host)
      assertEquals(View.VISIBLE, dismiss.visibility)
      assertEquals(0.9f, dismiss.scaleY, 0.001f)

      root.removeView(host)

      assertEquals(View.INVISIBLE, dismiss.visibility)
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, dismiss.importantForAccessibility)
      assertFalse(dismiss.isFocusable)
      assertFalse(dismiss.isClickable)
      assertEquals(0f, dismiss.scaleY, 0f)

      root.addView(host)
      assertEquals(View.INVISIBLE, dismiss.visibility)
      assertEquals(0f, dismiss.scaleY, 0f)

      shadowOf(Looper.getMainLooper()).idle()

      assertEquals(View.VISIBLE, dismiss.visibility)
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, dismiss.importantForAccessibility)
      assertEquals(0.9f, dismiss.scaleY, 0.001f)
    }
  }

  @Test
  fun `opening and open to open spring publish moving and stable Dismiss control geometry`() {
    withActivity { activity ->
      val host = threeDetentHost(activity, animateIn = true)
      activity.setContentView(host)
      layout(host)
      val dismiss = dismissAccessibilityView(host)
      var hostLayoutCount = 0
      var dismissLayoutCount = 0
      host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> hostLayoutCount++ }
      dismiss.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> dismissLayoutCount++ }

      val opening = activeAnimationDriver(host)
      opening.advance(frameCount = 2)

      val openingTop = sheetTop(host)
      assertTrue(openingTop in 900f..999.999f)
      assertEquals(openingTop / host.height, dismiss.scaleY, 0.001f)
      opening.finish()

      assertEquals(900f, sheetContainer(host).translationY, 0.001f)
      assertEquals(0.9f, dismiss.scaleY, 0.001f)
      assertEquals(View.VISIBLE, dismiss.visibility)

      host.setIndex(2)
      val openToOpen = activeAnimationDriver(host)
      openToOpen.advance(frameCount = 2)

      val movingTop = sheetTop(host)
      assertTrue(movingTop in 700f..899.999f)
      assertEquals(movingTop / host.height, dismiss.scaleY, 0.001f)
      openToOpen.finish()

      assertEquals(700f, sheetContainer(host).translationY, 0.001f)
      assertEquals(0.7f, dismiss.scaleY, 0.001f)
      assertEquals(0, hostLayoutCount)
      assertEquals(0, dismissLayoutCount)
      assertFalse(dismiss.isLayoutRequested)
    }
  }

  @Test
  fun `reanchor and closing reopen publish fresh Dismiss control geometry before exposure`() {
    withActivity { activity ->
      val host = configuredHost(activity)
      activity.setContentView(host)
      layout(host)
      val dismiss = dismissAccessibilityView(host)

      host.setDetents(
        listOf(
          mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
          mapOf("value" to 200.0, "kind" to "points", "programmatic" to false),
        )
      )
      activeAnimationDriver(host).finish()

      assertEquals(800f, sheetTop(host), 0.001f)
      assertEquals(0.8f, dismiss.scaleY, 0.001f)
      assertTrue(dismiss.requestFocus())
      dismiss.isPressed = true

      host.setIndex(0)

      assertEquals(View.INVISIBLE, dismiss.visibility)
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, dismiss.importantForAccessibility)
      assertFalse(dismiss.hasFocus())
      assertFalse(dismiss.isPressed)
      val closing = activeAnimationDriver(host)
      closing.advance(frameCount = 2)
      val closingTop = sheetTop(host)
      assertTrue(closingTop in 800.001f..1000f)

      host.setIndex(1)

      assertEquals(closingTop / host.height, dismiss.scaleY, 0.001f)
      assertEquals(View.VISIBLE, dismiss.visibility)
      assertTrue(dismiss.isClickable)
      val reopening = activeAnimationDriver(host)
      reopening.advance(frameCount = 2)

      val reopeningTop = sheetTop(host)
      assertEquals(reopeningTop / host.height, dismiss.scaleY, 0.001f)
      assertEquals(View.VISIBLE, dismiss.visibility)
      assertTrue(dismiss.isClickable)
      reopening.finish()

      assertEquals(800f, sheetTop(host), 0.001f)
      assertEquals(0.8f, dismiss.scaleY, 0.001f)
    }
  }

  @Test
  fun `changed status bar insets republish Dismiss control bounds`() {
    withActivity(visible = true) { activity ->
      val host =
        configuredHost(
          activity,
          openDetent = 1.0,
          openDetentKind = "percentage",
          extendUnderStatusBar = false,
        )
      activity.setContentView(host)
      layout(host)
      val dismiss = dismissAccessibilityView(host)
      assertTrue(host.isAttachedToWindow)
      val location = IntArray(2)
      host.getLocationInWindow(location)
      assertEquals(0, location[1])
      assertEquals(0f, dismiss.scaleY, 0f)
      assertEquals(View.INVISIBLE, dismiss.visibility)

      val insets =
        WindowInsets.Builder()
          .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 100, 0, 0))
          .setVisible(WindowInsets.Type.statusBars(), true)
          .build()
      host.onApplyWindowInsets(insets)

      assertEquals(100, sheetContainer(host).top)
      assertEquals(0f, sheetContainer(host).translationY, 0.001f)
      assertEquals(100f, sheetContainer(host).top + sheetContainer(host).translationY, 0.001f)
      assertEquals(0.1f, dismiss.scaleY, 0.001f)
      assertEquals(View.VISIBLE, dismiss.visibility)
    }
  }

  @Test
  fun `disabling Dismiss clears focus and pressed state without removing private children`() {
    withActivity { activity ->
      val host = configuredHost(activity)
      activity.setContentView(host)
      layout(host)
      val dismiss = dismissAccessibilityView(host)
      assertTrue(dismiss.requestFocus())
      dismiss.isPressed = true

      host.modal = false

      assertFalse(dismiss.hasFocus())
      assertFalse(dismiss.isPressed)
      assertFalse(dismiss.isFocusable)
      assertFalse(dismiss.isClickable)
      assertEquals(View.INVISIBLE, dismiss.visibility)
      assertEquals(3, host.childCount)
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
    scrimOpacity: Float = 1f,
    includeClosedDetent: Boolean = true,
    extendUnderStatusBar: Boolean = false,
  ) =
    BottomSheetHostView(activity).apply {
      this.listener = listener
      animateIn = false
      this.modal = modal
      this.extendUnderStatusBar = extendUnderStatusBar
      setScrimOpacities(listOf(0f, scrimOpacity))
      val closedDetents =
        if (includeClosedDetent) {
          listOf(
            mapOf<String, Any>(
              "value" to 0.0,
              "kind" to "points",
              "programmatic" to closedDetentProgrammatic,
            )
          )
        } else {
          emptyList()
        }
      setDetents(
        closedDetents +
          mapOf<String, Any>(
            "value" to openDetent,
            "kind" to openDetentKind,
            "programmatic" to false,
          )
      )
      setIndex(index)
    }

  private fun threeDetentHost(activity: Activity, animateIn: Boolean = false) =
    BottomSheetHostView(activity).apply {
      this.animateIn = animateIn
      modal = true
      setScrimOpacities(listOf(0f, 1f, 1f))
      setDetents(
        listOf(
          mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
          mapOf("value" to 100.0, "kind" to "points", "programmatic" to false),
          mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
        )
      )
      setIndex(1)
    }

  private fun layout(
    host: ViewGroup,
    width: Int = HOST_WIDTH,
    height: Int = HOST_HEIGHT,
  ) {
    host.measure(
      View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    host.layout(0, 0, width, height)
  }

  private fun sheetTop(host: BottomSheetHostView): Float =
    sheetContainer(host).top + sheetContainer(host).translationY

  private fun activeAnimationDriver(host: BottomSheetHostView) = AnimationDriver(host)

  private fun dispatchTap(host: View, y: Float) {
    host.dispatchTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, y))
    host.dispatchTouchEvent(motionEvent(MotionEvent.ACTION_UP, y))
  }

  private fun motionEvent(action: Int, y: Float): MotionEvent =
    MotionEvent.obtain(1L, 2L, action, 100f, y, 0)

  private fun visualScrim(host: BottomSheetHostView): View = host.getChildAt(0)

  private fun dismissAccessibilityView(host: BottomSheetHostView): View = host.getChildAt(1)

  private fun sheetContainer(host: BottomSheetHostView): ViewGroup = host.getChildAt(2) as ViewGroup

  private inline fun withActivity(visible: Boolean = false, block: (Activity) -> Unit) {
    Robolectric.buildActivity(Activity::class.java).setup().let { controller ->
      if (visible) {
        WindowCompat.setDecorFitsSystemWindows(controller.get().window, false)
        controller.visible()
      }
      controller.use {
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
  }

  private data class HostState(
    val modal: Boolean = true,
    val index: Int = 1,
    val openDetent: Double = OPEN_DETENT_DP.toDouble(),
    val openDetentKind: String = "points",
    val closedDetentProgrammatic: Boolean = false,
    val scrimOpacity: Float = 1f,
    val includeClosedDetent: Boolean = true,
  )

  private class AnimationDriver(host: BottomSheetHostView) {
    private val animation =
      requireNotNull(ReflectionHelpers.getField<SpringAnimation?>(host, "activeAnimation"))
    private var frameTimeMillis = SystemClock.uptimeMillis()

    fun advance(frameCount: Int) {
      repeat(frameCount) {
        frameTimeMillis += 16
        assertFalse(
          "SpringAnimation settled before the observed frame",
          animation.doAnimationFrame(frameTimeMillis),
        )
      }
    }

    fun finish() {
      repeat(240) {
        frameTimeMillis += 16
        if (animation.doAnimationFrame(frameTimeMillis)) return
      }
      throw AssertionError("SpringAnimation did not settle within 240 frames")
    }
  }

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
