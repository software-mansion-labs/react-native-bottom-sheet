// BridgeReactContext is required by the legacy-architecture test fixture.
@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.events.BatchEventDispatchedListener
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.EventDispatcher
import com.facebook.react.uimanager.events.EventDispatcherListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BottomSheetViewCloseRequestTest {
  private var nextEscapeDownTime = 1_000L

  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `Back emits exactly one request without changing the detent`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val indexChanges = listener.indexChanges.toList()
      val settles = listener.settles.toList()
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, listener.closeRequestCount)
      assertEquals(indexChanges, listener.indexChanges)
      assertEquals(settles, listener.settles)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(2, listener.closeRequestCount)
      sheet.destroy()
    }
  }

  @Config(sdk = [27, 35])
  @Test
  fun `Escape emits exactly one request without changing the detent`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val indexChanges = listener.indexChanges.toList()
      val settles = listener.settles.toList()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(1, listener.closeRequestCount)
      assertEquals(indexChanges, listener.indexChanges)
      assertEquals(settles, listener.settles)
      sheet.destroy()
    }
  }

  @Test
  fun `ordinary keys still reach a focused portal descendant`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val focusedView = EscapeConsumingView(activity).apply { isFocusableInTouchMode = true }
      sheet.addView(focusedView, ViewGroup.LayoutParams(1, 1))
      layoutView(sheet)
      assertTrue(focusedView.requestFocus())
      val downTime = 90L
      assertTrue(
        focusedView.dispatchKeyEvent(
          KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0)
        )
      )
      assertTrue(
        focusedView.dispatchKeyEvent(
          KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0)
        )
      )
      assertEquals(2, focusedView.ordinaryEventCount)
      assertEquals(0, listener.closeRequestCount)
      sheet.destroy()
    }
  }

  @Test
  fun `modal without a handler passes Back and Escape through`() {
    withActivity<ComponentActivity> { activity ->
      var navigationCount = 0
      activity.onBackPressedDispatcher.addCallback(navigationCallback { navigationCount++ })
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener, hasCloseRequestHandler = false)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = false)
      assertEquals(1, navigationCount)
      assertEquals(0, listener.closeRequestCount)
      sheet.destroy()
    }
  }

  @Test
  fun `Back during an animated close is consumed without emission`() {
    withActivity<ComponentActivity> { activity ->
      var navigationCount = 0
      activity.onBackPressedDispatcher.addCallback(navigationCallback { navigationCount++ })
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      val indexChanges = listener.indexChanges.toList()
      val settles = listener.settles.toList()
      sheet.setIndex(0)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(0, listener.closeRequestCount)
      assertEquals(0, navigationCount)
      assertEquals(indexChanges, listener.indexChanges)
      assertEquals(settles, listener.settles)
      sheet.destroy()
    }
  }

  @Test
  fun `predictive Back that loses eligibility commits as a no-op`() {
    withActivity<ComponentActivity> { activity ->
      var navigationCount = 0
      activity.onBackPressedDispatcher.addCallback(navigationCallback { navigationCount++ })
      val listener = CountingBottomSheetListener()
      val sheet = openPortalSheet(activity, listener)
      activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent())
      sheet.setIndex(0)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEquals(0, listener.closeRequestCount)
      assertEquals(0, navigationCount)
      sheet.destroy()
    }
  }

  @Test
  fun `view-tree dispatcher and lifecycle own request handling`() {
    withActivity<ComponentActivity> { activity ->
      var fallbackCount = 0
      val owner = MutableTestDispatcherOwner { fallbackCount++ }.apply { resume() }
      val container = FrameLayout(activity)
      container.setViewTreeOnBackPressedDispatcherOwner(owner)
      container.setViewTreeLifecycleOwner(owner)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      container.addView(sheet)
      activity.setContentView(container)
      layoutPortal(sheet)
      owner.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      owner.pause()
      owner.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = false)
      owner.resumeFromPause()
      owner.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(1, fallbackCount)
      assertEquals(4, listener.closeRequestCount)
      sheet.destroy()
    }
  }

  @Test
  fun `detach and reattach clear then restore request handling`() {
    withActivity<ComponentActivity> { activity ->
      var navigationCount = 0
      activity.onBackPressedDispatcher.addCallback(navigationCallback { navigationCount++ })
      val root = FrameLayout(activity)
      activity.setContentView(root)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(activity, listener)
      root.addView(sheet)
      layoutPortal(sheet)
      activity.onBackPressedDispatcher.onBackPressed()
      root.removeView(sheet)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = false)
      root.addView(sheet)
      layoutPortal(sheet)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(1, navigationCount)
      assertEquals(3, listener.closeRequestCount)
      sheet.destroy()
    }
  }

  @Test
  fun `most recently attached portal receives Back and Escape`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperSheet = configuredOpenSheet(activity, upperListener)
      root.addView(lowerSheet)
      root.addView(upperSheet)
      activity.setContentView(root)
      layoutPortal(lowerSheet)
      layoutPortal(upperSheet)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(0, lowerListener.closeRequestCount)
      assertEquals(2, upperListener.closeRequestCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `duplicate zero detent retains ownership through closing`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperSheet =
        configuredOpenSheet(activity, upperListener).apply {
          setDetents(
            listOf(
              mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
              mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
              mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
            )
          )
          setIndex(2)
        }
      root.addView(lowerSheet)
      root.addView(upperSheet)
      activity.setContentView(root)
      layoutPortal(lowerSheet)
      layoutPortal(upperSheet)
      upperSheet.setIndex(1)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(0, lowerListener.closeRequestCount)
      assertEquals(0, upperListener.closeRequestCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `content detent changes ownership at zero and positive heights`() {
    withActivity<ComponentActivity> { activity ->
      val root = FrameLayout(activity)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperContent = MutableContentHeightView(activity)
      val upperSheet = configuredContentSheet(activity, upperListener, upperContent)
      root.addView(lowerSheet)
      root.addView(upperSheet)
      activity.setContentView(root)
      layoutView(root)
      lowerSheet.isFocusableInTouchMode = true
      assertTrue(lowerSheet.requestFocus())
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(2, lowerListener.closeRequestCount)
      assertEquals(0, upperListener.closeRequestCount)
      upperContent.contentHeight = 300
      upperContent.requestLayout()
      layoutView(root)
      upperSheet.setDetents(contentDetents())
      assertEquals(300, upperContent.markerTop)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(2, lowerListener.closeRequestCount)
      assertEquals(2, upperListener.closeRequestCount)
      upperContent.contentHeight = 0
      upperContent.requestLayout()
      layoutView(root)
      upperSheet.setDetents(contentDetents())
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(4, lowerListener.closeRequestCount)
      assertEquals(2, upperListener.closeRequestCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `inactive upper sheet passes requests lower and resumes ownership`() {
    withActivity<ComponentActivity> { activity ->
      val upperOwner = MutableTestDispatcherOwner().apply { resume() }
      val root = FrameLayout(activity)
      val upperContainer = FrameLayout(activity)
      upperContainer.setViewTreeOnBackPressedDispatcherOwner(upperOwner)
      upperContainer.setViewTreeLifecycleOwner(upperOwner)
      val lowerListener = CountingBottomSheetListener()
      val upperListener = CountingBottomSheetListener()
      val lowerSheet = configuredOpenSheet(activity, lowerListener)
      val upperSheet = configuredOpenSheet(activity, upperListener)
      root.addView(lowerSheet)
      upperContainer.addView(upperSheet)
      root.addView(upperContainer)
      activity.setContentView(root)
      layoutView(root)
      lowerSheet.isFocusableInTouchMode = true
      assertTrue(lowerSheet.requestFocus())
      upperOwner.pause()
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(2, lowerListener.closeRequestCount)
      assertEquals(0, upperListener.closeRequestCount)
      upperOwner.resumeFromPause()
      upperOwner.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(2, lowerListener.closeRequestCount)
      assertEquals(2, upperListener.closeRequestCount)
      upperSheet.destroy()
      lowerSheet.destroy()
    }
  }

  @Test
  fun `native overlay routes Back and Escape without automatic closing`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val focusedView = EscapeConsumingView(activity).apply { isFocusableInTouchMode = true }
      val fixture = openNativeOverlaySheet(activity, listener) { it.addSheetChild(focusedView, 0) }
      val indexChanges = listener.indexChanges.toList()
      val settles = listener.settles.toList()
      fixture.dialog.onBackPressedDispatcher.onBackPressed()
      assertEscape(fixture.dialog::dispatchKeyEvent, expectedHandled = true)
      assertEquals(2, listener.closeRequestCount)
      assertEquals(0, focusedView.escapeEventCount)
      assertEquals(indexChanges, listener.indexChanges)
      assertEquals(settles, listener.settles)
      assertTrue(fixture.dialog.isShowing)
      fixture.destroy()
    }
  }

  @Test
  fun `open native overlay without a handler is interactive and passes close input through`() {
    withActivity<ComponentActivity> { activity ->
      var navigationCount = 0
      activity.onBackPressedDispatcher.addCallback(navigationCallback { navigationCount++ })
      val listener = CountingBottomSheetListener()
      val fixture = openNativeOverlaySheet(activity, listener, hasCloseRequestHandler = false)
      val windowAttributes = requireNotNull(fixture.dialog.window).attributes
      assertEquals(
        0,
        windowAttributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      )
      assertEquals(
        0,
        windowAttributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
      )
      assertEquals(1f, windowAttributes.alpha)
      fixture.dialog.onBackPressedDispatcher.onBackPressed()
      assertEscape(fixture.dialog::dispatchKeyEvent, expectedHandled = false)
      assertEquals(1, navigationCount)
      assertEquals(0, listener.closeRequestCount)
      assertTrue(fixture.dialog.isShowing)
      fixture.destroy()
    }
  }

  @Test
  fun `native overlay predictive Back stays ineligible after handler restoration`() {
    withActivity<ComponentActivity> { activity ->
      val listener = CountingBottomSheetListener()
      val fixture = openNativeOverlaySheet(activity, listener)
      fixture.dialog.onBackPressedDispatcher.dispatchOnBackStarted(backEvent())
      fixture.sheet.setHasCloseRequestHandler(false)
      fixture.sheet.setHasCloseRequestHandler(true)
      fixture.dialog.onBackPressedDispatcher.onBackPressed()
      assertEquals(0, listener.closeRequestCount)
      fixture.dialog.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, listener.closeRequestCount)
      fixture.destroy()
    }
  }

  @Test
  fun `switching between portal and native overlay changes the active request path`() {
    withActivity<ComponentActivity> { activity ->
      val reactContext = BridgeReactContext(activity.applicationContext)
      reactContext.onHostResume(activity)
      val themedContext = ThemedReactContext(reactContext, activity, "test", 1)
      var navigationCount = 0
      activity.onBackPressedDispatcher.addCallback(navigationCallback { navigationCount++ })
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(themedContext, listener)
      sheet.eventDispatcher = NoOpEventDispatcher
      activity.setContentView(sheet)
      layoutPortal(sheet)
      sheet.onHostResume()
      sheet.setNativeOverlay(true)
      shadowOf(Looper.getMainLooper()).idle()
      val dialog = requireNotNull(ShadowDialog.getLatestDialog()) as ComponentDialog
      layoutView(requireNotNull(dialog.window).decorView)
      activity.onBackPressedDispatcher.onBackPressed()
      dialog.onBackPressedDispatcher.onBackPressed()
      assertEquals(1, navigationCount)
      assertEquals(1, listener.closeRequestCount)
      sheet.setNativeOverlay(false)
      layoutPortal(sheet)
      activity.onBackPressedDispatcher.onBackPressed()
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(1, navigationCount)
      assertEquals(3, listener.closeRequestCount)
      sheet.destroy()
      reactContext.onHostDestroy()
    }
  }

  @Config(shadows = [ThrowingDialogShadow::class])
  @Test
  fun `dialog show failure restores portal Escape handling`() {
    withActivity<ComponentActivity> { activity ->
      val reactContext = BridgeReactContext(activity.applicationContext)
      reactContext.onHostResume(activity)
      val themedContext = ThemedReactContext(reactContext, activity, "test", 1)
      val listener = CountingBottomSheetListener()
      val sheet = configuredOpenSheet(themedContext, listener)
      sheet.eventDispatcher = NoOpEventDispatcher
      activity.setContentView(sheet)
      layoutPortal(sheet)
      sheet.setNativeOverlay(true)
      layoutPortal(sheet)
      assertEscape(activity::dispatchKeyEvent, expectedHandled = true)
      assertEquals(1, listener.closeRequestCount)
      sheet.destroy()
      reactContext.onHostDestroy()
    }
  }

  private fun configuredOpenSheet(
    context: Context,
    listener: CountingBottomSheetListener,
    hasCloseRequestHandler: Boolean = true,
  ) =
    BottomSheetView(context).apply {
      this.listener = listener
      animateIn = false
      modal = true
      setHasCloseRequestHandler(hasCloseRequestHandler)
      setDetents(
        listOf(
          mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
          mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
        )
      )
      setIndex(1)
    }

  private fun configuredContentSheet(
    context: Context,
    listener: CountingBottomSheetListener,
    content: MutableContentHeightView,
  ) =
    BottomSheetView(context).apply {
      this.listener = listener
      animateIn = false
      animateContentHeight = false
      modal = true
      setHasCloseRequestHandler(true)
      addSheetChild(content, 0)
      setDetents(contentDetents())
      setIndex(1)
    }

  private fun contentDetents(): List<Map<String, Any>> =
    listOf(
      mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
      mapOf("value" to 0.0, "kind" to "content", "programmatic" to false),
    )

  private fun openPortalSheet(
    activity: Activity,
    listener: CountingBottomSheetListener,
    hasCloseRequestHandler: Boolean = true,
  ) =
    configuredOpenSheet(activity, listener, hasCloseRequestHandler).also {
      activity.setContentView(it)
      layoutPortal(it)
    }

  private fun openNativeOverlaySheet(
    activity: ComponentActivity,
    listener: CountingBottomSheetListener,
    hasCloseRequestHandler: Boolean = true,
    configure: (BottomSheetView) -> Unit = {},
  ): NativeOverlayTestFixture {
    val reactContext = BridgeReactContext(activity.applicationContext)
    reactContext.onHostResume(activity)
    val sheet =
      configuredOpenSheet(
        ThemedReactContext(reactContext, activity, "test", 1),
        listener,
        hasCloseRequestHandler,
      )
    sheet.setScrimColor(Color.BLACK)
    sheet.setScrimOpacities(listOf(0f, 1f))
    configure(sheet)
    sheet.eventDispatcher = NoOpEventDispatcher
    activity.setContentView(sheet)
    layoutPortal(sheet)
    sheet.setNativeOverlay(true)
    shadowOf(Looper.getMainLooper()).idle()
    val dialog = requireNotNull(ShadowDialog.getLatestDialog()) as ComponentDialog
    layoutView(requireNotNull(dialog.window).decorView)
    // The test context resumed before BottomSheetView registered its lifecycle listener.
    sheet.onHostResume()
    return NativeOverlayTestFixture(sheet, dialog, reactContext)
  }

  private fun layoutPortal(sheet: BottomSheetView) {
    layoutView(sheet)
    sheet.isFocusableInTouchMode = true
    assertTrue(sheet.requestFocus())
  }

  private fun layoutView(view: View) {
    val width = 1080
    val height = 1920
    view.measure(
      View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, width, height)
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun navigationCallback(onBack: () -> Unit) =
    object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() = onBack()
    }

  private fun assertEscape(dispatchKeyEvent: (KeyEvent) -> Boolean, expectedHandled: Boolean) {
    val downTime = nextEscapeDownTime
    nextEscapeDownTime += 10L
    assertEquals(expectedHandled, dispatchKeyEvent(escapeDown(downTime)))
    assertEquals(expectedHandled, dispatchKeyEvent(escapeUp(downTime)))
  }

  private fun escapeDown(downTime: Long) =
    KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0)

  private fun escapeUp(downTime: Long) =
    KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0)

  private fun backEvent() = BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)

  private inline fun <reified T : Activity> withActivity(block: (T) -> Unit) {
    val controller = Robolectric.buildActivity(T::class.java).setup()
    try {
      block(controller.get())
    } finally {
      controller.close()
    }
  }
}

@Implements(Dialog::class)
internal class ThrowingDialogShadow {
  @Implementation protected fun show(): Nothing = throw RuntimeException("dialog show failed")
}

private data class NativeOverlayTestFixture(
  val sheet: BottomSheetView,
  val dialog: ComponentDialog,
  val reactContext: BridgeReactContext,
) {
  fun destroy() {
    sheet.destroy()
    reactContext.onHostDestroy()
  }
}

private class MutableTestDispatcherOwner(fallback: () -> Unit = {}) : OnBackPressedDispatcherOwner {
  private val lifecycleRegistry = LifecycleRegistry(this)
  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  override val onBackPressedDispatcher = OnBackPressedDispatcher(fallback)

  fun resume() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }

  fun pause() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
  }

  fun resumeFromPause() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }
}

private class CountingBottomSheetListener : BottomSheetViewListener {
  var closeRequestCount = 0
  val indexChanges = mutableListOf<Int>()
  val settles = mutableListOf<Int>()

  override fun onIndexChange(index: Int) {
    indexChanges.add(index)
  }

  override fun onSettle(index: Int) {
    settles.add(index)
  }

  override fun onPositionChange(position: Double, index: Double) = Unit

  override fun onCloseRequest() {
    closeRequestCount++
  }
}

private class MutableContentHeightView(context: Context) : ViewGroup(context) {
  private val marker = View(context)
  var contentHeight = 0
    set(value) {
      field = value
      marker.layout(0, value, 1, value + 1)
      requestLayout()
    }

  val markerTop: Int
    get() = marker.top

  init {
    addView(marker)
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
    marker.layout(0, contentHeight, 1, contentHeight + 1)
  }
}

private class EscapeConsumingView(context: Context, private val consumesEscape: Boolean = true) :
  View(context) {
  var escapeEventCount = 0
  var ordinaryEventCount = 0

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
      escapeEventCount++
      return consumesEscape
    }
    ordinaryEventCount++
    return true
  }
}

private object NoOpEventDispatcher : EventDispatcher {
  override fun dispatchEvent(event: Event<*>) = Unit

  override fun dispatchAllEvents() = Unit

  override fun addListener(listener: EventDispatcherListener) = Unit

  override fun removeListener(listener: EventDispatcherListener) = Unit

  override fun addBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  override fun removeBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  @Suppress("OVERRIDE_DEPRECATION") override fun onCatalystInstanceDestroyed() = Unit
}
