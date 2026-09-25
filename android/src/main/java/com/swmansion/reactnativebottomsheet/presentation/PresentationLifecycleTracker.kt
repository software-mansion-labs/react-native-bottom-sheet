package com.swmansion.reactnativebottomsheet.presentation

private const val PRESENTATION_VISIBLE_EPSILON_PX = 0.5f

/**
 * Tracks the neutral Active presentation through opening and closing.
 *
 * An open target is Active once layout is ready. A visible sheet animating toward a closed target
 * remains Active until the transition settles. Centralizing that lifecycle here keeps layout and
 * animation paths consistent.
 */
internal class PresentationLifecycleTracker {
  private var closingPresentationActive = false

  fun onAnimationStarted(
    isTargetOpen: Boolean,
    visibleHeight: Float,
  ) {
    closingPresentationActive =
      !isTargetOpen &&
        (closingPresentationActive || visibleHeight > PRESENTATION_VISIBLE_EPSILON_PX)
  }

  fun onTransitionSettled() = resetClosingPresentation()

  fun onInvalidTarget() = resetClosingPresentation()

  fun onHostDestroyed() = resetClosingPresentation()

  private fun resetClosingPresentation() {
    closingPresentationActive = false
  }

  fun isPresentationActive(
    isTargetDetentOpen: Boolean,
    isLayoutReady: Boolean,
  ): Boolean = isLayoutReady && (isTargetDetentOpen || closingPresentationActive)
}
