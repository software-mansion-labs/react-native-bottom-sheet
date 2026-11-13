package com.swmansion.reactnativebottomsheet.host

import android.os.SystemClock
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.swmansion.reactnativebottomsheet.closerequest.CloseRequestPresentationTracker
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

// Seconds of release velocity projected onto the sheet's position before the nearest-detent search
// that resolves a drag release, so a gentle but deliberate swipe counts toward the detent it was
// aimed at. Matches `releaseProjection` on iOS and web.
private const val RELEASE_PROJECTION = 0.1f

// Matches `defaultSettleDuration` on iOS.
private const val DEFAULT_SETTLE_DURATION = 0.45f
private const val SETTLE_MIN_VISIBLE_CHANGE = 0.001f

/** Snap math, the spring that settles the sheet, and the position it reports along the way. */
internal class Snapping(
  private val state: SheetState,
  private val scrim: Scrim,
  private val card: Card,
  private val contentRegion: ContentRegion,
  private val closeRequestPresentationTracker: CloseRequestPresentationTracker,
  private val host: SheetHost,
) {

  /**
   * Whether a close request has anything to act on while the scrim shows: an `onCloseRequest`
   * handler or, without one, a closed detent the sheet may snap to.
   */
  val handlesCloseRequests: Boolean
    get() =
      scrim.isScrimVisible() && (state.hasCloseRequestHandler || state.scrimDismissIndex != null)

  // Requiring an open target also ignores requests once a close is running, including one to
  // another zero-height detent: restarting it would emit a second index change and drop the
  // running spring's velocity.
  val isCloseRequestAvailable: Boolean
    get() =
      handlesCloseRequests && (state.detentSpecs.getOrNull(state.targetIndex)?.position ?: 0f) > 0f

  /**
   * Asks a modal sheet to close, as a scrim tap does, returning whether the request was acted on. A
   * sheet with an `onCloseRequest` handler only reports the request; one without snaps to its
   * closed detent.
   */
  fun requestClose(): Boolean {
    if (!isCloseRequestAvailable) return false
    return host.performCloseRequest()
  }

  /** Snaps to the closed detent a scrim tap dismisses to, if there is one. */
  fun snapToScrimDismissIndex(): Boolean {
    val closeIndex = state.scrimDismissIndex ?: return false
    snapToIndex(closeIndex, 0f)
    return true
  }

  fun translationY(index: Int): Float {
    return state.height.toFloat() - (state.detentSpecs.getOrNull(index)?.position ?: 0f)
  }

  private fun snapCandidateIndices(includeIndex: Int? = null): List<Int> {
    val indices =
      state.detentSpecs.indices.filter { !state.detentSpecs[it].programmatic }.toMutableList()
    if (
      includeIndex != null &&
        includeIndex in state.detentSpecs.indices &&
        state.detentSpecs[includeIndex].programmatic
    ) {
      indices.add(includeIndex)
    }
    return indices.distinct().sortedBy { state.detentSpecs[it].position }
  }

  fun draggableRange(includeIndex: Int? = null): ClosedFloatingPointRange<Float> {
    val candidates = snapCandidateIndices(includeIndex)
    if (candidates.isEmpty()) return 0f..0f
    val translations = candidates.map(::translationY)
    return (translations.minOrNull() ?: 0f)..(translations.maxOrNull() ?: 0f)
  }

  private fun snapshotTranslationY(index: Int, specs: List<DetentSpec>): Float {
    return state.height.toFloat() - (specs.getOrNull(index)?.position ?: 0f)
  }

  private fun snapshotCandidateIndices(includeIndex: Int?, specs: List<DetentSpec>): List<Int> {
    val indices = specs.indices.filter { !specs[it].programmatic }.toMutableList()
    if (includeIndex != null && includeIndex in specs.indices && specs[includeIndex].programmatic) {
      indices.add(includeIndex)
    }
    return indices.distinct().sortedBy { specs[it].position }
  }

  fun snapshotDraggableRange(
    includeIndex: Int?,
    specs: List<DetentSpec>,
  ): ClosedFloatingPointRange<Float> {
    val candidates = snapshotCandidateIndices(includeIndex, specs)
    if (candidates.isEmpty()) return 0f..0f
    val translations = candidates.map { snapshotTranslationY(it, specs) }
    return (translations.minOrNull() ?: 0f)..(translations.maxOrNull() ?: 0f)
  }

  fun isAtMaxDragCandidate(includeIndex: Int? = null): Boolean {
    val range = draggableRange(includeIndex)
    return state.sheetContainer.translationY <= range.start + 1f
  }

  fun emitPosition() {
    val maxHeight = state.height.toFloat()
    val ty = state.sheetContainer.translationY
    val position = maxHeight - ty
    scrim.updateScrim(position)
    scrim.updateSheetVisibility(position)
    host.updateInteractionState()
    host.listener?.onPositionChange(
      (position / state.density).toDouble(),
      detentIndexAt(position).toDouble(),
    )
    card.updateCardGeometry()
    contentRegion.updateShadowState(ty)
    state.scrollIndicators.refit()
  }

  // Fractional detent index in 0..(detentSpecs.size - 1): 0 at the shortest
  // detent, 1 at the next, and so on, interpolated by position in between. The
  // continuous counterpart of `onIndexChange`, so consumers can drive a backdrop
  // or animate per detent without knowing the sheet's height.
  private fun detentIndexAt(position: Float): Float =
    state.interpolateAtPosition(position, state.detentSpecs.indices.map { it.toFloat() })

  fun snapToIndex(
    index: Int,
    velocity: Float,
    emitIndexChange: Boolean = true,
    emitSettle: Boolean = true,
    preserveScrimPin: Boolean = false,
  ) {
    if (index < 0 || index >= state.detentSpecs.size) return
    if (emitIndexChange) {
      // A user-driven snap supersedes an index retained from props.
      state.pendingIndex = null
    }
    state.targetIndex = index
    if (!state.isTargetingClosedDetent) {
      state.suppressScrimForClosingTarget = false
    }
    if (!preserveScrimPin) {
      state.scrimPinnedFull = false
    }

    val targetTy = translationY(index)
    state.activeAnimationEmitsSettle = emitSettle
    state.activeAnimation?.cancel()

    val currentTy = state.sheetContainer.translationY
    val distance = targetTy - currentTy
    val settleDuration =
      if (state.settleDuration > 0f) state.settleDuration else DEFAULT_SETTLE_DURATION
    // A critically damped spring is settled (within ~0.5% of target) at omega*t ~= 8, so a
    // duration maps to omega = 8/duration. This is the spring iOS runs, so both platforms settle
    // at the same speed.
    // The spring starts at the release velocity, as native sheets do. A positive ratio heads
    // toward the target; above omega the spring would carry the sheet past it, so stiffen it to
    // the ratio instead: the sheet keeps the release velocity, decays straight onto the target
    // and finishes sooner. Moving away from the target, the spring turns the sheet around.
    val velocityRatio = if (distance != 0f) velocity / distance else 0f
    val initialVelocity = max(velocityRatio, -5f) * distance
    val omega = max(8f / settleDuration, velocityRatio)
    val settleDeadline = SystemClock.uptimeMillis() + (8000f / omega).roundToLong()

    val spring =
      SpringAnimation(state.sheetContainer, DynamicAnimation.TRANSLATION_Y, targetTy).apply {
        spring =
          SpringForce(targetTy).apply {
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            // SpringForce's stiffness is omega^2.
            stiffness = omega.pow(2)
          }
        // Like iOS, the duration fixes when the settle ends rather than how close to the target
        // the spring has to get: keep the spring from ending itself before the deadline.
        setMinimumVisibleChange(SETTLE_MIN_VISIBLE_CHANGE)
        setStartVelocity(initialVelocity)
        // Forward the position on every frame of the settle. The listener fires
        // immediately after the spring writes `translationY`, so `emitPosition`
        // reads the value being shown this frame — keeping followers that track
        // `onPositionChange` (e.g. a Reanimated view) in lockstep with the sheet.
        addUpdateListener { animation, _, _ ->
          emitPosition()
          if (SystemClock.uptimeMillis() >= settleDeadline) {
            (animation as SpringAnimation).skipToEnd()
          }
        }
        addEndListener { _, canceled, _, _ ->
          if (canceled) {
            return@addEndListener
          }
          state.activeAnimation = null
          state.activeAnimationEmitsSettle = false
          state.suppressScrimForClosingTarget = false
          state.scrimPinnedFull = false
          if (state.closedIndex == index) {
            state.sheetContainer.translationY = translationY(index)
            scrim.hideScrim()
          }
          emitPosition()
          contentRegion.recomputeNativeGeometry()
          closeRequestPresentationTracker.onTransitionSettled()
          host.notifyCloseRequestStateChanged()
          host.updateInteractionState()
          if (emitSettle) host.listener?.onSettle(index)
        }
      }

    state.activeAnimation = spring
    val visibleHeight = state.height.toFloat() - currentTy
    closeRequestPresentationTracker.onAnimationStarted(
      isTargetOpen = !state.isTargetingClosedDetent,
      visibleHeight = visibleHeight,
    )
    // Publish only after the replacement spring is assigned. Re-anchoring a close must not create
    // a transient pass-through window between canceling the old animation and starting the new.
    host.notifyCloseRequestStateChanged()
    // Report the index change as soon as the snap is committed, not when it
    // finishes: targetIndex is already set, and a programmatic snap's start is
    // known to the caller. onSettle remains the signal for movement end.
    if (emitIndexChange) host.listener?.onIndexChange(index)
    spring.start()
  }

  private fun bestSnapIndex(currentHeight: Float, velocity: Float, includeIndex: Int? = null): Int =
    releaseSnapIndex(
      snapCandidateIndices(includeIndex),
      { state.detentSpecs[it].position },
      currentHeight,
      velocity,
      600f * state.density,
    ) ?: state.targetIndex

  // Same resolution as `bestSnapIndex`, against the detents captured when the drag began.
  private fun snapshotBestSnapIndex(
    currentHeight: Float,
    velocity: Float,
    includeIndex: Int?,
    specs: List<DetentSpec>,
  ): Int =
    releaseSnapIndex(
      snapshotCandidateIndices(includeIndex, specs),
      { specs[it].position },
      currentHeight,
      velocity,
      600f * state.density,
    ) ?: state.targetIndex

  fun settleActivePan(velocity: Float) {
    val maxHeight = state.height.toFloat()
    val currentHeight = maxHeight - state.sheetContainer.translationY
    val startingIndex = state.panStartingIndex
    val index =
      state.activeDragDetentSpecs
        ?.takeIf { it.size == state.detentSpecs.size }
        ?.let { snapshotBestSnapIndex(currentHeight, velocity, startingIndex, it) }
        ?: bestSnapIndex(currentHeight, velocity, startingIndex)
    state.panStartingIndex = null
    state.activeDragRange = null
    state.activeDragDetentSpecs = null
    snapToIndex(index, velocity)
  }
}

/**
 * The detent a drag release resolves to, or null without candidates. [candidates] are detent
 * indices in ascending position order and [positionOf] gives each one's height. `velocity` is
 * downward-positive while heights grow upward.
 */
internal fun releaseSnapIndex(
  candidates: List<Int>,
  positionOf: (Int) -> Float,
  currentHeight: Float,
  velocity: Float,
  flickThreshold: Float,
): Int? {
  val above = candidates.firstOrNull { positionOf(it) > currentHeight }
  val below = candidates.lastOrNull { positionOf(it) < currentHeight }

  // A flick moves one detent along from where the sheet actually is.
  if (velocity < -flickThreshold) return above ?: candidates.lastOrNull()
  if (velocity > flickThreshold) return below ?: candidates.firstOrNull()

  // A slower release resolves against where it was heading, but never past the first detent ahead
  // of it: projecting further would skip a detent that a slightly faster flick stops at.
  val projected = currentHeight - velocity * RELEASE_PROJECTION
  val heading =
    when {
      projected > currentHeight && above != null -> min(projected, positionOf(above))
      projected < currentHeight && below != null -> max(projected, positionOf(below))
      else -> projected
    }
  return candidates.minByOrNull { abs(positionOf(it) - heading) }
}
