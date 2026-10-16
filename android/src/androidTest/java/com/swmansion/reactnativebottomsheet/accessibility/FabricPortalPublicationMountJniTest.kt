@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)

package com.swmansion.reactnativebottomsheet.accessibility

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactHost
import com.facebook.react.fabric.FabricUIManager
import com.facebook.react.interfaces.TaskInterface
import com.facebook.react.runtime.ReactSurfaceImpl
import com.facebook.react.shell.MainReactPackage
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.react.uimanager.IllegalViewOperationException
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
        acquirePublicationSource = { FabricPortalPublication.acquire(it).also { lease = it } }
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
              requireNotNull(lease).receiptFromRegistry(manager, surface.surfaceID, sheet)
                ?: return@verify
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
              "The fixture must retain a logical ancestor with no native registry view",
              receipt.ancestors.any {
                try {
                  manager.resolveView(it) == null
                } catch (_: IllegalViewOperationException) {
                  true
                }
              },
            )
            assertNull(receipt.attachedNativePath(manager::resolveView, sheet, root.rootView))
            assertNull(
              requireNotNull(lease)
                .attachedReceiptsFromRegistry(
                  manager,
                  surface.surfaceID,
                  listOf(sheet),
                  root.rootView,
                )
            )
            assertNull(requireNotNull(observation).readPortalReceipts(root.rootView, listOf(sheet)))
            assertNull(
              requireNotNull(observation)
                .readPortalIsolationBranches(
                  root.rootView,
                  listOf(sheet),
                  listOf(sheet),
                )
            )
            publicationId = receipt.publicationId
            awaitedPublication.countDown()
          } catch (caught: Throwable) {
            failure.set(caught)
            awaitedPublication.countDown()
          }
        }
        observation = adapter.observe(boundary, verifyMountedState)
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
            verifyMountedState,
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
        val manager =
          requireNotNull(ReactNativeMountReconciliationAdapter.resolve(root)).identity
            as FabricUIManager
        val sheet = requireNotNull(mountedSheet)
        val previousState = requireNotNull(sheet.stateWrapper)
        // The fixture reports each generation from its React layout effect, after completeRoot.
        // The UI thread stays occupied here, so neither native batch can execute yet.
        for (generation in 1..2) {
          (surface as ReactSurfaceImpl).updateInitProps(
            Bundle().apply {
              putInt("generation", generation)
              putBoolean("contextInWrapper", generation == 2)
            }
          )
          assertTrue(
            "React did not commit queued generation $generation",
            committed.getValue(generation).await(5, TimeUnit.SECONDS),
          )
        }
        assertSame(previousState, sheet.stateWrapper)
        val stillMounted =
          requireNotNull(lease).receiptFromRegistry(manager, surface.surfaceID, sheet)
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
        val sourceParent = sheet.parent as ViewGroup
        val sourceIndex = sourceParent.indexOfChild(sheet)
        sourceParent.removeView(sheet)
        root.addView(sheet)
        try {
          assertNull(requireNotNull(lease).receiptFromRegistry(manager, surface.surfaceID, sheet))
        } finally {
          root.removeView(sheet)
          sourceParent.addView(sheet, sourceIndex)
        }
        assertNotNull(requireNotNull(lease).receiptFromRegistry(manager, surface.surfaceID, sheet))
        val background = descendants(root).single { it.tag == "local-background" }
        val backgroundParent = background.parent as ViewGroup
        val backgroundIndex = backgroundParent.indexOfChild(background)
        backgroundParent.removeView(background)
        root.addView(background)
        try {
          assertNull(requireNotNull(lease).receiptFromRegistry(manager, surface.surfaceID, sheet))
        } finally {
          root.removeView(background)
          backgroundParent.addView(background, backgroundIndex)
        }
        assertNotNull(requireNotNull(lease).receiptFromRegistry(manager, surface.surfaceID, sheet))
        val inactive =
          descendants(root).filterIsInstance<BottomSheetView>().single {
            it.tag == "inactive-nonportal-sheet"
          }
        val parent = inactive.parent as ViewGroup
        val index = parent.indexOfChild(inactive)
        parent.removeView(inactive)
        try {
          assertSame(inactive, manager.resolveView(inactive.id))
          assertNull(requireNotNull(lease).receiptFromRegistry(manager, surface.surfaceID, sheet))
        } finally {
          parent.addView(inactive, index)
        }
        assertNotNull(requireNotNull(lease).receiptFromRegistry(manager, surface.surfaceID, sheet))
        val state = requireNotNull(inactive.stateWrapper)
        inactive.stateWrapper = null
        try {
          assertNull(requireNotNull(lease).receiptFromRegistry(manager, surface.surfaceID, sheet))
        } finally {
          inactive.stateWrapper = state
        }
        // Restoring the exact State is readable when the complete source/structure agrees again.
        assertNotNull(requireNotNull(lease).receiptFromRegistry(manager, surface.surfaceID, sheet))
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
        InstrumentationRegistry.getArguments().getString("bottomSheetPublicationMountJni") == "true"
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
