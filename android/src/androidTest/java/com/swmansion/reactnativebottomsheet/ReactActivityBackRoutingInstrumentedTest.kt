package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.swmansion.reactnativebottomsheet.closerequest.CloseRequestInputState
import com.swmansion.reactnativebottomsheet.closerequest.OverlayCloseRequestController
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReactActivityBackRoutingInstrumentedTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  /**
   * Without OnBackInvokedCallback routing (API 35 and below), ReactActivity sends Back to React
   * Native before the dispatcher; the portal must still receive it first.
   */
  @Test
  fun portalBackWithHandlerEmitsBeforeReactNativeAndKeepsActivity() {
    RecordingReactActivityDelegate.backEntryCount.set(0)
    val closeRequestCount = AtomicInteger()
    lateinit var sheet: BottomSheetView

    ActivityScenario.launch(RecordingReactActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        sheet = openPortalSheet(activity, closeRequestCount, hasCloseRequestHandler = true)
        activity.setContentView(sheet)
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
      instrumentation.waitForIdleSync()

      assertEquals(1, closeRequestCount.get())
      assertEquals(0, RecordingReactActivityDelegate.backEntryCount.get())
      scenario.onActivity { activity -> assertFalse(activity.isFinishing) }

      scenario.onActivity { sheet.destroy() }
    }
  }

  @Test
  fun portalBackWithoutHandlerEntersReactNativeOnce() {
    RecordingReactActivityDelegate.backEntryCount.set(0)
    val closeRequestCount = AtomicInteger()
    lateinit var sheet: BottomSheetView

    ActivityScenario.launch(RecordingReactActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        sheet = openPortalSheet(activity, closeRequestCount, hasCloseRequestHandler = false)
        activity.setContentView(sheet)
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
      instrumentation.waitForIdleSync()

      assertEquals(0, closeRequestCount.get())
      assertEquals(1, RecordingReactActivityDelegate.backEntryCount.get())

      scenario.onActivity { sheet.destroy() }
    }
  }

  @Test
  fun windowOverlayBackWithoutHandlerEntersReactNativeOnceAndLeavesDialogVisible() {
    RecordingReactActivityDelegate.backEntryCount.set(0)
    val closeRequestCount = AtomicInteger()
    lateinit var controller: OverlayCloseRequestController
    lateinit var dialog: ComponentDialog

    ActivityScenario.launch(RecordingReactActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        controller = OverlayCloseRequestController {
          closeRequestCount.incrementAndGet()
          true
        }
        dialog =
          ComponentDialog(activity).also {
            it.setContentView(FrameLayout(activity))
            it.show()
          }
        controller.bind(dialog)
        controller.update(
          CloseRequestInputState(
            isAttached = true,
            isLifecycleActive = true,
            isModal = true,
            handlesCloseRequests = false,
            isPresentationActive = true,
            isTargetResolvedAndOpen = true,
          ),
          usesOverlayDialog = true,
          isSheetInteractive = true,
        )
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
      instrumentation.waitForIdleSync()

      assertEquals(1, RecordingReactActivityDelegate.backEntryCount.get())
      assertEquals(0, closeRequestCount.get())
      scenario.onActivity { activity ->
        assertTrue(dialog.isShowing)
        assertFalse(activity.isFinishing)
      }

      scenario.onActivity {
        controller.dispose()
        dialog.dismiss()
      }
    }
  }

  private fun openPortalSheet(
    context: Context,
    closeRequestCount: AtomicInteger,
    hasCloseRequestHandler: Boolean,
  ) =
    BottomSheetView(context).apply {
      listener =
        object : BottomSheetViewListener {
          override fun onIndexChange(index: Int) = Unit

          override fun onSettle(index: Int) = Unit

          override fun onPositionChange(position: Double, index: Double) = Unit

          override fun onCloseRequest() {
            closeRequestCount.incrementAndGet()
          }
        }
      animateIn = false
      modal = true
      setHasCloseRequestHandler(hasCloseRequestHandler)
      setDetents(
        listOf(
          // Without a handler, a dismissible closed detent would take Back to close the sheet.
          mapOf("value" to 0.0, "kind" to "points", "programmatic" to !hasCloseRequestHandler),
          mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
        ),
      )
      setIndex(1)
    }
}
