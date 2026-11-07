package com.swmansion.reactnativebottomsheet.host

import android.graphics.Matrix
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.NestedScrollingChild
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.uimanager.ReactPointerEventsView
import com.facebook.react.views.scroll.ReactHorizontalScrollView
import com.facebook.react.views.scroll.ReactScrollView
import kotlin.math.roundToInt

/** Coordinates touch handling between the sheet and the scrollables inside it. */
internal class ScrollCoordination(private val state: SheetState, private val snapping: Snapping) {

  private val hitTestInverseMatrix = Matrix()
  private val hitTestPoint = FloatArray(2)
  private val nestedScrollingParentHelper = NestedScrollingParentHelper(state.hostView)
  var nestedTouchTarget: View? = null
  private var nestedTouchWasEnabled = false
  var nestedTouchInverted = false
  var nestedScrollInProgress = false
  var nestedSheetMoved = false
  var nestedPanSettled = false

  val nestedScrollAxes: Int
    get() = nestedScrollingParentHelper.nestedScrollAxes

  fun coordinationLevel(expanding: Boolean): ScrollCoordinationLevel =
    ScrollCoordinationLevel.from(
      if (expanding) state.scrollExpandCoordination else state.scrollCollapseCoordination,
    )

  val hasAnyHandoffCoordination: Boolean
    get() =
      coordinationLevel(expanding = true) == ScrollCoordinationLevel.HANDOFF ||
        coordinationLevel(expanding = false) == ScrollCoordinationLevel.HANDOFF

  private fun beginNestedPanIfNeeded() {
    if (state.isPanning) return
    state.isPanning = true
    // The user takes control: drop any index retained from props.
    state.pendingIndex = null
    state.scrimPinnedFull = false
    state.panStartingIndex = state.targetIndex
    val dragDetentSpecs = state.detentSpecs.toList()
    state.activeDragDetentSpecs = dragDetentSpecs
    state.activeDragRange = snapping.snapshotDraggableRange(state.targetIndex, dragDetentSpecs)
    state.activeAnimation?.let {
      it.cancel()
      state.activeAnimation = null
    }
  }

  fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
    return type == ViewCompat.TYPE_TOUCH &&
      axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0 &&
      hasAnyHandoffCoordination &&
      nestedTouchTarget != null &&
      target.isDescendantOf(state.sheetContainer)
  }

  fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
    nestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes, type)
    nestedScrollInProgress = true
  }

  fun onStopNestedScroll(target: View, type: Int) {
    nestedScrollingParentHelper.onStopNestedScroll(target, type)
    if (type == ViewCompat.TYPE_TOUCH && nestedScrollInProgress) {
      state.isPanning = false
      if (nestedSheetMoved && !nestedPanSettled) {
        snapping.settleActivePan(0f)
      } else if (!nestedSheetMoved) {
        state.panStartingIndex = null
        state.activeDragRange = null
        state.activeDragDetentSpecs = null
      }
      clearNestedScrollState()
    }
  }

  fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
    if (type != ViewCompat.TYPE_TOUCH || dy == 0) return

    val normalizedDy = if (nestedTouchInverted) -dy else dy
    if (
      normalizedDy > 0 && coordinationLevel(expanding = true) == ScrollCoordinationLevel.HANDOFF
    ) {
      consumed[1] += consumeNestedSheetScroll(dy)
    } else if (
      normalizedDy < 0 &&
        coordinationLevel(expanding = false) == ScrollCoordinationLevel.HANDOFF &&
        scrollableAncestorsAreAtStart()
    ) {
      // Consume before native overscroll/RefreshControl while the sheet still
      // has a smaller detent. Once the sheet reaches that boundary this returns
      // zero, leaving the same gesture available to the child.
      consumed[1] += consumeNestedSheetScroll(dy)
    }
  }

  fun onNestedScroll(
    target: View,
    dxConsumed: Int,
    dyConsumed: Int,
    dxUnconsumed: Int,
    dyUnconsumed: Int,
    type: Int,
    consumed: IntArray,
  ) {
    if (type != ViewCompat.TYPE_TOUCH || dyUnconsumed == 0) return

    val normalizedDy = if (nestedTouchInverted) -dyUnconsumed else dyUnconsumed
    if (
      normalizedDy < 0 && coordinationLevel(expanding = false) == ScrollCoordinationLevel.HANDOFF
    ) {
      consumed[1] += consumeNestedSheetScroll(dyUnconsumed)
    }
  }

  fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
    if (!nestedScrollInProgress || !nestedSheetMoved) return false

    val normalizedVelocityY = if (nestedTouchInverted) -velocityY else velocityY
    if (coordinationLevel(expanding = normalizedVelocityY > 0) != ScrollCoordinationLevel.HANDOFF) {
      return false
    }
    val range = state.activeDragRange ?: snapping.draggableRange(state.panStartingIndex)
    val isAtLargestDetent = state.sheetContainer.translationY <= range.start + 1f
    val isAtSmallestDetent = state.sheetContainer.translationY >= range.endInclusive - 1f

    // Once the sheet reaches a boundary, an outward release belongs to the
    // child so it retains native fling/deceleration (or refresh overscroll).
    // Every other release settles the sheet and prevents content from moving.
    if (
      (isAtLargestDetent && normalizedVelocityY > 0) ||
        (isAtSmallestDetent && normalizedVelocityY < 0)
    ) {
      return false
    }

    state.isPanning = false
    nestedPanSettled = true
    snapping.settleActivePan(-normalizedVelocityY)
    return true
  }

  fun onNestedFling(
    target: View,
    velocityX: Float,
    velocityY: Float,
    consumed: Boolean,
  ): Boolean = false

  private fun consumeNestedSheetScroll(rawDy: Int): Int {
    val normalizedDy = if (nestedTouchInverted) -rawDy else rawDy
    if (normalizedDy == 0) return 0

    beginNestedPanIfNeeded()
    val range = state.activeDragRange ?: snapping.draggableRange(state.panStartingIndex)
    val oldTy = state.sheetContainer.translationY
    val newTy = (oldTy - normalizedDy).coerceIn(range.start, range.endInclusive)
    if (newTy == oldTy) return 0

    state.sheetContainer.translationY = newTy
    nestedSheetMoved = true
    snapping.emitPosition()

    val normalizedConsumed = (oldTy - newTy).roundToInt()
    return if (nestedTouchInverted) -normalizedConsumed else normalizedConsumed
  }

  fun clearNestedScrollState() {
    val touchTarget = nestedTouchTarget
    val shouldRestoreDisabled = touchTarget is ScrollView && !nestedTouchWasEnabled

    // Restoring nested scrolling to false calls View.stopNestedScroll(), which
    // synchronously re-enters onStopNestedScroll(). Clear ownership first so
    // that callback observes an already-finished gesture instead of settling
    // and restoring again recursively.
    nestedTouchTarget = null
    nestedTouchWasEnabled = false
    nestedTouchInverted = false
    nestedScrollInProgress = false
    nestedSheetMoved = false
    nestedPanSettled = false

    if (shouldRestoreDisabled) {
      ViewCompat.setNestedScrollingEnabled(touchTarget, false)
    }
  }

  private fun View.isDescendantOf(ancestor: ViewGroup): Boolean {
    var node: View? = this
    while (node != null) {
      if (node === ancestor) return true
      node = node.parent as? View
    }
    return false
  }

  // MARK: - Scroll view helpers

  // Nested-scrolling children use the parent callbacks above for a continuous
  // handoff. Explicit edge detection remains as the compatibility fallback for
  // scrollables that do not implement Android's nested-scrolling child contract.

  fun prepareNestedScrollableAtTouch(): View? {
    val scrollView = findScrollableAtTouch() ?: return null
    nestedTouchWasEnabled = ViewCompat.isNestedScrollingEnabled(scrollView)
    when (scrollView) {
      is ScrollView -> ViewCompat.setNestedScrollingEnabled(scrollView, true)
      is NestedScrollingChild -> {
        // Respect an explicit opt-out on arbitrary third-party scrollables.
        if (!ViewCompat.isNestedScrollingEnabled(scrollView)) return null
      }
      else -> return null
    }
    return scrollView.takeIf { ViewCompat.isNestedScrollingEnabled(it) }
  }

  fun isScrollViewAtTop(): Boolean {
    val scrollView = findScrollableAtTouch() ?: return true
    val inverted = isViewInverted(scrollView)
    return if (inverted) !scrollView.canScrollVertically(1) else !scrollView.canScrollVertically(-1)
  }

  private fun scrollableAncestorsAreAtStart(): Boolean {
    var current = nestedTouchTarget ?: return true
    while (current !== state.sheetContainer) {
      if (isVerticallyScrollable(current)) {
        val inverted = isViewInverted(current)
        val canScrollTowardStart =
          if (inverted) current.canScrollVertically(1) else current.canScrollVertically(-1)
        if (canScrollTowardStart) return false
      }
      current = current.parent as? View ?: break
    }
    return true
  }

  private fun findScrollableAtTouch(): View? = findScrollableHitAtTouch()?.view

  fun findScrollableHitAtTouch(): TouchHit.Scrollable? {
    val containerX =
      state.initialTouchX - state.sheetContainer.left - state.sheetContainer.translationX
    val containerY =
      state.initialTouchY - state.sheetContainer.top - state.sheetContainer.translationY
    if (
      containerX < 0f ||
        containerX >= state.sheetContainer.width ||
        containerY < 0f ||
        containerY >= state.sheetContainer.height
    ) {
      return null
    }
    return findScrollableAtPoint(state.sheetContainer, containerX, containerY)
      as? TouchHit.Scrollable
  }

  sealed interface TouchHit {
    /** Nothing here takes the touch; views underneath may. */
    object Miss : TouchHit

    /**
     * A non-scrollable view takes the touch, shielding views underneath.
     * [throughHorizontalScroller] records a horizontal scroller on the path to it.
     */
    class Blocked(val throughHorizontalScroller: Boolean) : TouchHit

    /**
     * [throughHorizontalScroller] is whether a horizontal scroller lies on the touched path, above
     * or below [view], and so owns sideways movement.
     */
    class Scrollable(val view: View, val throughHorizontalScroller: Boolean) : TouchHit
  }

  // Mirrors RN's TouchTargetHelper: the topmost child that can take the touch
  // (per pointerEvents) wins, and the result is the nearest vertically
  // scrollable ancestor of that target. A view drawn over a scrollable (e.g. an
  // absolutely positioned header) therefore shields it.
  private fun findScrollableAtPoint(view: View, x: Float, y: Float): TouchHit {
    if (!view.isShown) return TouchHit.Miss

    var pointerEvents =
      when (view) {
        is ReactPointerEventsView -> view.pointerEvents
        // RN scroll views expose pointerEvents without implementing ReactPointerEventsView.
        is ReactScrollView -> view.pointerEvents
        is ReactHorizontalScrollView -> view.pointerEvents
        else -> PointerEvents.AUTO
      }
    if (!view.isEnabled) {
      pointerEvents =
        when (pointerEvents) {
          PointerEvents.AUTO -> PointerEvents.BOX_NONE
          PointerEvents.BOX_ONLY -> PointerEvents.NONE
          else -> pointerEvents
        }
    }
    if (pointerEvents == PointerEvents.NONE) return TouchHit.Miss

    var childBlocked = false
    var childThroughHorizontalScroller = false
    if (view is ViewGroup && pointerEvents != PointerEvents.BOX_ONLY) {
      for (i in view.childCount - 1 downTo 0) {
        val child = view.getChildAt(i)
        // Transform-aware, like TouchTargetHelper.getChildPoint: the child's
        // matrix carries translation, scale and rotation around its pivot.
        var childX = x + view.scrollX - child.left
        var childY = y + view.scrollY - child.top
        val childMatrix = child.matrix
        if (!childMatrix.isIdentity) {
          // A non-invertible matrix (scale 0) leaves the inverse untouched, so
          // mapping with it would use another view's inverse. Such a child has
          // no area and cannot be hit; skip it like TouchTargetHelper does.
          if (!childMatrix.invert(hitTestInverseMatrix)) continue
          hitTestPoint[0] = childX
          hitTestPoint[1] = childY
          hitTestInverseMatrix.mapPoints(hitTestPoint)
          childX = hitTestPoint[0]
          childY = hitTestPoint[1]
        }
        if (childX < 0f || childX >= child.width || childY < 0f || childY >= child.height) {
          continue
        }
        when (val hit = findScrollableAtPoint(child, childX, childY)) {
          is TouchHit.Scrollable ->
            return TouchHit.Scrollable(
              hit.view,
              hit.throughHorizontalScroller || isHorizontallyScrollable(view),
            )
          is TouchHit.Blocked -> {
            childBlocked = true
            childThroughHorizontalScroller = hit.throughHorizontalScroller
            break
          }
          TouchHit.Miss -> Unit
        }
      }
    }

    val throughHorizontalScroller = childThroughHorizontalScroller || isHorizontallyScrollable(view)
    if (isVerticallyScrollable(view)) {
      return TouchHit.Scrollable(view, throughHorizontalScroller)
    }
    return if (childBlocked || pointerEvents != PointerEvents.BOX_NONE) {
      TouchHit.Blocked(throughHorizontalScroller)
    } else {
      TouchHit.Miss
    }
  }

  private fun isHorizontallyScrollable(view: View): Boolean {
    if (!view.canScrollHorizontally(1) && !view.canScrollHorizontally(-1)) {
      return false
    }
    return getReactScrollEnabled(view) != false
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

  fun isViewInverted(view: View): Boolean {
    val values = FloatArray(9)
    var current: View? = view
    while (current != null && current !== state.sheetContainer) {
      if (!current.matrix.isIdentity) {
        current.matrix.getValues(values)
        if (values[android.graphics.Matrix.MSCALE_Y] < 0) return true
      }
      current = current.parent as? View
    }
    return false
  }
}
