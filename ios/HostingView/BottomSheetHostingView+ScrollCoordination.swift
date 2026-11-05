import UIKit

final class ActiveScrollViewState {
  weak var scrollView: UIScrollView?
  var pinnedOffset: CGPoint
  let inverted: Bool
  let bounces: Bool
  let directionalLockEnabled: Bool
  let showsVerticalScrollIndicator: Bool
  private var contentOffsetObservation: NSKeyValueObservation?
  private var isRestoringPinnedOffset = false

  init(scrollView: UIScrollView, inverted: Bool) {
    self.scrollView = scrollView
    pinnedOffset = scrollView.contentOffset
    self.inverted = inverted
    bounces = scrollView.bounces
    directionalLockEnabled = scrollView.isDirectionalLockEnabled
    showsVerticalScrollIndicator = scrollView.showsVerticalScrollIndicator
  }

  func startPinning(at offset: CGPoint) {
    stopPinning()
    pinnedOffset = offset
    guard let scrollView else { return }

    contentOffsetObservation = scrollView.observe(\.contentOffset, options: [.new]) {
      [weak self] observedScrollView, _ in
      self?.restorePinnedOffset(on: observedScrollView)
    }
    restorePinnedOffset(on: scrollView)
  }

  func restorePinnedOffset() {
    guard let scrollView else { return }
    restorePinnedOffset(on: scrollView)
  }

  func stopPinning() {
    contentOffsetObservation?.invalidate()
    contentOffsetObservation = nil
    isRestoringPinnedOffset = false
  }

  private func restorePinnedOffset(on scrollView: UIScrollView) {
    guard
      contentOffsetObservation != nil,
      !isRestoringPinnedOffset,
      scrollView.contentOffset != pinnedOffset
    else {
      return
    }
    isRestoringPinnedOffset = true
    scrollView.setContentOffset(pinnedOffset, animated: false)
    isRestoringPinnedOffset = false
  }
}

extension BottomSheetHostingView {
  func setContentInteractionEnabled(_ isEnabled: Bool) {
    if isContentInteractionDisabled == !isEnabled {
      return
    }

    for subview in clipContainer.subviews {
      subview.isUserInteractionEnabled = isEnabled
    }
    isContentInteractionDisabled = !isEnabled
  }

  func cancelContentTouchesForPanIfNeeded() {
    guard !didCancelTouchesForPan else { return }
    didCancelTouchesForPan = true
    if let handler = surfaceTouchHandler {
      handler.isEnabled = false
      handler.isEnabled = true
    }
  }

  func lockActiveScrollViews(pinToStart: Bool = false) {
    guard !activeScrollViewStates.isEmpty else { return }

    for state in activeScrollViewStates {
      guard let scrollView = state.scrollView else { continue }
      let pinnedOffset = pinToStart
        ? scrollStartOffset(for: scrollView, inverted: state.inverted)
        : scrollView.contentOffset
      scrollView.bounces = false
      scrollView.isDirectionalLockEnabled = true
      scrollView.showsVerticalScrollIndicator = false
      state.startPinning(at: pinnedOffset)
    }
    activeScrollViewsLocked = true
  }

  func pinActiveScrollViews() {
    guard activeScrollViewsLocked else { return }
    for state in activeScrollViewStates {
      state.restorePinnedOffset()
    }
  }

  func unlockActiveScrollViews() {
    guard activeScrollViewsLocked else { return }
    for state in activeScrollViewStates {
      state.stopPinning()
      guard let scrollView = state.scrollView else { continue }
      scrollView.bounces = state.bounces
      scrollView.isDirectionalLockEnabled = state.directionalLockEnabled
      scrollView.showsVerticalScrollIndicator = state.showsVerticalScrollIndicator
    }
    activeScrollViewsLocked = false
  }

  func cancelActiveScrollViewPans() {
    let states = activeScrollViewStates
    for state in states {
      guard let scrollView = state.scrollView else { continue }
      scrollView.panGestureRecognizer.isEnabled = false
      scrollView.setContentOffset(state.pinnedOffset, animated: false)
    }

    // The sheet and scroll-view recognizers receive the same release event,
    // but UIKit does not guarantee which target callback runs first. Re-enable
    // on the next main-loop turn so the scroll view cannot process the tail of
    // this release after us and start decelerating with the fling velocity.
    DispatchQueue.main.async {
      for state in states {
        guard let scrollView = state.scrollView else { continue }
        scrollView.setContentOffset(state.pinnedOffset, animated: false)
        scrollView.panGestureRecognizer.isEnabled = true
      }
    }
  }

  func finishScrollViewCoordination() {
    unlockActiveScrollViews()
    activeScrollViewStates = []
    panCoordinationMode = .sheet
    activeScrollableNegotiationLevel = .none
    didMoveSheetDuringPan = false
    didCancelTouchesForPan = false
    scrollViewOwnsLowerBoundary = false
  }

  func isVerticallyScrollable(_ scrollView: UIScrollView) -> Bool {
    guard scrollView.isScrollEnabled else { return false }
    let verticalInset = scrollView.adjustedContentInset.top + scrollView.adjustedContentInset.bottom
    let visibleHeight = max(0, scrollView.bounds.height - verticalInset)
    return scrollView.alwaysBounceVertical || scrollView.contentSize.height > visibleHeight
  }

  /// The view a touch at `location` lands on. UIKit hit-testing honors RN
  /// `pointerEvents`, `zIndex`, hidden and transparent views, so a view drawn
  /// over a scrollable (e.g. an absolutely positioned header) shields it.
  /// Content interaction is switched off while a sheet-owned pan settles; hit
  /// test as if it were on so a grab mid-settle still resolves the content.
  private func touchTarget(at location: CGPoint) -> UIView? {
    let wasDisabled = isContentInteractionDisabled
    if wasDisabled {
      setContentInteractionEnabled(true)
    }
    defer {
      if wasDisabled {
        setContentInteractionEnabled(false)
      }
    }
    return sheetContainer.hitTest(location, with: nil)
  }

  /// The views under a touch at `location`, from the hit-test target up to,
  /// excluding, the sheet container.
  private func touchedViews(at location: CGPoint) -> [UIView] {
    guard let target = touchTarget(at: location) else {
      return []
    }

    var views: [UIView] = []
    var node: UIView? = target
    while let view = node, view !== sheetContainer {
      views.append(view)
      node = view.superview
    }
    return views
  }

  func scrollableAncestorChain(
    containing location: CGPoint
  ) -> [(scrollView: UIScrollView, inverted: Bool)] {
    scrollableAncestorChain(in: touchedViews(at: location))
  }

  func scrollableAncestorChain(
    in touchedViews: [UIView]
  ) -> [(scrollView: UIScrollView, inverted: Bool)] {
    var inverted = false
    var result: [(scrollView: UIScrollView, inverted: Bool)] = []
    for view in touchedViews.reversed() {
      inverted = inverted || view.transform.d < 0
      if let candidate = view as? UIScrollView, isVerticallyScrollable(candidate) {
        result.append((candidate, inverted))
      }
    }
    return result
  }

  /// The vertical scrollables a sideways start under the touch defers to. A
  /// horizontal recognizer on the way to the sheet container — the pan of a
  /// scroll view that can scroll sideways, or any other pan — keeps sideways
  /// movement instead, so nothing is deferred and the sheet yields to it as
  /// before.
  func sidewaysDeferralScrollViews(at location: CGPoint) -> [UIScrollView] {
    guard hasAnyScrollableNegotiation else { return [] }
    let views = touchedViews(at: location)
    let chain = scrollableAncestorChain(in: views).map(\.scrollView)
    guard !chain.isEmpty else { return [] }
    for view in views {
      for recognizer in view.gestureRecognizers ?? [] {
        guard recognizer.isEnabled, recognizer is UIPanGestureRecognizer else { continue }
        if
          let scrollView = view as? UIScrollView,
          recognizer === scrollView.panGestureRecognizer,
          chain.contains(where: { $0 === scrollView }),
          !isHorizontallyScrollable(scrollView)
        {
          continue
        }
        return []
      }
    }
    return chain
  }

  private func isHorizontallyScrollable(_ scrollView: UIScrollView) -> Bool {
    let horizontalInset = scrollView.adjustedContentInset.left + scrollView.adjustedContentInset.right
    let visibleWidth = max(0, scrollView.bounds.width - horizontalInset)
    return scrollView.alwaysBounceHorizontal || scrollView.contentSize.width > visibleWidth
  }

  private func scrollStartOffset(for scrollView: UIScrollView, inverted: Bool) -> CGPoint {
    let minimumY = -scrollView.adjustedContentInset.top
    guard inverted else {
      return CGPoint(x: scrollView.contentOffset.x, y: minimumY)
    }
    let maximumY = max(
      minimumY,
      scrollView.contentSize.height - scrollView.bounds.height
        + scrollView.adjustedContentInset.bottom
    )
    return CGPoint(x: scrollView.contentOffset.x, y: maximumY)
  }

  func activeScrollViewsAreAtStart(requireExactOffset: Bool = false) -> Bool {
    let edgeTolerance: CGFloat = 0.5
    guard !activeScrollViewStates.isEmpty else { return false }
    return activeScrollViewStates.allSatisfy { state in
      guard let scrollView = state.scrollView else { return true }
      let startY = scrollStartOffset(for: scrollView, inverted: state.inverted).y
      if requireExactOffset {
        return abs(scrollView.contentOffset.y - startY) <= edgeTolerance
      }
      return state.inverted
        ? scrollView.contentOffset.y >= startY - edgeTolerance
        : scrollView.contentOffset.y <= startY + edgeTolerance
    }
  }

  func negotiationLevel(forVerticalVelocity velocityY: CGFloat) -> ScrollableNegotiationLevel {
    let rawValue = velocityY < 0 ? scrollableExpandNegotiation : scrollableCollapseNegotiation
    return ScrollableNegotiationLevel(clamping: rawValue)
  }

  var hasAnyScrollableNegotiation: Bool {
    ScrollableNegotiationLevel(clamping: scrollableExpandNegotiation) != .none
      || ScrollableNegotiationLevel(clamping: scrollableCollapseNegotiation) != .none
  }

  func scrollableChainIsAtStart(
    _ chain: [(scrollView: UIScrollView, inverted: Bool)]
  ) -> Bool {
    let edgeTolerance: CGFloat = 0.5
    return chain.allSatisfy { item in
      let startY = scrollStartOffset(for: item.scrollView, inverted: item.inverted).y
      return item.inverted
        ? item.scrollView.contentOffset.y >= startY - edgeTolerance
        : item.scrollView.contentOffset.y <= startY + edgeTolerance
    }
  }
}
