package com.swmansion.reactnativebottomsheet.closerequest

import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationController
import com.swmansion.reactnativebottomsheet.presentation.TestReactRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalCloseRequestCoordinatorTest {
  @Test
  fun `higher handlerless presentation blocks lower and handler updates keep visual ownership`() {
    Fixture().use { fixture ->
      val upper = fixture.portal().apply { view.elevation = 10f }
      val lower = fixture.portal()
      upper.update(portalInputState(hasHandler = false))
      fixture.back()
      assertFalse(lower.escapeDown())
      assertFalse(lower.escapeUp())
      assertEquals(1, fixture.hostBackCount)
      assertEquals(0, lower.requests)
      upper.update(portalInputState())
      fixture.back()
      assertTrue(lower.escapeDown())
      assertTrue(lower.escapeUp())
      assertEquals(2, upper.requests)
      assertEquals(0, lower.requests)
    }
  }

  @Test
  fun `closing owner consumes until settle and then transfers to lower`() {
    Fixture().use { fixture ->
      val lower = fixture.portal()
      val upper = fixture.portal()
      upper.update(portalInputState(targetOpen = false))
      fixture.back()
      assertTrue(lower.escapeDown())
      assertTrue(lower.escapeUp())
      assertEquals(0, upper.requests)
      assertEquals(0, lower.requests)
      assertEquals(0, fixture.hostBackCount)

      upper.update(portalInputState(active = false, targetOpen = false))
      fixture.back()
      assertTrue(lower.escapeDown())
      assertTrue(lower.escapeUp())
      assertEquals(2, lower.requests)
    }
  }

  @Test
  fun `opening Active presentation handles input before any settle`() {
    Fixture().use { fixture ->
      val portal = fixture.portal(portalInputState(active = false))
      fixture.back()
      portal.update(portalInputState())
      fixture.back()
      assertEquals(1, fixture.hostBackCount)
      assertEquals(1, portal.requests)
    }
  }

  @Test
  fun `unknown order uses stable shared Close fallback`() {
    Fixture(unknownOrder = true).use { fixture ->
      val first = fixture.portal()
      val second = fixture.portal()
      first.update(portalInputState(hasHandler = false))
      first.update(portalInputState())
      fixture.back()
      assertTrue(first.escapeDown())
      assertTrue(first.escapeUp())
      assertEquals(0, first.requests)
      assertEquals(2, second.requests)
    }
  }

  @Test
  fun `captured Escape cannot emit after ownership leaves and returns`() {
    Fixture().use { fixture ->
      val lower = fixture.portal()
      val upper = fixture.portal()
      assertTrue(lower.escapeDown())
      upper.update(portalInputState(active = false))
      upper.update(portalInputState())
      assertTrue(lower.escapeUp())
      assertEquals(0, lower.requests)
      assertEquals(0, upper.requests)
      assertTrue(lower.escapeDown())
      assertTrue(lower.escapeUp())
      assertEquals(1, upper.requests)
    }
  }

  @Test
  fun `captured Escape cannot emit after handler loss and restoration`() {
    Fixture().use { fixture ->
      val portal = fixture.portal()
      assertTrue(portal.escapeDown())
      portal.update(portalInputState(hasHandler = false))
      portal.update(portalInputState())
      assertTrue(portal.escapeUp())
      assertEquals(0, portal.requests)
    }
  }

  @Test
  fun `removing Escape owner consumes the captured up without retargeting`() {
    Fixture().use { fixture ->
      val lower = fixture.portal()
      val upper = fixture.portal()
      assertTrue(lower.escapeDown())
      upper.dispose()
      assertTrue(lower.escapeUp())
      assertEquals(0, lower.requests)
      fixture.back()
      assertEquals(1, lower.requests)
    }
  }

  @Test
  fun `predictive Back remains captured when a higher presentation appears`() {
    Fixture().use { fixture ->
      val lower = fixture.portal()
      fixture.activity.onBackPressedDispatcher.dispatchOnBackStarted(
        BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)
      )
      val upper = fixture.portal()
      fixture.back()
      assertEquals(0, lower.requests)
      assertEquals(0, upper.requests)
      assertEquals(0, fixture.hostBackCount)
      fixture.back()
      assertEquals(1, upper.requests)
    }
  }

  @Test
  fun `a rendered elevation change transfers Back without a sheet state or layout update`() {
    Fixture().use { fixture ->
      val lower = fixture.portal()
      val upper = fixture.portal()
      lower.view.elevation = 10f
      lower.view.viewTreeObserver.dispatchOnPreDraw()
      fixture.back()
      assertEquals(1, lower.requests)
      assertEquals(0, upper.requests)
    }
  }

  @Test
  fun `raising a handlerless presentation before drawing releases Back to the host`() {
    Fixture().use { fixture ->
      val lower = fixture.portal(portalInputState(hasHandler = false))
      val upper = fixture.portal()
      lower.view.translationZ = 10f
      lower.view.viewTreeObserver.dispatchOnPreDraw()
      fixture.back()
      assertEquals(1, fixture.hostBackCount)
      assertEquals(0, upper.requests)
      assertEquals(0, lower.requests)
      lower.view.translationZ = 0f
      lower.view.viewTreeObserver.dispatchOnPreDraw()
      fixture.back()
      assertEquals(1, upper.requests)
    }
  }

  @Test
  fun `visual transfer and return cannot reactivate predictive emission`() {
    Fixture().use { fixture ->
      val lower = fixture.portal()
      val upper = fixture.portal()
      fixture.activity.onBackPressedDispatcher.dispatchOnBackStarted(
        BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)
      )
      lower.view.elevation = 10f
      lower.view.viewTreeObserver.dispatchOnPreDraw()
      lower.view.elevation = 0f
      lower.view.viewTreeObserver.dispatchOnPreDraw()
      fixture.back()
      assertEquals(0, lower.requests)
      assertEquals(0, upper.requests)
      fixture.back()
      assertEquals(1, upper.requests)
    }
  }

  private class Fixture(unknownOrder: Boolean = false) : AutoCloseable {
    private val activityController =
      Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = activityController.get()
    private val root = TestReactRoot(activity)
    val parent = if (unknownOrder) root else FrameLayout(activity).also(root::addView)
    private val portals = mutableListOf<Portal>()
    var hostBackCount = 0

    init {
      activity.setContentView(root)
      activity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            hostBackCount++
          }
        }
      )
    }

    fun portal(state: CloseRequestInputState = portalInputState()): Portal =
      Portal(this).also {
        portals.add(it)
        it.update(state)
      }

    fun back() = activity.onBackPressedDispatcher.onBackPressed()

    override fun close() {
      portals.asReversed().forEach { it.dispose() }
      activityController.close()
    }
  }

  private class Portal(fixture: Fixture) {
    val view = View(fixture.activity).also(fixture.parent::addView)
    var requests = 0
    private val close =
      PortalCloseRequestController(view, { fixture.activity }) {
        requests++
        true
      }
    val presentation = PortalPresentationController(view, close::onPresentationChanged)

    fun update(state: CloseRequestInputState) {
      close.update(state, usesPortalPresentation = true)
      presentation.update(
        isPortal = state.isAttached && state.isModal,
        isActive = state.isPresentationActive,
      )
    }

    fun escapeDown() =
      close.dispatchEscape(KeyEvent(10, 10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0))

    fun escapeUp() =
      close.dispatchEscape(KeyEvent(10, 11, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0))

    fun dispose() {
      presentation.dispose()
      close.dispose()
    }
  }
}

private fun portalInputState(
  hasHandler: Boolean = true,
  active: Boolean = true,
  targetOpen: Boolean = true,
) =
  CloseRequestInputState(
    isAttached = true,
    isLifecycleActive = true,
    isModal = true,
    hasCloseRequestHandler = hasHandler,
    isPresentationActive = active,
    isTargetResolvedAndOpen = targetOpen,
  )
