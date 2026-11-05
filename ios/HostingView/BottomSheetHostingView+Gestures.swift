import UIKit

extension BottomSheetHostingView {
  @objc func handlePan(_ gesture: UIPanGestureRecognizer) {
    let maxHeight = sheetContainerHeight

    switch gesture.state {
    case .began:
      if activeSpring != nil {
        cancelActiveSpring()
      }
      isPanning = true
      // The user takes control: drop any index retained from props.
      pendingIndex = nil
      scrimPinnedFull = false
      panStartingIndex = targetIndex
      activeDragDetentSpecs = detentSpecs
      activeDragRange = snapshotDraggableRange(including: targetIndex, in: detentSpecs)
      sheetContainer.endEditing(true)
      didMoveSheetDuringPan = false
      didCancelTouchesForPan = false
      scrollViewOwnsLowerBoundary = false
      activeScrollableNegotiationLevel = negotiationLevel(forVerticalVelocity: gesture.velocity(in: self).y)

      let locationInContainer = initialTouchLocation ?? gesture.location(in: sheetContainer)
      activeScrollViewStates = scrollableAncestorChain(containing: locationInContainer).map {
        ActiveScrollViewState(scrollView: $0.scrollView, inverted: $0.inverted)
      }

      if activeScrollViewStates.isEmpty {
        setContentInteractionEnabled(false)
        cancelContentTouchesForPanIfNeeded()
        panCoordinationMode = .sheet
      } else {
        // Keep every scroll recognizer alive from touch-down. While the sheet
        // moves, pin the scrollable(s); at the largest detent, release them so
        // UIKit continues the same pan and supplies native deceleration.
        setContentInteractionEnabled(true)
        let range = activeDragRange ?? draggableRange(including: panStartingIndex)
        let isAtLargestDetent = sheetContainer.transform.ty <= range.minTy + 0.5
        let velocity = gesture.velocity(in: self).y
        if
          activeScrollableNegotiationLevel == .handoff,
          isAtLargestDetent, velocity < 0 || !activeScrollViewsAreAtStart()
        {
          panCoordinationMode = .scrollView
        } else {
          panCoordinationMode = .sheet
          lockActiveScrollViews(pinToStart: isAtLargestDetent)
        }
      }
      gesture.setTranslation(.zero, in: self)

    case .changed:
      let delta = gesture.translation(in: self).y
      gesture.setTranslation(.zero, in: self)
      let range = activeDragRange ?? draggableRange(including: panStartingIndex)

      if
        activeScrollableNegotiationLevel == .handoff,
        !activeScrollViewStates.isEmpty,
        panCoordinationMode == .scrollView
      {
        if scrollViewOwnsLowerBoundary {
          // Once the sheet is fully collapsed, downward movement belongs to
          // native bounce/RefreshControl. On reversal, let that overscroll
          // return to its resting offset before the sheet can expand again.
          if delta < 0, activeScrollViewsAreAtStart(requireExactOffset: true) {
            scrollViewOwnsLowerBoundary = false
            panCoordinationMode = .sheet
            lockActiveScrollViews(pinToStart: true)
          }
          return
        }
        // A downward scroll remains with the content until every vertical
        // scrollable in the touched ancestor chain reaches its visual start.
        // Start moving the sheet on the following update so the boundary
        // movement is not consumed twice by UIKit and the sheet.
        if delta > 0, activeScrollViewsAreAtStart() {
          panCoordinationMode = .sheet
          lockActiveScrollViews(pinToStart: true)
        }
        return
      }

      pinActiveScrollViews()
      let minTy = range.minTy
      let maxTy = range.maxTy
      let newTy = max(minTy, min(maxTy, sheetContainer.transform.ty + delta))
      if abs(newTy - sheetContainer.transform.ty) > .ulpOfOne {
        cancelContentTouchesForPanIfNeeded()
        didMoveSheetDuringPan = true
        setSheetTranslation(newTy)
        emitPosition()
      }
      pinActiveScrollViews()

      if
        activeScrollableNegotiationLevel == .handoff,
        !activeScrollViewStates.isEmpty,
        delta < 0,
        newTy <= minTy + 0.5
      {
        // The boundary update remains pinned; subsequent updates and the
        // eventual release are handled natively by the still-active scroll pan.
        panCoordinationMode = .scrollView
        scrollViewOwnsLowerBoundary = false
        unlockActiveScrollViews()
      } else if
        activeScrollableNegotiationLevel == .handoff,
        !activeScrollViewStates.isEmpty,
        delta > 0,
        newTy >= maxTy - 0.5
      {
        // The sheet has no smaller detent to consume. Release subsequent
        // downward movement to native bounce and RefreshControl.
        panCoordinationMode = .scrollView
        scrollViewOwnsLowerBoundary = true
        unlockActiveScrollViews()
      }

    case .ended, .cancelled:
      isPanning = false
      setContentInteractionEnabled(true)
      let coordinatedScrollView = !activeScrollViewStates.isEmpty
      let sheetMoved = didMoveSheetDuringPan
      if sheetMoved, panCoordinationMode == .sheet {
        // The sheet never handed ownership to content. Cancel the still-live
        // scroll pans so they cannot begin decelerating after being unpinned.
        cancelActiveScrollViewPans()
      }
      unlockActiveScrollViews()
      let velocity = gesture.velocity(in: self).y
      if coordinatedScrollView, !sheetMoved {
        panStartingIndex = nil
        activeDragRange = nil
        activeDragDetentSpecs = nil
        finishScrollViewCoordination()
        return
      }
      let currentHeight = maxHeight - sheetContainer.transform.ty
      let index =
        activeDragDetentSpecs.flatMap {
          $0.count == detentSpecs.count ? $0 : nil
        }.map {
          snapshotBestSnapIndex(
            for: currentHeight,
            velocity: velocity,
            including: panStartingIndex,
            in: $0
          )
        } ?? bestSnapIndex(
          for: currentHeight,
          velocity: velocity,
          including: panStartingIndex
        )
      panStartingIndex = nil
      activeDragRange = nil
      activeDragDetentSpecs = nil
      finishScrollViewCoordination()
      snapToIndex(index, velocity: velocity)

    case .failed:
      isPanning = false
      panStartingIndex = nil
      activeDragRange = nil
      activeDragDetentSpecs = nil
      setContentInteractionEnabled(true)
      finishScrollViewCoordination()

    default:
      break
    }
  }

  func shouldBeginSheetPan() -> Bool {
    let velocity = panGesture.velocity(in: self)
    // A sideways start yields to horizontal recognizers, unless it was held
    // for a vertical scrollable under the touch and has since turned vertical:
    // that scrollable's pan recognizes alongside this one, so yielding would
    // let it scroll content the sheet owns at this detent.
    if !panGesture.tookSidewaysStart {
      guard abs(velocity.y) > abs(velocity.x) else { return false }
    }

    let candidates = snapCandidateIndices(including: targetIndex)
    guard candidates.count > 1 else { return false }

    let locationInContainer = initialTouchLocation ?? panGesture.location(in: sheetContainer)
    let scrollableChain = scrollableAncestorChain(containing: locationInContainer)
    guard !scrollableChain.isEmpty else {
      // Outside a scrollable, the sheet owns any direction in which it has a
      // detent available.
      let range = draggableRange(including: targetIndex)
      let currentTy = sheetContainerHeight - currentSheetHeight
      return velocity.y < 0
        ? currentTy > range.minTy + 0.5
        : currentTy < range.maxTy - 0.5
    }

    let level = negotiationLevel(forVerticalVelocity: velocity.y)
    guard level != .none else { return false }

    let range = draggableRange(including: targetIndex)
    let currentTy = sheetContainerHeight - currentSheetHeight
    let canMoveSheet = velocity.y < 0
      ? currentTy > range.minTy + 0.5
      : currentTy < range.maxTy - 0.5
    guard canMoveSheet else { return false }

    if level == .handoff {
      return true
    }

    // Initial-only collapse is available at the largest detent only when all
    // scrollable ancestors were already at their visual start at touch-down.
    // Below the largest detent, this preserves the original sheet-first owner
    // selection in either direction.
    let isAtLargestDetent = currentTy <= range.minTy + 0.5
    if velocity.y > 0, isAtLargestDetent {
      return scrollableChainIsAtStart(scrollableChain)
    }
    return true
  }
}

extension BottomSheetHostingView: UIGestureRecognizerDelegate {
  private func canCoordinate(with other: UIGestureRecognizer) -> Bool {
    guard
      hasAnyScrollableNegotiation,
      let scrollView = other.view as? UIScrollView,
      other === scrollView.panGestureRecognizer,
      scrollView.isDescendant(of: sheetContainer),
      isVerticallyScrollable(scrollView)
    else {
      return false
    }
    return true
  }

  public func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldReceive touch: UITouch
  ) -> Bool {
    // Record the touch-down point before the pan's translation threshold is
    // exceeded. gestureRecognizerShouldBegin and handlePan(.began) hit-test
    // scrollables from this point instead of the post-threshold pan location,
    // matching Android's ACTION_DOWN semantics.
    if gestureRecognizer === panGesture, panGesture.numberOfTouches == 0 {
      let location = touch.location(in: sheetContainer)
      initialTouchLocation = location
      panGesture.prepareHold(for: touch, deferringTo: sidewaysDeferralScrollViews(at: location))
    }
    return true
  }

  public func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldBeRequiredToFailBy other: UIGestureRecognizer
  ) -> Bool {
    guard gestureRecognizer === panGesture else { return false }
    if canCoordinate(with: other) {
      return false
    }
    // While a touch inside a vertical scrollable is held, the sheet's pan
    // behaves like that scrollable's own: a pan above the sheet — a navigation
    // pop, a pager — recognizes a sideways swipe on its own and cancels the
    // held pan, rather than waiting for a failure the hold never produces.
    // UIKit settles this requirement as the touch lands, before its direction
    // is known, so it cannot wait for sideways travel; like the scrollable's
    // pan, the held pan therefore also yields a vertical start to a pan above
    // it whose own threshold is below the slop. Pans inside the sheet disable
    // the hold instead.
    //
    // Apple documents one query per recognition attempt. Its timing relative
    // to touch delivery is observed, not documented: on iOS 27, UIKit asks
    // right after `touchesBegan`, before movement. The hold is armed earlier,
    // in `gestureRecognizer(_:shouldReceive:)`, so the exemption also applies
    // if UIKit asks before delivering the touch. If a future release asks
    // after the hold has been released, the exemption would no longer apply.
    // Recheck this timing with each major iOS release.
    if
      panGesture.isHoldingSidewaysStart,
      other is UIPanGestureRecognizer,
      let otherView = other.view,
      !otherView.isDescendant(of: sheetContainer)
    {
      return false
    }
    return other is UIPanGestureRecognizer || other is UITapGestureRecognizer
  }

  public func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
  ) -> Bool {
    if gestureRecognizer === panGesture {
      return canCoordinate(with: other)
    }
    if other === panGesture {
      return canCoordinate(with: gestureRecognizer)
    }
    return false
  }
}
