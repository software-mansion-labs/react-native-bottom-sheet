package com.swmansion.reactnativebottomsheet.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationLifecycleTrackerTest {
  @Test
  fun `open target becomes Active as soon as layout is ready`() {
    val tracker = PresentationLifecycleTracker()

    assertFalse(tracker.isPresentationActive(isTargetDetentOpen = true, isLayoutReady = false))
    assertTrue(tracker.isPresentationActive(isTargetDetentOpen = true, isLayoutReady = true))
    assertFalse(tracker.isPresentationActive(isTargetDetentOpen = false, isLayoutReady = true))
  }

  @Test
  fun `visible closing animation stays Active while layout is ready`() {
    val tracker = PresentationLifecycleTracker()

    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 1f)

    assertTrue(
      tracker.isPresentationActive(
        isTargetDetentOpen = false,
        isLayoutReady = true,
      )
    )
    assertFalse(
      tracker.isPresentationActive(
        isTargetDetentOpen = false,
        isLayoutReady = false,
      )
    )
  }

  @Test
  fun `reanchoring a closing animation retains Active state`() {
    val tracker = PresentationLifecycleTracker()
    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 120f)

    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 0f)

    assertTrue(
      tracker.isPresentationActive(
        isTargetDetentOpen = false,
        isLayoutReady = true,
      )
    )
  }

  @Test
  fun `closing animation beginning at zero does not become Active`() {
    val tracker = PresentationLifecycleTracker()

    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 0f)

    assertFalse(
      tracker.isPresentationActive(
        isTargetDetentOpen = false,
        isLayoutReady = true,
      )
    )
  }

  @Test
  fun `retargeting to an open detent clears closing Active state`() {
    val tracker = PresentationLifecycleTracker()
    tracker.onAnimationStarted(isTargetOpen = false, visibleHeight = 120f)

    tracker.onAnimationStarted(isTargetOpen = true, visibleHeight = 80f)

    assertTrue(
      tracker.isPresentationActive(
        isTargetDetentOpen = true,
        isLayoutReady = true,
      )
    )
    assertFalse(
      tracker.isPresentationActive(
        isTargetDetentOpen = false,
        isLayoutReady = true,
      )
    )
  }

  @Test
  fun `settled transition and terminal conditions reset Active state`() {
    val tracker = PresentationLifecycleTracker()

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
            isLayoutReady = true,
          )
        )
      }
  }
}
