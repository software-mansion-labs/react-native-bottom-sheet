@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)

package com.swmansion.reactnativebottomsheet.accessibility

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactHost
import com.facebook.react.fabric.FabricUIManager
import com.facebook.react.interfaces.TaskInterface
import com.facebook.react.runtime.ReactSurfaceImpl
import com.facebook.react.shell.MainReactPackage
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ViewManager
import com.facebook.soloader.SoLoader
import com.swmansion.reactnativebottomsheet.BottomSheetPackage
import com.swmansion.reactnativebottomsheet.BottomSheetView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** Runs alone with the app's actual codegen registration; no Activity or window is created. */
@RunWith(AndroidJUnit4::class)
class FabricPortalPublicationMountJniTest {
  @Test
  fun reobservingMountedSheetBootstrapsNewPublicationUntilItsSurfaceStops() {
    val context = InstrumentationRegistry.getInstrumentation().context
    val mounted = CountDownLatch(1)
    val refreshed = CountDownLatch(1)
    val updated = CountDownLatch(1)
    val committed = mapOf(1 to CountDownLatch(1), 2 to CountDownLatch(1))
    var awaitedPublication = mounted
    val failure = AtomicReference<Throwable?>()
    val host =
      DefaultReactHost.getDefaultReactHost(
        context,
        listOf(
          MainReactPackage(),
          BottomSheetPackage(),
          PublicationMountProbePackage {
            committed[it]?.countDown()
          },
        ),
        jsBundleAssetPath = "publication-mount.bundle",
        useDevSupport = false,
        exceptionHandler = {
          failure.set(it)
          mounted.countDown()
          refreshed.countDown()
          updated.countDown()
        },
      )
    val surface = host.createSurface(context, "MountedPublication", null)
    val root = requireNotNull(surface.view)
    var lease: FabricPublicationLease? = null
    val adapter =
      ReactNativeMountReconciliationAdapter(
        acquirePublicationSource = { FabricPortalPublication.acquire(it).also { lease = it } },
      )
    var observation: ReactNativeMountReconciliationAdapter.Observation? = null
    var deliveredState: StateWrapper? = null
    var sheetTag = View.NO_ID
    var mountedSheet: BottomSheetView? = null
    var publicationId: String? = null
    var expectedWrapperContext = false
    lateinit var verifyMountedState: () -> Unit
    var started = false
    try {
      await(host.start())
      onMain {
        val size = View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)
        root.measure(size, size)
        root.layout(0, 0, 640, 640)
        val boundary = requireNotNull(ReactNativeMountReconciliationAdapter.resolve(root))
        val manager = boundary.identity as FabricUIManager
        verifyMountedState = verify@{
          if (awaitedPublication.count == 0L) return@verify
          try {
            val views = descendants(root)
            val sheets = views.filterIsInstance<BottomSheetView>()
            if (sheets.size != 2) return@verify
            val sheet = sheets.single { it.tag == "mounted-sheet" }
            val area = views.single { it.tag == "local-area" }
            val painted = views.single { it.tag == "painted-ancestor" } as ViewGroup
            val content = views.single { it.tag == "mounted-content" }
            val portalHost = views.single {
              it.getTag(com.facebook.react.R.id.view_tag_native_id) ==
                "__react_native_bottom_sheet_portal_host"
            }
            assertSame(sheet, manager.resolveView(sheet.id))
            assertFalse(root.isAttachedToWindow)
            assertFalse(sheet.isAttachedToWindow)
            if (mountedSheet != null) assertSame(mountedSheet, sheet)
            mountedSheet = sheet
            deliveredState = requireNotNull(sheet.stateWrapper)
            sheetTag = sheet.id
            val receipt =
              requireNotNull(lease)
                .receipt(
                  surface.surfaceID,
                  sheet.id,
                  sheets.associate { it.id to it.stateWrapper },
                ) ?: return@verify
            assertTrue(
              "The painted view remains a logical ancestor",
              painted.id in receipt.ancestors,
            )
            assertTrue("The painted body remains materialized", painted.id in receipt.nativeParents)
            assertEquals(0, painted.childCount)
            assertSame(painted.parent, area.parent)
            assertEquals((area.parent as View).id, receipt.nativeParents[area.id])
            assertNotNull(receipt.nativeMembers(manager::resolveView))
            assertSame(content, manager.resolveView(content.id))
            assertEquals(sheet.id, receipt.nativeParents[content.id])
            val paintedParent = painted.parent as ViewGroup
            val paintedIndex = paintedParent.indexOfChild(painted)
            paintedParent.removeView(painted)
            try {
              assertNull(receipt.nativeMembers(manager::resolveView))
              val local =
                receipt.nativeMembers(manager::resolveView, setOf(area.id, portalHost.id, sheet.id))
              assertNotNull(
                "An unrelated disconnected body must not invalidate local source members",
                local,
              )
              assertSame(content, requireNotNull(local)[content.id])
            } finally {
              paintedParent.addView(painted, paintedIndex)
            }
            assertNotNull(
              "Hoisted descendants must follow source mount parents instead of every painted logical ancestor",
              receipt.nativePath(manager::resolveView, sheet),
            )
            assertTrue(receipt.publicationId.isNotEmpty())
            assertEquals(portalHost.id, receipt.hostTag)
            if (expectedWrapperContext) {
              assertEquals(1, receipt.contexts.size)
              assertNotEquals(area.id, receipt.contexts.single())
              assertTrue(receipt.contexts.single() in receipt.ancestors)
              assertEquals(
                "Background generation 2",
                views.single { it.tag == "local-background" }.contentDescription?.toString(),
              )
            } else {
              assertEquals(listOf(area.id), receipt.contexts)
            }
            assertTrue(
              "The fixture must retain a flattened logical ancestor",
              receipt.ancestors.any { it !in receipt.nativeParents },
            )
            assertNull(receipt.attachedNativePath(manager::resolveView, sheet, root.rootView))
            assertNull(
              adapter.readPortalInputContexts(
                boundary,
                surface.surfaceID,
                listOf(sheet),
                root.rootView,
                content,
              ),
            )
            assertNull(
              requireNotNull(observation)
                .readPortalIsolationCuts(
                  root.rootView,
                  listOf(sheet),
                ),
            )
            publicationId = receipt.publicationId
            awaitedPublication.countDown()
          } catch (caught: Throwable) {
            failure.set(caught)
            awaitedPublication.countDown()
          }
        }
        observation = adapter.observe(boundary, reconcile = verifyMountedState)
        host.onHostResume(null)
      }
      // Drain acquisition/bootstrap before React starts its first render.
      onMain { assertNotNull(lease) }
      await(surface.start())
      started = true
      assertTrue("Production State delivery timed out", mounted.await(20, TimeUnit.SECONDS))
      failure.get()?.let { throw it }
      val firstPublicationId = requireNotNull(publicationId)
      val firstState = requireNotNull(deliveredState)
      val firstSource = requireNotNull(lease)
      onMain {
        val manager =
          requireNotNull(ReactNativeMountReconciliationAdapter.resolve(root)).identity
            as FabricUIManager
        val sheet = requireNotNull(mountedSheet)
        val receipt =
          requireNotNull(
            firstSource.receipt(
              surface.surfaceID,
              sheet.id,
              deliveredStates(firstSource, manager, surface.surfaceID, sheet),
            ),
          )
        val flatTag = receipt.ancestors.first { it !in receipt.nativeParents }
        val beforePreallocation = sheet.stateWrapper
        // Exercise the SDK's real preallocation without mounting the flattened ancestor.
        // Reflection is confined to this external SDK boundary, never production code.
        val mounting =
          FabricUIManager::class
            .java
            .getDeclaredField("mMountingManager")
            .apply { isAccessible = true }
            .get(manager)
        val surfaceManager =
          requireNotNull(
            mounting.javaClass
              .getMethod("getSurfaceManager", Int::class.javaPrimitiveType)
              .invoke(mounting, surface.surfaceID),
          )
        surfaceManager.javaClass
          .getMethod(
            "preallocateView",
            String::class.java,
            Int::class.javaPrimitiveType,
            ReadableMap::class.java,
            StateWrapper::class.java,
            Boolean::class.javaPrimitiveType,
          )
          .invoke(surfaceManager, "RCTView", flatTag, JavaOnlyMap(), null, true)
        val allocated = requireNotNull(manager.resolveView(flatTag))
        assertNull(allocated.parent)
        assertFalse(allocated.isAttachedToWindow)
        assertSame(beforePreallocation, sheet.stateWrapper)
        assertNotNull(
          "Preallocation must not invalidate the mounted source path",
          receipt.nativePath(manager::resolveView, sheet),
        )
      }
      onMain {
        observation?.remove()
        observation = null
      }
      onMain {
        // The queued release ran first. Keep the mounted surface and its JS tree unchanged.
        assertNull(firstSource.sheetTags(surface.surfaceID, sheetTag, firstState))
        awaitedPublication = refreshed
        observation =
          adapter.observe(
            requireNotNull(ReactNativeMountReconciliationAdapter.resolve(root)),
            reconcile = verifyMountedState,
          )
      }
      onMain {
        assertNotSame(firstSource, lease)
        assertNull(requireNotNull(lease).sheetTags(surface.surfaceID, sheetTag, firstState))
      }
      assertTrue(
        "Reobserved mounted State delivery timed out",
        refreshed.await(20, TimeUnit.SECONDS),
      )
      failure.get()?.let { throw it }
      assertNotEquals(firstPublicationId, publicationId)
      val beforeQueuedUpdates = requireNotNull(publicationId)
      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      onMain {
        val sheet = requireNotNull(mountedSheet)
        val previousState = requireNotNull(sheet.stateWrapper)
        // The fixture reports each generation from its React layout effect, after completeRoot.
        // The UI thread stays occupied here, so neither native batch can execute yet.
        for (generation in 1..2) {
          (surface as ReactSurfaceImpl).updateInitProps(
            Bundle().apply {
              putInt("generation", generation)
              putBoolean("contextInWrapper", generation == 2)
            },
          )
          assertTrue(
            "React did not commit queued generation $generation",
            committed.getValue(generation).await(5, TimeUnit.SECONDS),
          )
        }
        assertSame(previousState, sheet.stateWrapper)
        val source = requireNotNull(lease)
        val manager =
          requireNotNull(ReactNativeMountReconciliationAdapter.resolve(root)).identity
            as FabricUIManager
        val stillMounted =
          source.receipt(
            surface.surfaceID,
            sheet.id,
            deliveredStates(source, manager, surface.surfaceID, sheet),
          )
        assertNotNull(stillMounted)
        assertEquals(beforeQueuedUpdates, stillMounted?.publicationId)
        expectedWrapperContext = true
        awaitedPublication = updated
      }
      assertTrue("Queued production State delivery timed out", updated.await(20, TimeUnit.SECONDS))
      failure.get()?.let { throw it }
      assertNotEquals(beforeQueuedUpdates, publicationId)
      onMain {
        val manager =
          requireNotNull(ReactNativeMountReconciliationAdapter.resolve(root)).identity
            as FabricUIManager
        val sheet = requireNotNull(mountedSheet)
        val source = requireNotNull(lease)
        val states = deliveredStates(source, manager, surface.surfaceID, sheet)
        val receipt = requireNotNull(source.receipt(surface.surfaceID, sheet.id, states))
        val retained = mapOf(receipt.hostTag to listOf(sheet.id))
        val projection =
          requireNotNull(
            source.isolation(surface.surfaceID, states, receipt.nativeParents, retained),
          )
        val scope =
          projection.hiddenBranches +
            projection.retainedChildren.keys +
            projection.retainedChildren.values.flatten()
        assertNotNull(receipt.nativeMembers(manager::resolveView, scope))
        val sourceParent = sheet.parent as ViewGroup
        val sourceIndex = sourceParent.indexOfChild(sheet)
        sourceParent.removeView(sheet)
        root.addView(sheet)
        try {
          assertNull(receipt.nativePath(manager::resolveView, sheet))
          assertNull(receipt.nativeMembers(manager::resolveView, scope))
        } finally {
          root.removeView(sheet)
          sourceParent.addView(sheet, sourceIndex)
        }
        assertNotNull(receipt.nativePath(manager::resolveView, sheet))
        assertNotNull(receipt.nativeMembers(manager::resolveView, scope))
        val background = descendants(root).single { it.tag == "local-background" }
        // Generation 2 moves the logical context into the wrapper, excluding this sibling.
        assertFalse(background.id in scope)
        val backgroundParent = background.parent as ViewGroup
        val backgroundIndex = backgroundParent.indexOfChild(background)
        backgroundParent.removeView(background)
        root.addView(background)
        try {
          assertNotNull(receipt.nativeMembers(manager::resolveView, scope))
        } finally {
          root.removeView(background)
          backgroundParent.addView(background, backgroundIndex)
        }
        assertNotNull(receipt.nativeMembers(manager::resolveView, scope))
        val inactive =
          descendants(root).filterIsInstance<BottomSheetView>().single {
            it.tag == "inactive-nonportal-sheet"
          }
        val parent = inactive.parent as ViewGroup
        val index = parent.indexOfChild(inactive)
        parent.removeView(inactive)
        try {
          assertSame(inactive, manager.resolveView(inactive.id))
          assertNotNull(
            source.receipt(
              surface.surfaceID,
              sheet.id,
              deliveredStates(source, manager, surface.surfaceID, sheet),
            ),
          )
          assertNotNull(receipt.nativePath(manager::resolveView, sheet))
          assertNotNull(receipt.nativeMembers(manager::resolveView, scope))
        } finally {
          parent.addView(inactive, index)
        }
        assertNotNull(receipt.nativeMembers(manager::resolveView, scope))
        val state = requireNotNull(inactive.stateWrapper)
        inactive.stateWrapper = null
        try {
          val missing = deliveredStates(source, manager, surface.surfaceID, sheet)
          assertNull(source.receipt(surface.surfaceID, sheet.id, missing))
          assertNull(source.isolation(surface.surfaceID, missing, receipt.nativeParents, retained))
        } finally {
          inactive.stateWrapper = state
        }
        val restored = deliveredStates(source, manager, surface.surfaceID, sheet)
        assertSame(state, restored[inactive.id])
        assertNotNull(source.receipt(surface.surfaceID, sheet.id, restored))
        assertNotNull(
          source.isolation(surface.surfaceID, restored, receipt.nativeParents, retained),
        )
      }
      var stoppedStates = emptyMap<Int, StateWrapper?>()
      onMain {
        stoppedStates =
          descendants(root).filterIsInstance<BottomSheetView>().associate {
            it.id to it.stateWrapper
          }
        assertNotNull(requireNotNull(lease).receipt(surface.surfaceID, sheetTag, stoppedStates))
      }
      await(surface.stop())
      started = false
      onMain {
        assertNull(requireNotNull(lease).receipt(surface.surfaceID, sheetTag, stoppedStates))
      }
    } finally {
      try {
        onMain {
          observation?.remove()
          host.onHostPause()
        }
        onMain {} // Drain the final native release before SDK teardown.
        if (started) await(surface.stop())
      } finally {
        await(host.destroy("Publication mount test", null))
      }
    }
  }

  /** Fixture assembly only: validation remains in the real lease and native-path primitives. */
  private fun deliveredStates(
    source: FabricPublicationLease,
    manager: FabricUIManager,
    surfaceId: Int,
    sheet: BottomSheetView,
  ): Map<Int, StateWrapper?> =
    requireNotNull(source.sheetTags(surfaceId, sheet.id, sheet.stateWrapper)).associateWith { tag ->
      (manager.resolveView(tag) as? BottomSheetView)?.stateWrapper
    }

  private fun descendants(view: View): List<View> =
    listOf(view) +
      if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
      } else emptyList()

  private fun await(task: TaskInterface<Void>) {
    assertTrue("ReactHost lifecycle timed out", task.waitForCompletion(20, TimeUnit.SECONDS))
    task.getError()?.let { throw it }
    assertFalse(task.isCancelled())
  }

  private fun onMain(block: () -> Unit) {
    var failure: Throwable? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      try {
        block()
      } catch (caught: Throwable) {
        failure = caught
      }
    }
    failure?.let { throw it }
  }

  companion object {
    @BeforeClass
    @JvmStatic
    fun initializeSdk() {
      assumeTrue(
        InstrumentationRegistry.getArguments().getString("bottomSheetPublicationMountJni") ==
          "true",
      )
      SoLoader.init(InstrumentationRegistry.getInstrumentation().context, OpenSourceMergedSoMapping)
      DefaultNewArchitectureEntryPoint.load()
    }
  }
}

private class PublicationMountProbePackage(private val onCommit: (Int) -> Unit) : ReactPackage {
  @Suppress("OVERRIDE_DEPRECATION")
  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
    listOf(PublicationMountProbeModule(reactContext, onCommit))

  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
    emptyList()
}

class PublicationMountProbeModule(
  context: ReactApplicationContext,
  private val onCommit: (Int) -> Unit,
) : ReactContextBaseJavaModule(context) {
  override fun getName() = "PublicationMountProbe"

  @ReactMethod fun didCommit(generation: Int) = onCommit(generation)
}
