package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PortalRequestCloseHostTest {
  @Test
  fun `ComponentActivity provides all portal owners`() {
    withActivity<ComponentActivity> { activity ->
      val portal = View(activity)
      activity.setContentView(portal)

      val host = requireNotNull(portal.resolvePortalRequestCloseHost(activity))

      assertSame(activity, host.dispatcherOwner)
      assertSame(activity, host.lifecycleOwner)
    }
  }

  @Test
  fun `nearest view-tree dispatcher and lifecycle owners win independently`() {
    withActivity<ComponentActivity> { activity ->
      val outerDispatcherOwner = TestDispatcherOwner().apply { resume() }
      val innerDispatcherOwner = TestDispatcherOwner().apply { resume() }
      val outerLifecycleOwner = TestLifecycleOwner().apply { resume() }
      val innerLifecycleOwner = TestLifecycleOwner().apply { resume() }
      val outer = FrameLayout(activity)
      val inner = FrameLayout(activity)
      val portal = View(activity)
      outer.setViewTreeOnBackPressedDispatcherOwner(outerDispatcherOwner)
      outer.setViewTreeLifecycleOwner(outerLifecycleOwner)
      inner.setViewTreeOnBackPressedDispatcherOwner(innerDispatcherOwner)
      inner.setViewTreeLifecycleOwner(innerLifecycleOwner)
      inner.addView(portal)
      outer.addView(inner)
      activity.setContentView(outer)

      val host = requireNotNull(portal.resolvePortalRequestCloseHost(activity))

      assertSame(innerDispatcherOwner, host.dispatcherOwner)
      assertSame(innerLifecycleOwner, host.lifecycleOwner)
    }
  }

  @Test
  fun `custom dispatcher supplies lifecycle when Activity lifecycle is the only alternative`() {
    withActivity<ComponentActivity> { activity ->
      val owner = TestDispatcherOwner().apply { resume() }
      val container = FrameLayout(activity)
      val portal = View(activity)
      container.setViewTreeOnBackPressedDispatcherOwner(owner)
      container.addView(portal)
      activity.setContentView(container)

      val host = requireNotNull(portal.resolvePortalRequestCloseHost(activity))

      assertSame(owner, host.dispatcherOwner)
      assertSame(owner, host.lifecycleOwner)
    }
  }

  @Test
  fun `closer custom lifecycle remains callback lifecycle for Activity dispatcher`() {
    withActivity<ComponentActivity> { activity ->
      val lifecycleOwner = TestLifecycleOwner().apply { resume() }
      val container = FrameLayout(activity)
      val portal = View(activity)
      container.setViewTreeLifecycleOwner(lifecycleOwner)
      container.addView(portal)
      activity.setContentView(container)

      val host = requireNotNull(portal.resolvePortalRequestCloseHost(activity))

      assertSame(activity, host.dispatcherOwner)
      assertSame(lifecycleOwner, host.lifecycleOwner)
    }
  }

  @Test
  fun `dispatcher owner supplies lifecycle only when the view tree has none`() {
    withActivity<Activity> { activity ->
      val owner = TestDispatcherOwner().apply { resume() }
      val container = FrameLayout(activity)
      val portal = View(activity)
      container.setViewTreeOnBackPressedDispatcherOwner(owner)
      container.addView(portal)
      activity.setContentView(container)

      val host = requireNotNull(portal.resolvePortalRequestCloseHost(activity))

      assertSame(owner, host.dispatcherOwner)
      assertSame(owner, host.lifecycleOwner)
    }
  }

  @Test
  fun `destroyed nearest lifecycle owner does not fall back to Activity`() {
    withActivity<ComponentActivity> { activity ->
      val destroyedOwner = TestLifecycleOwner().apply { destroy() }
      val container = FrameLayout(activity)
      val portal = View(activity)
      container.setViewTreeLifecycleOwner(destroyedOwner)
      container.addView(portal)
      activity.setContentView(container)

      val host = requireNotNull(portal.resolvePortalRequestCloseHost(activity))

      assertSame(activity, host.dispatcherOwner)
      assertSame(destroyedOwner, host.lifecycleOwner)
      assertSame(Lifecycle.State.DESTROYED, host.lifecycleOwner?.lifecycle?.currentState)
    }
  }

  @Test
  fun `ComponentDialog provides its dispatcher and lifecycle`() {
    withActivity<ComponentActivity> { activity ->
      val dialog = ComponentDialog(activity)
      val portal = View(activity)
      dialog.setContentView(portal)
      dialog.show()
      shadowOf(Looper.getMainLooper()).idle()
      try {
        val host = requireNotNull(portal.resolvePortalRequestCloseHost(activity))

        assertSame(dialog, host.dispatcherOwner)
        assertSame(dialog, host.lifecycleOwner)
      } finally {
        dialog.dismiss()
      }
    }
  }

  @Test
  fun `custom owners inside ComponentDialog remain nearest`() {
    withActivity<ComponentActivity> { activity ->
      val dialog = ComponentDialog(activity)
      val dispatcherOwner = TestDispatcherOwner().apply { resume() }
      val lifecycleOwner = TestLifecycleOwner().apply { resume() }
      val container = FrameLayout(activity)
      val portal = View(activity)
      container.setViewTreeOnBackPressedDispatcherOwner(dispatcherOwner)
      container.setViewTreeLifecycleOwner(lifecycleOwner)
      container.addView(portal)
      dialog.setContentView(container)
      dialog.show()
      shadowOf(Looper.getMainLooper()).idle()
      try {
        val host = requireNotNull(portal.resolvePortalRequestCloseHost(activity))

        assertSame(dispatcherOwner, host.dispatcherOwner)
        assertSame(lifecycleOwner, host.lifecycleOwner)
      } finally {
        dialog.dismiss()
      }
    }
  }

  @Test
  fun `plain Activity has no Back dispatcher or lifecycle owner`() {
    withActivity<Activity> { activity ->
      val portal = View(activity)
      activity.setContentView(portal)

      val host = requireNotNull(portal.resolvePortalRequestCloseHost(activity))

      assertNull(host.dispatcherOwner)
      assertNull(host.lifecycleOwner)
    }
  }

  @Test
  fun `stale ComponentActivity fallback cannot own a portal in a plain Activity`() {
    val firstController = Robolectric.buildActivity(Activity::class.java).setup()
    val secondController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    try {
      val firstActivity = firstController.get()
      val secondActivity = secondController.get()
      val portal = View(firstActivity)
      firstActivity.setContentView(portal)

      val host = requireNotNull(portal.resolvePortalRequestCloseHost(secondActivity))

      assertNull(host.dispatcherOwner)
      assertNull(host.lifecycleOwner)
    } finally {
      secondController.close()
      firstController.close()
    }
  }

  @Test
  fun `finishing ComponentActivity cannot provide any portal host element`() {
    withActivity<ComponentActivity> { activity ->
      val portal = View(activity)
      activity.setContentView(portal)
      activity.finish()

      val host = requireNotNull(portal.resolvePortalRequestCloseHost(activity))

      assertNull(host.dispatcherOwner)
      assertNull(host.lifecycleOwner)
    }
  }

  @Test
  fun `destroyed ComponentActivity fallback cannot provide any portal host element`() {
    val portalController = Robolectric.buildActivity(Activity::class.java).setup()
    val staleController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val staleActivity = staleController.get()
    staleController.pause().stop().destroy()
    try {
      assertTrue(staleActivity.isDestroyed)
      val portal = View(portalController.get())
      portalController.get().setContentView(portal)

      val host = requireNotNull(portal.resolvePortalRequestCloseHost(staleActivity))

      assertNull(host.dispatcherOwner)
      assertNull(host.lifecycleOwner)
    } finally {
      staleController.close()
      portalController.close()
    }
  }

  @Test
  fun `detached portal has no request-close host`() {
    withActivity<ComponentActivity> { activity ->
      val portal = View(activity)

      assertNull(portal.resolvePortalRequestCloseHost(activity))
    }
  }

  private inline fun <reified T : Activity> withActivity(block: (T) -> Unit) {
    val controller = Robolectric.buildActivity(T::class.java).setup()
    try {
      block(controller.get())
    } finally {
      controller.close()
    }
  }
}

internal class TestDispatcherOwner(fallback: () -> Unit = {}) : OnBackPressedDispatcherOwner {
  private val lifecycleRegistry = LifecycleRegistry(this)

  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  override val onBackPressedDispatcher = OnBackPressedDispatcher(fallback)

  fun resume() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }
}

internal class TestLifecycleOwner : LifecycleOwner {
  private val lifecycleRegistry = LifecycleRegistry(this)

  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  fun resume() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }

  fun destroy() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  }
}
