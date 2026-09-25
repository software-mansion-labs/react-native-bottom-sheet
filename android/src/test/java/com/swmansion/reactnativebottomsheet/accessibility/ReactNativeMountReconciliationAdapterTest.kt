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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReactNativeMountReconciliationAdapterTest {
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
      reconciler.reconcile(listOf(owner))
      val observation = adapter.observe(boundary) { reconciler.reconcile(listOf(owner)) }
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
