@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)

package com.swmansion.reactnativebottomsheet.accessibility

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactHost
import com.facebook.react.fabric.FabricUIManager
import com.facebook.react.interfaces.TaskInterface
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** Runs alone: it loads the production JNI library rather than the fixture's native copy. */
@RunWith(AndroidJUnit4::class)
class FabricPortalPublicationInstallJniTest {
  @Test
  fun observationsShareDeferredNativeSourceOnLiveFabricManager() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.context
    val bundle = File.createTempFile("bottom-sheet-install-", ".js", context.cacheDir)
    bundle.writeText("void 0;")
    val host =
      DefaultReactHost.getDefaultReactHost(
        context,
        emptyList(),
        jsBundleFilePath = bundle.absolutePath,
        useDevSupport = false,
      )
    val events = mutableListOf<String>()
    val adapter =
      ReactNativeMountReconciliationAdapter(
        acquirePublicationSource = { identity ->
          events.add("acquire")
          FabricPortalPublication.acquire(identity)?.let { native ->
            object : FabricPublicationLease by native {
              override fun bootstrap() {
                native.bootstrap()
                events.add("bootstrap")
              }

              override fun release() {
                native.release()
                events.add("release")
              }
            }
          }
        }
      )
    var first: ReactNativeMountReconciliationAdapter.Observation? = null
    var second: ReactNativeMountReconciliationAdapter.Observation? = null
    lateinit var manager: FabricUIManager
    try {
      await(host.start())
      onMain {
        // The SDK root keeps the Android host Context, not a ReactContext.
        // Its Surface stays stopped and unattached; no Activity or UI is displayed.
        val root = requireNotNull(host.createSurface(context, "Unstarted", null).view)
        val boundary = requireNotNull(ReactNativeMountReconciliationAdapter.resolve(root))
        manager = boundary.identity as FabricUIManager
        assertTrue(boundary.isFabric)
        first = adapter.observe(boundary) {}
        val otherBoundary = requireNotNull(ReactNativeMountReconciliationAdapter.resolve(root))
        assertSame(manager, otherBoundary.identity)
        second = adapter.observe(otherBoundary) {}
        assertTrue("Native acquisition must leave the observer's stack first", events.isEmpty())
      }
      onMain {
        assertEquals(listOf("acquire", "bootstrap"), events)
        first?.remove()
        first = null
        assertFalse(events.contains("release"))
        second?.remove()
        second = null
        assertFalse("Native release must be deferred", events.contains("release"))
      }
      onMain { assertEquals(listOf("acquire", "bootstrap", "release"), events) }
    } finally {
      try {
        onMain {
          first?.remove()
          second?.remove()
        }
        onMain {} // Drain the adapter's final posted native release before SDK destruction.
        await(host.destroy("Publication installation test", null))
      } finally {
        bundle.delete()
      }
    }
    onMain { assertNull(FabricPortalPublication.acquire(manager)) }
  }

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
        InstrumentationRegistry.getArguments().getString("bottomSheetPublicationInstallJni") ==
          "true"
      )
      SoLoader.init(InstrumentationRegistry.getInstrumentation().context, OpenSourceMergedSoMapping)
      DefaultNewArchitectureEntryPoint.load()
    }
  }
}
