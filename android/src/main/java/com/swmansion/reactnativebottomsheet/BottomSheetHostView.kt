package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.facebook.react.bridge.Arguments
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.uimanager.RootView
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.views.view.ReactViewGroup
import kotlin.math.abs

private enum class DetentKind {
  POINTS,
  CONTENT,
}

private data class RawDetentSpec(val value: Float, val kind: DetentKind, val programmatic: Boolean)

private data class DetentSpec(val height: Float, val programmatic: Boolean)

interface BottomSheetViewListener {
  fun onIndexChange(index: Int)

  fun onSettle(index: Int)

  fun onPositionChange(position: Double, index: Double)
}

class BottomSheetHostView(context: Context) : ReactViewGroup(context) {

  // MARK: - Listener

  var listener: BottomSheetViewListener? = null

  // The state wrapper can arrive after geometry has already been derived
  // (mount ordering is not guaranteed), so flush the current snapshot and
  // re-derive on assignment; pushes dedup, so this is cheap when nothing was
  // pending.
  var stateWrapper: StateWrapper? = null
    set(value) {
      field = value
      if (value != null) {
        pushStateSnapshot()
        recomputeNativeGeometry()
      }
    }

  /**
   * Notified whenever the sheet's interactivity changes (true while it is animating, being dragged,
   * or showing its scrim). In native-overlay mode the coordinator uses this to toggle the host
   * dialog window's touchability so taps fall through to the screen behind while the sheet is
   * closed.
   */
  var interactionListener: ((Boolean) -> Unit)? = null

  // MARK: - State

  private var rawDetentSpecs: List<RawDetentSpec> = emptyList()
  private var detentSpecs: List<DetentSpec> = emptyList()
  private var targetIndex: Int = 0
  var animateIn: Boolean = true
  var animateContentHeight: Boolean = true
  var modal: Boolean = false
    set(value) {
      field = value
      updateInteractionState()
      updateScrim()
    }

  var disableScrollableNegotiation: Boolean = false
  private var pendingIndex: Int? = null
  private var hasLaidOut = false
  private var isPanning = false
  private var panStartingIndex: Int? = null
  private var activeDragRange: ClosedFloatingPointRange<Float>? = null
  private var activeDragDetentSpecs: List<DetentSpec>? = null

  // MARK: - Internal

  private val sheetContainer = FrameLayout(context)
  private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private var activeAnimation: SpringAnimation? = null
  private var activeAnimationEmitsSettle = false
  private var velocityTracker: VelocityTracker? = null
  private val density = context.resources.displayMetrics.density
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

  // Touch tracking
  private var initialTouchY = 0f
  private var initialTouchX = 0f
  private var lastTouchY = 0f
  private var activePointerId = MotionEvent.INVALID_POINTER_ID
  private var scrimPressed = false
  private var scrimTouchActive = false
  private var scrimColor = Color.TRANSPARENT
  // The JS layer always supplies a per-detent array; the fully-opaque fallback
  // only guards against empty input (indexing requires a non-empty array).
  private var scrimOpacities = listOf(1f)
  private var scrimProgress = 0f
  private var suppressScrimForClosingTarget = false
  private var scrimPinnedFull = false
  private var contentHeightMarker: View? = null
  private var surfaceView: View? = null
  private var pendingInitialContentDetentSnap = false
  private var pendingInitialContentDetentObserver: ViewTreeObserver? = null
  private var pendingInitialContentDetentPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
  private var pendingInitialContentDetentFrames = 0

  private val contentHeightMarkerLayoutListener =
    View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshDetentsFromLayout() }

  init {
    clipChildren = false
    clipToPadding = false
    // Set directly rather than via the JSX prop because Fabric doesn't forward
    // pointerEvents to the native view on Android. Without BOX_NONE the view
    // itself becomes a touch target and its onTouchEvent would claim gestures
    // that should go to children.
    pointerEvents = PointerEvents.BOX_NONE
    sheetContainer.clipChildren = false
    sheetContainer.clipToPadding = false
    super.addView(
      sheetContainer,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
    )
  }

  val sheetChildCount: Int
    get() = sheetContainer.childCount

  fun getSheetChildAt(index: Int): View? = sheetContainer.getChildAt(index)

  fun addSheetChild(child: View, index: Int) {
    sheetContainer.addView(child, index)
    refreshContentHeightMarker()
  }

  fun removeSheetChildAt(index: Int) {
    sheetContainer.removeViewAt(index)
    refreshContentHeightMarker()
  }

  // MARK: - Child view management

  override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
    if (child === sheetContainer) {
      super.addView(child, index, params)
    } else {
      sheetContainer.addView(child, index, params)
    }
  }

  override fun removeView(view: View) {
    if (view === sheetContainer) {
      super.removeView(view)
    } else {
      sheetContainer.removeView(view)
      refreshContentHeightMarker()
    }
  }

  override fun removeViewAt(index: Int) {
    sheetContainer.removeViewAt(index)
    refreshContentHeightMarker()
  }

  // MARK: - Layout

  // Fabric measures this view's direct children but never lays them out:
  // needsCustomLayoutForChildren() opts the sheet into owning their placement,
  // which only layoutSheetContainer performs. ReactViewGroup.requestLayout()
  // is a deliberate no-op terminator, so without this override the request
  // issued when a mounted child is added to sheetContainer dies here, no
  // traversal is scheduled, and a child mounted after the host's last layout
  // pass keeps 0x0 native bounds — Android hit-testing cannot descend into a
  // zero-bounds view, leaving the sheet content untappable (issue #48). The
  // placement pass is posted rather than run inline so it executes once, after
  // the current Fabric mount batch has measured the new children.
  private var sheetChildrenLayoutEnqueued = false
  private val sheetChildrenLayoutPass = Runnable {
    sheetChildrenLayoutEnqueued = false
    if (width > 0 && height > 0) {
      layoutSheetContainer(width, height)
    }
  }

  override fun requestLayout() {
    super.requestLayout()
    // The null check guards calls made during superclass construction (adding
    // sheetContainer in init), before this class's fields are initialized.
    @Suppress("SENSELESS_COMPARISON")
    if (sheetChildrenLayoutPass != null && !sheetChildrenLayoutEnqueued) {
      sheetChildrenLayoutEnqueued = true
      post(sheetChildrenLayoutPass)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    // Native geometry (cap, frame) is derived from the window; recompute on
    // (re)attach — including the inline<->overlay reparent — and ask for a
    // fresh insets pass.
    requestApplyInsets()
    recomputeNativeGeometry()
    // A re-attach gives us a fresh, live ViewTreeObserver; the previous one was
    // dropped on detach. Resume observing if the initial snap is still pending.
    if (pendingInitialContentDetentSnap) {
      observePendingInitialContentDetent()
    }
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    recomputeNativeGeometry()
  }

  override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
    // The cap depends on the top inset, which can change without a resize.
    recomputeNativeGeometry()
    return super.onApplyWindowInsets(insets)
  }

  override fun onDetachedFromWindow() {
    // Release the listener from the soon-to-be-replaced observer and clear our
    // references so a later re-attach registers on the new live observer.
    removePendingInitialContentDetentObserver()
    super.onDetachedFromWindow()
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    val w = right - left
    val h = bottom - top
    if (w <= 0 || h <= 0) return

    // The cap depends on this view's window position (top-inset overlap),
    // which can change without a resize.
    recomputeNativeGeometry()
    refreshContentHeightMarker()
    refreshDetentsFromLayout()
    layoutSheetContainer(w, h)

    if (!hasLaidOut && detentSpecs.isNotEmpty()) {
      val indexToApply = pendingIndex ?: targetIndex
      val clampedIndex = indexToApply.coerceIn(0, detentSpecs.size - 1)

      if (animateIn && isInvalidContentDetentTarget(clampedIndex)) {
        hasLaidOut = true
        pendingIndex = null
        targetIndex = clampedIndex
        pendingInitialContentDetentSnap = true
        sheetContainer.translationY = resolvedMaxDetentHeight(h)
        emitPosition()
        observePendingInitialContentDetent()
        return
      }

      hasLaidOut = true
      pendingIndex = null
      targetIndex = clampedIndex
      clearPendingInitialContentDetentSnap()

      if (animateIn) {
        val closedTy = resolvedMaxDetentHeight(h)
        sheetContainer.translationY = closedTy
        emitPosition()
        snapToIndex(targetIndex, 0f, emitIndexChange = false, emitSettle = true)
      } else {
        sheetContainer.translationY = translationY(targetIndex)
        emitPosition()
      }
      return
    }

    if (activeAnimation != null || isPanning) return
    sheetContainer.translationY = translationY(targetIndex)
    updateShadowState(sheetContainer.translationY)
  }

  override fun dispatchDraw(canvas: Canvas) {
    drawScrim(canvas)
    super.dispatchDraw(canvas)
  }

  private fun layoutSheetChildren(containerWidth: Int, containerHeight: Int) {
    for (i in 0 until sheetContainer.childCount) {
      val child = sheetContainer.getChildAt(i)
      if (child === surfaceView) {
        // The surface fills the full container so it always covers the visible
        // sheet (the container is translated to the current sheet position),
        // regardless of how short the content becomes.
        child.layout(0, 0, containerWidth, containerHeight)
      } else {
        child.layout(0, 0, child.measuredWidth, child.measuredHeight)
      }
    }
  }

  // The cap value the container geometry and translations are currently
  // anchored to. The native cap can change after a layout (insets arriving,
  // rotation), and translations computed against the old value must then be
  // re-anchored even when the resolved detent specs are unchanged — the
  // specs store heights, but translationY derives from the cap.
  private var lastAppliedMaxDetentHeight = Float.NaN

  private fun layoutSheetContainer(viewWidth: Int, viewHeight: Int) {
    val maxHeight = resolvedMaxDetentHeight(viewHeight)
    // Anchor the container bottom at the floating edge (lifted by bottomInsetPx),
    // not the raw view bottom.
    val containerTop = (viewHeight - bottomInsetPx - maxHeight).toInt()
    lastAppliedMaxDetentHeight = maxHeight
    sheetContainer.layout(0, containerTop, viewWidth, containerTop + maxHeight.toInt())
    layoutSheetChildren(viewWidth, maxHeight.toInt())
    if (bottomInsetPx > 0f) invalidateOutline()
  }

  // MARK: - Prop setters

  fun setDetents(raw: List<Map<String, Any>>) {
    rawDetentSpecs =
      raw.mapNotNull { dict ->
        val value = (dict["value"] as? Number)?.toDouble() ?: return@mapNotNull null
        val kind =
          when ((dict["kind"] as? String)?.lowercase()) {
            "content" -> DetentKind.CONTENT
            else -> DetentKind.POINTS
          }
        val programmatic = dict["programmatic"] as? Boolean ?: false
        RawDetentSpec(value = (value * density).toFloat(), kind = kind, programmatic = programmatic)
      }
    refreshDetentsFromLayout()
  }

  fun setIndex(newIndex: Int) {
    if (newIndex < 0) return

    if (!hasLaidOut) {
      pendingIndex = newIndex
      targetIndex = newIndex
      return
    }

    if (pendingInitialContentDetentSnap) {
      // The initial open is still deferred because the target content detent is
      // not measurable yet. Retarget the deferred snap so the requested index is
      // reflected, then either complete it now (e.g. a points detent that is
      // already resolvable) or keep waiting for the content to measure.
      targetIndex = newIndex.coerceIn(0, detentSpecs.size - 1)
      if (!trySnapPendingInitialContentDetent()) {
        observePendingInitialContentDetent()
      }
      return
    }

    if (newIndex >= detentSpecs.size || newIndex == targetIndex) return
    snapToIndex(newIndex, 0f, emitIndexChange = false)
  }

  fun setScrimColor(color: Int?) {
    scrimColor = color ?: Color.TRANSPARENT
    invalidate()
  }

  fun setScrimOpacities(values: List<Float>) {
    scrimOpacities = if (values.isEmpty()) listOf(1f) else values
    updateScrim()
  }

  // Stable coordinate base for the sheet container. The container is sized to
  // the full available height rather than the tallest detent, so it stays a
  // fixed-size canvas: when content — and thus the `content` detent — shrinks,
  // the container does not collapse underneath the sheet, leaving room to
  // animate the sheet down to its new height. The surface fills this canvas, so
  // the area below the shrunken content stays covered throughout.
  private fun resolvedMaxDetentHeight(viewHeight: Int = height): Float {
    val viewHeightPx = viewHeight.toFloat()
    // The cap is computed natively from this view's measured window geometry
    // (see recomputeNativeGeometry); before the first measure the full height
    // applies.
    if (!nativeCapPx.isFinite() || nativeCapPx <= 0f) {
      return viewHeightPx
    }
    return nativeCapPx.coerceIn(0f, viewHeightPx)
  }

  private fun resolveDetentSpecs(): List<DetentSpec> {
    val maxHeight = resolvedMaxDetentHeight()
    val measuredContentHeight =
      validContentHeight().takeIf { maxHeight > 0f && it.isFinite() }?.coerceAtMost(maxHeight)
    var previousHeight: Float? = null
    return rawDetentSpecs.mapIndexed { index, spec ->
      val height =
        when (spec.kind) {
          DetentKind.POINTS -> spec.value
          DetentKind.CONTENT ->
            measuredContentHeight ?: unresolvedContentDetentHeight(index, maxHeight)
        }.coerceIn(0f, maxHeight)
      previousHeight?.let {
        if (height < it) {
          throw IllegalArgumentException(
            "Invalid bottom sheet detent at index $index: resolved height ${height / density} is lower than previous detent height ${it / density}. Detents must be passed in ascending order."
          )
        }
      }
      previousHeight = height
      DetentSpec(height = height, programmatic = spec.programmatic)
    }
  }

  private fun unresolvedContentDetentHeight(index: Int, maxHeight: Float): Float {
    val nextPointHeight =
      rawDetentSpecs.drop(index + 1).firstOrNull { it.kind == DetentKind.POINTS }?.value
    return (nextPointHeight ?: maxHeight).coerceIn(0f, maxHeight)
  }

  private fun refreshDetentsFromLayout() {
    if (!isPanning) {
      activeDragRange = null
      activeDragDetentSpecs = null
    }
    if (hasLaidOut && isInvalidContentDetentTarget(targetIndex)) {
      updateScrim()
      return
    }

    val resolvedDetents = resolveDetentSpecs()
    if (resolvedDetents == detentSpecs && resolvedMaxDetentHeight() == lastAppliedMaxDetentHeight) {
      if (trySnapPendingInitialContentDetent()) {
        return
      }
      updateScrim()
      return
    }

    // The re-anchor math below preserves the on-screen sheet height across the
    // geometry change, so it must relate the current translation to the cap it
    // was computed against — not the freshly resolved one.
    val previousMaxHeight =
      if (lastAppliedMaxDetentHeight.isFinite()) lastAppliedMaxDetentHeight
      else resolvedMaxDetentHeight()
    // Whether the scrim is currently fully opaque, i.e. the sheet is settled at
    // or above the first non-zero detent. If so, a detent resize must not dip
    // the scrim while the sheet re-anchors to the new geometry.
    val wasScrimFull =
      modal &&
        firstNonZeroDetentHeight > 0f &&
        currentSheetHeight() + 0.5f >= firstNonZeroDetentHeight
    detentSpecs = resolvedDetents
    if (width > 0 && height > 0 && detentSpecs.isNotEmpty()) {
      layoutSheetContainer(width, height)

      if (hasLaidOut && !isPanning) {
        targetIndex = targetIndex.coerceIn(0, detentSpecs.size - 1)
        val newMaxHeight = resolvedMaxDetentHeight()
        val targetTy = translationY(targetIndex)
        if (trySnapPendingInitialContentDetent()) {
          return
        }
        if (activeAnimation != null && isTargetingClosedDetent) {
          suppressScrimForClosingTarget = true
          hideScrim()
        }
        if (activeAnimation != null) {
          val currentTy = sheetContainer.translationY
          val shouldEmitSettle = activeAnimationEmitsSettle
          activeAnimation?.cancel()
          activeAnimation = null
          activeAnimationEmitsSettle = false
          // Re-anchor the in-flight position to the new container height so the
          // sheet surface keeps the same on-screen height across the resize.
          val visibleHeight = previousMaxHeight - currentTy
          sheetContainer.translationY = (newMaxHeight - visibleHeight).coerceIn(0f, newMaxHeight)
          scrimPinnedFull = scrimPinnedFull || wasScrimFull
          emitPosition()
          snapToIndex(
            targetIndex,
            0f,
            emitIndexChange = false,
            emitSettle = shouldEmitSettle,
            preserveScrimPin = true,
          )
        } else {
          val currentVisibleHeight = previousMaxHeight - sheetContainer.translationY
          val targetHeight = detentSpecs.getOrNull(targetIndex)?.height ?: 0f
          val shouldAnimateHeight = shouldAnimateContentHeight(targetIndex)
          if (kotlin.math.abs(targetHeight - currentVisibleHeight) <= 0.5f) {
            // No meaningful change.
            sheetContainer.translationY = targetTy
            emitPosition()
          } else if (!shouldAnimateHeight) {
            sheetContainer.translationY = targetTy
            emitPosition()
          } else {
            // The content detent changed (grew or shrank): re-anchor at the
            // current visible height, then animate to the new target. The
            // surface covers the full sheet, so a shrink no longer exposes
            // blank space.
            sheetContainer.translationY =
              (newMaxHeight - currentVisibleHeight).coerceIn(0f, newMaxHeight)
            scrimPinnedFull = scrimPinnedFull || wasScrimFull
            emitPosition()
            snapToIndex(
              targetIndex,
              0f,
              emitIndexChange = false,
              emitSettle = false,
              preserveScrimPin = true,
            )
          }
        }
      }
    }

    requestLayout()
    updateScrim()
  }

  private fun shouldAnimateContentHeight(index: Int): Boolean =
    animateContentHeight || rawDetentSpecs.getOrNull(index)?.kind != DetentKind.CONTENT

  private fun currentContentHeight(): Float {
    val marker = contentHeightMarker ?: return Float.NaN
    return marker.top.toFloat()
  }

  private fun validContentHeight(): Float {
    return currentContentHeight().takeIf { it.isFinite() && it > 0f } ?: Float.NaN
  }

  private fun isInvalidContentDetentTarget(index: Int): Boolean {
    return rawDetentSpecs.getOrNull(index)?.kind == DetentKind.CONTENT &&
      !validContentHeight().isFinite()
  }

  private fun trySnapPendingInitialContentDetent(): Boolean {
    if (!pendingInitialContentDetentSnap || isInvalidContentDetentTarget(targetIndex)) {
      return false
    }

    pendingInitialContentDetentSnap = false
    removePendingInitialContentDetentObserver()
    snapToIndex(targetIndex, 0f, emitIndexChange = false, emitSettle = true)
    return true
  }

  private fun clearPendingInitialContentDetentSnap() {
    pendingInitialContentDetentSnap = false
    removePendingInitialContentDetentObserver()
  }

  private fun observePendingInitialContentDetent() {
    if (pendingInitialContentDetentPreDrawListener != null) return

    val observer = viewTreeObserver
    if (!observer.isAlive) return

    pendingInitialContentDetentFrames = 0
    val listener =
      ViewTreeObserver.OnPreDrawListener {
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

  private fun removePendingInitialContentDetentObserver() {
    val observer = pendingInitialContentDetentObserver
    val listener = pendingInitialContentDetentPreDrawListener

    if (observer?.isAlive == true && listener != null) {
      observer.removeOnPreDrawListener(listener)
    }

    pendingInitialContentDetentObserver = null
    pendingInitialContentDetentPreDrawListener = null
  }

  private fun refreshContentHeightMarker() {
    surfaceView = findSurfaceView()
    val marker = findContentHeightMarker()
    if (marker === contentHeightMarker) return
    contentHeightMarker?.removeOnLayoutChangeListener(contentHeightMarkerLayoutListener)
    contentHeightMarker = marker
    contentHeightMarker?.addOnLayoutChangeListener(contentHeightMarkerLayoutListener)
  }

  private fun findSurfaceView(): View? {
    for (i in 0 until sheetContainer.childCount) {
      val child = sheetContainer.getChildAt(i)
      if (child is BottomSheetSurfaceView) return child
    }
    return null
  }

  private fun findContentHeightMarker(): View? {
    // The surface is a sibling of the content wrapper; skip it so the marker is
    // always read from the content, never from the surface.
    val contentView =
      (0 until sheetContainer.childCount)
        .map { sheetContainer.getChildAt(it) }
        .firstOrNull { it !== surfaceView } as? ViewGroup ?: return null
    if (contentView.childCount == 0) return null
    return contentView.getChildAt(contentView.childCount - 1)
  }

  // MARK: - Snap logic

  private fun translationY(index: Int): Float {
    val maxHeight = resolvedMaxDetentHeight()
    val snapHeight = detentSpecs.getOrNull(index)?.height ?: 0f
    return maxHeight - snapHeight
  }

  private val minDetentTranslationY: Float
    get() = detentSpecs.indices.minOfOrNull(::translationY) ?: 0f

  private val maxDetentTranslationY: Float
    get() = detentSpecs.indices.maxOfOrNull(::translationY) ?: 0f

  private val closedIndex: Int?
    get() = detentSpecs.indexOfFirst { it.height == 0f }.takeIf { it >= 0 }

  private val scrimDismissIndex: Int?
    get() = closedIndex?.takeIf { !detentSpecs[it].programmatic }

  private val firstNonZeroDetentHeight: Float
    get() = detentSpecs.firstOrNull { it.height > 0f }?.height ?: 0f

  private val isTargetingClosedDetent: Boolean
    get() = closedIndex?.let { targetIndex == it } == true

  private fun snapCandidateIndices(includeIndex: Int? = null): List<Int> {
    val indices = detentSpecs.indices.filter { !detentSpecs[it].programmatic }.toMutableList()
    if (
      includeIndex != null &&
        includeIndex in detentSpecs.indices &&
        detentSpecs[includeIndex].programmatic
    ) {
      indices.add(includeIndex)
    }
    return indices.distinct().sortedBy { detentSpecs[it].height }
  }

  private fun draggableRange(includeIndex: Int? = null): ClosedFloatingPointRange<Float> {
    val candidates = snapCandidateIndices(includeIndex)
    if (candidates.isEmpty()) return 0f..0f
    val translations = candidates.map(::translationY)
    return (translations.minOrNull() ?: 0f)..(translations.maxOrNull() ?: 0f)
  }

  private fun snapshotTranslationY(index: Int, specs: List<DetentSpec>): Float {
    val maxHeight = resolvedMaxDetentHeight()
    val snapHeight = specs.getOrNull(index)?.height ?: 0f
    return maxHeight - snapHeight
  }

  private fun snapshotCandidateIndices(includeIndex: Int?, specs: List<DetentSpec>): List<Int> {
    val indices = specs.indices.filter { !specs[it].programmatic }.toMutableList()
    if (includeIndex != null && includeIndex in specs.indices && specs[includeIndex].programmatic) {
      indices.add(includeIndex)
    }
    return indices.distinct().sortedBy { specs[it].height }
  }

  private fun snapshotDraggableRange(
    includeIndex: Int?,
    specs: List<DetentSpec>,
  ): ClosedFloatingPointRange<Float> {
    val candidates = snapshotCandidateIndices(includeIndex, specs)
    if (candidates.isEmpty()) return 0f..0f
    val translations = candidates.map { snapshotTranslationY(it, specs) }
    return (translations.minOrNull() ?: 0f)..(translations.maxOrNull() ?: 0f)
  }

  private fun isAtMaxDragCandidate(includeIndex: Int? = null): Boolean {
    val range = draggableRange(includeIndex)
    return sheetContainer.translationY <= range.start + 1f
  }

  private fun emitPosition() {
    val maxHeight = resolvedMaxDetentHeight()
    val ty = sheetContainer.translationY
    val position = maxHeight - ty
    updateScrim(position)
    updateSheetVisibility(position)
    updateInteractionState()
    listener?.onPositionChange((position / density).toDouble(), detentIndexAt(position).toDouble())
    updateShadowState(ty)
  }

  // Fractional detent index in 0..(detentSpecs.size - 1): 0 at the shortest
  // detent, 1 at the next, and so on, interpolated by position in between. The
  // continuous counterpart of `onIndexChange`, so consumers can drive a backdrop
  // or animate per detent without knowing the sheet's height.
  private fun detentIndexAt(position: Float): Float =
    interpolateAtPosition(position, detentSpecs.indices.map { it.toFloat() })

  private fun updateSheetVisibility(position: Float) {
    sheetContainer.alpha = if (position <= 0.5f) 0f else 1f
  }

  private var lastShadowOffsetY = Float.NaN
  private var lastGeometryStateWidth = 0
  private var lastGeometryStateHeight = 0
  private var lastGeometryStateInsetTop = Float.NaN
  // The natively computed detent cap: this view's height minus the part of the
  // window's top (status bar / display cutout) inset that actually overlaps it.
  // Measured from real window geometry in every mode — inline, portal, and
  // overlay — so no JS-estimated dimensions are involved. NaN until the view is
  // attached and sized, in which case the full height applies.
  private var nativeCapPx = Float.NaN

  var extendUnderStatusBar: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      recomputeNativeGeometry()
    }

  // Floats the sheet up off the bottom edge by this many px — a detached /
  // "floating card" sheet. The detent cap shrinks so the sheet stays inside the
  // region above the inset, and the floating bottom edge is clipped + rounded
  // via `cornerRadiusPx`. Stored in px (the setter converts from dp).
  var bottomInsetPx: Float = 0f
    private set

  fun setBottomInset(dp: Float) {
    val px = dp * density
    if (bottomInsetPx == px) return
    bottomInsetPx = px
    updateDetachedClip()
    recomputeNativeGeometry()
    requestLayout()
  }

  // Corner radius (px) for the detached sheet's floating bottom corners.
  var cornerRadiusPx: Float = 0f
    private set

  fun setCornerRadius(dp: Float) {
    val px = dp * density
    if (cornerRadiusPx == px) return
    cornerRadiusPx = px
    invalidateOutline()
  }

  // The Y the sheet's bottom edge is anchored to: this view's bottom edge,
  // lifted by `bottomInsetPx` for a detached sheet.
  private val sheetBottomAnchor: Float
    get() = (height - bottomInsetPx).coerceAtLeast(0f)

  // Clips the over-sized surface canvas to the floating bottom edge and rounds
  // its bottom corners, so a detached sheet reads as a card floating above the
  // bottom. A no-op (clip off) when anchored, preserving the anchored sheet's
  // slide-off-screen close. Clips this host, so a native scrim (if any) is
  // clipped with it — detached sheets should use an external backdrop.
  private fun updateDetachedClip() {
    val detached = bottomInsetPx > 0f
    clipToOutline = detached
    if (detached && outlineProvider !is DetachedOutlineProvider) {
      outlineProvider = DetachedOutlineProvider()
    }
    invalidateOutline()
  }

  private inner class DetachedOutlineProvider : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
      val anchor = sheetBottomAnchor.toInt().coerceAtLeast(0)
      // Round the bottom corners at the floating edge; the top corners sit at
      // y=0 above the (capped) content, where the surface owns the rounding.
      outline.setRoundRect(0, 0, view.width, anchor, cornerRadiusPx)
    }
  }

  /**
   * Re-derives all natively measured geometry from this view's own size, window position, and the
   * window's top inset, then pushes it into the shadow tree: the sheet frame (consumed by the
   * component descriptor only in overlay mode, exactly as <Modal> does) and the content region's
   * top inset (applied as Yoga top padding in every mode, so in-flow content lays out exactly
   * within the detent cap). Yoga thus lays the sheet subtree out against real window geometry
   * instead of JS-provided dimensions (issue #48). In overlay mode this view fills the dialog, so
   * its own metrics are the dialog's.
   */
  private fun recomputeNativeGeometry() {
    if (width <= 0 || height <= 0 || !isAttachedToWindow) return

    val topInset =
      ViewCompat.getRootWindowInsets(this)
        ?.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
        ?.top ?: 0
    val location = IntArray(2)
    getLocationInWindow(location)
    // Only the part of the inset this view actually extends under matters: a
    // sheet inside a container that starts below the status bar keeps its full
    // height.
    val topOverlap = (topInset - location[1]).coerceAtLeast(0)
    // Anchor the cap to the (possibly lifted) floating bottom, not the raw view
    // bottom, so a detached sheet stays inside the region above the inset.
    val anchor = (height - bottomInsetPx).coerceAtLeast(0f)
    val cap =
      (if (extendUnderStatusBar) anchor else anchor - topOverlap).coerceAtLeast(0f)

    val capChanged = cap != nativeCapPx
    nativeCapPx = cap
    pushGeometryState(width, height, height - cap)
    if (capChanged) {
      refreshDetentsFromLayout()
      requestLayout()
    }
  }

  private fun pushGeometryState(widthPx: Int, heightPx: Int, insetTopPx: Float) {
    if (
      widthPx == lastGeometryStateWidth &&
        heightPx == lastGeometryStateHeight &&
        insetTopPx == lastGeometryStateInsetTop
    ) {
      return
    }
    lastGeometryStateWidth = widthPx
    lastGeometryStateHeight = heightPx
    lastGeometryStateInsetTop = insetTopPx
    pushStateSnapshot()
  }

  /**
   * Pushes the complete state — geometry and content offset together — in every update. Android's
   * state-update path builds the new C++ state data from a base captured at call time and replaces
   * it wholesale at commit time, so partial payloads from independent sources can wipe each other's
   * keys when updates race within a commit window. Complete snapshots make the replacement harmless
   * regardless of ordering.
   */
  private fun pushStateSnapshot() {
    val sw = stateWrapper ?: return
    val map = Arguments.createMap()
    map.putDouble(
      "contentOffsetY",
      if (lastShadowOffsetY.isNaN()) 0.0 else lastShadowOffsetY.toDouble(),
    )
    map.putDouble("frameWidth", (lastGeometryStateWidth / density).toDouble())
    map.putDouble("frameHeight", (lastGeometryStateHeight / density).toDouble())
    map.putDouble(
      "contentRegionInset",
      ((if (lastGeometryStateInsetTop.isNaN()) 0f else lastGeometryStateInsetTop) / density)
        .toDouble(),
    )
    sw.updateState(map)
  }

  private fun updateShadowState(translationY: Float) {
    val maxDetentHeight = resolvedMaxDetentHeight()
    val containerTop = (height - bottomInsetPx) - maxDetentHeight
    // The content's in-host displacement from its Yoga position: the container
    // offset plus the sheet's translation. The content-region inset shrinks
    // the content via Yoga BOTTOM padding, keeping the Yoga origin at zero, so
    // the full displacement is carried here.
    val offsetY = ((containerTop + translationY) / density).toDouble()
    if (offsetY.toFloat() == lastShadowOffsetY) return
    lastShadowOffsetY = offsetY.toFloat()
    pushStateSnapshot()
  }

  // MARK: - Spring animation

  private fun snapToIndex(
    index: Int,
    velocity: Float,
    emitIndexChange: Boolean = true,
    emitSettle: Boolean = true,
    preserveScrimPin: Boolean = false,
  ) {
    if (index < 0 || index >= detentSpecs.size) return
    targetIndex = index
    if (!isTargetingClosedDetent) {
      suppressScrimForClosingTarget = false
    }
    if (!preserveScrimPin) {
      scrimPinnedFull = false
    }

    val targetTy = translationY(index)
    activeAnimationEmitsSettle = emitSettle
    activeAnimation?.cancel()

    val currentTy = sheetContainer.translationY

    val minAnimationTy = minOf(minDetentTranslationY, currentTy, targetTy)
    val maxAnimationTy = maxOf(maxDetentTranslationY, currentTy, targetTy)
    val distance = targetTy - currentTy
    val velocityRatio = if (distance != 0f) velocity / distance else 0f
    val initialVelocity = velocityRatio.coerceIn(-5f, 5f) * distance

    val spring =
      SpringAnimation(sheetContainer, DynamicAnimation.TRANSLATION_Y, targetTy).apply {
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
          activeAnimation = null
          activeAnimationEmitsSettle = false
          suppressScrimForClosingTarget = false
          scrimPinnedFull = false
          if (closedIndex == index) {
            sheetContainer.translationY = translationY(index)
            hideScrim()
          }
          emitPosition()
          updateInteractionState()
          if (emitSettle) listener?.onSettle(index)
        }
      }

    activeAnimation = spring
    // Report the index change as soon as the snap is committed, not when it
    // finishes: targetIndex is already set, and a programmatic snap's start is
    // known to the caller. onSettle remains the signal for movement end.
    if (emitIndexChange) listener?.onIndexChange(index)
    spring.start()
  }

  private fun bestSnapIndex(currentHeight: Float, velocity: Float, includeIndex: Int? = null): Int {
    val candidates = snapCandidateIndices(includeIndex)
    if (candidates.isEmpty()) return targetIndex

    val flickThreshold = 600f * density

    if (velocity < -flickThreshold) {
      return candidates.firstOrNull { detentSpecs[it].height > currentHeight }
        ?: candidates.lastOrNull()
        ?: targetIndex
    }
    if (velocity > flickThreshold) {
      return candidates.lastOrNull { detentSpecs[it].height < currentHeight }
        ?: candidates.firstOrNull()
        ?: targetIndex
    }

    return candidates.minByOrNull { abs(detentSpecs[it].height - currentHeight) } ?: targetIndex
  }

  private fun snapshotBestSnapIndex(
    currentHeight: Float,
    velocity: Float,
    includeIndex: Int?,
    specs: List<DetentSpec>,
  ): Int {
    val candidates = snapshotCandidateIndices(includeIndex, specs)
    if (candidates.isEmpty()) return targetIndex

    val flickThreshold = 600f * density

    if (velocity < -flickThreshold) {
      return candidates.firstOrNull { specs[it].height > currentHeight }
        ?: candidates.lastOrNull()
        ?: targetIndex
    }
    if (velocity > flickThreshold) {
      return candidates.lastOrNull { specs[it].height < currentHeight }
        ?: candidates.firstOrNull()
        ?: targetIndex
    }

    return candidates.minByOrNull { abs(specs[it].height - currentHeight) } ?: targetIndex
  }

  // MARK: - Touch handling

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    val sheetTop = sheetContainer.top + sheetContainer.translationY
    if (event.actionMasked == MotionEvent.ACTION_DOWN && event.y < sheetTop) {
      if (isScrimVisible()) {
        initialTouchX = event.x
        initialTouchY = event.y
        lastTouchY = event.y
        activePointerId = event.getPointerId(0)
        scrimPressed = true
        scrimTouchActive = true
        return true
      }
      return false
    }

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        initialTouchX = event.x
        initialTouchY = event.y
        lastTouchY = event.y
        activePointerId = event.getPointerId(0)
      }
      MotionEvent.ACTION_MOVE -> {
        if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) return false
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        val dx = x - initialTouchX
        val dy = y - initialTouchY

        val dragRange = draggableRange(targetIndex)
        if (abs(dy) > touchSlop && abs(dy) > abs(dx) && dragRange.start < dragRange.endInclusive) {
          if (disableScrollableNegotiation && findScrollableAtTouch() != null) {
            return false
          }
          if (!isAtMaxDragCandidate(targetIndex)) {
            lastTouchY = y
            announceGestureTakeover(event)
            return true
          }
          if (dy > 0 && isScrollViewAtTop()) {
            lastTouchY = y
            announceGestureTakeover(event)
            return true
          }
        }
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        initialTouchX = 0f
        initialTouchY = 0f
        activePointerId = MotionEvent.INVALID_POINTER_ID
        scrimPressed = false
        scrimTouchActive = false
      }
    }
    return false
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (scrimTouchActive) {
      when (event.actionMasked) {
        MotionEvent.ACTION_MOVE -> {
          val sheetTop = sheetContainer.top + sheetContainer.translationY
          if (event.y >= sheetTop || abs(event.y - initialTouchY) > touchSlop) {
            scrimPressed = false
          }
          return true
        }
        MotionEvent.ACTION_UP -> {
          val closeIndex = scrimDismissIndex
          val shouldDismiss = scrimPressed && isScrimVisible()
          scrimPressed = false
          scrimTouchActive = false
          activePointerId = MotionEvent.INVALID_POINTER_ID
          if (shouldDismiss && closeIndex != null) {
            snapToIndex(closeIndex, 0f)
          }
          return true
        }
        MotionEvent.ACTION_CANCEL -> {
          scrimPressed = false
          scrimTouchActive = false
          activePointerId = MotionEvent.INVALID_POINTER_ID
          return true
        }
        MotionEvent.ACTION_POINTER_UP -> {
          return true
        }
      }
    }

    when (event.actionMasked) {
      MotionEvent.ACTION_MOVE -> {
        if (!isPanning) beginPan(event)
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) return true
        val y = event.getY(pointerIndex)
        velocityTracker?.addMovement(event)
        val dy = y - lastTouchY
        lastTouchY = y

        val dragRange = activeDragRange ?: draggableRange(panStartingIndex)
        val newTy =
          (sheetContainer.translationY + dy).coerceIn(dragRange.start, dragRange.endInclusive)
        sheetContainer.translationY = newTy
        emitPosition()
        return true
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        isPanning = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        val velocity =
          velocityTracker?.let { tracker ->
            tracker.computeCurrentVelocity(1000)
            val v = tracker.yVelocity
            tracker.recycle()
            v
          } ?: 0f
        velocityTracker = null
        val maxHeight = resolvedMaxDetentHeight()
        val currentHeight = maxHeight - sheetContainer.translationY
        val startingIndex = panStartingIndex
        val index =
          activeDragDetentSpecs
            ?.takeIf { it.size == detentSpecs.size }
            ?.let { snapshotBestSnapIndex(currentHeight, velocity, startingIndex, it) }
            ?: bestSnapIndex(currentHeight, velocity, startingIndex)
        panStartingIndex = null
        activeDragRange = null
        activeDragDetentSpecs = null
        snapToIndex(index, velocity)
        return true
      }
      MotionEvent.ACTION_POINTER_UP -> {
        val actionIndex = event.actionIndex
        if (event.getPointerId(actionIndex) == activePointerId) {
          val newPointerIndex = if (actionIndex == 0) 1 else 0
          activePointerId = event.getPointerId(newPointerIndex)
          lastTouchY = event.getY(newPointerIndex)
          velocityTracker?.clear()
        }
        return true
      }
    }
    return super.onTouchEvent(event)
  }

  private fun beginPan(event: MotionEvent) {
    isPanning = true
    scrimPinnedFull = false
    panStartingIndex = targetIndex
    val dragDetentSpecs = detentSpecs.toList()
    activeDragDetentSpecs = dragDetentSpecs
    activeDragRange = snapshotDraggableRange(targetIndex, dragDetentSpecs)
    activePointerId = event.getPointerId(0)
    lastTouchY = event.y
    velocityTracker?.recycle()
    velocityTracker = VelocityTracker.obtain()
    velocityTracker?.addMovement(event)
    activeAnimation?.let {
      it.cancel()
      activeAnimation = null
    }
  }

  // Announce to ancestors that the sheet is taking over the touch stream. The
  // press "decider" for any child lives above us, so both signals travel up:
  //   - onChildStartedNativeGesture cancels React Native's own JS touch
  //     pipeline (JSTouchDispatcher), covering Pressable/Touchable.
  //   - requestDisallowInterceptTouchEvent(true) is the standard Android
  //     handshake honored by ancestor gesture systems such as
  //     react-native-gesture-handler's orchestrator, which would otherwise
  //     still recognize a tap on a native button mid-drag. The flag is cleared
  //     automatically on the next ACTION_DOWN.
  private fun announceGestureTakeover(event: MotionEvent) {
    notifyNativeGestureStarted(event)
    requestDisallowInterceptTouchEvent(true)
  }

  private fun notifyNativeGestureStarted(event: MotionEvent) {
    findReactRootView()?.onChildStartedNativeGesture(this, event)
  }

  private fun findReactRootView(): RootView? {
    var current: View = this
    while (true) {
      if (current is RootView) return current
      current = current.parent as? View ?: return null
    }
  }

  // MARK: - Scroll view helpers
  //
  // Explicit scroll-view detection is required because Android's touch dispatch
  // doesn't support mid-gesture handoff. Once a ScrollView claims a gesture it
  // keeps it for the entire sequence, and it always claims (returns true from
  // onTouchEvent) even when at the scroll boundary. Without this check the sheet
  // could never collapse by dragging down when a ScrollView is at the top.

  private fun isScrollViewAtTop(): Boolean {
    val scrollView = findScrollableAtTouch() ?: return true
    val inverted = isViewInverted(scrollView)
    return if (inverted) !scrollView.canScrollVertically(1) else !scrollView.canScrollVertically(-1)
  }

  private fun findScrollableAtTouch(): View? {
    val containerX = initialTouchX - sheetContainer.left - sheetContainer.translationX
    val containerY = initialTouchY - sheetContainer.top - sheetContainer.translationY
    if (
      containerX < 0f ||
        containerX >= sheetContainer.width ||
        containerY < 0f ||
        containerY >= sheetContainer.height
    ) {
      return null
    }
    return findScrollableAtPoint(sheetContainer, containerX, containerY)
  }

  private fun findScrollableAtPoint(view: View, x: Float, y: Float): View? {
    if (!view.isShown) return null

    if (view is ViewGroup) {
      for (i in view.childCount - 1 downTo 0) {
        val child = view.getChildAt(i)
        val childX = x - child.left - child.translationX
        val childY = y - child.top - child.translationY
        if (childX < 0f || childX >= child.width || childY < 0f || childY >= child.height) {
          continue
        }
        findScrollableAtPoint(child, childX, childY)?.let {
          return it
        }
      }
    }

    if (isVerticallyScrollable(view)) {
      return view
    }
    return null
  }

  private fun isVerticallyScrollable(view: View): Boolean {
    if (!view.canScrollVertically(1) && !view.canScrollVertically(-1)) {
      return false
    }
    return getReactScrollEnabled(view) != false
  }

  private fun getReactScrollEnabled(view: View): Boolean? {
    val method =
      try {
        view.javaClass.getMethod("getScrollEnabled")
      } catch (_: NoSuchMethodException) {
        return null
      }

    if (
      method.returnType != Boolean::class.javaPrimitiveType &&
        method.returnType != Boolean::class.javaObjectType
    ) {
      return null
    }

    return try {
      method.isAccessible = true
      method.invoke(view) as? Boolean
    } catch (_: Exception) {
      null
    }
  }

  private fun isViewInverted(view: View): Boolean {
    val values = FloatArray(9)
    var current: View? = view
    while (current != null && current !== sheetContainer) {
      if (!current.matrix.isIdentity) {
        current.matrix.getValues(values)
        if (values[android.graphics.Matrix.MSCALE_Y] < 0) return true
      }
      current = current.parent as? View
    }
    return false
  }

  // MARK: - Cleanup

  fun destroy() {
    activeAnimation?.cancel()
    activeAnimation = null
    velocityTracker?.recycle()
    velocityTracker = null
    removeCallbacks(sheetChildrenLayoutPass)
    sheetChildrenLayoutEnqueued = false
    nativeCapPx = Float.NaN
    lastAppliedMaxDetentHeight = Float.NaN
    lastGeometryStateWidth = 0
    lastGeometryStateHeight = 0
    lastGeometryStateInsetTop = Float.NaN
    clearPendingInitialContentDetentSnap()
    contentHeightMarker?.removeOnLayoutChangeListener(contentHeightMarkerLayoutListener)
    contentHeightMarker = null
    surfaceView = null
    rawDetentSpecs = emptyList()
    detentSpecs = emptyList()
    targetIndex = 0
    pendingIndex = null
    hasLaidOut = false
    isPanning = false
    panStartingIndex = null
    activeDragRange = null
    activeDragDetentSpecs = null
    initialTouchY = 0f
    initialTouchX = 0f
    lastTouchY = 0f
    activePointerId = MotionEvent.INVALID_POINTER_ID
    scrimPressed = false
    scrimTouchActive = false
    scrimProgress = 0f
    suppressScrimForClosingTarget = false
    sheetContainer.removeAllViews()
    stateWrapper = null
    lastShadowOffsetY = Float.NaN
  }

  private fun updateScrim(position: Float = currentSheetHeight()) {
    if (!modal) {
      scrimProgress = 0f
      invalidate()
      return
    }

    // When settled at the closed detent, dynamic content updates can briefly
    // produce stale non-zero positions. Keep scrim hidden in this state.
    if (
      (isTargetingClosedDetent && activeAnimation == null && !isPanning) ||
        (suppressScrimForClosingTarget && isTargetingClosedDetent)
    ) {
      hideScrim()
      return
    }

    // While the sheet is fully open and only its content/detent geometry is
    // resizing, the position momentarily lags the grown detent height. Keep the
    // scrim pinned to the fully-open opacity instead of dipping it until the
    // re-anchor settles.
    if (scrimPinnedFull) {
      scrimProgress = fullyOpenScrimOpacity()
      invalidate()
      return
    }

    scrimProgress = scrimOpacityAt(position)
    invalidate()
  }

  /** The opacity at the tallest detent, held while the sheet re-anchors. */
  private fun fullyOpenScrimOpacity(): Float {
    val maxHeight = detentSpecs.maxOfOrNull { it.height } ?: return 1f
    return scrimOpacityAt(maxHeight)
  }

  /**
   * Interpolates the scrim opacity for a sheet height by bracketing it between adjacent detent
   * heights and lerping each detent index's configured value.
   */
  private fun scrimOpacityAt(position: Float): Float =
    interpolateAtPosition(
      position,
      detentSpecs.indices.map {
        scrimOpacities[it.coerceAtMost(scrimOpacities.size - 1)].coerceIn(0f, 1f)
      },
    )

  // Interpolates a per-detent value (one per detent, by index) by the sheet
  // position, using each detent's resolved height as the breakpoint.
  private fun interpolateAtPosition(position: Float, values: List<Float>): Float {
    if (detentSpecs.isEmpty()) return 0f
    val pairs =
      detentSpecs.indices.map { detentSpecs[it].height to values[it] }.sortedBy { it.first }

    val first = pairs.first()
    val last = pairs.last()
    if (position <= first.first) return first.second
    if (position >= last.first) return last.second

    for (i in 1 until pairs.size) {
      val upper = pairs[i]
      if (position <= upper.first) {
        val lower = pairs[i - 1]
        val span = upper.first - lower.first
        val t = if (span <= 0f) 1f else (position - lower.first) / span
        return lower.second + (upper.second - lower.second) * t
      }
    }
    return last.second
  }

  private fun hideScrim() {
    scrimProgress = 0f
    invalidate()
  }

  private fun updateInteractionState() {
    val interactive = modal && (activeAnimation != null || isPanning || isScrimVisible())
    pointerEvents = if (interactive) PointerEvents.AUTO else PointerEvents.BOX_NONE
    interactionListener?.invoke(interactive)
  }

  private fun currentSheetHeight(): Float {
    val maxHeight = resolvedMaxDetentHeight()
    return maxHeight - sheetContainer.translationY
  }

  private fun isScrimVisible(): Boolean = modal && scrimProgress > 0.001f

  private fun drawScrim(canvas: Canvas) {
    if (!modal || scrimProgress <= 0.001f) {
      return
    }

    val alpha = (Color.alpha(scrimColor) * scrimProgress).toInt().coerceIn(0, 255)
    scrimPaint.color =
      Color.argb(alpha, Color.red(scrimColor), Color.green(scrimColor), Color.blue(scrimColor))
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
  }

  companion object {
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
