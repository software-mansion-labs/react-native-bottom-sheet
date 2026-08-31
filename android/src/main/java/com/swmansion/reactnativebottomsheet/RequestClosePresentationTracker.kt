package com.swmansion.reactnativebottomsheet

private const val REQUEST_CLOSE_VISIBLE_EPSILON_PX = 0.5f

/**
 * Tracks whether a visibly closing sheet still owns Back/Escape input.
 *
 * Request-close eligibility follows the target detent, but a visible sheet animating toward a
 * closed target must retain input ownership until the transition settles. Centralizing that
 * lifecycle here keeps layout and animation paths consistent.
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

  fun onTransitionSettled() = resetClosingPresentation()

  fun onInvalidTarget() = resetClosingPresentation()

  fun onHostDestroyed() = resetClosingPresentation()

  private fun resetClosingPresentation() {
    closingPresentationActive = false
  }

  fun isPresentationActive(
    isTargetDetentOpen: Boolean,
    isRequestCloseLayoutReady: Boolean,
  ): Boolean = isRequestCloseLayoutReady && (isTargetDetentOpen || closingPresentationActive)
}
