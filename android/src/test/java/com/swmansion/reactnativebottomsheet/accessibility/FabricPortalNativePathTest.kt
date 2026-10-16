@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet.accessibility

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.views.modal.ReactModalHostView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FabricPortalNativePathTest {
  @Before fun useLocalReactNativeFeatureFlags() = ReactNativeFeatureFlagsForTests.setUp()

  @Test
  fun `flattened source ancestors stay logical while native wrappers preserve the physical path`() {
    Fixture().use { f ->
      assertEquals(
        listOf(f.sheet, f.wrapper, f.host, f.area, f.root),
        f.receipt.nativePath(f.registry::get, f.sheet),
      )
    }
  }

  @Test
  fun `native frontiers hide source background and native chrome while preserving wrappers and outside controls`() {
    Fixture().use { f ->
      val chrome = View(f.activity.get()).also(f.area::addView)
      val wrapperSide = View(f.activity.get()).also(f.wrapper::addView)
      val outside = View(f.activity.get()).also(f.root::addView)
      val projection =
        FabricPortalIsolationProjection(
          intArrayOf(12),
          intArrayOf(11, 31, 31, 13, 13, 14),
        )

      assertEquals(
        setOf(f.background, chrome, wrapperSide),
        f.receipt.nativeIsolationBranches(projection, f.registry::get, f.sheet.rootView),
      )
      listOf(f.background, chrome, wrapperSide, outside, f.wrapper, f.sheet).forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
      }
    }
  }

  @Test
  fun `owned redirected containers expose their native background without using the source owners window`() {
    Fixture().use { f ->
      val owner = f.host
      val container =
        object : FrameLayout(f.activity.get()), PortalNativeContainer {
            override val portalNativeOwner: View = owner
          }
          .apply { id = 13 }
      owner.removeView(f.wrapper)
      container.addView(f.wrapper)
      val chrome = View(f.activity.get()).also(container::addView)
      val other = Robolectric.buildActivity(Activity::class.java).setup()
      try {
        other.get().setContentView(container)
        val projection = FabricPortalIsolationProjection(intArrayOf(), intArrayOf(13, 14))
        assertNotNull(f.receipt.nativeMembers(f.registry::get))
        assertEquals(
          setOf(chrome),
          f.receipt.nativeIsolationBranches(projection, f.registry::get, f.sheet.rootView),
        )
      } finally {
        other.close()
      }
    }
  }

  @Test
  fun `a flattened boundary does not adopt native siblings of its materialized ancestor`() {
    Fixture().use { f ->
      f.area.removeView(f.background)
      f.area.removeView(f.host)
      f.root.addView(f.background)
      f.root.addView(f.host)
      f.root.removeView(f.area)
      f.registry.remove(11)
      val outside = View(f.activity.get()).also(f.root::addView)
      val chrome = View(f.activity.get()).also(f.host::addView)
      val receipt =
        FabricPortalReceipt(
          "source:1",
          13,
          intArrayOf(11),
          intArrayOf(14, 13, 31, 11, 2),
          intArrayOf(2, 11, 12, 13, 14, 31),
          intArrayOf(2, 0, 12, 2, 13, 2, 14, 13),
        )
      val projection =
        FabricPortalIsolationProjection(intArrayOf(12), intArrayOf(11, 31, 31, 13, 13, 14))
      assertEquals(
        setOf(f.background, chrome),
        receipt.nativeIsolationBranches(projection, f.registry::get, f.sheet.rootView),
      )
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, outside.importantForAccessibility)
    }
  }

  @Test
  fun `invalid source targets retained paths or window rejects the complete native proposal`() {
    Fixture().use { f ->
      val window = f.sheet.rootView
      val edges = intArrayOf(11, 31, 31, 13, 13, 14)
      listOf(2, 13, 99).forEach { invalid ->
        assertNull(
          f.receipt.nativeIsolationBranches(
            FabricPortalIsolationProjection(intArrayOf(12, invalid), edges),
            f.registry::get,
            window,
          )
        )
      }
      assertNull(
        f.receipt.nativeIsolationBranches(
          FabricPortalIsolationProjection(intArrayOf(12), intArrayOf(99, 14)),
          f.registry::get,
          window,
        )
      )
      val projection = FabricPortalIsolationProjection(intArrayOf(12), edges)
      assertNull(f.receipt.nativeIsolationBranches(projection, f.registry::get, f.root))
      f.area.removeView(f.background)
      assertNull(f.receipt.nativeIsolationBranches(projection, f.registry::get, window))
      assertNull(
        f.receipt.nativeIsolationBranches(
          FabricPortalIsolationProjection(intArrayOf(), intArrayOf()),
          f.registry::get,
          window,
        )
      )
    }
  }

  @Test
  fun `native insertions and wrapper frontiers are read currently and redundant descendant masks are removed`() {
    Fixture().use { f ->
      val projection =
        FabricPortalIsolationProjection(intArrayOf(12), intArrayOf(11, 31, 31, 13, 13, 14))
      val window = f.sheet.rootView
      assertEquals(
        emptySet<View>(),
        f.receipt.nativeIsolationBranches(
          FabricPortalIsolationProjection(intArrayOf(), intArrayOf()),
          f.registry::get,
          window,
        ),
      )
      val native = FrameLayout(f.activity.get()).also(f.area::addView)
      native.addView(View(f.activity.get()))
      assertEquals(
        setOf(f.background, native),
        f.receipt.nativeIsolationBranches(projection, f.registry::get, window),
      )
      f.area.removeView(native)
      assertEquals(
        setOf(f.background),
        f.receipt.nativeIsolationBranches(projection, f.registry::get, window),
      )
      val wrapper = FrameLayout(f.activity.get())
      f.area.removeView(f.background)
      wrapper.addView(f.background)
      f.area.addView(wrapper)
      assertEquals(
        setOf(wrapper),
        f.receipt.nativeIsolationBranches(projection, f.registry::get, window),
      )
    }
  }

  @Test
  fun `RN Modal projects redirected native chrome without reaching its application root`() {
    Fixture().use { f ->
      val context = BridgeReactContext(f.activity.get().applicationContext)
      val modal =
        ReactModalHostView(ThemedReactContext(context, f.activity.get(), "test", 1)).apply {
          id = 30
        }
      try {
        (f.root.parent as android.view.ViewGroup).removeView(f.root)
        modal.addView(f.root, 0)
        val container = f.root.parent as android.view.ViewGroup
        val chrome = View(f.activity.get()).also(container::addView)
        f.activity.get().setContentView(container)
        val application = FrameLayout(f.activity.get()).apply { id = 1 }
        application.addView(modal)
        application.addView(View(f.activity.get()))
        f.registry[1] = application
        f.registry[30] = modal
        val receipt =
          FabricPortalReceipt(
            "source:1",
            13,
            intArrayOf(30),
            intArrayOf(14, 13, 31, 11, 2, 30),
            intArrayOf(1, 2, 11, 12, 13, 14, 30, 31),
            intArrayOf(1, 0, 30, 1, 2, 30, 11, 2, 12, 11, 13, 11, 14, 13),
          )
        val projection =
          FabricPortalIsolationProjection(
            intArrayOf(12),
            intArrayOf(30, 2, 2, 11, 11, 31, 31, 13, 13, 14),
          )
        assertEquals(
          setOf(f.background, chrome),
          receipt.nativeIsolationBranches(projection, f.registry::get, f.sheet.rootView),
        )
      } finally {
        modal.onDropInstance()
      }
    }
  }

  @Test
  fun `a registered view cannot substitute a different source tag`() {
    Fixture().use { f ->
      f.area.id = 99
      f.registry[99] = f.area
      assertNull(f.receipt.nativePath(f.registry::get, f.sheet))
    }
  }

  @Test
  fun `RN Modal redirected children identify the physical container without its off-window JSX parent`() {
    Fixture().use { f ->
      val reactContext = BridgeReactContext(f.activity.get().applicationContext)
      val themedContext = ThemedReactContext(reactContext, f.activity.get(), "test", 1)
      val modal = ReactModalHostView(themedContext).apply { id = 30 }
      (f.root.parent as android.view.ViewGroup).removeView(f.root)
      modal.addView(f.root, 0)
      val container = f.root.parent as android.view.ViewGroup
      f.activity.get().setContentView(container)
      f.registry[30] = modal
      val outside = FrameLayout(f.activity.get()).apply { id = 1 }
      outside.addView(modal)
      f.registry[1] = outside
      val receipt =
        FabricPortalReceipt(
          "source:1",
          13,
          intArrayOf(11),
          intArrayOf(14, 13, 31, 11, 2, 30),
          intArrayOf(1, 2, 11, 12, 13, 14, 30, 31),
          intArrayOf(1, 0, 30, 1, 2, 30, 11, 2, 12, 11, 13, 11, 14, 13),
        )

      assertEquals(
        listOf(f.sheet, f.wrapper, f.host, f.area, f.root, container),
        receipt.nativePath(f.registry::get, f.sheet),
      )
      assertNotNull(receipt.nativeMembers(f.registry::get))
      container.removeView(f.root)
      val impostor = FrameLayout(f.activity.get()).apply { id = 30 }
      impostor.addView(f.root)
      f.activity.get().setContentView(impostor)
      assertNull(receipt.nativePath(f.registry::get, f.sheet))
      assertNull(receipt.nativeMembers(f.registry::get))
    }
  }

  @Test
  fun `a materialized logical ancestor cannot be skipped as a flattened wrapper`() {
    Fixture().use { f ->
      f.registry[31] = FrameLayout(f.activity.get()).apply { id = 31 }
      assertNull(f.receipt.nativePath(f.registry::get, f.sheet))
    }
  }

  @Test
  fun `the registered host must be the actual marked native instance`() {
    Fixture().use { f ->
      f.host.setTag(com.facebook.react.R.id.view_tag_native_id, null)
      assertNull(f.receipt.nativePath(f.registry::get, f.sheet))
      f.host.setTag(
        com.facebook.react.R.id.view_tag_native_id,
        "__react_native_bottom_sheet_portal_host",
      )
      f.registry[13] = FrameLayout(f.activity.get()).apply { id = 13 }
      assertNull(f.receipt.nativePath(f.registry::get, f.sheet))
    }
  }

  @Test
  fun `attachment and the expected window remain separate from source ancestry after migration`() {
    Fixture().use { f ->
      val expected = listOf(f.sheet, f.wrapper, f.host, f.area, f.root)
      val firstWindow = f.sheet.rootView
      assertEquals(expected, f.receipt.attachedNativePath(f.registry::get, f.sheet, firstWindow))
      (f.root.parent as android.view.ViewGroup).removeView(f.root)
      assertEquals(expected, f.receipt.nativePath(f.registry::get, f.sheet))
      assertNull(f.receipt.attachedNativePath(f.registry::get, f.sheet, firstWindow))
      val second = Robolectric.buildActivity(Activity::class.java).setup()
      try {
        second.get().setContentView(f.root)
        assertNull(f.receipt.attachedNativePath(f.registry::get, f.sheet, firstWindow))
        assertEquals(
          expected,
          f.receipt.attachedNativePath(f.registry::get, f.sheet, f.sheet.rootView),
        )
      } finally {
        second.close()
      }
    }
  }

  @Test
  fun `source membership rejects missing or reparented background outside the queried path`() {
    Fixture().use { f ->
      assertEquals(setOf(2, 11, 12, 13, 14), f.receipt.nativeMembers(f.registry::get)?.keys)
      f.area.removeView(f.background)
      assertNull(f.receipt.nativeMembers(f.registry::get))
      f.root.addView(f.background)
      assertNull(f.receipt.nativeMembers(f.registry::get))
      f.root.removeView(f.background)
      f.area.addView(f.background)
      assertNotNull(f.receipt.nativeMembers(f.registry::get))
    }
  }

  @Test
  fun `ordinary native chrome is allowed but an unexpected SDK member or materialized flat node is rejected`() {
    Fixture().use { f ->
      val chrome = FrameLayout(f.activity.get()).apply { id = 99 }
      f.root.addView(chrome)
      assertNotNull(f.receipt.nativeMembers(f.registry::get))
      f.registry[99] = chrome
      assertNull(f.receipt.nativeMembers(f.registry::get))
      f.registry.remove(99)
      f.registry[31] = View(f.activity.get()).apply { id = 31 }
      assertNull(f.receipt.nativeMembers(f.registry::get))
    }
  }

  private class Fixture : AutoCloseable {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val root = FrameLayout(activity.get()).apply { id = 2 }
    val area = FrameLayout(activity.get()).apply { id = 11 }
    val background = View(activity.get()).apply { id = 12 }
    val host =
      FrameLayout(activity.get()).apply {
        id = 13
        setTag(
          com.facebook.react.R.id.view_tag_native_id,
          "__react_native_bottom_sheet_portal_host",
        )
      }
    val wrapper = FrameLayout(activity.get())
    val sheet = View(activity.get()).apply { id = 14 }
    val registry =
      mutableMapOf<Int, View>(2 to root, 11 to area, 12 to background, 13 to host, 14 to sheet)
    val receipt =
      FabricPortalReceipt(
        "source:1",
        13,
        intArrayOf(11),
        intArrayOf(14, 13, 31, 11, 2),
        intArrayOf(2, 11, 12, 13, 14, 31),
        intArrayOf(2, 0, 11, 2, 12, 11, 13, 11, 14, 13),
      )

    init {
      wrapper.addView(sheet)
      host.addView(wrapper)
      area.addView(background)
      area.addView(host)
      root.addView(area)
      activity.get().setContentView(root)
    }

    override fun close() = activity.close()
  }
}
