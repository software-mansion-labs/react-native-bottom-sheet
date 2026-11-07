import UIKit

extension BottomSheetHostingView {
  @objc func handlePan(_ gesture: UIPanGestureRecognizer) {
    let maxHeight = sheetContainerHeight

    switch gesture.state {
    case .began:
      interruptedSpringEmitsSettle = activeSpring == nil ? nil : activeSpringEmitsSettle
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
      let verticalDirection = panVerticalDirection(gesture)
      activeScrollCoordinationLevel = coordinationLevel(forVerticalVelocity: verticalDirection)

      activeScrollViewStates = initialScrollViewStates

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
        if
          activeScrollCoordinationLevel == .handoff,
          isAtLargestDetent, verticalDirection < 0 || !activeScrollViewsAreAtStart()
        {
          panCoordinationMode = .scrollView
        } else if
          activeScrollCoordinationLevel == .handoff,
          verticalDirection < 0,
          activeScrollViewsArePulledPastStart()
        {
          // A bounce or refresh pull holds the content past its start, left to
          // it by a held start or still returning as the touch landed. As at
          // the lower boundary, the sheet waits until it clears: pinning now
          // would snap it back in a single frame.
          panCoordinationMode = .scrollView
          scrollViewOwnsLowerBoundary = true
          noteReleasedScrollViewsAtStart()
        } else {
          panCoordinationMode = .sheet
          lockActiveScrollViews(pinToStart: isAtLargestDetent, pinInitialStarts: true)
        }
      }
      gesture.setTranslation(.zero, in: self)

    case .changed:
      let delta = gesture.translation(in: self).y
      gesture.setTranslation(.zero, in: self)
      let range = activeDragRange ?? draggableRange(including: panStartingIndex)

      if
        activeScrollCoordinationLevel == .handoff,
        !activeScrollViewStates.isEmpty,
        panCoordinationMode == .scrollView
      {
        if scrollViewOwnsLowerBoundary {
          // Once the sheet is fully collapsed, or the pan began with the content
          // pulled past its start, downward movement belongs to native
          // bounce/RefreshControl. On reversal, the sheet expands as soon as
          // no scrollable is still pulled past its start: a quick
          // reversal crosses the resting offset between two updates, and a
          // scrollable that was scrolled when the sheet collapsed never lands
          // there, so waiting for an exact landing would leave the touch with
          // the content. A scrollable that did reach its start is pinned back
          // there: the two pans handle one update in no defined order, so the
          // reversal may already have scrolled it when the sheet sees it.
          noteReleasedScrollViewsAtStart()
          if delta < 0, !activeScrollViewsArePulledPastStart() {
            scrollViewOwnsLowerBoundary = false
            panCoordinationMode = .sheet
            lockActiveScrollViews()
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
        fitScrollIndicators(of: activeScrollViewStates)
        emitPosition()
      }
      pinActiveScrollViews()

      if
        activeScrollCoordinationLevel == .handoff,
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
        activeScrollCoordinationLevel == .handoff,
        !activeScrollViewStates.isEmpty,
        delta > 0,
        newTy >= maxTy - 0.5
      {
        // The sheet has no smaller detent to consume. Release subsequent
        // downward movement to native bounce and RefreshControl.
        panCoordinationMode = .scrollView
        scrollViewOwnsLowerBoundary = true
        noteReleasedScrollViewsAtStart()
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
        resumeInterruptedSpring()
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
    // A sideways start yields to horizontal recognizers, unless the start was
    // held for a vertical scrollable under the touch: the hold decided the
    // direction from vertical travel alone, since that scrollable's pan
    // recognizes alongside this one and yielding would let it scroll content
    // the sheet owns at this detent.
    let verticalDirection: CGFloat
    if panGesture.holdDecidedDirection {
      verticalDirection = panGesture.decidedVerticalDirection
    } else {
      let velocity = panGesture.velocity(in: self)
      guard abs(velocity.y) > abs(velocity.x) else { return false }
      verticalDirection = velocity.y
    }

    return canBeginPan(inDirection: verticalDirection)
  }

  private func canBeginPan(inDirection verticalDirection: CGFloat) -> Bool {
    let candidates = snapCandidateIndices(including: targetIndex)
    guard candidates.count > 1 else { return false }

    guard !initialScrollViewStates.isEmpty else {
      // Outside a scrollable, the sheet owns any direction in which it has a
      // detent available.
      let range = draggableRange(including: targetIndex)
      let currentTy = sheetContainerHeight - currentSheetHeight
      return verticalDirection < 0
        ? currentTy > range.minTy + 0.5
        : currentTy < range.maxTy - 0.5
    }

    let level = coordinationLevel(forVerticalVelocity: verticalDirection)
    guard level != .none else { return false }

    let range = draggableRange(including: targetIndex)
    let currentTy = sheetContainerHeight - currentSheetHeight
    let canMoveSheet = verticalDirection < 0
      ? currentTy > range.minTy + 0.5
      : currentTy < range.maxTy - 0.5
    guard canMoveSheet else { return false }

    if level == .handoff {
      return true
    }

    // Initial-only collapse is available at the largest detent only when all
    // scrollable ancestors were already at their visual start at touch-down:
    // a held start can scroll them before the pan begins, which must not
    // change the owner. Below the largest detent, this preserves the original
    // sheet-first owner selection in either direction.
    let isAtLargestDetent = currentTy <= range.minTy + 0.5
    if verticalDirection > 0, isAtLargestDetent {
      return initialScrollableChainAtStart
    }
    return true
  }
}

extension BottomSheetHostingView: UIGestureRecognizerDelegate {
  private func canCoordinate(with other: UIGestureRecognizer) -> Bool {
    guard
      hasAnyScrollCoordination,
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
    // Keep the touched scrollable chain and its initial eligibility. A held
    // start lets content move before recognition, so re-hit-testing the same
    // coordinate later could find a different nested scrollable.
    if gestureRecognizer === panGesture, panGesture.numberOfTouches == 0 {
      let location = touch.location(in: sheetContainer)
      let chain = scrollableAncestorChain(containing: location)
      initialScrollViewStates = chain.map {
        ActiveScrollViewState(
          scrollView: $0.scrollView,
          inverted: $0.inverted,
          wasAtStartWhenTouched: scrollViewIsAtStart($0.scrollView, inverted: $0.inverted)
        )
      }
      initialScrollableChainAtStart = initialScrollViewStates.allSatisfy(\.wasAtStartWhenTouched)
      fitScrollIndicators(of: initialScrollViewStates)
      let canHold = !holdDeferralScrollViews(at: location).isEmpty
      let canBeginUp = canHold && canBeginPan(inDirection: -1)
      let canBeginDown = canHold && canBeginPan(inDirection: 1)
      panGesture.prepareHold(
        for: touch,
        usable: .init(up: canBeginUp, down: canBeginDown),
        handoff: .init(
          up: canBeginUp && coordinationLevel(forVerticalVelocity: -1) == .handoff,
          down: canBeginDown && coordinationLevel(forVerticalVelocity: 1) == .handoff
        )
      )
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
      panGesture.isHoldingStart,
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
