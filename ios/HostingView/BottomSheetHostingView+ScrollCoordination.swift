import UIKit

private extension UIScrollView {
  var minimumRestingY: CGFloat {
    -adjustedContentInset.top
  }

  var maximumRestingY: CGFloat {
    max(minimumRestingY, contentSize.height - bounds.height + adjustedContentInset.bottom)
  }

  /// `y` clamped to the offsets the scroll view can rest at.
  func restingY(for y: CGFloat) -> CGFloat {
    min(max(y, minimumRestingY), maximumRestingY)
  }
}

/// A scroll view's indicator inset, as its caller set it and as the sheet last
/// applied it.
final class ScrollIndicatorFit {
  var callerInset: CGFloat
  var appliedInset: CGFloat

  init(inset: CGFloat) {
    callerInset = inset
    appliedInset = inset
  }
}

final class ActiveScrollViewState {
  weak var scrollView: UIScrollView?
  var pinnedOffset: CGPoint
  let inverted: Bool
  /// Whether the scroll view was at or before its visual start as the touch
  /// landed. A held start lets it scroll before the sheet's pan begins; the
  /// lock that begins the pan pins it back.
  let wasAtStartWhenTouched: Bool
  /// Whether the scroll view reached or passed its visual start while the
  /// sheet had left the touch to it: at its lowest detent, or until a pull the
  /// pan began with cleared. The lock that takes the touch back pins it there:
  /// the reversal update may already have scrolled it by a few points.
  var reachedStartWhileReleased = false
  /// The caller's preferences, captured as the sheet locks the scroll view and
  /// restored when it unlocks.
  var directionalLockEnabled: Bool
  var showsVerticalScrollIndicator: Bool
  private var contentOffsetObservation: NSKeyValueObservation?
  private var isRestoringPinnedOffset = false

  init(scrollView: UIScrollView, inverted: Bool, wasAtStartWhenTouched: Bool) {
    self.scrollView = scrollView
    pinnedOffset = scrollView.contentOffset
    self.inverted = inverted
    self.wasAtStartWhenTouched = wasAtStartWhenTouched
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

  /// Pins the touched scroll views while the sheet moves. Each is pinned at
  /// its visual start when asked for all of them, when it reached that start
  /// while released, or, for the lock that begins the pan, when it was there
  /// as the touch landed; otherwise where it rests now.
  func lockActiveScrollViews(pinToStart: Bool = false, pinInitialStarts: Bool = false) {
    guard !activeScrollViewStates.isEmpty else { return }

    for state in activeScrollViewStates {
      guard let scrollView = state.scrollView else { continue }
      if !activeScrollViewsLocked {
        // Captured here rather than at touch-down: a held start can outlive a
        // prop change, which the unlock would otherwise overwrite.
        state.directionalLockEnabled = scrollView.isDirectionalLockEnabled
        state.showsVerticalScrollIndicator = scrollView.showsVerticalScrollIndicator
      }
      let pinnedToStart = pinToStart
        || state.reachedStartWhileReleased
        || (pinInitialStarts && state.wasAtStartWhenTouched)
      state.reachedStartWhileReleased = false
      let pinnedOffset = pinnedToStart
        ? scrollStartOffset(for: scrollView, inverted: state.inverted)
        : restingOffset(for: scrollView)
      // Bounces stay on: with them off, a pinned scroll view at its edge sees no
      // offset to restore, and its pan keeps the travel, applying it as one
      // rubber-banded jump when the pan is released to it.
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
        // The resting range can change under a pin: a refresh that ends removes
        // its inset. Setting the offset above stops a fling, and with it the
        // bounce a scroll view released mid-drag would make, so this makes it.
        let restingOffset = CGPoint(x: state.pinnedOffset.x, y: scrollView.restingY(for: state.pinnedOffset.y))
        if restingOffset != state.pinnedOffset {
          scrollView.setContentOffset(restingOffset, animated: true)
        }
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

  /// The vertical scrollables a held start under the touch defers to. A
  /// horizontal recognizer on the way to the sheet container — the pan of a
  /// scroll view that can scroll sideways, or any other pan — keeps sideways
  /// movement instead, so nothing is deferred and the sheet yields to it as
  /// before.
  func holdDeferralScrollViews(at location: CGPoint) -> [UIScrollView] {
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
    CGPoint(x: scrollView.contentOffset.x, y: inverted ? scrollView.maximumRestingY : scrollView.minimumRestingY)
  }

  /// The scroll view's offset, clamped to where it can rest: a held start can
  /// leave it overscrolled, and pinning that offset would keep it there.
  private func restingOffset(for scrollView: UIScrollView) -> CGPoint {
    CGPoint(x: scrollView.contentOffset.x, y: scrollView.restingY(for: scrollView.contentOffset.y))
  }

  /// Whether the scroll view is at or before its visual start.
  func scrollViewIsAtStart(_ scrollView: UIScrollView, inverted: Bool) -> Bool {
    let edgeTolerance: CGFloat = 0.5
    let startY = scrollStartOffset(for: scrollView, inverted: inverted).y
    return inverted
      ? scrollView.contentOffset.y >= startY - edgeTolerance
      : scrollView.contentOffset.y <= startY + edgeTolerance
  }

  func scrollViewIsAtStart(_ state: ActiveScrollViewState) -> Bool {
    guard let scrollView = state.scrollView else { return true }
    return scrollViewIsAtStart(scrollView, inverted: state.inverted)
  }

  func activeScrollViewsAreAtStart() -> Bool {
    guard !activeScrollViewStates.isEmpty else { return false }
    return activeScrollViewStates.allSatisfy { scrollViewIsAtStart($0) }
  }

  /// Records which released scroll views are at or before their visual start,
  /// for the lock that takes the touch back.
  func noteReleasedScrollViewsAtStart() {
    for state in activeScrollViewStates where scrollViewIsAtStart(state) {
      state.reachedStartWhileReleased = true
    }
  }

  /// Whether a vertical scrollable in the touched chain is overscrolled
  /// before its visual start, by a bounce or a refresh control pull.
  func activeScrollViewsArePulledPastStart() -> Bool {
    let edgeTolerance: CGFloat = 0.5
    return activeScrollViewStates.contains { state in
      guard let scrollView = state.scrollView else { return false }
      let startY = scrollStartOffset(for: scrollView, inverted: state.inverted).y
      return state.inverted
        ? scrollView.contentOffset.y > startY + edgeTolerance
        : scrollView.contentOffset.y < startY - edgeTolerance
    }
  }

  /// Shortens each scroll view's indicator track by the part of it below the
  /// host. The content region is laid out against the tallest detent, so at a
  /// lower detent the scroll view extends past the bottom, and its indicator
  /// would be sized and placed for a height nobody can see.
  func fitScrollIndicators(of states: [ActiveScrollViewState]) {
    for state in states {
      guard let scrollView = state.scrollView else { continue }
      let hidden = max(0, scrollView.convert(scrollView.bounds, to: self).maxY - bounds.height)
      let insets = scrollView.verticalScrollIndicatorInsets
      let current = state.inverted ? insets.top : insets.bottom
      let fit = scrollIndicatorFits.object(forKey: scrollView) ?? ScrollIndicatorFit(inset: current)
      scrollIndicatorFits.setObject(fit, forKey: scrollView)
      // Any other inset than the one applied last is the caller's.
      if current != fit.appliedInset {
        fit.callerInset = current
      }
      fit.appliedInset = fit.callerInset + hidden
      guard fit.appliedInset != current else { continue }
      if state.inverted {
        scrollView.verticalScrollIndicatorInsets.top = fit.appliedInset
      } else {
        scrollView.verticalScrollIndicatorInsets.bottom = fit.appliedInset
      }
    }
  }

  func negotiationLevel(forVerticalVelocity velocityY: CGFloat) -> ScrollableNegotiationLevel {
    let rawValue = velocityY < 0 ? scrollableExpandNegotiation : scrollableCollapseNegotiation
    return ScrollableNegotiationLevel(clamping: rawValue)
  }

  /// The pan's vertical direction, negative up: the one its hold decided when
  /// the start was held, else the velocity's.
  func panVerticalDirection(_ gesture: UIPanGestureRecognizer) -> CGFloat {
    panGesture.holdDecidedDirection ? panGesture.decidedVerticalDirection : gesture.velocity(in: self).y
  }

  var hasAnyScrollableNegotiation: Bool {
    ScrollableNegotiationLevel(clamping: scrollableExpandNegotiation) != .none
      || ScrollableNegotiationLevel(clamping: scrollableCollapseNegotiation) != .none
  }
}
