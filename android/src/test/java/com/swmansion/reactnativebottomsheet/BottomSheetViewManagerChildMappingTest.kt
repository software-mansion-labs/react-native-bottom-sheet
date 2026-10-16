package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BottomSheetViewManagerChildMappingTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `Fabric child indices remain local to sheet container`() {
    Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
      val sheet = BottomSheetView(controller.get())
      val manager = BottomSheetViewManager()
      val first = View(controller.get())
      val second = View(controller.get())

      val host = sheet.getChildAt(0) as ViewGroup
      assertEquals(3, host.childCount)
      assertEquals(0, manager.getChildCount(sheet))

      manager.addView(sheet, first, 0)
      manager.addView(sheet, second, 1)

      assertEquals(2, manager.getChildCount(sheet))
      assertSame(first, manager.getChildAt(sheet, 0))
      assertSame(second, manager.getChildAt(sheet, 1))

      manager.removeViewAt(sheet, 0)

      assertEquals(1, manager.getChildCount(sheet))
      assertSame(second, manager.getChildAt(sheet, 0))
      assertEquals(3, host.childCount)
      sheet.destroy()
    }
  }
}
