package com.swmansion.reactnativebottomsheet

import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swmansion.reactnativebottomsheet.closerequest.CloseRequestInputState
import com.swmansion.reactnativebottomsheet.closerequest.OverlayCloseRequestController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReactActivityBackRoutingInstrumentedTest {
  @Test
  fun nativeOverlayBackWithoutHandlerEntersReactNativeOnceAndLeavesDialogVisible() {
    RecordingReactActivityDelegate.backEntryCount.set(0)
    val closeRequestCount = AtomicInteger()
    val dialogWindowFocused = CountDownLatch(1)
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
            val decorView = requireNotNull(it.window).decorView
            decorView.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
              if (hasFocus) dialogWindowFocused.countDown()
            }
            it.show()
            if (decorView.hasWindowFocus()) dialogWindowFocused.countDown()
          }
        controller.bind(dialog)
        controller.update(
          CloseRequestInputState(
            isAttached = true,
            isLifecycleActive = true,
            isModal = true,
            hasCloseRequestHandler = false,
            isPresentationActive = true,
            isTargetResolvedAndOpen = true,
          ),
          usesOverlayDialog = true,
          isSheetInteractive = true,
        )
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      assertTrue(
        "Dialog window did not receive focus before system Back",
        dialogWindowFocused.await(5, TimeUnit.SECONDS),
      )
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
}
