@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)
@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet.accessibility

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.UIManager
import com.facebook.react.bridge.UIManagerListener
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.views.modal.ReactModalHostView
import com.swmansion.reactnativebottomsheet.BottomSheetView
import com.swmansion.reactnativebottomsheet.TestArgumentsShadow
import com.swmansion.reactnativebottomsheet.presentation.TestReactRoot
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestArgumentsShadow::class])
class FabricPortalRegistryTest {
  @Before fun useLocalReactNativeFeatureFlags() = ReactNativeFeatureFlagsForTests.setUp()

  @Test
  fun `native-only focus descendant uses its current source containing context`() {
    Fixture().use { f ->
      val origin = View(f.activity.get()).also(f.areaA::addView)
      val snapshot =
        f.adapter.readPortalInputContexts(
          f.boundary,
          2,
          listOf(f.a, f.b),
          origin.rootView,
          origin,
        )

      assertEquals(listOf(11), snapshot?.focusContexts)
      assertEquals(listOf(f.a), snapshot?.contextOwners?.get(11))
      assertEquals(listOf(f.b), snapshot?.contextOwners?.get(21))
      assertEquals(1, snapshot?.nativeDepth)
    }
  }

  @Test
  fun `shared logical context keeps its visual Top even when the lower sheet registered later`() {
    Fixture().use { f ->
      f.stackInAreaA()

      val cut = f.observation.readPortalIsolationCuts(f.a.rootView, listOf(f.b, f.a))?.get(2)

      assertEquals(listOf(f.b), cut?.retainedOwners)
      assertEquals(setOf(f.a), cut?.branches)
      assertEquals(mapOf(13 to listOf(24)), f.projectionRetained)

      f.b.translationZ = -1f
      f.projectionResult =
        FabricPortalIsolationProjection(intArrayOf(24), intArrayOf(11, 13, 13, 14))
      val changed = f.observation.readPortalIsolationCuts(f.a.rootView, listOf(f.b, f.a))?.get(2)
      assertEquals(listOf(f.a), changed?.retainedOwners)
      assertEquals(setOf(f.b), changed?.branches)
      assertEquals(mapOf(13 to listOf(14)), f.projectionRetained)
    }
  }

  @Test
  fun `unresolved local order retains every supplied Active sheet without treating registration as Top`() {
    Fixture().use { f ->
      f.stackInAreaA()
      f.b.startAnimation(TranslateAnimation(0f, 10f, 0f, 0f).apply { duration = 1000 })
      f.projectionResult =
        FabricPortalIsolationProjection(intArrayOf(), intArrayOf(11, 13, 13, 14, 13, 24))

      val cut = f.observation.readPortalIsolationCuts(f.a.rootView, listOf(f.a, f.b))?.get(2)

      assertEquals(listOf(f.a, f.b), cut?.retainedOwners)
      assertEquals(mapOf(13 to listOf(14, 24)), f.projectionRetained)
      assertEquals(emptySet<View>(), cut?.branches)
    }
  }

  @Test
  fun `missing logical context rejects the complete group without substituting a native root`() {
    Fixture().use { f ->
      val receipt = requireNotNull(f.read(f.a.rootView)).getValue(f.a)
      f.receiptOverrides[14] =
        FabricPortalReceipt(
          receipt.publicationId,
          receipt.hostTag,
          intArrayOf(),
          receipt.ancestors.toIntArray(),
          receipt.logicalTags.toIntArray(),
          receipt.nativeParents.flatMap { (tag, parent) -> listOf(tag, parent) }.toIntArray(),
        )

      assertNull(f.observation.readPortalIsolationCuts(f.a.rootView, listOf(f.a, f.b)))
      assertNull(f.projectionRetained)
    }
  }

  @Test
  fun `every supplied owner must be attached before any window receipt is accepted`() {
    Fixture().use { f ->
      val window = f.a.rootView
      assertEquals(setOf(f.a, f.b), f.read(window)?.keys)
      f.root.removeView(f.modalContent)
      assertNotNull(f.source.receiptFromRegistry(f.manager, 2, f.a))
      assertNull(f.read(window))
      f.root.addView(f.modalContent)
      assertEquals(setOf(f.a, f.b), f.read(window)?.keys)
    }
  }

  @Test
  fun `coherent delivered states are accepted without execution markers`() {
    Fixture().use { f ->
      val next = f.newState()
      f.a.stateWrapper = next
      f.b.stateWrapper = next
      assertEquals(setOf(f.a, f.b), f.read(f.a.rootView)?.keys)
    }
  }

  @Test
  fun `coalesced reconciliation reads the states delivered when queued work executes`() {
    Fixture().use { f ->
      val window = f.a.rootView
      var result: Map<BottomSheetView, FabricPortalReceipt>? = null
      val observation = f.adapter.observe(f.boundary) { result = f.read(window) }
      try {
        val earlier = f.newState()
        f.a.stateWrapper = earlier
        f.b.stateWrapper = earlier
        f.mountListener?.didMountItems(f.manager)
        assertNull(result)
        assertEquals(0, f.readStates.size)

        val current = f.newState()
        f.a.stateWrapper = current
        f.b.stateWrapper = current
        f.mountListener?.didMountItems(f.manager)
        assertEquals(1, f.posted.size)
        f.posted.removeAt(0).run()

        assertEquals(setOf(f.a, f.b), result?.keys)
        assertEquals(2, f.readStates.size)
        f.readStates.forEach { states ->
          assertSame(current, states[14])
          assertSame(current, states[24])
        }
        assertEquals(0, f.posted.size)
      } finally {
        observation.remove()
      }
    }
  }

  @Test
  fun `queued observation reads all supplied owners from its own current source`() {
    Fixture().use { f ->
      val window = f.a.rootView
      var result: Map<BottomSheetView, FabricPortalReceipt>? = null
      lateinit var observation: ReactNativeMountReconciliationAdapter.Observation
      observation =
        f.adapter.observe(f.boundary) {
          result = observation.readPortalReceipts(window, listOf(f.a, f.b))
        }
      try {
        f.mountListener?.didMountItems(f.manager)
        assertNull(result)
        f.posted.removeAt(0).run()
        assertEquals(setOf(f.a, f.b), result?.keys)
        assertEquals(listOf(11), result?.get(f.a)?.contexts)
        assertEquals(listOf(21), result?.get(f.b)?.contexts)
      } finally {
        observation.remove()
      }
    }
  }

  @Test
  fun `lifecycle requests read the current owner group once and stop with the observation`() {
    Fixture().use { f ->
      val window = f.a.rootView
      var owners = listOf(f.a)
      var result: Map<BottomSheetView, FabricPortalReceipt>? = null
      lateinit var observation: ReactNativeMountReconciliationAdapter.Observation
      observation =
        f.adapter.observe(f.boundary) {
          result = observation.readPortalReceipts(window, owners)
        }
      observation.requestReconciliation()
      owners = listOf(f.a, f.b)
      observation.requestReconciliation()
      assertNull(result)
      while (f.posted.isNotEmpty()) f.posted.removeAt(0).run()
      assertEquals(setOf(f.a, f.b), result?.keys)

      result = null
      observation.requestReconciliation()
      observation.remove()
      while (f.posted.isNotEmpty()) f.posted.removeAt(0).run()
      assertNull(result)
      assertNull(observation.readPortalReceipts(window, emptyList()))
      observation.requestReconciliation()
      assertEquals(0, f.posted.size)
    }
  }

  @Test
  fun `queued observation projects current supplied owners using validated source members`() {
    Fixture().use { f ->
      val window = f.a.rootView
      val chrome = View(f.activity.get()).also(f.areaA::addView)
      var active = listOf(f.a)
      var result: Map<Int, PortalAccessibilityCut>? = null
      lateinit var observation: ReactNativeMountReconciliationAdapter.Observation
      observation =
        f.adapter.observe(f.boundary) {
          result = observation.readPortalIsolationCuts(window, active)
        }
      try {
        observation.requestReconciliation()
        active = listOf(f.a, f.b)
        val current = f.newState()
        f.a.stateWrapper = current
        f.b.stateWrapper = current
        f.posted.removeAt(0).run()

        assertEquals(setOf(2), result?.keys)
        assertEquals(setOf(chrome), result?.get(2)?.branches)
        assertEquals(listOf(f.a, f.b), result?.get(2)?.retainedOwners)
        assertSame(current, f.projectionStates?.get(14))
        assertSame(current, f.projectionStates?.get(24))
        assertEquals(mapOf(13 to listOf(14), 23 to listOf(24)), f.projectionRetained)
        assertEquals(13, f.projectionParents?.get(14))
        assertEquals(23, f.projectionParents?.get(24))

        observation.requestReconciliation()
        active = listOf(f.b)
        f.projectionResult =
          FabricPortalIsolationProjection(intArrayOf(), intArrayOf(21, 23, 23, 24))
        f.posted.removeAt(0).run()
        assertEquals(listOf(f.b), result?.get(2)?.retainedOwners)
        assertEquals(mapOf(23 to listOf(24)), f.projectionRetained)
        assertEquals(setOf(14, 24), f.projectionStates?.keys)
      } finally {
        observation.remove()
      }
    }
  }

  @Test
  fun `local cuts keep complete source state checks without requiring unrelated branches to be attached`() {
    Fixture().use { f ->
      val window = f.a.rootView
      val receipts = requireNotNull(f.read(window))
      val outside = View(f.activity.get()).apply { id = 40 }
      FrameLayout(f.activity.get()).addView(outside)
      f.registry[40] = outside
      for ((owner, receipt) in receipts) {
        f.receiptOverrides[owner.id] =
          FabricPortalReceipt(
            receipt.publicationId,
            receipt.hostTag,
            receipt.contexts.toIntArray(),
            receipt.ancestors.toIntArray(),
            (receipt.logicalTags + 40).toIntArray(),
            (receipt.nativeParents.flatMap { (tag, parent) -> listOf(tag, parent) } + listOf(40, 2))
              .toIntArray(),
          )
      }
      val background = View(f.activity.get()).also(f.areaA::addView)

      val cut = f.observation.readPortalIsolationCuts(window, listOf(f.a, f.b))?.get(2)

      assertEquals(setOf(background), cut?.branches)
      assertEquals(listOf(f.a, f.b), cut?.retainedOwners)
      assertEquals(setOf(14, 24), f.projectionStates?.keys)
      f.b.stateWrapper = null
      assertNull(f.observation.readPortalIsolationCuts(window, listOf(f.a)))
    }
  }

  @Test
  fun `projection reads source sheets outside the supplied Active owners and rejects missing source data`() {
    Fixture().use { f ->
      val window = f.a.rootView
      f.projectionResult = FabricPortalIsolationProjection(intArrayOf(), intArrayOf(11, 13, 13, 14))
      assertEquals(
        emptySet<View>(),
        f.observation.readPortalIsolationCuts(window, listOf(f.a))?.get(2)?.branches,
      )
      assertEquals(setOf(14, 24), f.projectionStates?.keys)
      assertEquals(mapOf(13 to listOf(14)), f.projectionRetained)

      val state = f.b.stateWrapper
      f.b.stateWrapper = null
      assertNull(f.observation.readPortalIsolationCuts(window, listOf(f.a)))
      f.b.stateWrapper = state
      f.projectionResult = null
      assertNull(f.observation.readPortalIsolationCuts(window, listOf(f.a)))
    }
  }

  @Test
  fun `projection validates every supplied owner and the source edges of non-Active sheets`() {
    Fixture().use { f ->
      val window = f.a.rootView
      assertNotNull(f.observation.readPortalIsolationCuts(window, listOf(f.a, f.b)))
      assertNull(f.observation.readPortalIsolationCuts(f.root, listOf(f.a)))
      f.root.removeView(f.modalContent)
      assertNull(f.observation.readPortalIsolationCuts(window, listOf(f.a, f.b)))
      f.root.addView(f.modalContent)
      assertNotNull(f.observation.readPortalIsolationCuts(window, listOf(f.a, f.b)))

      f.hostB.removeView(f.b)
      f.root.addView(f.b)
      assertNull(f.observation.readPortalIsolationCuts(window, listOf(f.a)))
    }
  }

  @Test
  fun `an observation cannot read before acquisition or after it is removed`() {
    Fixture().use { f ->
      val window = f.a.rootView
      f.observation.remove()
      val replacement = f.adapter.observe(f.boundary) {}
      try {
        assertNull(replacement.readPortalReceipts(window, listOf(f.a, f.b)))
        assertNull(replacement.readPortalIsolationCuts(window, listOf(f.a)))
        while (f.posted.isNotEmpty()) f.posted.removeAt(0).run()
        assertEquals(
          setOf(f.a, f.b),
          replacement.readPortalReceipts(window, listOf(f.a, f.b))?.keys,
        )
        assertEquals(
          emptyMap<BottomSheetView, FabricPortalReceipt>(),
          replacement.readPortalReceipts(window, emptyList()),
        )
        assertEquals(
          emptyMap<Int, PortalAccessibilityCut>(),
          replacement.readPortalIsolationCuts(window, emptyList()),
        )
        assertNotNull(replacement.readPortalIsolationCuts(window, listOf(f.a)))
        assertNull(f.observation.readPortalReceipts(window, listOf(f.a)))
        assertNull(f.observation.readPortalIsolationCuts(window, emptyList()))
      } finally {
        replacement.remove()
      }
      assertNull(replacement.readPortalReceipts(window, listOf(f.a, f.b)))
      assertNull(replacement.readPortalReceipts(window, emptyList()))
      assertNull(replacement.readPortalIsolationCuts(window, listOf(f.a)))
      assertNull(replacement.readPortalIsolationCuts(window, emptyList()))
    }
  }

  @Test
  fun `all surfaces in one manager must agree before a window group is returned`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val reactContext = BridgeReactContext(activity.get().applicationContext)
    val window = FrameLayout(activity.get())
    val surfaces =
      listOf(
        Surface(activity.get(), reactContext, 2, 10, 12, 14),
        Surface(activity.get(), reactContext, 4, 20, 22, 24),
      )
    val chrome = surfaces.map { View(activity.get()).also(it.area::addView) }
    try {
      surfaces.forEach { window.addView(it.root) }
      activity.get().setContentView(window)
      val registry = surfaces.flatMap { it.registry.entries }.associate { it.toPair() }
      val manager =
        Proxy.newProxyInstance(
          UIManager::class.java.classLoader,
          arrayOf(UIManager::class.java),
        ) { _, method, args ->
          if (method.name == "resolveView") registry[args!![0] as Int] else null
        } as UIManager
      val requestedSurfaces = mutableSetOf<Int>()
      val source =
        object : FabricPublicationLease {
          override fun bootstrap() = Unit

          override fun release() = Unit

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
            val surface = surfaces.singleOrNull { it.root.id == surfaceId } ?: return null
            return FabricPortalIsolationProjection(
              intArrayOf(),
              intArrayOf(surface.area.id, surface.host.id, surface.host.id, surface.sheet.id),
            )
          }

          override fun sheetTags(surfaceId: Int, tag: Int, state: StateWrapper?) =
            surfaces
              .singleOrNull { it.root.id == surfaceId }
              ?.let {
                intArrayOf(it.sheet.id)
              }

          override fun receipt(
            surfaceId: Int,
            tag: Int,
            states: Map<Int, StateWrapper?>,
          ): FabricPortalReceipt? {
            val surface = surfaces.singleOrNull { it.root.id == surfaceId } ?: return null
            if (states.keys != setOf(surface.sheet.id) || states.values.any { it == null })
              return null
            requestedSurfaces.add(surfaceId)
            return surface.receipt
          }
        }
      val owners = surfaces.map { it.sheet }
      assertEquals(
        setOf(owners[0], owners[1]),
        source.attachedReceiptsFromRegistry(manager, owners, window.rootView)?.keys,
      )
      assertEquals(setOf(2, 4), requestedSurfaces)
      val projections = source.attachedIsolationCutsFromRegistry(manager, owners, window.rootView)
      assertEquals(setOf(2, 4), projections?.keys)
      assertEquals(setOf(chrome[0]), projections?.get(2)?.branches)
      assertEquals(setOf(chrome[1]), projections?.get(4)?.branches)
      assertEquals(listOf(owners[0]), projections?.get(2)?.retainedOwners)
      assertEquals(listOf(owners[1]), projections?.get(4)?.retainedOwners)
      window.removeView(surfaces[1].root)
      assertNull(source.attachedReceiptsFromRegistry(manager, owners, window.rootView))
      assertNull(source.attachedIsolationCutsFromRegistry(manager, owners, window.rootView))
      window.addView(surfaces[1].root)
      assertEquals(
        setOf(owners[0], owners[1]),
        source.attachedReceiptsFromRegistry(manager, owners, window.rootView)?.keys,
      )
      assertEquals(
        setOf(2, 4),
        source.attachedIsolationCutsFromRegistry(manager, owners, window.rootView)?.keys,
      )
    } finally {
      surfaces.forEach { it.sheet.destroy() }
      activity.close()
    }
  }

  @Test
  fun `queued reconciliation rejects the hierarchy as it exists when work executes`() {
    Fixture().use { f ->
      val window = f.a.rootView
      var result: Map<BottomSheetView, FabricPortalReceipt>? = emptyMap()
      val observation = f.adapter.observe(f.boundary) { result = f.read(window) }
      try {
        f.mountListener?.didMountItems(f.manager)
        f.root.removeView(f.modalContent)
        f.posted.removeAt(0).run()
        assertNull(result)
        assertEquals(0, f.posted.size)

        f.root.addView(f.modalContent)
        f.mountListener?.didMountItems(f.manager)
        f.posted.removeAt(0).run()
        assertEquals(setOf(f.a, f.b), result?.keys)
      } finally {
        observation.remove()
      }
    }
  }

  private class Surface(
    context: android.content.Context,
    reactContext: BridgeReactContext,
    surfaceId: Int,
    areaTag: Int,
    hostTag: Int,
    sheetTag: Int,
  ) {
    val root = TestReactRoot(context).apply { id = surfaceId }
    val area = FrameLayout(context).apply { id = areaTag }
    val host =
      FrameLayout(context).apply {
        id = hostTag
        setTag(
          com.facebook.react.R.id.view_tag_native_id,
          "__react_native_bottom_sheet_portal_host",
        )
      }
    val sheet =
      BottomSheetView(ThemedReactContext(reactContext, context, "test", surfaceId)).apply {
        id = sheetTag
        stateWrapper =
          Proxy.newProxyInstance(
            StateWrapper::class.java.classLoader,
            arrayOf(StateWrapper::class.java),
          ) { _, _, _ ->
            null
          } as StateWrapper
      }
    val registry = listOf(root, area, host, sheet).associateBy { it.id }
    val receipt =
      FabricPortalReceipt(
        "source:$surfaceId",
        hostTag,
        intArrayOf(areaTag),
        intArrayOf(sheetTag, hostTag, areaTag, surfaceId),
        intArrayOf(surfaceId, areaTag, hostTag, sheetTag),
        intArrayOf(surfaceId, 0, areaTag, surfaceId, hostTag, areaTag, sheetTag, hostTag),
      )

    init {
      host.addView(sheet)
      area.addView(host)
      root.addView(area)
    }
  }

  private class Fixture : AutoCloseable {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    val context = BridgeReactContext(activity.get().applicationContext)
    val themed = ThemedReactContext(context, activity.get(), "test", 2)
    val root = TestReactRoot(activity.get()).apply { id = 2 }
    val areaA = FrameLayout(activity.get()).apply { id = 11 }
    val areaB = FrameLayout(activity.get()).apply { id = 21 }
    val hostA = host(13)
    val hostB = host(23)
    val a = BottomSheetView(themed).apply { id = 14 }
    val b = BottomSheetView(themed).apply { id = 24 }
    val modal = ReactModalHostView(themed).apply { id = 30 }
    val modalContent: ViewGroup
    val registry =
      mutableMapOf<Int, View>(
        2 to root,
        11 to areaA,
        13 to hostA,
        14 to a,
        21 to areaB,
        23 to hostB,
        24 to b,
        30 to modal,
      )
    val manager =
      Proxy.newProxyInstance(
        UIManager::class.java.classLoader,
        arrayOf(UIManager::class.java),
      ) { _, method, args ->
        if (method.name == "resolveView") registry[args!![0] as Int] else null
      } as UIManager
    val readStates = mutableListOf<Map<Int, StateWrapper?>>()
    val receiptOverrides = mutableMapOf<Int, FabricPortalReceipt>()
    var projectionResult: FabricPortalIsolationProjection? =
      FabricPortalIsolationProjection(intArrayOf(), intArrayOf(11, 13, 13, 14, 21, 23, 23, 24))
    var projectionStates: Map<Int, StateWrapper?>? = null
    var projectionRetained: Map<Int, List<Int>>? = null
    var projectionParents: Map<Int, Int>? = null
    val contextPaths =
      mutableMapOf(11 to listOf(11), 14 to listOf(11), 21 to listOf(21), 24 to listOf(21))
    val source =
      object : FabricPublicationLease {
        override fun bootstrap() = Unit

        override fun release() = Unit

        override fun contextPath(
          surfaceId: Int,
          tag: Int,
          states: Map<Int, com.facebook.react.uimanager.StateWrapper?>,
        ): List<Int>? = contextPaths[tag]

        override fun isolation(
          surfaceId: Int,
          states: Map<Int, StateWrapper?>,
          nativeParents: Map<Int, Int>,
          retainedByHost: Map<Int, List<Int>>,
        ): FabricPortalIsolationProjection? {
          projectionStates = states.toMap()
          projectionRetained = retainedByHost.toMap()
          projectionParents = nativeParents.toMap()
          return projectionResult
        }

        override fun sheetTags(surfaceId: Int, tag: Int, state: StateWrapper?) = intArrayOf(14, 24)

        override fun receipt(
          surfaceId: Int,
          tag: Int,
          states: Map<Int, StateWrapper?>,
        ): FabricPortalReceipt? {
          readStates.add(states.toMap())
          if (states.keys != setOf(14, 24) || states.values.any { it == null }) return null
          receiptOverrides[tag]?.let {
            return it
          }
          return FabricPortalReceipt(
            "source:1",
            if (tag == 14) 13 else 23,
            intArrayOf(if (tag == 14) 11 else 21),
            if (tag == 14) intArrayOf(14, 13, 11, 2) else intArrayOf(24, 23, 21, 30),
            intArrayOf(2, 11, 13, 14, 21, 23, 24, 30),
            intArrayOf(2, 0, 11, 2, 13, 11, 14, 13, 30, 2, 21, 30, 23, 21, 24, 23),
          )
        }
      }
    var mountListener: UIManagerListener? = null
    val boundary =
      object : ReactNativeMountCallbacks {
        override val identity: Any = manager
        override val isFabric = true

        override fun addListener(listener: UIManagerListener) {
          mountListener = listener
        }

        override fun removeListener(listener: UIManagerListener) {
          mountListener = null
        }

        override fun enqueueAfterPaperMount(block: () -> Unit) = Unit
      }
    val posted = mutableListOf<Runnable>()
    val adapter =
      ReactNativeMountReconciliationAdapter(
        postToUiThread = { posted.add(it) },
        acquirePublicationSource = { source },
      )
    val observation = adapter.observe(boundary) {}

    init {
      posted.removeAt(0).run()
      val state = newState()
      a.stateWrapper = state
      b.stateWrapper = state
      hostA.addView(a)
      areaA.addView(hostA)
      hostB.addView(b)
      areaB.addView(hostB)
      modal.addView(areaB, 0)
      modalContent = areaB.parent as ViewGroup
      root.addView(areaA)
      root.addView(modal)
      root.addView(modalContent)
      activity.get().setContentView(root)
    }

    fun stackInAreaA() {
      hostB.removeView(b)
      hostA.addView(b)
      for (tag in listOf(14, 24)) {
        receiptOverrides[tag] =
          FabricPortalReceipt(
            "source:1",
            13,
            intArrayOf(11),
            intArrayOf(tag, 13, 11, 2),
            intArrayOf(2, 11, 13, 14, 21, 23, 24, 30),
            intArrayOf(2, 0, 11, 2, 13, 11, 14, 13, 30, 2, 21, 30, 23, 21, 24, 13),
          )
      }
      projectionResult = FabricPortalIsolationProjection(intArrayOf(14), intArrayOf(11, 13, 13, 24))
    }

    fun read(window: View, owners: Collection<BottomSheetView> = listOf(a, b)) =
      source.attachedReceiptsFromRegistry(manager, 2, owners, window)

    fun newState() =
      Proxy.newProxyInstance(
        StateWrapper::class.java.classLoader,
        arrayOf(StateWrapper::class.java),
      ) { _, _, _ ->
        null
      } as StateWrapper

    private fun host(tag: Int) =
      FrameLayout(activity.get()).apply {
        id = tag
        setTag(
          com.facebook.react.R.id.view_tag_native_id,
          "__react_native_bottom_sheet_portal_host",
        )
      }

    override fun close() {
      observation.remove()
      while (posted.isNotEmpty()) posted.removeAt(0).run()
      a.destroy()
      b.destroy()
      modal.onDropInstance()
      activity.close()
    }
  }
}
