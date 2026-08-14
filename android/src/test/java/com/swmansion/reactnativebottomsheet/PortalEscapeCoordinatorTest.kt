package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class PortalEscapeCoordinatorTest {
  @Test
  fun `dispatch uses reverse registration order and stops after a handled event`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      val calls = mutableListOf<String>()
      val first = PortalEscapeTarget {
        calls += "first"
        true
      }
      val second = PortalEscapeTarget {
        calls += "second"
        true
      }
      val firstRegistration = PortalEscapeCoordinator.register(root, first)
      val secondRegistration = PortalEscapeCoordinator.register(root, second)

      try {
        assertTrue(PortalEscapeCoordinator.dispatch(root, escapeDown()))
        assertEquals(listOf("second"), calls)
      } finally {
        secondRegistration.remove()
        firstRegistration.remove()
      }
    }
  }

  @Test
  fun `dispatch falls back after false and isolates roots`() {
    withActivity { activity ->
      val firstRoot = FrameLayout(activity)
      val secondRoot = FrameLayout(activity)
      val calls = mutableListOf<String>()
      val first = PortalEscapeTarget {
        calls += "first"
        true
      }
      val second = PortalEscapeTarget {
        calls += "second"
        false
      }
      val isolated = PortalEscapeTarget {
        calls += "isolated"
        true
      }
      val firstRegistration = PortalEscapeCoordinator.register(firstRoot, first)
      val secondRegistration = PortalEscapeCoordinator.register(firstRoot, second)
      val isolatedRegistration = PortalEscapeCoordinator.register(secondRoot, isolated)

      try {
        assertTrue(PortalEscapeCoordinator.dispatch(firstRoot, escapeDown()))
        assertEquals(listOf("second", "first"), calls)
      } finally {
        isolatedRegistration.remove()
        secondRegistration.remove()
        firstRegistration.remove()
      }
    }
  }

  @Test
  fun `remove is idempotent and prevents later dispatch`() {
    withActivity { activity ->
      val root = FrameLayout(activity)
      var callCount = 0
      val target = PortalEscapeTarget {
        callCount++
        true
      }
      val registration = PortalEscapeCoordinator.register(root, target)

      registration.remove()
      registration.remove()

      assertFalse(PortalEscapeCoordinator.dispatch(root, escapeDown()))
      assertEquals(0, callCount)
    }
  }

  private fun escapeDown() = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)

  private fun withActivity(block: (Activity) -> Unit) {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      block(controller.get())
    } finally {
      controller.close()
    }
  }
}
