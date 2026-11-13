package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.ViewCompat
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.views.view.ReactViewGroup
import com.swmansion.reactnativebottomsheet.closerequest.CloseRequestPresentationTracker
import com.swmansion.reactnativebottomsheet.host.Card
import com.swmansion.reactnativebottomsheet.host.ContentRegion
import com.swmansion.reactnativebottomsheet.host.DetentInsets
import com.swmansion.reactnativebottomsheet.host.Detents
import com.swmansion.reactnativebottomsheet.host.Gestures
import com.swmansion.reactnativebottomsheet.host.Scrim
import com.swmansion.reactnativebottomsheet.host.ScrollCoordination
import com.swmansion.reactnativebottomsheet.host.SheetHost
import com.swmansion.reactnativebottomsheet.host.SheetState
import com.swmansion.reactnativebottomsheet.host.Snapping

interface BottomSheetViewListener {
  fun onIndexChange(index: Int)

  fun onSettle(index: Int)

  fun onPositionChange(position: Double, index: Double)

  fun onCloseRequest()
}

class BottomSheetHostView(context: Context) : ReactViewGroup(context), NestedScrollingParent3 {

  // MARK: - Listener

  var listener: BottomSheetViewListener? = null

  var stateWrapper: StateWrapper?
    get() = contentRegion.stateWrapper
    set(value) {
      contentRegion.stateWrapper = value
    }

  /**
   * Notified whenever the sheet's interactivity changes (true while it is animating, being dragged,
   * or presented at an open detent). In window-overlay mode the coordinator uses this to toggle the
   * host dialog window's touchability so taps fall through to the screen behind while the sheet is
   * closed.
   */
  var interactionListener: ((Boolean) -> Unit)? = null

  /**
   * Invokes the callback immediately when assigned and whenever later host state can change close
   * request qualification.
   */
  var closeRequestStateChangedListener: (() -> Unit)? = null
    set(value) {
      field = value
      value?.invoke()
    }

  // MARK: - State

  var animateIn: Boolean = true
  var animateContentHeight: Boolean
    get() = state.animateContentHeight
    set(value) {
      state.animateContentHeight = value
    }

  var modal: Boolean
    get() = state.modal
    set(value) {
      state.modal = value
      // The scrim first: the Dismiss button refreshed by updateInteractionState reads it.
      scrim.updateScrim()
      updateInteractionState()
    }

  var scrollExpandCoordination: Int
    get() = state.scrollExpandCoordination
    set(value) {
      state.scrollExpandCoordination = value
    }

  var scrollCollapseCoordination: Int
    get() = state.scrollCollapseCoordination
    set(value) {
      state.scrollCollapseCoordination = value
    }

  var settleDuration: Float
    get() = state.settleDuration
    set(value) {
      state.settleDuration = value
    }

  // MARK: - Internal

  private val sheetContainer = BottomSheetCardContainer(context)
  private val clipContainer = BottomSheetCardClipLayout(context)
  private val state = SheetState(this, sheetContainer)
  private val sheetHost: SheetHost =
    object : SheetHost {
      override val listener: BottomSheetViewListener?
        get() = this@BottomSheetHostView.listener

      override fun notifyCloseRequestStateChanged() =
        this@BottomSheetHostView.notifyCloseRequestStateChanged()

      override fun performCloseRequest() = this@BottomSheetHostView.performCloseRequest()

      override fun updateInteractionState() = this@BottomSheetHostView.updateInteractionState()

      override fun layoutSheetContainer(viewWidth: Int, viewHeight: Int) =
        this@BottomSheetHostView.layoutSheetContainer(viewWidth, viewHeight)

      override fun refreshDetentsFromLayout() = detents.refreshDetentsFromLayout()
    }
  private val scrim = Scrim(state)
  private val card = Card(state, clipContainer, scrim)
  private val cardTouchSensor = BottomSheetCardTouchSensor(context, card::onTouchTargeting)

  private var hasPerformedHostLayoutSinceAttach = false
  private val closeRequestPresentationTracker = CloseRequestPresentationTracker()
  private val contentRegion = ContentRegion(state, card, sheetHost)
  private val snapping =
    Snapping(state, scrim, card, contentRegion, closeRequestPresentationTracker, sheetHost)
  private val detents: Detents =
    Detents(
      state,
      clipContainer,
      scrim,
      contentRegion,
      snapping,
      closeRequestPresentationTracker,
      sheetHost,
    )
  private val scrollCoordination = ScrollCoordination(state, snapping)
  private val gestures = Gestures(state, card, scrim, snapping, scrollCoordination)
  private val scrimDismissView =
    ScrimDismissView(
      context,
      performDismiss = ::attemptAccessibleDismissal,
      traversalAfter = sheetContainer,
    )

  init {
    scrim.onVisibilityChanged = ::notifyCloseRequestStateChanged
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
    // Below the clip container, so the clip never cuts the shadows.
    for (shadowView in card.shadowViews) {
      sheetContainer.addView(
        shadowView,
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
      )
    }
    sheetContainer.addView(
      clipContainer,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
    )
    // Above the sheet container, so React Native's target walk reaches it first.
    super.addView(
      cardTouchSensor,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
    )
    // Last, so the sheet container and the touch sensor keep their places.
    // It ends at the sheet's top, overlapping only the card's clipped
    // corners, where the scrim shows through. Touch walks skip it.
    super.addView(
      scrimDismissView,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
    )
    ViewCompat.setAccessibilityDelegate(
      sheetContainer,
      SheetDismissAccessibilityDelegate(
        isDismissAvailable = { snapping.isCloseRequestAvailable },
        performDismiss = ::attemptAccessibleDismissal,
      ),
    )
  }

  val sheetChildCount: Int
    get() = clipContainer.childCount

  fun getSheetChildAt(index: Int): View? = clipContainer.getChildAt(index)

  fun addSheetChild(child: View, index: Int) {
    clipContainer.addView(child, index)
    detents.refreshContentHeightMarker()
  }

  fun removeSheetChildAt(index: Int) {
    clipContainer.getChildAt(index)?.let(card::resetContentTransform)
    clipContainer.removeViewAt(index)
    detents.refreshContentHeightMarker()
  }

  // MARK: - Child view management

  override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
    if (child === sheetContainer || child === cardTouchSensor || child === scrimDismissView) {
      super.addView(child, index, params)
    } else {
      clipContainer.addView(child, index, params)
    }
  }

  override fun removeView(view: View) {
    if (view === sheetContainer) {
      super.removeView(view)
    } else {
      card.resetContentTransform(view)
      clipContainer.removeView(view)
      detents.refreshContentHeightMarker()
    }
  }

  override fun removeViewAt(index: Int) {
    clipContainer.getChildAt(index)?.let(card::resetContentTransform)
    clipContainer.removeViewAt(index)
    detents.refreshContentHeightMarker()
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
  private val ensureHostLayoutAfterAttach = Runnable {
    if (isAttachedToWindow && !hasPerformedHostLayoutSinceAttach && width > 0 && height > 0) {
      performHostLayout(width, height)
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
    hasPerformedHostLayoutSinceAttach = false
    notifyCloseRequestStateChanged()
    // Native geometry (status bar overlap, frame) is derived from the window; recompute on
    // (re)attach — including the inline<->overlay reparent — and ask for a
    // fresh insets pass. Fabric may assign unchanged bounds before attaching
    // the view, in which case Android does not owe us another onSizeChanged or
    // onLayout callback. Schedule the same host layout pass explicitly so
    // close request eligibility cannot remain invalid after the attach.
    requestApplyInsets()
    contentRegion.recomputeNativeGeometry()
    removeCallbacks(ensureHostLayoutAfterAttach)
    post(ensureHostLayoutAfterAttach)
    // A re-attach gives us a fresh, live ViewTreeObserver; the previous one was
    // dropped on detach. Resume observing if the initial snap is still pending.
    if (detents.pendingInitialContentDetentSnap) {
      detents.observePendingInitialContentDetent()
    }
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    contentRegion.recomputeNativeGeometry()
  }

  override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
    // The default top inset can change without a resize.
    contentRegion.recomputeNativeGeometry()
    return super.onApplyWindowInsets(insets)
  }

  override fun onDetachedFromWindow() {
    removeCallbacks(ensureHostLayoutAfterAttach)
    hasPerformedHostLayoutSinceAttach = false
    notifyCloseRequestStateChanged()
    // Release the listener from the soon-to-be-replaced observer and clear our
    // references so a later re-attach registers on the new live observer.
    detents.removePendingInitialContentDetentObserver()
    state.scrollIndicators.releaseAll()
    super.onDetachedFromWindow()
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    val w = right - left
    val h = bottom - top
    if (w <= 0 || h <= 0) return

    performHostLayout(w, h)
  }

  private fun performHostLayout(w: Int, h: Int) {
    // Whether the maximum width leaves a gap depends on the host's width.
    detents.refreshCardGaps()
    // The default top inset depends on this view's window position, which can
    // change without a resize.
    contentRegion.recomputeNativeGeometry()
    detents.refreshContentHeightMarker()
    detents.refreshDetentsFromLayout()
    hasPerformedHostLayoutSinceAttach = true
    layoutSheetContainer(w, h)

    if (!state.hasLaidOut && state.detentSpecs.isNotEmpty()) {
      val indexToApply = state.pendingIndex ?: state.targetIndex
      val clampedIndex = indexToApply.coerceIn(0, state.detentSpecs.size - 1)

      if (animateIn && detents.isInvalidContentDetentTarget(clampedIndex)) {
        state.hasLaidOut = true
        state.pendingIndex = null
        state.targetIndex = clampedIndex
        detents.pendingInitialContentDetentSnap = true
        sheetContainer.translationY = h.toFloat()
        snapping.emitPosition()
        notifyCloseRequestStateChanged()
        detents.observePendingInitialContentDetent()
        return
      }

      state.hasLaidOut = true
      state.pendingIndex = null
      state.targetIndex = clampedIndex
      detents.clearPendingInitialContentDetentSnap()

      if (animateIn) {
        val closedTy = h.toFloat()
        sheetContainer.translationY = closedTy
        snapping.emitPosition()
        snapping.snapToIndex(state.targetIndex, 0f, emitIndexChange = false, emitSettle = true)
      } else {
        closeRequestPresentationTracker.onTransitionSettled()
        sheetContainer.translationY = snapping.translationY(state.targetIndex)
        snapping.emitPosition()
        notifyCloseRequestStateChanged()
      }
      return
    }

    if (state.activeAnimation != null || state.isPanning) {
      notifyCloseRequestStateChanged()
      return
    }
    closeRequestPresentationTracker.onTransitionSettled()
    sheetContainer.translationY = snapping.translationY(state.targetIndex)
    card.updateCardGeometry()
    contentRegion.updateShadowState(sheetContainer.translationY)
    notifyCloseRequestStateChanged()
  }

  override fun dispatchDraw(canvas: Canvas) {
    scrim.drawScrim(canvas)
    super.dispatchDraw(canvas)
  }

  // Stable coordinate base for the sheet container. The container is sized to
  // the host's height rather than the tallest detent, so it stays a fixed-size
  // canvas: when content — and thus the `content` detent — shrinks, the container
  // does not collapse underneath the sheet, leaving room to animate the sheet
  // down to its new height.
  private fun layoutSheetContainer(viewWidth: Int, viewHeight: Int) {
    state.lastAppliedMaxDetentHeight = viewHeight.toFloat()
    sheetContainer.layout(0, 0, viewWidth, viewHeight)
    cardTouchSensor.layout(0, 0, viewWidth, viewHeight)
    scrimDismissView.layout(0, 0, viewWidth, viewHeight)
    updateScrimDismissView()
    card.layoutSheetChildren(viewWidth, viewHeight)
    card.updateCardGeometry()
    contentRegion.pushStateSnapshot()
  }

  // MARK: - Prop setters

  fun setDetents(raw: List<Map<String, Any>>) = detents.setDetents(raw)

  fun setIndex(newIndex: Int) = detents.setIndex(newIndex)

  fun setScrimColor(color: Int?) = scrim.setScrimColor(color)

  /** The label accessibility services announce for the scrim's "Dismiss" button. */
  fun setScrimAccessibilityLabel(label: String?) {
    scrimDismissView.contentDescription = label
  }

  // MARK: - Close requests

  private val isCloseRequestLayoutReady: Boolean
    get() =
      hasPerformedHostLayoutSinceAttach &&
        isAttachedToWindow &&
        width > 0 &&
        height > 0 &&
        !detents.isInvalidContentDetentTarget(state.targetIndex)

  private val isTargetDetentOpen: Boolean
    get() = state.detentSpecs.getOrNull(state.targetIndex)?.position?.let { it > 0f } == true

  // Request emission follows the resolved target rather than the transient animated position.
  val isCloseRequestTargetResolvedAndOpen: Boolean
    get() = isCloseRequestLayoutReady && isTargetDetentOpen

  /** Keeps the modal input boundary through a visible animated close. */
  val isCloseRequestPresentationActive: Boolean
    get() =
      closeRequestPresentationTracker.isPresentationActive(
        isTargetDetentOpen = isTargetDetentOpen,
        isCloseRequestLayoutReady = isCloseRequestLayoutReady,
      )

  /** Whether JS supplied `onCloseRequest`. */
  var hasCloseRequestHandler: Boolean
    get() = state.hasCloseRequestHandler
    set(value) {
      if (state.hasCloseRequestHandler == value) return
      state.hasCloseRequestHandler = value
      updateScrimDismissView()
      notifyCloseRequestStateChanged()
    }

  /**
   * Whether Back and Escape belong to the sheet: it has an `onCloseRequest` handler or a closed
   * detent to snap to, while its scrim shows or it is still visibly closing, so a close keeps Back
   * and Escape until it settles either way.
   */
  val handlesCloseRequests: Boolean
    get() {
      if (state.hasCloseRequestHandler || snapping.handlesCloseRequests) return true
      val isVisiblyClosing = isCloseRequestPresentationActive && !isTargetDetentOpen
      return state.scrimDismissIndex != null && isVisiblyClosing
    }

  /**
   * The one path every close request takes: a scrim tap, an accessibility dismiss action, Back and
   * Escape. With an `onCloseRequest` handler the request is only reported; without one the sheet
   * snaps to its closed detent, which reports through `onIndexChange`.
   */
  fun performCloseRequest(): Boolean {
    if (state.hasCloseRequestHandler) {
      val currentListener = listener ?: return false
      currentListener.onCloseRequest()
      return true
    }
    return snapping.snapToScrimDismissIndex()
  }

  private fun notifyCloseRequestStateChanged() {
    closeRequestStateChangedListener?.invoke()
  }

  // MARK: - Card geometry

  /** Physical corner radii of the card in dp: top-left, top-right, bottom-right, bottom-left. */
  fun setSurfaceCornerRadii(values: List<Float>) = card.setSurfaceCornerRadii(values)

  /** Outset card shadows, with lengths in dp. */
  internal fun setSurfaceShadows(values: List<CardShadow>) = card.setSurfaceShadows(values)

  /** The card's elevation shadow, with its length in dp. */
  fun setSurfaceElevation(value: Float) = card.setSurfaceElevation(value)

  fun setSurfaceShadowColor(color: Int) = card.setSurfaceShadowColor(color)

  /** Forwarded to [ContentRegion.onStateCommitted]. */
  fun onStateCommitted(
    frameWidthDp: Double,
    frameHeightDp: Double,
    contentRegionInsetDp: Double,
    surfaceOverflowDp: Double,
  ) =
    contentRegion.onStateCommitted(
      frameWidthDp,
      frameHeightDp,
      contentRegionInsetDp,
      surfaceOverflowDp,
    )

  // MARK: - Touch handling

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
    gestures.onInterceptTouchEvent(event)

  override fun onTouchEvent(event: MotionEvent): Boolean =
    gestures.onTouchEvent(event) ?: super.onTouchEvent(event)

  // MARK: - Nested scrolling

  override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean =
    scrollCoordination.onStartNestedScroll(child, target, axes, type)

  override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) =
    scrollCoordination.onNestedScrollAccepted(child, target, axes, type)

  override fun onStopNestedScroll(target: View, type: Int) =
    scrollCoordination.onStopNestedScroll(target, type)

  override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) =
    scrollCoordination.onNestedPreScroll(target, dx, dy, consumed, type)

  override fun onNestedScroll(
    target: View,
    dxConsumed: Int,
    dyConsumed: Int,
    dxUnconsumed: Int,
    dyUnconsumed: Int,
    type: Int,
    consumed: IntArray,
  ) =
    scrollCoordination.onNestedScroll(
      target,
      dxConsumed,
      dyConsumed,
      dxUnconsumed,
      dyUnconsumed,
      type,
      consumed,
    )

  override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean =
    scrollCoordination.onNestedPreFling(target, velocityX, velocityY)

  override fun onNestedFling(
    target: View,
    velocityX: Float,
    velocityY: Float,
    consumed: Boolean,
  ): Boolean = scrollCoordination.onNestedFling(target, velocityX, velocityY, consumed)

  override fun getNestedScrollAxes(): Int = scrollCoordination.nestedScrollAxes

  override fun onStartNestedScroll(child: View, target: View, axes: Int): Boolean =
    onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH)

  override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
    onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH)
  }

  override fun onStopNestedScroll(target: View) {
    onStopNestedScroll(target, ViewCompat.TYPE_TOUCH)
  }

  override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
    onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH)
  }

  override fun onNestedScroll(
    target: View,
    dxConsumed: Int,
    dyConsumed: Int,
    dxUnconsumed: Int,
    dyUnconsumed: Int,
  ) {
    onNestedScroll(
      target,
      dxConsumed,
      dyConsumed,
      dxUnconsumed,
      dyUnconsumed,
      ViewCompat.TYPE_TOUCH,
    )
  }

  override fun onNestedScroll(
    target: View,
    dxConsumed: Int,
    dyConsumed: Int,
    dxUnconsumed: Int,
    dyUnconsumed: Int,
    type: Int,
  ) {
    onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, IntArray(2))
  }

  // MARK: - Cleanup

  fun destroy() {
    scrollCoordination.clearNestedScrollState()
    state.activeAnimation?.cancel()
    state.activeAnimation = null
    gestures.releaseVelocityTracker()
    removeCallbacks(sheetChildrenLayoutPass)
    sheetChildrenLayoutEnqueued = false
    removeCallbacks(ensureHostLayoutAfterAttach)
    state.statusBarOverlapPx = 0f
    state.lastAppliedMaxDetentHeight = Float.NaN
    contentRegion.clearGeometryState()
    detents.clearPendingInitialContentDetentSnap()
    detents.releaseContentHeightMarker()
    state.rawDetentSpecs = emptyList()
    state.hasCardGaps = false
    state.hasInsetGaps = false
    clipContainer.setClipsToCard(false)
    state.widestCardInsets = DetentInsets()
    state.cardMaxWidth = 0f
    state.cardPlacementBias = 0.5f
    scrim.scrimOpacities = emptyList()
    state.detentSpecs = emptyList()
    state.targetIndex = 0
    state.hasCloseRequestHandler = false
    hasPerformedHostLayoutSinceAttach = false
    closeRequestPresentationTracker.onHostDestroyed()
    notifyCloseRequestStateChanged()
    state.pendingIndex = null
    state.hasLaidOut = false
    state.isPanning = false
    state.panStartingIndex = null
    state.activeDragRange = null
    state.activeDragDetentSpecs = null
    gestures.clearTouchState()
    scrim.clearScrimProgress()
    state.suppressScrimForClosingTarget = false
    state.scrimPinnedFull = false
    for (i in 0 until clipContainer.childCount) {
      card.resetContentTransform(clipContainer.getChildAt(i))
    }
    clipContainer.removeAllViews()
    stateWrapper = null
    contentRegion.clearShadowOffset()
    card.clearShadowContentTransform()
    closeRequestStateChangedListener = null
    listener = null
  }

  internal val isInteractive: Boolean
    // Follow the sheet, not the scrim: scrim opacity is styling, so a modal sheet configured with
    // a transparent scrim is still presented and must keep receiving touches.
    get() =
      modal &&
        (state.activeAnimation != null ||
          state.isPanning ||
          (!state.isTargetingClosedDetent && state.currentSheetHeight() > 0.5f))

  private fun updateInteractionState() {
    val interactive = isInteractive
    pointerEvents = if (interactive) PointerEvents.AUTO else PointerEvents.BOX_NONE
    interactionListener?.invoke(interactive)
    updateScrimDismissView()
  }

  /**
   * Requests a close through the scrim-tap path, and withdraws the Dismiss button at once rather
   * than on the close's first frame, so accessibility focus doesn't linger on a button that no
   * longer acts.
   */
  private fun attemptAccessibleDismissal(): Boolean =
    snapping.requestClose().also { if (it) updateScrimDismissView() }

  private fun updateScrimDismissView() {
    scrimDismissView.update(
      isDismissAvailable = snapping.isCloseRequestAvailable,
      sheetTop = sheetContainer.top + sheetContainer.translationY,
      hostHeight = height,
    )
  }

  companion object {
    // The `nativeID` the JS side gives the zero-height marker it appends to
    // the content wrapper. Mirrored in `bottomSheetUtils.ts` and
    // `BottomSheetHostingView.swift`; keep the three in sync.
    const val CONTENT_HEIGHT_MARKER_NATIVE_ID = "swm-bottom-sheet-content-height-marker"
  }
}
