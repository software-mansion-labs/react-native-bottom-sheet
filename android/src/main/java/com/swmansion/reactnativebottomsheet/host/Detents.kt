package com.swmansion.reactnativebottomsheet.host

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.facebook.react.R
import com.swmansion.reactnativebottomsheet.BottomSheetCardClipLayout
import com.swmansion.reactnativebottomsheet.BottomSheetHostView.Companion.CONTENT_HEIGHT_MARKER_NATIVE_ID
import com.swmansion.reactnativebottomsheet.BottomSheetSurfaceView
import com.swmansion.reactnativebottomsheet.closerequest.CloseRequestPresentationTracker

/** Resolves the detents from props and layout, and keeps the sheet anchored to the result. */
internal class Detents(
  private val state: SheetState,
  private val clipContainer: BottomSheetCardClipLayout,
  private val scrim: Scrim,
  private val contentRegion: ContentRegion,
  private val snapping: Snapping,
  private val closeRequestPresentationTracker: CloseRequestPresentationTracker,
  private val host: SheetHost,
) {

  private var contentHeightMarker: View? = null
  private var surfaceView: View? = null
  var pendingInitialContentDetentSnap = false
  private var pendingInitialContentDetentObserver: ViewTreeObserver? = null
  private var pendingInitialContentDetentPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
  private var pendingInitialContentDetentFrames = 0

  private val contentHeightMarkerLayoutListener =
    View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      refreshDetentsFromLayout()
    }

  // Recomputes whether the card clips, from the detents' insets and whether the maximum width makes
  // the sheet's column narrower than this host.
  fun refreshCardGaps() {
    val hostWidth = state.width.toFloat()
    val isNarrowedByMaxWidth = state.columnWidth(hostWidth) < hostWidth
    state.hasCardGaps = state.hasInsetGaps || isNarrowedByMaxWidth
    clipContainer.setClipsToCard(state.hasCardGaps)
  }

  fun setDetents(raw: List<Map<String, Any>>) {
    val previousTallestDetent = state.rawDetentSpecs.lastOrNull()
    val previousWidestCardInsets = state.widestCardInsets
    val previousCardMaxWidth = state.cardMaxWidth
    val previousCardPlacementBias = state.cardPlacementBias
    val previousScrimOpacities = scrim.scrimOpacities
    // Entries without a value are dropped up front, so the specs and the scrim
    // opacities stay aligned by index.
    val entries = raw.filter { it["value"] is Number }
    scrim.scrimOpacities = entries.map { (it["scrimOpacity"] as? Number)?.toFloat() ?: 1f }
    state.rawDetentSpecs = entries.map { dict ->
      val value = (dict["value"] as Number).toDouble()
      val kind =
        when (dict["kind"] as? String) {
          "content" -> DetentKind.CONTENT
          "percentage" -> DetentKind.PERCENTAGE
          else -> DetentKind.POINTS
        }
      val programmatic = dict["programmatic"] as? Boolean ?: false
      val nativeValue =
        if (kind == DetentKind.POINTS) (value * state.density).toFloat() else value.toFloat()
      val inset = { key: String -> ((dict[key] as? Number)?.toFloat() ?: 0f) * state.density }
      val usesStatusBarTopInset = dict["usesStatusBarTopInset"] as? Boolean ?: false
      RawDetentSpec(
        value = nativeValue,
        kind = kind,
        programmatic = programmatic,
        insets =
          DetentInsets(
            top = if (usesStatusBarTopInset) null else inset("insetTop"),
            left = inset("insetLeft"),
            right = inset("insetRight"),
            bottom = inset("insetBottom"),
          ),
        widestCard = dict["widestCard"] as? Boolean ?: false,
      )
    }
    state.widestCardInsets =
      state.rawDetentSpecs.firstOrNull { it.widestCard }?.insets ?: DetentInsets()
    // Sheet-wide, carried on every detent so they arrive with the detents.
    val firstEntry = entries.firstOrNull()
    state.cardMaxWidth =
      ((firstEntry?.get("maxWidth") as? Number)?.toFloat() ?: 0f).coerceAtLeast(0f) * state.density
    state.cardPlacementBias =
      ((firstEntry?.get("placementBias") as? Number)?.toFloat() ?: 0.5f).coerceIn(0f, 1f)
    state.hasInsetGaps =
      state.rawDetentSpecs.any {
        it.insets.left > 0f || it.insets.right > 0f || it.insets.bottom > 0f
      }
    refreshCardGaps()
    // The tallest detent bounds the content region, so its inset must be
    // re-derived — and republished when it changed — before the detents are
    // re-resolved against the current layout.
    if (
      state.hasLaidOut &&
        (state.rawDetentSpecs.lastOrNull() != previousTallestDetent ||
          state.widestCardInsets != previousWidestCardInsets ||
          state.cardMaxWidth != previousCardMaxWidth)
    ) {
      // The content stays laid out against the previous region until Fabric
      // commits the new inset; hold its measurement until then. Decided from
      // the detents alone so it also holds while detached, when no geometry
      // can be pushed.
      contentRegion.isContentRegionChangePending = true
    }
    contentRegion.recomputeNativeGeometry()
    refreshDetentsFromLayout()
    // The re-resolved detents can change the card's shape at its current position.
    contentRegion.recomputeNativeGeometry()
    if (scrim.scrimOpacities != previousScrimOpacities) scrim.updateScrim()
    // The resolved detents don't carry the maximum width or the placement, so a change to either
    // alone can leave them as they were, and nothing above re-places the card. Request a layout:
    // the host posts its pass, which runs after this mount batch, once Fabric has re-measured the
    // children against the new cap, and re-places the card, its clip, its content and the state
    // snapshot.
    if (
      state.hasLaidOut &&
        state.width > 0 &&
        state.height > 0 &&
        (state.cardMaxWidth != previousCardMaxWidth ||
          state.cardPlacementBias != previousCardPlacementBias)
    ) {
      state.hostView.requestLayout()
    }
  }

  fun setIndex(newIndex: Int) {
    if (newIndex < 0) return

    if (!state.hasLaidOut) {
      state.pendingIndex = newIndex
      state.targetIndex = newIndex
      host.notifyCloseRequestStateChanged()
      return
    }

    if (newIndex >= state.detentSpecs.size) {
      // The resolved detents lag the raw specs while a content measurement is
      // held (see isContentRegionChangePending): `detents` and `index` arrive
      // in one update, so an index that only the new detents cover is retained
      // and replayed once they resolve rather than dropped — or clamped by the
      // deferred initial open below.
      if (newIndex < state.rawDetentSpecs.size) {
        state.pendingIndex = newIndex
      }
      return
    }
    // A newer request supersedes a retained one.
    state.pendingIndex = null

    if (pendingInitialContentDetentSnap) {
      // The initial open is still deferred because the target content detent is
      // not measurable yet. Retarget the deferred snap so the requested index is
      // reflected, then either complete it now (e.g. a points detent that is
      // already resolvable) or keep waiting for the content to measure.
      state.targetIndex = newIndex.coerceIn(0, state.detentSpecs.size - 1)
      if (!trySnapPendingInitialContentDetent()) {
        host.notifyCloseRequestStateChanged()
        observePendingInitialContentDetent()
      }
      return
    }

    if (newIndex == state.targetIndex) return
    snapping.snapToIndex(newIndex, 0f, emitIndexChange = false)
  }

  // Applies an index that was requested before the resolved detents covered it.
  // Runs after every detent resolution; a drag in progress keeps it waiting,
  // and the user's snap at the end of the drag drops it.
  fun replayPendingIndexIfResolved() {
    val index = state.pendingIndex ?: return
    if (!state.hasLaidOut || state.isPanning || index >= state.detentSpecs.size) return
    state.pendingIndex = null
    setIndex(index)
  }

  private fun resolveDetentSpecs(): List<DetentSpec> {
    val maxHeight = state.height.toFloat()
    val measuredContentHeight = validContentHeight().takeIf { maxHeight > 0f && it.isFinite() }
    var previousPosition: Float? = null
    return state.rawDetentSpecs.mapIndexed { index, spec ->
      val available = state.availableCardHeight(spec)
      val height =
        when (spec.kind) {
          DetentKind.POINTS -> spec.value
          DetentKind.PERCENTAGE -> available * spec.value
          DetentKind.CONTENT ->
            measuredContentHeight?.let { it * state.contentScale(spec.insets) }
              ?: unresolvedContentDetentHeight(index, maxHeight)
        }.coerceIn(0f, available)
      val position = if (height > 0f) height + spec.insets.bottom else 0f
      previousPosition?.let {
        if (position < it) {
          throw IllegalArgumentException(
            "Invalid bottom sheet detent at index $index: resolved position ${position / state.density} is lower than previous detent position ${it / state.density}. Detents must be passed in ascending order.",
          )
        }
      }
      previousPosition = position
      DetentSpec(position = position, programmatic = spec.programmatic, insets = spec.insets)
    }
  }

  private fun unresolvedContentDetentHeight(index: Int, maxHeight: Float): Float {
    val nextBound =
      state.rawDetentSpecs.drop(index + 1).firstOrNull { it.kind != DetentKind.CONTENT }
        ?: return maxHeight
    val available = state.availableCardHeight(nextBound)
    val nextBoundHeight =
      when (nextBound.kind) {
        DetentKind.POINTS -> nextBound.value
        DetentKind.PERCENTAGE -> available * nextBound.value
        DetentKind.CONTENT -> available
      }
    // Whatever its inset, this detent must not rise above the next one's top.
    return nextBoundHeight.coerceIn(0f, available) + nextBound.insets.bottom -
      state.rawDetentSpecs[index].insets.bottom
  }

  fun refreshDetentsFromLayout() {
    resolveDetentsFromLayout()
    replayPendingIndexIfResolved()
  }

  private fun resolveDetentsFromLayout() {
    if (!state.isPanning) {
      state.activeDragRange = null
      state.activeDragDetentSpecs = null
    }
    if (state.hasLaidOut && isInvalidContentDetentTarget(state.targetIndex)) {
      closeRequestPresentationTracker.onInvalidTarget()
      host.notifyCloseRequestStateChanged()
      scrim.updateScrim()
      return
    }

    val resolvedDetents = resolveDetentSpecs()
    if (
      resolvedDetents == state.detentSpecs &&
        state.height.toFloat() == state.lastAppliedMaxDetentHeight
    ) {
      if (trySnapPendingInitialContentDetent()) {
        return
      }
      host.notifyCloseRequestStateChanged()
      scrim.updateScrim()
      return
    }

    // The re-anchor math below preserves the on-screen sheet height across the
    // geometry change, so it must relate the current translation to the height it
    // was computed against — not the freshly resolved one.
    val previousMaxHeight =
      if (state.lastAppliedMaxDetentHeight.isFinite()) state.lastAppliedMaxDetentHeight
      else state.height.toFloat()
    // Whether the scrim is currently fully opaque, i.e. the sheet is settled at
    // or above the first non-zero detent. If so, a detent resize must not dip
    // the scrim while the sheet re-anchors to the new geometry.
    val wasScrimFull =
      state.modal &&
        state.firstOpenDetentPosition > 0f &&
        state.currentSheetHeight() + 0.5f >= state.firstOpenDetentPosition
    state.detentSpecs = resolvedDetents
    if (state.width > 0 && state.height > 0 && state.detentSpecs.isNotEmpty()) {
      host.layoutSheetContainer(state.width, state.height)

      if (state.hasLaidOut && !state.isPanning) {
        state.targetIndex = state.targetIndex.coerceIn(0, state.detentSpecs.size - 1)
        val newMaxHeight = state.height.toFloat()
        val targetTy = snapping.translationY(state.targetIndex)
        if (trySnapPendingInitialContentDetent()) {
          return
        }
        if (state.activeAnimation != null && state.isTargetingClosedDetent) {
          state.suppressScrimForClosingTarget = true
          scrim.hideScrim()
        }
        if (state.activeAnimation != null) {
          val currentTy = state.sheetContainer.translationY
          val shouldEmitSettle = state.activeAnimationEmitsSettle
          state.activeAnimation?.cancel()
          state.activeAnimation = null
          state.activeAnimationEmitsSettle = false
          // Re-anchor the in-flight position to the new container height so the
          // sheet surface keeps the same on-screen height across the resize.
          val visibleHeight = previousMaxHeight - currentTy
          state.sheetContainer.translationY =
            (newMaxHeight - visibleHeight).coerceIn(0f, newMaxHeight)
          state.scrimPinnedFull = state.scrimPinnedFull || wasScrimFull
          snapping.emitPosition()
          snapping.snapToIndex(
            state.targetIndex,
            0f,
            emitIndexChange = false,
            emitSettle = shouldEmitSettle,
            preserveScrimPin = true,
          )
        } else {
          val currentVisibleHeight = previousMaxHeight - state.sheetContainer.translationY
          val targetHeight = state.detentSpecs.getOrNull(state.targetIndex)?.position ?: 0f
          val shouldAnimateHeight = shouldAnimateContentHeight(state.targetIndex)
          if (kotlin.math.abs(targetHeight - currentVisibleHeight) <= 0.5f) {
            // No meaningful change.
            closeRequestPresentationTracker.onTransitionSettled()
            state.sheetContainer.translationY = targetTy
            snapping.emitPosition()
          } else if (!shouldAnimateHeight) {
            closeRequestPresentationTracker.onTransitionSettled()
            state.sheetContainer.translationY = targetTy
            snapping.emitPosition()
          } else {
            // The content detent changed (grew or shrank): re-anchor at the
            // current visible height, then animate to the new target. The
            // surface covers the full sheet, so a shrink no longer exposes
            // blank space.
            state.sheetContainer.translationY =
              (newMaxHeight - currentVisibleHeight).coerceIn(0f, newMaxHeight)
            state.scrimPinnedFull = state.scrimPinnedFull || wasScrimFull
            snapping.emitPosition()
            snapping.snapToIndex(
              state.targetIndex,
              0f,
              emitIndexChange = false,
              emitSettle = false,
              preserveScrimPin = true,
            )
          }
        }
      }
    }

    state.hostView.requestLayout()
    host.notifyCloseRequestStateChanged()
    scrim.updateScrim()
  }

  private fun shouldAnimateContentHeight(index: Int): Boolean =
    state.animateContentHeight || state.rawDetentSpecs.getOrNull(index)?.kind != DetentKind.CONTENT

  private fun currentContentHeight(): Float {
    val marker = contentHeightMarker ?: return Float.NaN
    return marker.top.toFloat()
  }

  private fun validContentHeight(): Float {
    if (contentRegion.isContentRegionChangePending) {
      // The content — and thus its marker — is laid out against the applied
      // inset. Until that is the inset the current detents call for, the
      // measurement describes the previous region and is treated as
      // unavailable.
      val expectedInsetDp = contentRegion.expectedContentRegionInsetDp()
      if (
        !expectedInsetDp.isFinite() ||
          !contentRegion.appliedContentRegionInsetDp.isFinite() ||
          kotlin.math.abs(contentRegion.appliedContentRegionInsetDp - expectedInsetDp) > 0.01f
      ) {
        return Float.NaN
      }
      contentRegion.isContentRegionChangePending = false
    }
    return currentContentHeight().takeIf { it.isFinite() && it > 0f } ?: Float.NaN
  }

  fun isInvalidContentDetentTarget(index: Int): Boolean {
    return state.rawDetentSpecs.getOrNull(index)?.kind == DetentKind.CONTENT &&
      !validContentHeight().isFinite()
  }

  private fun trySnapPendingInitialContentDetent(): Boolean {
    if (!pendingInitialContentDetentSnap || isInvalidContentDetentTarget(state.targetIndex)) {
      return false
    }

    pendingInitialContentDetentSnap = false
    removePendingInitialContentDetentObserver()
    snapping.snapToIndex(state.targetIndex, 0f, emitIndexChange = false, emitSettle = true)
    return true
  }

  fun clearPendingInitialContentDetentSnap() {
    pendingInitialContentDetentSnap = false
    removePendingInitialContentDetentObserver()
  }

  fun observePendingInitialContentDetent() {
    if (pendingInitialContentDetentPreDrawListener != null) return

    val observer = state.hostView.viewTreeObserver
    if (!observer.isAlive) return

    pendingInitialContentDetentFrames = 0
    val listener = ViewTreeObserver.OnPreDrawListener {
      refreshContentHeightMarker()
      refreshDetentsFromLayout()
      // refreshDetentsFromLayout() completes and stops observing via
      // trySnapPendingInitialContentDetent() once the target is measurable.
      // If it is still pending, this frame was unproductive: bound how many
      // such frames we spend so a content detent that never becomes
      // measurable cannot keep us redrawing forever.
      if (
        pendingInitialContentDetentSnap &&
          ++pendingInitialContentDetentFrames >= MAX_PENDING_INITIAL_CONTENT_DETENT_FRAMES
      ) {
        removePendingInitialContentDetentObserver()
      }
      true
    }

    // Keep the exact observer instance used for registration. Android can
    // replace a ViewTreeObserver across attach/detach boundaries, and listeners
    // must be removed from the same live observer that received them.
    pendingInitialContentDetentObserver = observer
    pendingInitialContentDetentPreDrawListener = listener
    observer.addOnPreDrawListener(listener)
  }

  fun removePendingInitialContentDetentObserver() {
    val observer = pendingInitialContentDetentObserver
    val listener = pendingInitialContentDetentPreDrawListener

    if (observer?.isAlive == true && listener != null) {
      observer.removeOnPreDrawListener(listener)
    }

    pendingInitialContentDetentObserver = null
    pendingInitialContentDetentPreDrawListener = null
  }

  fun refreshContentHeightMarker() {
    surfaceView = findSurfaceView()
    val marker = findContentHeightMarker()
    if (marker === contentHeightMarker) return
    contentHeightMarker?.removeOnLayoutChangeListener(contentHeightMarkerLayoutListener)
    contentHeightMarker = marker
    contentHeightMarker?.addOnLayoutChangeListener(contentHeightMarkerLayoutListener)
  }

  private fun findSurfaceView(): View? {
    for (i in 0 until clipContainer.childCount) {
      val child = clipContainer.getChildAt(i)
      if (child is BottomSheetSurfaceView) return child
    }
    return null
  }

  // The content wrapper: the surface is its sibling, so skip it and the wrapper
  // is whatever else the sheet mounts.
  private fun findContentView(): ViewGroup? =
    (0 until clipContainer.childCount)
      .map { clipContainer.getChildAt(it) }
      .firstOrNull { it !== surfaceView } as? ViewGroup

  private fun findContentHeightMarker(): View? {
    // Always read the marker from the content, never from the surface.
    val contentView = findContentView() ?: return null
    // The marker is identified by the nativeID the JS side gives it, not by
    // its position: Fabric mounts siblings sorted by zIndex, so any content
    // child with a positive zIndex lands after the marker in the wrapper.
    return (0 until contentView.childCount)
      .map { contentView.getChildAt(it) }
      .firstOrNull { it.getTag(R.id.view_tag_native_id) == CONTENT_HEIGHT_MARKER_NATIVE_ID }
  }

  fun releaseContentHeightMarker() {
    contentHeightMarker?.removeOnLayoutChangeListener(contentHeightMarkerLayoutListener)
    contentHeightMarker = null
    surfaceView = null
  }

  private companion object {

    // Upper bound on pre-draw passes spent waiting for the initial content
    // detent to become measurable. This is a safety valve, not the completion
    // mechanism: the snap is driven by the marker layout listener and the
    // pre-draw observer, which normally resolve within a few frames. Because a
    // pending-but-unmeasurable content detent keeps re-invalidating via
    // updateScrim(), an unbounded observer would redraw every frame forever if
    // the content never produces a measurable height. The budget is generous
    // (covers pathologically slow content) but finite; once exhausted we stop
    // observing to end the redraw loop, while the marker layout listener can
    // still complete the snap if the content becomes measurable later.
    private const val MAX_PENDING_INITIAL_CONTENT_DETENT_FRAMES = 240
  }
}
