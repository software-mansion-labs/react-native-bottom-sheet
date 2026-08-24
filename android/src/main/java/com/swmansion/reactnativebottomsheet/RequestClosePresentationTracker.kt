package com.swmansion.reactnativebottomsheet

private const val REQUEST_CLOSE_VISIBLE_EPSILON_PX = 0.5f

/**
 * Tracks whether a visibly closing sheet still owns close-request input.
 *
 * A close request is emitted from the target detent, while ownership also has to cover the
 * animation that takes a visible sheet to a closed target. Keeping that lifecycle here avoids
 * scattering the transition rules across layout and animation paths in the host.
 */
internal class RequestClosePresentationTracker {
  private var closingPresentationActive = false

  fun onAnimationStarted(
    isTargetOpen: Boolean,
    visibleHeight: Float,
  ) {
    closingPresentationActive =
      !isTargetOpen &&
        (closingPresentationActive || visibleHeight > REQUEST_CLOSE_VISIBLE_EPSILON_PX)
  }

  fun onMovementFinished() {
    closingPresentationActive = false
  }

  fun onNonAnimatedTransition() {
    closingPresentationActive = false
  }

  fun onInvalidTarget() {
    closingPresentationActive = false
  }

  fun onHostDestroyed() {
    closingPresentationActive = false
  }

  fun isPresentationActive(
    isTargetOpen: Boolean,
    isHostReady: Boolean,
  ): Boolean = isHostReady && (isTargetOpen || closingPresentationActive)
}
