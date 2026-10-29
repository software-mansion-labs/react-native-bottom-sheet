@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)

package com.swmansion.reactnativebottomsheet.accessibility

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.UIManager
import com.facebook.react.bridge.UIManagerListener
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReactNativeMountReconciliationAdapterTest {
  @Test
  fun `one UI batch installs both manager sources before the shared window reads once`() {
    val posted = mutableListOf<Runnable>()
    val firstManager = FakeMountCallbacks(isFabric = true)
    val secondManager = FakeMountCallbacks(isFabric = true)
    val bootstrapped = mutableSetOf<Any>()
    val adapter =
      ReactNativeMountReconciliationAdapter(
        postToUiThread = { posted.add(it) },
        acquirePublicationSource = { identity ->
          FakePublicationLease { bootstrapped.add(identity) }
        },
      )
    val window = Any()
    var reads = 0
    val readWindow = {
      assertEquals(setOf(firstManager.identity, secondManager.identity), bootstrapped)
      reads++
      Unit
    }
    val first = adapter.observe(firstManager, window, readWindow)
    val second = adapter.observe(secondManager, window, readWindow)
    first.requestReconciliation()
    second.requestReconciliation()
    assertEquals(1, posted.size)
    posted.removeAt(0).run()
    assertEquals(1, reads)
    first.remove()
    second.remove()
    posted.toList().forEach { it.run() }
  }

  @Test
  fun `callbacks sharing a collected window clean up every lease in the same batch`() {
    val posted = mutableListOf<Runnable>()
    val boundary = FakeMountCallbacks(isFabric = true)
    val adapter = ReactNativeMountReconciliationAdapter(postToUiThread = { posted.add(it) })
    val window = Any()
    var removals = 0
    lateinit var first: ReactNativeMountReconciliationAdapter.Observation
    lateinit var second: ReactNativeMountReconciliationAdapter.Observation
    first =
      adapter.observe(boundary, window) {
        first.remove()
        removals++
      }
    second =
      adapter.observe(boundary, window) {
        second.remove()
        removals++
      }
    first.requestReconciliation()
    posted.removeAt(0).run()
    assertEquals(2, removals)
    assertEquals(1, boundary.removeCount)
    assertEquals(0, posted.size)
  }

  @Test
  fun `one window reconciles once per manager batch while root leases remain independent`() {
    val posted = mutableListOf<Runnable>()
    val boundary = FakeMountCallbacks(isFabric = true)
    val adapter = ReactNativeMountReconciliationAdapter(postToUiThread = { posted.add(it) })
    val window = Any()
    var reads = 0
    val first = adapter.observe(boundary, window) { reads++ }
    val second = adapter.observe(boundary, window) { reads++ }

    first.requestReconciliation()
    second.requestReconciliation()
    posted.removeAt(0).run()
    assertEquals(1, reads)
    first.remove()
    assertEquals(0, boundary.removeCount)
    boundary.listener?.didMountItems(FakeUIManager)
    posted.removeAt(0).run()
    assertEquals(2, reads)
    second.remove()
    assertEquals(1, boundary.removeCount)
  }

  @Test
  fun `reentrant requests schedule fresh window work and removed snapshot callbacks do not run`() {
    val posted = mutableListOf<Runnable>()
    val boundary = FakeMountCallbacks(isFabric = true)
    val adapter = ReactNativeMountReconciliationAdapter(postToUiThread = { posted.add(it) })
    val window = Any()
    var reads = 0
    lateinit var other: ReactNativeMountReconciliationAdapter.Observation
    val first =
      adapter.observe(boundary, window) {
        reads++
        other.remove()
        if (reads == 1) boundary.listener?.didMountItems(FakeUIManager)
      }
    val second = adapter.observe(boundary, window) { error("duplicate window callback") }
    other = adapter.observe(boundary) { error("removed callback") }
    first.requestReconciliation()
    posted.removeAt(0).run()
    assertEquals(1, reads)
    assertEquals(1, posted.size)
    posted.removeAt(0).run()
    assertEquals(2, reads)
    first.remove()
    second.remove()
  }

  @Test
  fun `Fabric mount callbacks coalesce and one listener lives until the final observation ends`() {
    val posted = mutableListOf<Runnable>()
    val boundary = FakeMountCallbacks(isFabric = true)
    val adapter = ReactNativeMountReconciliationAdapter(postToUiThread = { posted.add(it) })
    var firstCount = 0
    var secondCount = 0
    val first = adapter.observe(boundary) { firstCount++ }
    val second = adapter.observe(boundary) { secondCount++ }

    boundary.listener?.didMountItems(FakeUIManager)
    boundary.listener?.didMountItems(FakeUIManager)
    assertEquals(1, posted.size)
    posted.removeAt(0).run()
    assertEquals(1, firstCount)
    assertEquals(1, secondCount)
    assertEquals(1, boundary.addCount)

    first.remove()
    assertEquals(0, boundary.removeCount)
    boundary.listener?.didMountItems(FakeUIManager)
    second.remove()
    posted.removeAt(0).run()
    assertEquals(1, firstCount)
    assertEquals(1, secondCount)
    assertEquals(1, boundary.removeCount)
  }

  @Test
  fun `Paper queues a post-mount block for every batch before coalescing reconciliation`() {
    val posted = mutableListOf<Runnable>()
    val boundary = FakeMountCallbacks(isFabric = false)
    val adapter = ReactNativeMountReconciliationAdapter(postToUiThread = { posted.add(it) })
    var reconciliationCount = 0
    val observation = adapter.observe(boundary) { reconciliationCount++ }

    boundary.listener?.willDispatchViewUpdates(FakeUIManager)
    boundary.listener?.willDispatchViewUpdates(FakeUIManager)
    assertEquals(2, boundary.paperBlocks.size)
    assertEquals(0, posted.size)
    boundary.paperBlocks.removeAt(0).invoke()
    assertEquals(1, posted.size)
    boundary.paperBlocks.removeAt(0).invoke()
    assertEquals(1, posted.size)
    posted.removeAt(0).run()
    assertEquals(1, reconciliationCount)

    observation.remove()
  }

  @Test
  fun `post-mount reconciliation covers inserted reordered and rewritten side branches`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val posted = mutableListOf<Runnable>()
      val boundary = FakeMountCallbacks(isFabric = true)
      val adapter = ReactNativeMountReconciliationAdapter(postToUiThread = { posted.add(it) })
      val root = FrameLayout(activity.get())
      val existing = View(activity.get()).also(root::addView)
      val owner = View(activity.get()).also(root::addView)
      activity.get().setContentView(root)
      val reconciler = PortalAccessibilityReconciler(root)
      val scopes = listOf(PortalAccessibilityScope(root, listOf(owner)))
      reconciler.reconcile(scopes)
      val observation = adapter.observe(boundary) { reconciler.reconcile(scopes) }
      val inserted = View(activity.get()).also(root::addView)
      existing.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      root.removeView(owner)
      root.addView(owner, 0)

      boundary.listener?.didMountItems(FakeUIManager)
      posted.removeAt(0).run()

      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        inserted.importantForAccessibility,
      )
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        existing.importantForAccessibility,
      )
      root.removeView(inserted)
      boundary.listener?.didMountItems(FakeUIManager)
      posted.removeAt(0).run()
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, inserted.importantForAccessibility)
      observation.remove()
      reconciler.reconcile(emptyList())
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, existing.importantForAccessibility)
    } finally {
      activity.close()
    }
  }

  @Test
  fun `Fabric observations share one deferred native source until the final observation is removed`() {
    val posted = mutableListOf<Runnable>()
    val boundary = FakeMountCallbacks(isFabric = true)
    var acquisitions = 0
    val native = FakePublicationLease()
    val adapter =
      ReactNativeMountReconciliationAdapter(
        postToUiThread = { posted.add(it) },
        acquirePublicationSource = {
          assertSame(boundary.identity, it)
          acquisitions++
          native
        },
      )
    val first = adapter.observe(boundary) {}
    val second = adapter.observe(boundary) {}
    assertEquals(0, acquisitions)
    boundary.listener?.didMountItems(FakeUIManager)
    posted.removeAt(0).run()
    assertEquals(1, acquisitions)
    assertEquals(1, native.bootstraps)
    boundary.listener?.didMountItems(FakeUIManager)
    posted.removeAt(0).run()
    assertEquals(1, acquisitions)
    first.remove()
    assertEquals(0, native.releases)
    second.remove()
    assertEquals(0, native.releases)
    posted.removeAt(0).run()
    assertEquals(1, native.releases)
  }

  @Test
  fun `removing an observation before queued installation prevents native acquisition`() {
    val posted = mutableListOf<Runnable>()
    var acquisitions = 0
    val adapter =
      ReactNativeMountReconciliationAdapter(
        postToUiThread = { posted.add(it) },
        acquirePublicationSource = {
          acquisitions++
          FakePublicationLease()
        },
      )
    val observation = adapter.observe(FakeMountCallbacks(true)) {}
    observation.remove()
    posted.removeAt(0).run()
    assertEquals(0, acquisitions)
    assertEquals(0, posted.size)
  }

  @Test
  fun `Paper reconciliation never acquires a native Fabric source`() {
    val posted = mutableListOf<Runnable>()
    var acquisitions = 0
    var reconciliations = 0
    val adapter =
      ReactNativeMountReconciliationAdapter(
        postToUiThread = { posted.add(it) },
        acquirePublicationSource = {
          acquisitions++
          FakePublicationLease()
        },
      )
    val boundary = FakeMountCallbacks(false)
    val observation = adapter.observe(boundary) { reconciliations++ }
    boundary.listener?.willDispatchViewUpdates(FakeUIManager)
    boundary.paperBlocks.removeAt(0).invoke()
    posted.removeAt(0).run()
    assertEquals(1, reconciliations)
    assertEquals(0, acquisitions)
    observation.remove()
  }

  @Test
  fun `bootstrap removal releases the native resource only after its call returns`() {
    val posted = mutableListOf<Runnable>()
    lateinit var observation: ReactNativeMountReconciliationAdapter.Observation
    lateinit var native: FakePublicationLease
    native = FakePublicationLease {
      observation.remove()
      assertEquals(0, native.releases)
    }
    val adapter =
      ReactNativeMountReconciliationAdapter(
        postToUiThread = { posted.add(it) },
        acquirePublicationSource = { native },
      )
    observation = adapter.observe(FakeMountCallbacks(true)) {}
    posted.removeAt(0).run()
    assertEquals(1, native.bootstraps)
    assertEquals(0, native.releases)
    posted.removeAt(0).run()
    assertEquals(1, native.releases)
  }

  @Test
  fun `an unavailable binding retries only after another supported mount event`() {
    val posted = mutableListOf<Runnable>()
    var acquisitions = 0
    val native = FakePublicationLease()
    val adapter =
      ReactNativeMountReconciliationAdapter(
        postToUiThread = { posted.add(it) },
        acquirePublicationSource = {
          acquisitions++
          if (acquisitions == 1) null else native
        },
      )
    val boundary = FakeMountCallbacks(true)
    val observation = adapter.observe(boundary) {}
    posted.removeAt(0).run()
    assertEquals(1, acquisitions)
    assertEquals(0, posted.size)
    boundary.listener?.didMountItems(FakeUIManager)
    posted.removeAt(0).run()
    assertEquals(2, acquisitions)
    assertEquals(1, native.bootstraps)
    observation.remove()
    posted.removeAt(0).run()
  }

  @Test
  fun `a bootstrap mount callback reconciles once without acquiring another source`() {
    val posted = mutableListOf<Runnable>()
    val boundary = FakeMountCallbacks(true)
    var acquisitions = 0
    var reconciliations = 0
    val native = FakePublicationLease { boundary.listener?.didMountItems(FakeUIManager) }
    val adapter =
      ReactNativeMountReconciliationAdapter(
        postToUiThread = { posted.add(it) },
        acquirePublicationSource = {
          acquisitions++
          native
        },
      )
    val observation = adapter.observe(boundary) { reconciliations++ }
    posted.removeAt(0).run()
    posted.removeAt(0).run()
    assertEquals(1, acquisitions)
    assertEquals(1, reconciliations)
    observation.remove()
    posted.removeAt(0).run()
  }

  private class FakePublicationLease(private val onBootstrap: () -> Unit = {}) :
    TestFabricPublicationLease() {
    var bootstraps = 0
    var releases = 0

    override fun bootstrap() {
      bootstraps++
      onBootstrap()
    }

    override fun sheetTags(
      surfaceId: Int,
      tag: Int,
      state: com.facebook.react.uimanager.StateWrapper?,
    ): IntArray? = null

    override fun receipt(
      surfaceId: Int,
      tag: Int,
      states: Map<Int, com.facebook.react.uimanager.StateWrapper?>,
    ): FabricPortalReceipt? = null

    override fun contextPath(
      surfaceId: Int,
      tag: Int,
      states: Map<Int, com.facebook.react.uimanager.StateWrapper?>,
    ): List<Int>? = null

    override fun isolation(
      surfaceId: Int,
      states: Map<Int, com.facebook.react.uimanager.StateWrapper?>,
      nativeParents: Map<Int, Int>,
      retainedByHost: Map<Int, List<Int>>,
    ): FabricPortalIsolationProjection? = null

    override fun release() {
      releases++
    }
  }

  private class FakeMountCallbacks(override val isFabric: Boolean) : ReactNativeMountCallbacks {
    override val identity = Any()
    var listener: UIManagerListener? = null
    var addCount = 0
    var removeCount = 0
    val paperBlocks = mutableListOf<() -> Unit>()

    override fun addListener(listener: UIManagerListener) {
      this.listener = listener
      addCount++
    }

    override fun removeListener(listener: UIManagerListener) {
      if (this.listener === listener) this.listener = null
      removeCount++
    }

    override fun enqueueAfterPaperMount(block: () -> Unit) {
      paperBlocks.add(block)
    }
  }

  private companion object {
    val FakeUIManager =
      Proxy.newProxyInstance(
        UIManager::class.java.classLoader,
        arrayOf(UIManager::class.java),
      ) { _, _, _ ->
        null
      } as UIManager
  }
}
