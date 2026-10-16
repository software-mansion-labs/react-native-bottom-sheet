@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)
@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet.accessibility

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.UIManager
import com.facebook.react.bridge.UIManagerListener
import com.facebook.react.uimanager.UIManagerHelper
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationCoordinator
import com.swmansion.reactnativebottomsheet.presentation.TestReactRoot
import java.lang.reflect.Proxy
import java.util.IdentityHashMap
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ActiveRootsUIManagerHelperShadow::class])
class PortalActiveMountObservationTest {
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

  private class Manager {
    private val listeners = mutableListOf<UIManagerListener>()
    val proxy =
      Proxy.newProxyInstance(
        UIManager::class.java.classLoader,
        arrayOf(UIManager::class.java),
      ) { _, method, args ->
        when (method.name) {
          "addUIManagerEventListener" -> listeners.add(args!![0] as UIManagerListener)
          "removeUIManagerEventListener" -> listeners.remove(args!![0] as UIManagerListener)
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
