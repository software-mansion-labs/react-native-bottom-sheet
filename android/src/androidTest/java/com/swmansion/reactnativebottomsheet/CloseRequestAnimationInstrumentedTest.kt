package com.swmansion.reactnativebottomsheet

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
    lateinit var sheet: BottomSheetView

    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        activity.onBackPressedDispatcher.addCallback(
          object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
              fallbackCount.incrementAndGet()
            }
          }
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
            modal = true
            setHasCloseRequestHandler(true)
            setDetents(
              listOf(
                mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
                mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
              )
            )
            setIndex(1)
          }
        activity.setContentView(sheet)
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      scenario.onActivity {
        sheet.setIndex(0)
        it.onBackPressedDispatcher.onBackPressed()
      }

      assertEquals(0, requestCount.get())
      assertEquals(0, fallbackCount.get())
      assertTrue("close animation did not settle", closeSettled.await(5, TimeUnit.SECONDS))
      instrumentation.waitForIdleSync()

      scenario.onActivity {
        it.onBackPressedDispatcher.onBackPressed()
        sheet.destroy()
      }
      assertEquals(0, requestCount.get())
      assertEquals(1, fallbackCount.get())
    }
  }
}
