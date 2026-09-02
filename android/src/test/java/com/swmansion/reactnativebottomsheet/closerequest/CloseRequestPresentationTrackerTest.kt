package com.swmansion.reactnativebottomsheet.closerequest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloseRequestPresentationTrackerTest {
  @Test
  fun `visible closing animation owns Back and Escape input`() {
    val tracker = CloseRequestPresentationTracker()

    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 1f)

    assertTrue(
      tracker.isPresentationActive(
        isTargetDetentOpen = false,
        isCloseRequestLayoutReady = true,
      )
    )
    assertFalse(
      tracker.isPresentationActive(
        isTargetDetentOpen = false,
        isCloseRequestLayoutReady = false,
      )
    )
  }

  @Test
  fun `reanchoring a closing animation retains ownership`() {
    val tracker = CloseRequestPresentationTracker()
    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 120f)

    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 0f)

    assertTrue(
      tracker.isPresentationActive(
        isTargetDetentOpen = false,
        isCloseRequestLayoutReady = true,
      )
    )
  }

  @Test
  fun `closing animation beginning at zero does not claim input`() {
    val tracker = CloseRequestPresentationTracker()

    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 0f)

    assertFalse(
      tracker.isPresentationActive(
        isTargetDetentOpen = false,
        isCloseRequestLayoutReady = true,
      )
    )
  }

  @Test
  fun `retargeting to an open detent clears closing ownership`() {
    val tracker = CloseRequestPresentationTracker()
    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 120f)

    tracker.onAnimationStarted(isTargetOpen = true, visibleHeight = 80f)

    assertTrue(
      tracker.isPresentationActive(
        isTargetDetentOpen = true,
        isCloseRequestLayoutReady = true,
      )
    )
    assertFalse(
      tracker.isPresentationActive(
        isTargetDetentOpen = false,
        isCloseRequestLayoutReady = true,
      )
    )
  }

  @Test
  fun `settled transition and terminal conditions reset ownership`() {
    val tracker = CloseRequestPresentationTracker()

    listOf(
        tracker::onTransitionSettled,
        tracker::onInvalidTarget,
        tracker::onHostDestroyed,
      )
      .forEach { reset ->
        tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 120f)
        reset()
        assertFalse(
          tracker.isPresentationActive(
            isTargetDetentOpen = false,
            isCloseRequestLayoutReady = true,
          )
        )
      }
  }
}
