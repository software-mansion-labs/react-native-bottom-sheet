@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)
@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet.accessibility

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.UIManager
import com.facebook.react.bridge.UIManagerListener
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.swmansion.reactnativebottomsheet.BottomSheetHostView
import com.swmansion.reactnativebottomsheet.BottomSheetView
import com.swmansion.reactnativebottomsheet.TestArgumentsShadow
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationCoordinator
import com.swmansion.reactnativebottomsheet.presentation.TestReactRoot
import java.lang.reflect.Proxy
import java.util.IdentityHashMap
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
import org.robolectric.util.ReflectionHelpers

@RunWith(AndroidJUnit4::class)
@Config(
  sdk = [35],
  shadows =
    [
      ActiveRootsUIManagerHelperShadow::class,
      TestArgumentsShadow::class,
      com.swmansion.reactnativebottomsheet.TestViewUtilShadow::class,
    ],
)
class PortalActiveMountObservationTest {
  @Before fun useLocalFlags() = ReactNativeFeatureFlagsForTests.setUp()

  @Test
  fun `detaching a root removes its mount listener while another portal is moving`() {
    Pipeline(separateManagers = true).use { f ->
      val sheet = f.surfaces[1].sheet
      prepareClosedSheet(sheet)
      f.registerBoth()
      f.drain()
      assertTrue(f.manager.listenerCount > 0)
      val movingRootListeners = f.secondManager.listenerCount

      sheet.setIndex(1)
      f.window.removeView(f.surfaces[0].root)
      f.mount()
      f.drain()
      shadowOf(Looper.getMainLooper()).idle()

      assertEquals(0, f.manager.listenerCount)
      assertEquals(movingRootListeners, f.secondManager.listenerCount)
    }
  }

  @Test
  fun `mounts during motion retain masks without source reads and settle flushes current state`() {
    Pipeline().use { f ->
      val sheet = f.surfaces[0].sheet
      prepareClosedSheet(sheet)
      f.registerBoth()
      f.drain()
      f.readSurfaces.clear()
      f.sourceReadCounts.clear()

      sheet.setIndex(1)
      val host = sheet.getChildAt(0) as BottomSheetHostView
      val animation =
        requireNotNull(ReflectionHelpers.getField<SpringAnimation?>(host, "activeAnimation"))
      var time = SystemClock.uptimeMillis()
      repeat(4) {
        time += 16
        animation.doAnimationFrame(time)
        f.mount()
        f.drain()
        assertTrue(
          "moving portals must not read the Fabric source",
          f.readSurfaces.isEmpty(),
        )
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
          f.surfaces[0].background.importantForAccessibility,
        )
      }
      var finished = false
      for (frame in 0 until 240) {
        time += 16
        if (animation.doAnimationFrame(time)) {
          finished = true
          break
        }
      }
      assertTrue("spring settles", finished)
      f.drain()
      assertEquals(setOf(2, 4), f.readSurfaces)
      assertEquals(mapOf(2 to 1, 4 to 1), f.sourceReadCounts)
    }
  }

  @Test
  fun `one rejected manager preserves the previous window masks and applies no partial update`() {
    Pipeline(separateManagers = true).use { f ->
      f.registerBoth()
      f.drain()
      val added = View(f.activity.get()).also(f.surfaces[0].area::addView)
      f.rejectedSurface = 4
      f.mount()
      f.drain()

      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, added.importantForAccessibility)
      f.surfaces.forEach {
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
          it.background.importantForAccessibility,
        )
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.toolbar.importantForAccessibility)
      }

      f.rejectedSurface = null
      f.mount()
      f.drain()
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        added.importantForAccessibility,
      )
    }
  }

  @Test
  fun `queued reads use current Active membership and detach restores masks before callbacks expire`() {
    Pipeline().use { f ->
      f.registerBoth()
      f.drain()
      f.mount()
      f.registrations[0].update(false)
      f.drain()
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
        f.surfaces[0].background.importantForAccessibility,
      )
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        f.surfaces[1].background.importantForAccessibility,
      )

      f.window.removeView(f.surfaces[1].root)
      f.mount()
      f.drain()
      f.surfaces.forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.background.importantForAccessibility)
      }
    }
  }

  @Test
  fun `unavailable Fabric source leaves local and outside views unchanged until a supported event retries`() {
    Pipeline().use { f ->
      f.sourceAvailable = false
      f.registerBoth()
      f.drain()
      f.surfaces.forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.background.importantForAccessibility)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.toolbar.importantForAccessibility)
      }
      f.sourceAvailable = true
      assertEquals(0, f.posted.size)
      f.mount()
      f.drain()
      f.surfaces.forEach {
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
          it.background.importantForAccessibility,
        )
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.toolbar.importantForAccessibility)
      }
    }
  }

  @Test
  fun `queued production cuts isolate independent Fabric panels while outside toolbars stay available`() {
    Pipeline().use { f ->
      f.registerBoth()
      f.surfaces.forEach { surface ->
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
          surface.toolbar.importantForAccessibility,
        )
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
          surface.background.importantForAccessibility,
        )
      }

      f.drain()

      f.surfaces.forEach { surface ->
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
          surface.background.importantForAccessibility,
        )
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
          surface.toolbar.importantForAccessibility,
        )
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, surface.sheet.importantForAccessibility)
      }
      assertEquals(setOf(2, 4), f.readSurfaces)
    }
  }

  @Test
  fun `mounts from a lower Active root refresh policy until that presentation becomes inactive`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val lease = PortalAccessibilityIsolationCoordinator.acquire()
    val registrations = mutableListOf<PortalPresentationCoordinator.Registration>()
    try {
      val contextA = BridgeReactContext(activity.get().applicationContext)
      val contextB = BridgeReactContext(activity.get().applicationContext)
      val managerA = Manager()
      val managerB = Manager()
      ActiveRootsUIManagerHelperShadow.managers[contextA] = managerA.proxy
      ActiveRootsUIManagerHelperShadow.managers[contextB] = managerB.proxy
      val window = FrameLayout(activity.get())
      val rootA = TestReactRoot(contextA).apply { id = 2 }.also(window::addView)
      val rootB = TestReactRoot(contextB).apply { id = 4 }.also(window::addView)
      val a = View(activity.get()).also(rootA::addView)
      val backgroundB = View(activity.get()).also(rootB::addView)
      val b = View(activity.get()).also(rootB::addView)
      activity.get().setContentView(window)
      val registrationA = requireNotNull(PortalPresentationCoordinator.register(a, true) {})
      val registrationB = requireNotNull(PortalPresentationCoordinator.register(b, true) {})
      registrations.addAll(listOf(registrationA, registrationB))

      backgroundB.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      managerA.mount()
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        backgroundB.importantForAccessibility,
      )

      registrationA.update(false)
      backgroundB.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      managerA.mount()
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, backgroundB.importantForAccessibility)
      managerB.mount()
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        backgroundB.importantForAccessibility,
      )
      registrationB.update(false)
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, backgroundB.importantForAccessibility)
    } finally {
      registrations.forEach { it.remove() }
      lease.release()
      ActiveRootsUIManagerHelperShadow.managers.clear()
      activity.close()
    }
  }

  private fun prepareClosedSheet(sheet: BottomSheetView) {
    sheet.modal = true
    sheet.animateIn = false
    sheet.setDetents(
      listOf(
        mapOf("value" to 0.0, "kind" to "points", "programmatic" to false),
        mapOf("value" to 300.0, "kind" to "points", "programmatic" to false),
      ),
    )
    sheet.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
    )
    sheet.layout(0, 0, 1080, 1920)
  }

  private class Pipeline(separateManagers: Boolean = false) : AutoCloseable {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val context = BridgeReactContext(activity.get().applicationContext)
    val manager = Manager()
    val secondContext =
      if (separateManagers) BridgeReactContext(activity.get().applicationContext) else context
    val secondManager = if (separateManagers) Manager() else manager
    val window = FrameLayout(activity.get())
    val surfaces =
      listOf(Surface(activity.get(), context, 2, 10), Surface(activity.get(), secondContext, 4, 20))
    val posted = mutableListOf<Runnable>()
    val readSurfaces = mutableSetOf<Int>()
    val sourceReadCounts = mutableMapOf<Int, Int>()
    var rejectedSurface: Int? = null
    var sourceAvailable = true

    private fun source(managed: List<Surface>): FabricPublicationLease =
      object : TestFabricPublicationLease() {
        override fun bootstrap() = Unit

        override fun release() = Unit

        override fun sheetTags(surfaceId: Int, tag: Int, state: StateWrapper?): IntArray? {
          readSurfaces.add(surfaceId)
          sourceReadCounts[surfaceId] = sourceReadCounts.getOrDefault(surfaceId, 0) + 1
          return managed.singleOrNull { it.root.id == surfaceId }?.let { intArrayOf(it.sheet.id) }
        }

        override fun receipt(
          surfaceId: Int,
          tag: Int,
          states: Map<Int, StateWrapper?>,
        ): FabricPortalReceipt? {
          val surface = managed.singleOrNull { it.root.id == surfaceId } ?: return null
          if (
            surfaceId == rejectedSurface ||
              states.keys != setOf(surface.sheet.id) ||
              states.values.any { it == null }
          )
            return null
          return surface.receipt
        }

        override fun contextPath(
          surfaceId: Int,
          tag: Int,
          states: Map<Int, com.facebook.react.uimanager.StateWrapper?>,
        ): List<Int>? = null

        override fun isolation(
          surfaceId: Int,
          states: Map<Int, StateWrapper?>,
          nativeParents: Map<Int, Int>,
          retainedByHost: Map<Int, List<Int>>,
        ): FabricPortalIsolationProjection? {
          val surface = managed.singleOrNull { it.root.id == surfaceId } ?: return null
          return FabricPortalIsolationProjection(
            intArrayOf(),
            intArrayOf(surface.area.id, surface.host.id, surface.host.id, surface.sheet.id),
          )
        }
      }

    private val sources =
      IdentityHashMap<Any, FabricPublicationLease>().apply {
        if (secondManager === manager) put(manager.proxy, source(surfaces))
        else {
          put(manager.proxy, source(listOf(surfaces[0])))
          put(secondManager.proxy, source(listOf(surfaces[1])))
        }
      }
    val adapter =
      ReactNativeMountReconciliationAdapter(
        postToUiThread = { posted.add(it) },
        acquirePublicationSource = { if (sourceAvailable) sources[it] else null },
      )
    val coordinator = PortalAccessibilityIsolationCoordinator(adapter)
    val lease = coordinator.acquire()
    val registrations = mutableListOf<PortalPresentationCoordinator.Registration>()

    init {
      ActiveRootsUIManagerHelperShadow.managers[context] = manager.proxy
      ActiveRootsUIManagerHelperShadow.managers[secondContext] = secondManager.proxy
      surfaces.forEachIndexed { index, surface ->
        window.addView(surface.root)
        (if (index == 0) manager else secondManager)
          .registry
          .putAll(
            listOf(surface.root, surface.area, surface.host, surface.sheet).associateBy { it.id },
          )
      }
      activity.get().setContentView(window)
    }

    fun registerBoth() {
      surfaces.forEach {
        registrations.add(requireNotNull(PortalPresentationCoordinator.register(it.sheet, true) {}))
      }
    }

    fun mount() {
      manager.mount()
      if (secondManager !== manager) secondManager.mount()
    }

    fun drain() {
      var work = 0
      while (posted.isNotEmpty()) {
        assertTrue("reconciliation must not keep reposting itself", work++ < 20)
        posted.removeAt(0).run()
      }
    }

    override fun close() {
      registrations.forEach { it.remove() }
      lease.release()
      drain()
      surfaces.forEach { it.sheet.destroy() }
      ActiveRootsUIManagerHelperShadow.managers.clear()
      activity.close()
    }
  }

  private class Surface(
    activity: Activity,
    context: BridgeReactContext,
    surfaceId: Int,
    base: Int,
  ) {
    val root = TestReactRoot(context).apply { id = surfaceId }
    val toolbar = View(activity).also(root::addView)
    val area = FrameLayout(activity).apply { id = base + 1 }.also(root::addView)
    val background = View(activity).also(area::addView)
    val host =
      FrameLayout(activity)
        .apply {
          id = base + 3
          setTag(
            com.facebook.react.R.id.view_tag_native_id,
            "__react_native_bottom_sheet_portal_host",
          )
        }
        .also(area::addView)
    val sheet =
      BottomSheetView(ThemedReactContext(context, activity, "test", surfaceId))
        .apply {
          id = base + 4
          stateWrapper =
            Proxy.newProxyInstance(
              StateWrapper::class.java.classLoader,
              arrayOf(StateWrapper::class.java),
            ) { _, _, _ ->
              null
            } as StateWrapper
        }
        .also(host::addView)
    val receipt =
      FabricPortalReceipt(
        "source:$surfaceId",
        host.id,
        intArrayOf(area.id),
        intArrayOf(sheet.id, host.id, area.id, root.id),
        intArrayOf(root.id, area.id, host.id, sheet.id),
        intArrayOf(root.id, 0, area.id, root.id, host.id, area.id, sheet.id, host.id),
      )
  }

  private class Manager {
    val registry = mutableMapOf<Int, View>()
    private val listeners = mutableListOf<UIManagerListener>()
    val listenerCount: Int
      get() = listeners.size

    val proxy =
      Proxy.newProxyInstance(
        UIManager::class.java.classLoader,
        arrayOf(UIManager::class.java),
      ) { _, method, args ->
        when (method.name) {
          "addUIManagerEventListener" -> listeners.add(args!![0] as UIManagerListener)
          "removeUIManagerEventListener" -> listeners.remove(args!![0] as UIManagerListener)
          "resolveView" -> return@newProxyInstance registry[args!![0] as Int]
        }
        null
      } as UIManager

    fun mount() {
      listeners.toList().forEach { it.didMountItems(proxy) }
    }
  }
}

@Implements(UIManagerHelper::class)
class ActiveRootsUIManagerHelperShadow {
  companion object {
    val managers = IdentityHashMap<ReactContext, UIManager>()

    @JvmStatic
    @Implementation
    fun getUIManager(context: ReactContext, managerType: Int): UIManager? = managers[context]
  }
}
