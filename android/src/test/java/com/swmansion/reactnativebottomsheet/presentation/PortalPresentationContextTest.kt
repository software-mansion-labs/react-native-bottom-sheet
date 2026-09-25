package com.swmansion.reactnativebottomsheet.presentation

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.uimanager.RootView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalPresentationContextTest {
  @Test
  fun `attached native content never falls back to the Activity decor`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val anchor = View(activity.get())
      activity.get().setContentView(anchor)
      assertNull(anchor.resolvePortalPresentationContext())
    } finally {
      activity.close()
    }
  }

  @Test
  fun `nearest attached React root supplies the exact path independently of the window root`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val outer = TestReactRoot(activity.get())
      val inner = TestReactRoot(activity.get())
      val branch = FrameLayout(activity.get())
      val anchor = View(activity.get())
      branch.addView(anchor)
      inner.addView(branch)
      outer.addView(inner)
      activity.get().setContentView(outer)

      val context = requireNotNull(anchor.resolvePortalPresentationContext())

      assertSame(inner, context.reactRoot)
      assertEquals(listOf(inner, branch, anchor), context.path)
      assertSame(anchor.rootView, context.windowRoot)
      assertSame(anchor.windowToken, context.windowToken)
      branch.removeView(anchor)
      assertNull(anchor.resolvePortalPresentationContext())
    } finally {
      activity.close()
    }
  }
}

internal class TestReactRoot(context: Context) : FrameLayout(context), RootView {
  override fun onChildStartedNativeGesture(childView: View?, ev: MotionEvent) = Unit

  override fun onChildEndedNativeGesture(childView: View, ev: MotionEvent) = Unit

  override fun handleException(t: Throwable) = throw t
}
