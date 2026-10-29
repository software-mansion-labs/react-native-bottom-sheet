@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet.accessibility

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.ThemedReactContext
import com.swmansion.reactnativebottomsheet.BottomSheetView
import com.swmansion.reactnativebottomsheet.NoOpEventDispatcher
import com.swmansion.reactnativebottomsheet.presentation.TestReactRoot
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FabricPortalNativeOverlayTest {
  @Before fun useLocalReactNativeFeatureFlags() = ReactNativeFeatureFlagsForTests.setUp()

  @Test
  fun `an actual overlay preserves source edges and binds the portal path to its current dialog window`() {
    Fixture().use { f ->
      val applicationWindow = f.content.rootView
      assertNotNull(f.receipt.nativeMembers(f.registry::get))
      f.showOverlay()
      val overlayWindow = f.content.rootView
      assertNotSame(applicationWindow, overlayWindow)
      assertNotNull(
        "The owned overlay must preserve source membership",
        f.receipt.nativeMembers(f.registry::get),
      )
      assertNotNull(f.receipt.attachedNativePath(f.registry::get, f.content, overlayWindow))
      assertNull(f.receipt.attachedNativePath(f.registry::get, f.content, applicationWindow))

      f.owner.setNativeOverlay(false)
      f.layout(f.root)
      assertNull(f.receipt.attachedNativePath(f.registry::get, f.content, overlayWindow))
      assertNotNull(
        f.inlineReceipt.attachedNativePath(f.registry::get, f.content, applicationWindow),
      )
      assertNotNull(f.inlineReceipt.nativeMembers(f.registry::get))
    }
  }

  @Test
  fun `an unexpected SDK branch in the hoisted native prefix rejects the whole source`() {
    Fixture().use { f ->
      f.showOverlay()
      val container = f.overlayContainer()
      val foreign = View(f.activity.get()).apply { id = 99 }
      container.addView(foreign)
      f.registry[99] = foreign
      try {
        assertNull(f.receipt.nativeMembers(f.registry::get))
      } finally {
        container.removeView(foreign)
        f.registry.remove(99)
      }
      assertNotNull(f.receipt.nativeMembers(f.registry::get))
    }
  }

  @Test
  fun `copied container IDs and retired dialog containers cannot claim the sheet owner`() {
    Fixture().use { f ->
      f.showOverlay()
      val retired = f.overlayContainer()
      val nativeHost = retired.getChildAt(0)
      retired.removeView(nativeHost)
      val impostor = FrameLayout(f.activity.get()).apply { id = f.owner.id }
      impostor.addView(nativeHost)
      try {
        assertNull(f.receipt.nativeMembers(f.registry::get))
        assertNull(f.receipt.nativePath(f.registry::get, f.content))
      } finally {
        impostor.removeView(nativeHost)
        retired.addView(nativeHost)
      }
      f.owner.setNativeOverlay(false)
      f.owner.removeView(nativeHost)
      retired.addView(nativeHost)
      try {
        assertNull(f.receipt.nativeMembers(f.registry::get))
        assertNull(f.receipt.nativePath(f.registry::get, f.content))
      } finally {
        retired.removeView(nativeHost)
        f.owner.addView(nativeHost)
      }
    }
  }

  private class Fixture : AutoCloseable {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val reactContext = BridgeReactContext(activity.get().applicationContext)
    val root = TestReactRoot(activity.get()).apply { id = 2 }
    val area = FrameLayout(activity.get()).apply { id = 11 }
    val host =
      FrameLayout(activity.get()).apply {
        id = 13
        setTag(
          com.facebook.react.R.id.view_tag_native_id,
          "__react_native_bottom_sheet_portal_host",
        )
      }
    val content = View(activity.get()).apply { id = 14 }
    val owner: BottomSheetView
    val registry: MutableMap<Int, View>
    val receipt = receipt(intArrayOf(14, 13, 11, 24))
    val inlineReceipt = receipt(intArrayOf(14, 13, 11, 24, 2))

    init {
      reactContext.onHostResume(activity.get())
      owner =
        BottomSheetView(ThemedReactContext(reactContext, activity.get(), "test", 1)).apply {
          id = 24
          eventDispatcher = NoOpEventDispatcher
          animateIn = false
          modal = true
          setDetents(
            listOf(
              mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
              mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
            ),
          )
          setIndex(1)
        }
      registry = mutableMapOf(2 to root, 11 to area, 13 to host, 14 to content, 24 to owner)
      host.addView(content)
      area.addView(host)
      owner.addSheetChild(area, 0)
      root.addView(owner)
      activity.get().setContentView(root)
      layout(root)
      owner.onHostResume()
    }

    fun showOverlay() {
      owner.setNativeOverlay(true)
      shadowOf(Looper.getMainLooper()).idle()
      val dialog = requireNotNull(ShadowDialog.getLatestDialog())
      layout(requireNotNull(dialog.window).decorView)
    }

    fun overlayContainer(): ViewGroup =
      generateSequence(content.parent as? View) { it.parent as? View }.first { it.id == owner.id }
        as ViewGroup

    fun layout(view: View) {
      view.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
      )
      view.layout(0, 0, 1080, 1920)
      shadowOf(Looper.getMainLooper()).idle()
    }

    override fun close() {
      owner.destroy()
      reactContext.onHostDestroy()
      activity.close()
    }

    private fun receipt(ancestors: IntArray) =
      FabricPortalReceipt(
        "source:1",
        13,
        intArrayOf(11),
        ancestors,
        intArrayOf(2, 11, 13, 14, 24),
        intArrayOf(2, 0, 24, 2, 11, 24, 13, 11, 14, 13),
      )
  }
}
