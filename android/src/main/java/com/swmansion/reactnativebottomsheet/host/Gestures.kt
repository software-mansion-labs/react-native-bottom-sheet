package com.swmansion.reactnativebottomsheet.host

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import com.facebook.react.uimanager.RootView
import kotlin.math.abs

/** The touch stream: scrim presses, the sheet's own drags, and the takeover from children. */
internal class Gestures(
  private val state: SheetState,
  private val card: Card,
  private val scrim: Scrim,
  private val snapping: Snapping,
  private val scrollCoordination: ScrollCoordination,
) {
  private var velocityTracker: VelocityTracker? = null
  private val touchSlop = ViewConfiguration.get(state.hostView.context).scaledTouchSlop

  // Touch tracking
  private var lastTouchY = 0f
  private var activePointerId = MotionEvent.INVALID_POINTER_ID
  private var scrimPressed = false
  private var scrimTouchActive = false

  fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
      scrollCoordination.clearNestedScrollState()
    }
    if (event.actionMasked == MotionEvent.ACTION_DOWN && !card.cardContains(event.x, event.y)) {
      if (scrim.isScrimVisible()) {
        state.initialTouchX = event.x
        state.initialTouchY = event.y
        lastTouchY = event.y
        activePointerId = event.getPointerId(0)
        scrimPressed = true
        scrimTouchActive = true
        // Interception alone does not cancel RNGH handlers on views beneath the scrim:
        // their recognizers run at an ancestor, which must also yield the touch stream.
        announceGestureTakeover(event)
        return true
      }
      return false
    }

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        state.initialTouchX = event.x
        state.initialTouchY = event.y
        lastTouchY = event.y
        activePointerId = event.getPointerId(0)
        scrollCoordination.nestedTouchTarget =
          if (scrollCoordination.hasAnyHandoffCoordination)
            scrollCoordination.prepareNestedScrollableAtTouch()
          else null
        scrollCoordination.nestedTouchInverted =
          scrollCoordination.nestedTouchTarget?.let(scrollCoordination::isViewInverted) == true
        scrollCoordination.nestedScrollInProgress = false
        scrollCoordination.nestedSheetMoved = false
        scrollCoordination.nestedPanSettled = false
      }
      MotionEvent.ACTION_MOVE -> {
        if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) return false
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        val dx = x - state.initialTouchX
        val dy = y - state.initialTouchY

        val dragRange = snapping.draggableRange(state.targetIndex)
        if (abs(dy) > touchSlop && dragRange.start < dragRange.endInclusive) {
          // A vertical scrollable intercepts any move past the slop whatever
          // its sideways component, so a shallow diagonal left to it would
          // scroll content the sheet owns at this detent. Inside one, the
          // sheet applies the same rule. Elsewhere, and where a horizontal
          // scroller lies on the touched path, a sideways move stays with
          // whatever owns it.
          val touchedHit = scrollCoordination.findScrollableHitAtTouch()
          val claimsSideways = touchedHit != null && !touchedHit.throughHorizontalScroller
          if (!claimsSideways && abs(dy) <= abs(dx)) return false

          val canMoveSheet =
            if (dy < 0) {
              state.sheetContainer.translationY > dragRange.start + 1f
            } else {
              state.sheetContainer.translationY < dragRange.endInclusive - 1f
            }
          if (!canMoveSheet) return false

          if (touchedHit != null) {
            val level = scrollCoordination.coordinationLevel(expanding = dy < 0)
            if (level == ScrollCoordinationLevel.NONE) return false

            // A supported nested-scrolling child keeps the touch stream for a
            // handoff direction. Initial-only—and unsupported handoff—use the
            // original touch-down owner selection below.
            if (
              level == ScrollCoordinationLevel.HANDOFF &&
                scrollCoordination.nestedTouchTarget != null
            ) {
              return false
            }
          }
          if (!snapping.isAtMaxDragCandidate(state.targetIndex)) {
            lastTouchY = y
            announceGestureTakeover(event)
            return true
          }
          if (dy > 0 && scrollCoordination.isScrollViewAtTop()) {
            lastTouchY = y
            announceGestureTakeover(event)
            return true
          }
        }
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        state.initialTouchX = 0f
        state.initialTouchY = 0f
        activePointerId = MotionEvent.INVALID_POINTER_ID
        scrimPressed = false
        scrimTouchActive = false
        if (!scrollCoordination.nestedScrollInProgress) {
          scrollCoordination.clearNestedScrollState()
        }
      }
    }
    return false
  }

  fun onTouchEvent(event: MotionEvent): Boolean? {
    if (scrimTouchActive) {
      when (event.actionMasked) {
        MotionEvent.ACTION_MOVE -> {
          if (
            card.cardContains(event.x, event.y) || abs(event.y - state.initialTouchY) > touchSlop
          ) {
            scrimPressed = false
          }
          return true
        }
        MotionEvent.ACTION_UP -> {
          val shouldDismiss = scrimPressed
          scrimPressed = false
          scrimTouchActive = false
          activePointerId = MotionEvent.INVALID_POINTER_ID
          if (shouldDismiss) snapping.attemptScrimDismissal()
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
        if (!state.isPanning) beginPan(event)
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) return true
        val y = event.getY(pointerIndex)
        velocityTracker?.addMovement(event)
        val dy = y - lastTouchY
        lastTouchY = y

        val dragRange = state.activeDragRange ?: snapping.draggableRange(state.panStartingIndex)
        val newTy =
          (state.sheetContainer.translationY + dy).coerceIn(dragRange.start, dragRange.endInclusive)
        state.sheetContainer.translationY = newTy
        snapping.emitPosition()
        return true
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        state.isPanning = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        val velocity =
          velocityTracker?.let { tracker ->
            tracker.computeCurrentVelocity(1000)
            val v = tracker.yVelocity
            tracker.recycle()
            v
          } ?: 0f
        velocityTracker = null
        snapping.settleActivePan(velocity)
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
    return null
  }

  private fun beginPan(event: MotionEvent) {
    state.isPanning = true
    // The user takes control: drop any index retained from props.
    state.pendingIndex = null
    state.scrimPinnedFull = false
    state.panStartingIndex = state.targetIndex
    val dragDetentSpecs = state.detentSpecs.toList()
    state.activeDragDetentSpecs = dragDetentSpecs
    state.activeDragRange = snapping.snapshotDraggableRange(state.targetIndex, dragDetentSpecs)
    activePointerId = event.getPointerId(0)
    lastTouchY = event.y
    velocityTracker?.recycle()
    velocityTracker = VelocityTracker.obtain()
    velocityTracker?.addMovement(event)
    state.activeAnimation?.let {
      it.cancel()
      state.activeAnimation = null
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
    state.hostView.requestDisallowInterceptTouchEvent(true)
  }

  private fun notifyNativeGestureStarted(event: MotionEvent) {
    findReactRootView()?.onChildStartedNativeGesture(state.hostView, event)
  }

  private fun findReactRootView(): RootView? {
    var current: View = state.hostView
    while (true) {
      if (current is RootView) return current
      current = current.parent as? View ?: return null
    }
  }

  fun releaseVelocityTracker() {
    velocityTracker?.recycle()
    velocityTracker = null
  }

  fun clearTouchState() {
    state.initialTouchY = 0f
    state.initialTouchX = 0f
    lastTouchY = 0f
    activePointerId = MotionEvent.INVALID_POINTER_ID
    scrimPressed = false
    scrimTouchActive = false
  }
}
