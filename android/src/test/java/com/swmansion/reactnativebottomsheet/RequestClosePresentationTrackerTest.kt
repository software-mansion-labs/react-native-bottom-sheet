package com.swmansion.reactnativebottomsheet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestClosePresentationTrackerTest {
  @Test
  fun `visible closing animation owns close input`() {
    val tracker = RequestClosePresentationTracker()

    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 1f)

    assertTrue(tracker.isPresentationActive(isTargetOpen = false, isHostReady = true))
    assertFalse(tracker.isPresentationActive(isTargetOpen = false, isHostReady = false))
  }

  @Test
  fun `reanchoring a closing animation retains ownership`() {
    val tracker = RequestClosePresentationTracker()
    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 120f)

    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 0f)

    assertTrue(tracker.isPresentationActive(isTargetOpen = false, isHostReady = true))
  }

  @Test
  fun `closing animation beginning at zero does not claim input`() {
    val tracker = RequestClosePresentationTracker()

    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 0f)

    assertFalse(tracker.isPresentationActive(isTargetOpen = false, isHostReady = true))
  }

  @Test
  fun `retargeting to an open detent clears closing ownership`() {
    val tracker = RequestClosePresentationTracker()
    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 120f)

    tracker.onAnimationStarted(isTargetOpen = true, visibleHeight = 80f)

    assertTrue(tracker.isPresentationActive(isTargetOpen = true, isHostReady = true))
    assertFalse(tracker.isPresentationActive(isTargetOpen = false, isHostReady = true))
  }

  @Test
  fun `movement completion and terminal transitions reset ownership`() {
    val tracker = RequestClosePresentationTracker()

    listOf(
        tracker::onAnimationFinished,
        tracker::onNonAnimatedTransition,
        tracker::onInvalidTarget,
        tracker::onHostDestroyed,
      )
      .forEach { reset ->
        tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 120f)
        reset()
        assertFalse(tracker.isPresentationActive(isTargetOpen = false, isHostReady = true))
      }
  }
}
