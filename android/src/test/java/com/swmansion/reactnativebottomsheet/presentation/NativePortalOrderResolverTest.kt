package com.swmansion.reactnativebottomsheet.presentation

import android.app.Activity
import android.view.View
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.ReactRootView
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.views.view.ReactViewGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [24, 35])
class NativePortalOrderResolverTest {
  @Test
  fun `unobservable custom drawing and transitions cannot publish strict order`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = TestReactRoot(activity.get())
      val parent = FrameLayout(activity.get())
      val first = View(activity.get())
      val second = View(activity.get())
      parent.addView(first)
      parent.addView(second)
      root.addView(parent)
      activity.get().setContentView(root)
      second.startAnimation(TranslateAnimation(0f, 10f, 0f, 0f).apply { duration = 1000 })
      assertEquals(NativePortalOrder.UNKNOWN, compare(second, first))
      second.clearAnimation()
      assertEquals(NativePortalOrder.ABOVE, compare(second, first))

      val custom =
        object : FrameLayout(activity.get()) {
          init {
            isChildrenDrawingOrderEnabled = true
          }

          override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int) =
            childCount - 1 - drawingPosition
        }
      parent.removeAllViews()
      custom.addView(first)
      custom.addView(second)
      parent.addView(custom)
      assertEquals(NativePortalOrder.UNKNOWN, compare(second, first))
    } finally {
      activity.close()
    }
  }

  @Test
  fun `ordinary React root exposes its native drawing order`() {
    ReactNativeFeatureFlagsForTests.setUp()
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = ReactRootView(activity.get())
      val first = View(activity.get())
      val second = View(activity.get())
      root.addView(first)
      root.addView(second)
      activity.get().setContentView(root)
      assertEquals(NativePortalOrder.ABOVE, compare(second, first))
    } finally {
      activity.close()
    }
  }

  @Test
  fun `divergent branches use the drawing order at their common ancestor`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = TestReactRoot(activity.get())
      val parent = FrameLayout(activity.get())
      val lowerBranch = FrameLayout(activity.get())
      val upperBranch = FrameLayout(activity.get())
      val lower = View(activity.get())
      val upper = View(activity.get())
      lowerBranch.addView(lower)
      upperBranch.addView(upper)
      parent.addView(lowerBranch)
      parent.addView(upperBranch)
      root.addView(parent)
      activity.get().setContentView(root)

      assertEquals(NativePortalOrder.ABOVE, compare(upper, lower))
      assertEquals(NativePortalOrder.BELOW, compare(lower, upper))
      parent.bringChildToFront(lowerBranch)
      assertEquals(NativePortalOrder.ABOVE, compare(lower, upper))
      upperBranch.translationZ = 10f
      lower.elevation = 100f
      assertEquals(NativePortalOrder.ABOVE, compare(upper, lower))
    } finally {
      activity.close()
    }
  }

  private fun compare(first: View, second: View) =
    NativePortalOrderResolver.compare(
      requireNotNull(first.resolvePortalPresentationContext()),
      requireNotNull(second.resolvePortalPresentationContext()),
    )

  @Test
  fun `React Native exposes its effective child order`() {
    ReactNativeFeatureFlagsForTests.setUp()
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = TestReactRoot(activity.get())
      val parent = ReactViewGroup(activity.get())
      val first = View(activity.get())
      val second = View(activity.get())
      parent.addView(first)
      parent.addView(second)
      root.addView(parent)
      activity.get().setContentView(root)
      assertEquals(NativePortalOrder.ABOVE, compare(second, first))
    } finally {
      activity.close()
    }
  }

  @Test
  fun `nested anchors are ordered but detached snapshots cannot prove order`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = TestReactRoot(activity.get())
      val outer = FrameLayout(activity.get())
      val inner = View(activity.get())
      outer.addView(inner)
      root.addView(outer)
      activity.get().setContentView(root)
      assertEquals(NativePortalOrder.ABOVE, compare(inner, outer))
      assertEquals(NativePortalOrder.BELOW, compare(outer, inner))
      assertEquals(NativePortalOrder.EQUAL, compare(inner, inner))
      val stale = requireNotNull(inner.resolvePortalPresentationContext())
      outer.removeView(inner)
      assertEquals(
        NativePortalOrder.UNKNOWN,
        NativePortalOrderResolver.compare(
          stale,
          requireNotNull(outer.resolvePortalPresentationContext()),
        ),
      )
    } finally {
      activity.close()
    }
  }
}
