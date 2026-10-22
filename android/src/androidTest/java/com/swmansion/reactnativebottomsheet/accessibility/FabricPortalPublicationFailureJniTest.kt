// Non-UI SDK regression: actual JNI State delivery with one test-only native layout failure.
@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)
@file:Suppress("DEPRECATION", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.swmansion.reactnativebottomsheet.accessibility

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactHost
import com.facebook.react.fabric.FabricUIManager
import com.facebook.react.interfaces.TaskInterface
import com.facebook.react.runtime.ReactSurfaceImpl
import com.facebook.react.shell.MainReactPackage
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.react.uimanager.*
import com.facebook.soloader.SoLoader
import com.swmansion.reactnativebottomsheet.BottomSheetPackage
import com.swmansion.reactnativebottomsheet.BottomSheetView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FabricPortalPublicationFailureJniTest {
  @Test
  fun coherentSourceRemainsReadableAfterAnUnrelatedNativeLayoutFailure() {
    val context = InstrumentationRegistry.getInstrumentation().context
    val runId = InstrumentationRegistry.getArguments().getString("failureProbeRun")
    val armed = AtomicBoolean(false)
    val failures = AtomicInteger(0)
    val committed = (0..2).associateWith { CountDownLatch(1) }
    val observed = (0..2).associateWith { CountDownLatch(1) }
    val crash = AtomicReference<Throwable?>()
    val events = mutableListOf<String>()
    val results = mutableListOf<JSONObject>()
    var phase = 0
    var previousPublication: String? = null
    var lease: FabricPublicationLease? = null
    var faultSnapshot: (() -> Unit)? = null
    var stateAtFault: StateWrapper? = null
    var publicationAtFault: String? = null
    var batchAtFault: String? = null
    var stateBeforeFault: StateWrapper? = null
    var observation: ReactNativeMountReconciliationAdapter.Observation? = null
    // Test-only diagnostics establish that the native fault interrupted its batch.
    val markers =
      object : ReactMarker.FabricMarkerListener {
        override fun logFabricMarker(
          name: ReactMarkerConstants,
          tag: String?,
          key: Int,
          time: Long,
        ) {
          if (!UiThreadUtil.isOnUiThread()) return
          when (name) {
            ReactMarkerConstants.FABRIC_BATCH_EXECUTION_START -> events.add("start:$key")
            ReactMarkerConstants.FABRIC_BATCH_EXECUTION_END -> events.add("end:$key")
            else -> Unit
          }
        }
      }
    val host =
      DefaultReactHost.getDefaultReactHost(
        context,
        listOf(
          MainReactPackage(),
          BottomSheetPackage(),
          MountFailureProbePackage(
            armed,
            failures,
            { faultSnapshot?.invoke() },
            { committed[it]?.countDown() },
          ),
        ),
        jsBundleAssetPath = "failure-mount.bundle",
        useDevSupport = false,
        exceptionHandler = { caught ->
          crash.set(caught)
          observed.values.forEach { it.countDown() }
        },
      )
    val surface = host.createSurface(context, "FailurePublication", null)
    val root = requireNotNull(surface.view)
    val adapter =
      ReactNativeMountReconciliationAdapter(
        acquirePublicationSource = {
          FabricPortalPublication.acquire(it).also { source -> lease = source }
        }
      )
    var started = false
    try {
      await(host.start())
      onMain {
        val size = View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)
        root.measure(size, size)
        root.layout(0, 0, 640, 640)
        val boundary = requireNotNull(ReactNativeMountReconciliationAdapter.resolve(root))
        val manager = boundary.identity as FabricUIManager
        faultSnapshot = {
          val sheet = descendants(root).filterIsInstance<BottomSheetView>().single()
          stateAtFault = sheet.stateWrapper
          publicationAtFault =
            requireNotNull(lease)
              .receipt(
                surface.surfaceID,
                sheet.id,
                mapOf(sheet.id to sheet.stateWrapper),
              )
              ?.publicationId
          batchAtFault = events.lastOrNull { it.startsWith("start:") }?.substringAfter(':')
          Log.i(
            "MountFailureProbe",
            "AT_FAULT batch=$batchAtFault publication=$publicationAtFault " +
              "stateChanged=${stateAtFault !== stateBeforeFault}",
          )
        }
        ReactMarker.addFabricListener(markers)
        observation =
          adapter.observe(boundary) callback@{
            events.add("didMount")
            if (observed.getValue(phase).count == 0L) return@callback
            try {
              val views = descendants(root)
              val sheet =
                views.filterIsInstance<BottomSheetView>().singleOrNull() ?: return@callback
              val source = lease ?: return@callback
              val state = sheet.stateWrapper ?: return@callback
              val receipt = source.receiptFromRegistry(manager, surface.surfaceID, sheet)
              if (phase == 0 && receipt == null) return@callback
              if (phase == 1 && failures.get() != 1) return@callback
              if (phase == 2 && (receipt == null || receipt.publicationId == previousPublication))
                return@callback
              val tags = source.sheetTags(surface.surfaceID, sheet.id, state)
              val raw = source.receipt(surface.surfaceID, sheet.id, mapOf(sheet.id to state))
              if (phase > 0 && raw?.publicationId == previousPublication) return@callback
              val area = views.single { it.tag == "local-area" }
              val row =
                JSONObject()
                  .put("run", runId)
                  .put("phase", phase)
                  .put("faults", failures.get())
                  .put("rawReceipt", raw != null)
                  .put("registryReceipt", receipt != null)
                  .put("publication", raw?.publicationId)
                  .put("inventory", tags?.toList().toString())
                  .put("contexts", raw?.contexts.toString())
                  .put("area", area.id)
                  .put("events", events.joinToString(","))
                  .put("OSattached", root.isAttachedToWindow)
              if (phase == 1)
                row
                  .put("faultPublication", publicationAtFault)
                  .put("faultBatch", batchAtFault)
                  .put("sameStateAtFault", sheet.stateWrapper === stateAtFault)
                  .put("newStateAtFault", stateAtFault !== stateBeforeFault)
              results.add(row)
              Log.i("MountFailureProbe", row.toString())
              if (raw != null) previousPublication = raw.publicationId
              observed.getValue(phase).countDown()
            } catch (caught: Throwable) {
              crash.set(caught)
              observed.getValue(phase).countDown()
            }
          }
        host.onHostResume(null)
      }
      onMain { assertNotNull(lease) }
      await(surface.start())
      started = true
      assertTrue("Initial real receipt timed out", observed.getValue(0).await(20, TimeUnit.SECONDS))
      crash.get()?.let { throw it }
      assertTrue(results.first().getBoolean("registryReceipt"))
      for (generation in 1..2) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        onMain {
          phase = generation
          events.clear()
          armed.set(generation == 1)
          if (generation == 1)
            stateBeforeFault =
              descendants(root).filterIsInstance<BottomSheetView>().single().stateWrapper
          (surface as ReactSurfaceImpl).updateInitProps(
            Bundle().apply {
              putInt("generation", generation)
            }
          )
          assertTrue(
            "React generation not committed",
            committed.getValue(generation).await(5, TimeUnit.SECONDS),
          )
        }
        assertTrue(
          "Generation $generation not observed",
          observed.getValue(generation).await(20, TimeUnit.SECONDS),
        )
        crash.get()?.let { throw it }
      }
      assertEquals(1, failures.get())
      val partial = results.single { it.getInt("phase") == 1 }
      val recovery = results.single { it.getInt("phase") == 2 }
      assertTrue(partial.getBoolean("rawReceipt"))
      // Matching source/structure is not proof that every layout instruction succeeded.
      assertTrue(partial.getBoolean("registryReceipt"))
      assertTrue(partial.getString("events").contains("start:"))
      assertNotNull(batchAtFault)
      assertFalse(partial.getString("events").split(',').contains("end:$batchAtFault"))
      assertTrue(partial.getBoolean("sameStateAtFault"))
      assertTrue(partial.getBoolean("newStateAtFault"))
      assertEquals(partial.getString("faultPublication"), partial.getString("publication"))
      assertTrue(recovery.getString("events").contains("end:"))
      assertTrue(recovery.getBoolean("registryReceipt"))
      InstrumentationRegistry.getInstrumentation()
        .sendStatus(
          0,
          Bundle().apply { putString("failureProbe", results.toString()) },
        )
    } finally {
      try {
        onMain {
          ReactMarker.removeFabricListener(markers)
          observation?.remove()
          host.onHostPause()
        }
        onMain {}
        if (started) await(surface.stop())
      } finally {
        await(host.destroy("Publication failure probe", null))
      }
    }
  }

  private fun descendants(view: View): List<View> =
    listOf(view) +
      if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
      else emptyList()

  private fun await(task: TaskInterface<Void>) {
    assertTrue("ReactHost lifecycle timed out", task.waitForCompletion(20, TimeUnit.SECONDS))
    task.getError()?.let { throw it }
    assertFalse(task.isCancelled())
  }

  private fun onMain(block: () -> Unit) {
    var caught: Throwable? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      try {
        block()
      } catch (failure: Throwable) {
        caught = failure
      }
    }
    caught?.let { throw it }
  }

  companion object {
    @BeforeClass
    @JvmStatic
    fun initializeSdk() {
      assumeTrue(
        InstrumentationRegistry.getArguments().getString("bottomSheetPublicationFailureJni") ==
          "true"
      )
      SoLoader.init(InstrumentationRegistry.getInstrumentation().context, OpenSourceMergedSoMapping)
      DefaultNewArchitectureEntryPoint.load()
    }
  }
}

private class MountFailureProbePackage(
  private val armed: AtomicBoolean,
  private val faults: AtomicInteger,
  private val atFault: () -> Unit,
  private val committed: (Int) -> Unit,
) : ReactPackage {
  @Suppress("OVERRIDE_DEPRECATION")
  override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
    listOf(MountFailureProbeModule(context, committed))

  override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
    listOf(
      object : SimpleViewManager<View>() {
        override fun getName() = "MountFailureProbeView"

        override fun createViewInstance(context: ThemedReactContext): View =
          object : View(context) {
            override fun onMeasure(width: Int, height: Int) {
              if (armed.compareAndSet(true, false)) {
                faults.incrementAndGet()
                atFault()
                Log.i("MountFailureProbe", "FAULT after State, during native layout")
                throw ReactIgnorableMountingException(
                  "intentional publication probe layout failure"
                )
              }
              super.onMeasure(width, height)
            }
          }
      }
    )
}

class MountFailureProbeModule(
  context: ReactApplicationContext,
  private val committed: (Int) -> Unit,
) : ReactContextBaseJavaModule(context) {
  override fun getName() = "MountFailureProbe"

  @ReactMethod fun didCommit(generation: Int) = committed(generation)
}
