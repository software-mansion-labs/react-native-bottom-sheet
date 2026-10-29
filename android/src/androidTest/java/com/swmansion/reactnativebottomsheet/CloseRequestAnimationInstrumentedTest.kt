package com.swmansion.reactnativebottomsheet

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloseRequestAnimationInstrumentedTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun portalCloseAnimationConsumesBackUntilSettled() {
    val requestCount = AtomicInteger()
    val fallbackCount = AtomicInteger()
    val closeSettled = CountDownLatch(1)
    val focused = CountDownLatch(1)
    val laidOut = CountDownLatch(1)
    val hostBack = CountDownLatch(1)
    lateinit var sheet: BottomSheetView

    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        activity.onBackPressedDispatcher.addCallback(
          object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
              fallbackCount.incrementAndGet()
              hostBack.countDown()
            }
          },
        )
        sheet =
          BottomSheetView(activity).apply {
            listener =
              object : BottomSheetViewListener {
                override fun onIndexChange(index: Int) = Unit

                override fun onSettle(index: Int) {
                  if (index == 0) closeSettled.countDown()
                }

                override fun onPositionChange(position: Double, index: Double) = Unit

                override fun onCloseRequest() {
                  requestCount.incrementAndGet()
                }
              }
            animateIn = false
            isFocusableInTouchMode = true
            modal = true
            setHasCloseRequestHandler(true)
            setDetents(
              listOf(
                mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
                mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
              ),
            )
            setIndex(1)
            addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
              if (right > left && bottom > top) laidOut.countDown()
            }
          }
        activity.setInstrumentedReactContentView(sheet)
        val decor = activity.window.decorView
        decor.viewTreeObserver.addOnWindowFocusChangeListener { if (it) focused.countDown() }
        if (decor.hasWindowFocus()) focused.countDown()
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      try {
        assertTrue("Testhost did not receive focus", focused.await(5, TimeUnit.SECONDS))
        assertTrue("Portal did not receive layout", laidOut.await(5, TimeUnit.SECONDS))
        scenario.onActivity {
          assertTrue(sheet.requestFocus())
          sheet.setIndex(0)
          assertEquals("Close must still be animating", 1L, closeSettled.count)
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

        assertEquals(0, requestCount.get())
        assertEquals(0, fallbackCount.get())
        assertTrue("close animation did not settle", closeSettled.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertTrue("Settled close did not release host Back", hostBack.await(5, TimeUnit.SECONDS))
        assertEquals(0, requestCount.get())
        assertEquals(1, fallbackCount.get())
      } finally {
        scenario.onActivity { sheet.destroy() }
      }
    }
  }
}
