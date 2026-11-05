package com.swmansion.reactnativebottomsheet.host

import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.swmansion.reactnativebottomsheet.closerequest.CloseRequestPresentationTracker
import kotlin.math.abs

/** Snap math, the spring that settles the sheet, and the position it reports along the way. */
internal class Snapping(
  private val state: SheetState,
  private val scrim: Scrim,
  private val card: Card,
  private val contentRegion: ContentRegion,
  private val closeRequestPresentationTracker: CloseRequestPresentationTracker,
  private val host: SheetHost,
) {

  fun translationY(index: Int): Float {
    return state.height.toFloat() - (state.detentSpecs.getOrNull(index)?.position ?: 0f)
  }

  private val minDetentTranslationY: Float
    get() = state.detentSpecs.indices.minOfOrNull(::translationY) ?: 0f

  private val maxDetentTranslationY: Float
    get() = state.detentSpecs.indices.maxOfOrNull(::translationY) ?: 0f

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

    val minAnimationTy = minOf(minDetentTranslationY, currentTy, targetTy)
    val maxAnimationTy = maxOf(maxDetentTranslationY, currentTy, targetTy)
    val distance = targetTy - currentTy
    val velocityRatio = if (distance != 0f) velocity / distance else 0f
    val initialVelocity = velocityRatio.coerceIn(-5f, 5f) * distance

    val spring =
      SpringAnimation(state.sheetContainer, DynamicAnimation.TRANSLATION_Y, targetTy).apply {
        spring =
          SpringForce(targetTy).apply {
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            stiffness = SpringForce.STIFFNESS_MEDIUM
          }
        setMinValue(minAnimationTy)
        setMaxValue(maxAnimationTy)
        setStartVelocity(initialVelocity)
        // Forward the position on every frame of the settle. The listener fires
        // immediately after the spring writes `translationY`, so `emitPosition`
        // reads the value being shown this frame — keeping followers that track
        // `onPositionChange` (e.g. a Reanimated view) in lockstep with the sheet.
        addUpdateListener { _, _, _ -> emitPosition() }
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

  private fun bestSnapIndex(currentHeight: Float, velocity: Float, includeIndex: Int? = null): Int {
    val candidates = snapCandidateIndices(includeIndex)
    if (candidates.isEmpty()) return state.targetIndex

    val flickThreshold = 600f * state.density

    if (velocity < -flickThreshold) {
      return candidates.firstOrNull { state.detentSpecs[it].position > currentHeight }
        ?: candidates.lastOrNull()
        ?: state.targetIndex
    }
    if (velocity > flickThreshold) {
      return candidates.lastOrNull { state.detentSpecs[it].position < currentHeight }
        ?: candidates.firstOrNull()
        ?: state.targetIndex
    }

    return candidates.minByOrNull { abs(state.detentSpecs[it].position - currentHeight) }
      ?: state.targetIndex
  }

  private fun snapshotBestSnapIndex(
    currentHeight: Float,
    velocity: Float,
    includeIndex: Int?,
    specs: List<DetentSpec>,
  ): Int {
    val candidates = snapshotCandidateIndices(includeIndex, specs)
    if (candidates.isEmpty()) return state.targetIndex

    val flickThreshold = 600f * state.density

    if (velocity < -flickThreshold) {
      return candidates.firstOrNull { specs[it].position > currentHeight }
        ?: candidates.lastOrNull()
        ?: state.targetIndex
    }
    if (velocity > flickThreshold) {
      return candidates.lastOrNull { specs[it].position < currentHeight }
        ?: candidates.firstOrNull()
        ?: state.targetIndex
    }

    return candidates.minByOrNull { abs(specs[it].position - currentHeight) } ?: state.targetIndex
  }

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
