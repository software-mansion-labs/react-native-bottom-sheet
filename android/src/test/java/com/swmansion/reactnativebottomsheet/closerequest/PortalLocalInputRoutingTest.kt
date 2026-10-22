package com.swmansion.reactnativebottomsheet.closerequest

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.swmansion.reactnativebottomsheet.TestViewUtilShadow
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationController
import com.swmansion.reactnativebottomsheet.presentation.TestReactRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestWindowBackDispatcher::class, TestViewUtilShadow::class])
class PortalLocalInputRoutingTest {
  @Test
  fun `ending an independent global proxy does not suppress the eligible captured local owner`() {
    Fixture().use { f ->
      assertTrue(f.a.view.requestFocus())
      f.activity.startSystemBack()
      f.b.update(active = false)
      shadowOf(Looper.getMainLooper()).idle()
      f.activity.systemBack()
      assertEquals(1, f.a.requests)
      assertEquals(0, f.b.requests)
      assertEquals(0, f.hostBackCount)
    }
  }

  @Test
  @Config(sdk = [24])
  fun `legacy Back pins local owner from down through up without retargeting focus`() {
    Fixture().use { f ->
      assertTrue(f.a.view.requestFocus())
      f.activity.dispatchKeyEvent(KeyEvent(10, 10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
      assertTrue(f.b.view.requestFocus())
      f.activity.dispatchKeyEvent(KeyEvent(10, 11, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
      assertEquals(1, f.a.requests)
      assertEquals(0, f.b.requests)
      assertEquals(0, f.hostBackCount)
    }
  }

  @Test
  fun `React Native asynchronous default Back does not re-enter portal routing`() {
    Fixture(HostBackReactActivity::class.java).use { f ->
      val host = f.activity as HostBackReactActivity
      host.defaultBackLater = true
      assertTrue(f.toolbar.requestFocus())
      host.systemBack()
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(1, host.delegateBacks)
      assertEquals(1, f.hostBackCount)
      assertEquals(0, f.a.requests)
      assertEquals(0, f.b.requests)
    }
  }

  @Test
  fun `known outside focus enters React Native host Back rather than bypassing its delegate`() {
    Fixture(HostBackReactActivity::class.java).use { f ->
      assertTrue(f.toolbar.requestFocus())

      f.activity.systemBack()

      assertEquals(1, (f.activity as HostBackReactActivity).delegateBacks)
      assertEquals(0, f.hostBackCount)
      assertEquals(0, f.a.requests)
      assertEquals(0, f.b.requests)
    }
  }

  @Test
  fun `local Top loss and return cannot reactivate a captured Escape`() {
    Fixture().use { f ->
      val peer = f.anotherInA()
      peer.update(active = false)
      assertTrue(f.a.view.requestFocus())
      assertTrue(f.a.escape(KeyEvent.ACTION_DOWN))
      peer.update()
      shadowOf(Looper.getMainLooper()).idle()
      peer.update(active = false)
      shadowOf(Looper.getMainLooper()).idle()

      assertTrue(f.b.escape(KeyEvent.ACTION_UP))

      assertEquals(0, f.a.requests)
      assertEquals(0, peer.requests)
      assertEquals(0, f.b.requests)
    }
  }

  @Test
  fun `local Top loss and return cannot reactivate a captured predictive Back`() {
    Fixture().use { f ->
      val peer = f.anotherInA()
      peer.update(active = false)
      assertTrue(f.a.view.requestFocus())
      f.activity.startSystemBack()
      peer.update()
      shadowOf(Looper.getMainLooper()).idle()
      peer.update(active = false)
      shadowOf(Looper.getMainLooper()).idle()

      f.activity.systemBack()

      assertEquals(0, f.a.requests)
      assertEquals(0, peer.requests)
      assertEquals(0, f.b.requests)
    }
  }

  @Test
  fun `independent global order transfer does not cancel a local predictive capture`() {
    Fixture().use { f ->
      assertTrue(f.a.view.requestFocus())
      f.activity.startSystemBack()
      f.rootA.elevation = 10f
      f.rootA.viewTreeObserver.dispatchOnPreDraw()

      f.activity.systemBack()

      assertEquals(1, f.a.requests)
      assertEquals(0, f.b.requests)
      assertEquals(0, f.hostBackCount)
    }
  }

  @Test
  fun `current accessibility focus takes priority over keyboard focus in another context`() {
    Fixture().use { f ->
      f.accessibilityFocus(f.rootA)
      assertTrue(f.b.view.requestFocus())

      f.activity.systemBack()

      assertEquals(1, f.a.requests)
      assertEquals(0, f.b.requests)
    }
  }

  @Test
  fun `unresolved accessibility query preserves the window owner without keyboard substitution`() {
    Fixture().use { f ->
      shadowOf(f.activity.getSystemService(AccessibilityManager::class.java)).setEnabled(true)
      assertTrue(f.a.view.requestFocus())

      f.activity.systemBack()

      assertEquals(0, f.a.requests)
      assertEquals(1, f.b.requests)
    }
  }

  @Test
  fun `predictive Back keeps its local owner when keyboard focus changes`() {
    Fixture().use { f ->
      assertTrue(f.a.view.requestFocus())
      f.activity.startSystemBack()
      assertTrue(f.b.view.requestFocus())

      f.activity.systemBack()

      assertEquals(1, f.a.requests)
      assertEquals(0, f.b.requests)
      f.activity.systemBack()
      assertEquals(1, f.b.requests)
    }
  }

  @Test
  fun `local owner handler loss cannot reactivate a captured predictive Back`() {
    Fixture().use { f ->
      assertTrue(f.a.view.requestFocus())
      f.activity.startSystemBack()
      f.a.update(hasHandler = false)
      f.a.update()

      f.activity.systemBack()

      assertEquals(0, f.a.requests)
      assertEquals(0, f.b.requests)
      assertEquals(0, f.hostBackCount)
    }
  }

  @Test
  fun `Escape captures the local owner until up despite a focus change`() {
    Fixture().use { f ->
      assertTrue(f.a.view.requestFocus())
      assertTrue(f.b.escape(KeyEvent.ACTION_DOWN))
      assertTrue(f.b.view.requestFocus())
      assertTrue(f.a.escape(KeyEvent.ACTION_UP))
      assertEquals(1, f.a.requests)
      assertEquals(0, f.b.requests)

      assertTrue(f.a.escape(KeyEvent.ACTION_DOWN))
      assertTrue(f.a.escape(KeyEvent.ACTION_UP))
      assertEquals(1, f.b.requests)
    }
  }

  @Test
  fun `handlerless window owner does not disable Back in an independent eligible context`() {
    Fixture().use { f ->
      f.b.update(hasHandler = false)
      assertTrue(f.a.view.requestFocus())

      f.activity.systemBack()

      assertEquals(1, f.a.requests)
      assertEquals(0, f.b.requests)
      assertEquals(0, f.hostBackCount)
    }
  }

  @Test
  fun `known focus outside the portal contexts delivers Back to the host`() {
    Fixture().use { f ->
      assertTrue(f.toolbar.requestFocus())

      f.activity.systemBack()

      assertEquals(1, f.hostBackCount)
      assertEquals(0, f.a.requests)
      assertEquals(0, f.b.requests)
    }
  }

  @Test
  fun `fresh Back selects keyboard context A instead of the higher independent context B`() {
    Fixture().use { f ->
      assertTrue(f.a.view.requestFocus())

      f.activity.systemBack()

      assertEquals(1, f.a.requests)
      assertEquals(0, f.b.requests)
      assertEquals(0, f.hostBackCount)
    }
  }

  private class Fixture(
    activityClass: Class<out ComponentActivity> = ComponentActivity::class.java,
  ) : AutoCloseable {
    private val activityController =
      Robolectric.buildActivity(activityClass)
        .also {
          it.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        }
        .setup()
    val activity = activityController.get()
    val rootA = TestReactRoot(activity)
    private val parentA = FrameLayout(activity).also(rootA::addView)
    val rootB = TestReactRoot(activity)
    val toolbar = View(activity).apply { isFocusableInTouchMode = true }
    var hostBackCount = 0
    val a: Portal
    val b: Portal
    private val extra = mutableListOf<Portal>()

    init {
      shadowOf(activity.getSystemService(AccessibilityManager::class.java)).setEnabled(false)
      activity.setContentView(
        FrameLayout(activity).apply {
          addView(rootA)
          addView(rootB)
          addView(toolbar)
        },
      )
      activity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            hostBackCount++
          }
        },
      )
      a = Portal(this, parentA)
      b = Portal(this, rootB)
    }

    fun anotherInA() = Portal(this, parentA).also { extra.add(it) }

    fun accessibilityFocus(root: ViewGroup) {
      shadowOf(activity.getSystemService(AccessibilityManager::class.java)).setEnabled(true)
      root.addView(
        object : View(activity) {
          override fun isAccessibilityFocused() = true
        },
      )
    }

    override fun close() {
      extra.forEach { it.dispose() }
      b.dispose()
      a.dispose()
      activityController.close()
    }
  }

  private class Portal(f: Fixture, root: ViewGroup) {
    val view = View(f.activity).apply { isFocusableInTouchMode = true }.also(root::addView)
    var requests = 0
    private val close =
      PortalCloseRequestController(view, { f.activity }) {
        requests++
        true
      }
    private val presentation = PortalPresentationController(view, close::onPresentationChanged)

    init {
      update()
    }

    fun update(hasHandler: Boolean = true, active: Boolean = true) {
      close.update(
        CloseRequestInputState(
          isAttached = true,
          isLifecycleActive = true,
          isModal = true,
          hasCloseRequestHandler = hasHandler,
          isPresentationActive = active,
          isTargetResolvedAndOpen = true,
        ),
        usesPortalPresentation = true,
      )
      presentation.update(isPortal = true, isActive = active)
    }

    fun dispose() {
      presentation.dispose()
      close.dispose()
    }

    fun escape(action: Int) = close.dispatchEscape(KeyEvent(action, KeyEvent.KEYCODE_ESCAPE))
  }
}

private class HostBackReactActivity : ReactActivity() {
  var delegateBacks = 0
  var defaultBackLater = false

  override fun createReactActivityDelegate() =
    object : ReactActivityDelegate(this, null) {
      override fun onCreate(savedInstanceState: android.os.Bundle?) = Unit

      override fun onPause() = Unit

      override fun onResume() = Unit

      override fun onDestroy() = Unit

      override fun onKeyDown(keyCode: Int, event: KeyEvent) = false

      override fun onKeyUp(keyCode: Int, event: KeyEvent) = false

      override fun onKeyLongPress(keyCode: Int, event: KeyEvent) = false

      override fun onBackPressed(): Boolean {
        delegateBacks++
        if (defaultBackLater && delegateBacks == 1) {
          android.os.Handler(Looper.getMainLooper()).post { invokeDefaultOnBackPressed() }
        }
        return true
      }
    }
}
