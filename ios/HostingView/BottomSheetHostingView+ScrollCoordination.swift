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
/// applied it. The fit lives on the scroll view, so a list that moves to
/// another sheet hands its fit, and the caller's inset with it, to that sheet
/// instead of the new sheet taking the old sheet's adjustment for the caller's.
final class ScrollIndicatorFit {
  private static var key: UInt8 = 0

  var callerInset: CGFloat
  var appliedInset: CGFloat
  let inverted: Bool
  /// The sheet fitting the indicator now. Only it restores the caller's inset.
  weak var owner: AnyObject?

  init(inset: CGFloat, inverted: Bool) {
    callerInset = inset
    appliedInset = inset
    self.inverted = inverted
  }

  static func attached(to scrollView: UIScrollView) -> ScrollIndicatorFit? {
    objc_getAssociatedObject(scrollView, &key) as? ScrollIndicatorFit
  }

  static func attach(_ fit: ScrollIndicatorFit?, to scrollView: UIScrollView) {
    objc_setAssociatedObject(scrollView, &key, fit, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
  }
}

/// A scrub of a scroll view's indicator, and the sheet movement it hands off
/// to once it pushes past the content's start.
final class IndicatorDragState {
  struct Handoff {
    /// The finger's position and the sheet's translation as the sheet took over.
    let anchorY: CGFloat
    let anchorTy: CGFloat
    let startingIndex: Int
    /// The detents as the sheet took over, which the release resolves against,
    /// as a pan's does: a content-sized detent can change mid-scrub.
    let detentSpecs: [DetentSpec]
    let range: (minTy: CGFloat, maxTy: CGFloat)
    /// Holds the content at its start while the sheet moves.
    let pin: ActiveScrollViewState
    /// Whether the sheet reached its largest detent and left the scrub to the
    /// content again, to stretch past its start.
    var releasedAtLargestDetent = false
    /// How far the content was stretched past its start as the scrub came back
    /// from it, while the content follows the finger back to its start before
    /// the sheet moves on. Pinned straight at its start, it would jump.
    var unwindingStretch: CGFloat?
    /// The finger travel spent unwinding stretches, which the sheet does not
    /// follow.
    var lag: CGFloat = 0

    /// Where the finger brings the sheet back to where it took over.
    var effectiveAnchorY: CGFloat {
      anchorY + lag
    }

    /// Where the finger brings the sheet to its largest detent.
    var largestDetentY: CGFloat {
      effectiveAnchorY + range.minTy - anchorTy
    }

    /// Whether the sheet stands at its largest detent while the scrub moves
    /// the content.
    var contentOwnsScrub: Bool {
      releasedAtLargestDetent || unwindingStretch != nil
    }
  }

  weak var scrollView: UIScrollView?
  /// Whether the content was at its start as the scrub began: with `initial`
  /// coordination, only such a scrub may hand off to the sheet.
  let startedAtStart: Bool
  private(set) var lastY: CGFloat
  private var lastTime: CFTimeInterval
  /// The finger's vertical velocity, in points per second.
  private var velocity: CGFloat = 0
  var handoff: Handoff?
  /// Whether the settle a hand-off stopped emits `onSettle`, while that settle
  /// still has to be resumed: a hand-off that reverts leaves the sheet where
  /// the settle stopped.
  var interruptedSettleEmitsSettle: Bool?

  init(scrollView: UIScrollView, y: CGFloat, startedAtStart: Bool) {
    self.scrollView = scrollView
    self.startedAtStart = startedAtStart
    lastY = y
    lastTime = CACurrentMediaTime()
  }

  /// The velocity to release with. A finger that rests before lifting reports
  /// no further updates, so the last smoothed velocity would otherwise read as
  /// a flick.
  var releaseVelocity: CGFloat {
    CACurrentMediaTime() - lastTime > 0.1 ? 0 : velocity
  }

  func track(_ y: CGFloat) {
    let now = CACurrentMediaTime()
    let elapsed = now - lastTime
    if elapsed > 0 {
      // Smoothed over the last few updates, like a pan's release velocity.
      velocity = 0.6 * (y - lastY) / elapsed + 0.4 * velocity
    }
    lastY = y
    lastTime = now
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
    activeScrollCoordinationLevel = .none
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
    guard hasAnyScrollCoordination else { return [] }
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
      fitScrollIndicator(of: scrollView, inverted: state.inverted, shift: 0)
    }
  }

  private func fitScrollIndicator(of scrollView: UIScrollView) {
    fitScrollIndicator(of: scrollView, inverted: isInverted(scrollView), shift: 0)
  }

  /// Refits every scroll view fitted so far, as the sheet moves. A visible
  /// indicator then shrinks and grows with the sheet, as in a native sheet,
  /// rather than keeping the size of the detent it was last shown at. During a
  /// settle the model transform already holds the target, so `translation`
  /// passes the sheet's visual position. A scroll view that has left the sheet
  /// gets its caller's inset back and is no longer fitted, unless another sheet
  /// has taken its fit over.
  func refitScrollIndicators(atTranslation translation: CGFloat? = nil) {
    let shift = translation.map { $0 - sheetContainer.transform.ty } ?? 0
    for case let scrollView as UIScrollView in scrollIndicatorFits.keyEnumerator().allObjects {
      guard ScrollIndicatorFit.attached(to: scrollView)?.owner === self else {
        scrollIndicatorFits.removeObject(forKey: scrollView)
        continue
      }
      guard ownsScrollView(scrollView) else {
        releaseScrollIndicatorFit(of: scrollView)
        continue
      }
      fitScrollIndicator(of: scrollView, inverted: isInverted(scrollView), shift: shift)
    }
  }

  /// Gives every fitted scroll view its caller's inset back.
  func releaseScrollIndicatorFits() {
    for case let scrollView as UIScrollView in scrollIndicatorFits.keyEnumerator().allObjects {
      releaseScrollIndicatorFit(of: scrollView)
    }
  }

  /// Stops fitting the scroll view and, unless another sheet has taken its fit
  /// over, restores the caller's inset.
  private func releaseScrollIndicatorFit(of scrollView: UIScrollView) {
    scrollIndicatorFits.removeObject(forKey: scrollView)
    guard let fit = ScrollIndicatorFit.attached(to: scrollView), fit.owner === self else { return }
    restoreCallerInset(of: scrollView, fit: fit)
  }

  /// Restores the caller's inset on the edge the fit adjusted, unless
  /// something else set that edge since, and detaches the fit.
  private func restoreCallerInset(of scrollView: UIScrollView, fit: ScrollIndicatorFit) {
    ScrollIndicatorFit.attach(nil, to: scrollView)
    let insets = scrollView.verticalScrollIndicatorInsets
    if fit.inverted, insets.top == fit.appliedInset {
      scrollView.verticalScrollIndicatorInsets.top = fit.callerInset
    } else if !fit.inverted, insets.bottom == fit.appliedInset {
      scrollView.verticalScrollIndicatorInsets.bottom = fit.callerInset
    }
  }

  /// Whether the scroll view is still part of this sheet's content.
  func ownsScrollView(_ scrollView: UIScrollView) -> Bool {
    scrollView.isDescendant(of: sheetContainer)
  }

  /// Whether the scroll view or an ancestor in the sheet is flipped, as an
  /// inverted list is: its visual start, and its indicator's bottom, are then
  /// its content's end and the top inset.
  private func isInverted(_ scrollView: UIScrollView) -> Bool {
    var node: UIView? = scrollView
    while let view = node, view !== sheetContainer {
      if view.transform.d < 0 {
        return true
      }
      node = view.superview
    }
    return false
  }

  private func fitScrollIndicator(of scrollView: UIScrollView, inverted: Bool, shift: CGFloat) {
    if let fit = ScrollIndicatorFit.attached(to: scrollView), fit.inverted != inverted {
      // The list flipped: its indicator's bottom is the other edge now.
      restoreCallerInset(of: scrollView, fit: fit)
    }
    let hidden = max(0, scrollView.convert(scrollView.bounds, to: self).maxY + shift - bounds.height)
    let insets = scrollView.verticalScrollIndicatorInsets
    let current = inverted ? insets.top : insets.bottom
    // A fit another sheet made carries the caller's inset over, so its
    // adjustment isn't taken for the caller's.
    let fit = ScrollIndicatorFit.attached(to: scrollView)
      ?? ScrollIndicatorFit(inset: current, inverted: inverted)
    fit.owner = self
    ScrollIndicatorFit.attach(fit, to: scrollView)
    scrollIndicatorFits.setObject(fit, forKey: scrollView)
    // Any other inset than the one applied last is the caller's.
    if current != fit.appliedInset {
      fit.callerInset = current
    }
    fit.appliedInset = fit.callerInset + hidden
    guard fit.appliedInset != current else { return }
    if inverted {
      scrollView.verticalScrollIndicatorInsets.top = fit.appliedInset
    } else {
      scrollView.verticalScrollIndicatorInsets.bottom = fit.appliedInset
    }
  }

  /// Listens to the touched scroll views' indicator drags. A scrub moves the
  /// content as usual; once it pushes past the content's start, the rest of the
  /// upward travel expands the sheet, as a drag anywhere else in the content
  /// would. Indicator dragging is driven by a private recognizer, matched by
  /// class name: if a release renames it, scrubbing works as before and never
  /// moves the sheet.
  func observeScrollIndicatorDrags(of states: [ActiveScrollViewState]) {
    detachObsoleteIndicatorDrags()
    for state in states where !state.inverted {
      for recognizer in state.scrollView?.gestureRecognizers ?? []
        where NSStringFromClass(type(of: recognizer)).contains("KnobLongPress")
        && !observedIndicatorDrags.contains(recognizer)
      {
        recognizer.addTarget(self, action: #selector(handleScrollIndicatorDrag(_:)))
        observedIndicatorDrags.add(recognizer)
      }
    }
  }

  /// Stops listening to indicator drags of scroll views that have left the
  /// sheet: a list moved to another sheet would otherwise move both.
  private func detachObsoleteIndicatorDrags() {
    for recognizer in observedIndicatorDrags.allObjects
      where !((recognizer.view as? UIScrollView).map(ownsScrollView) ?? false)
    {
      detachIndicatorDrag(recognizer)
    }
  }

  private func detachIndicatorDrag(_ recognizer: UIGestureRecognizer) {
    recognizer.removeTarget(self, action: #selector(handleScrollIndicatorDrag(_:)))
    observedIndicatorDrags.remove(recognizer)
    if let drag = indicatorDrag, drag.scrollView == nil || drag.scrollView === recognizer.view {
      endIndicatorDrag(settle: true)
    }
  }

  /// Stops listening to every indicator drag and ends the one in progress, as
  /// the host resets.
  func detachIndicatorDrags() {
    endIndicatorDrag(settle: false)
    for recognizer in observedIndicatorDrags.allObjects {
      recognizer.removeTarget(self, action: #selector(handleScrollIndicatorDrag(_:)))
    }
    observedIndicatorDrags.removeAllObjects()
  }

  /// Ends the scrub in progress, unpinning its content. With `settle`, a sheet
  /// the scrub moved, or a settle it stopped, settles at the current detent.
  private func endIndicatorDrag(settle: Bool) {
    guard let drag = indicatorDrag else { return }
    indicatorDrag = nil
    if let handoff = drag.handoff {
      handoff.pin.stopPinning()
      isPanning = false
      if settle {
        snapToIndex(targetIndex, velocity: 0)
      }
    } else if settle, let emitSettle = drag.interruptedSettleEmitsSettle {
      snapToIndex(targetIndex, velocity: 0, emitIndexChange: false, emitSettle: emitSettle)
    }
  }

  @objc func handleScrollIndicatorDrag(_ recognizer: UIGestureRecognizer) {
    guard let scrollView = recognizer.view as? UIScrollView, ownsScrollView(scrollView) else {
      detachIndicatorDrag(recognizer)
      return
    }
    let y = recognizer.location(in: self).y

    switch recognizer.state {
    case .began:
      // An inverted list's start is at the bottom: its scrub never pushes past
      // it upward, so it only gets its indicator fitted.
      let inverted = isInverted(scrollView)
      indicatorDrag = inverted ? nil : IndicatorDragState(
        scrollView: scrollView,
        y: y,
        startedAtStart: scrollViewIsAtStart(scrollView, inverted: false)
      )
      fitScrollIndicator(of: scrollView)

    case .changed:
      guard let drag = indicatorDrag, drag.scrollView === scrollView else { return }
      let movedUp = y < drag.lastY
      drag.track(y)

      guard let handoff = drag.handoff else {
        if movedUp, scrollViewIsAtStart(scrollView, inverted: false), canExpandFromIndicator(drag) {
          beginIndicatorHandoff(drag, scrollView: scrollView, y: y)
        }
        return
      }

      let startY = scrollStartOffset(for: scrollView, inverted: false).y
      if y >= handoff.effectiveAnchorY {
        // Back below where the sheet took over: the scrub has the content again.
        handoff.pin.stopPinning()
        setSheetTranslation(handoff.anchorTy)
        refitScrollIndicators()
        emitPosition()
        drag.handoff = nil
        isPanning = false
        return
      }
      if y <= handoff.largestDetentY {
        if let stretch = handoff.unwindingStretch {
          let overscroll = handoff.largestDetentY - y
          if overscroll < stretch {
            // Unwinding: the content follows the finger back to its start.
            handoff.pin.pinnedOffset.y = startY - overscroll
            handoff.pin.restorePinnedOffset()
            return
          }
          // Back up to where the unwinding began: the scrub has the content
          // again, stretched as far as it was then.
          drag.handoff?.unwindingStretch = nil
          drag.handoff?.releasedAtLargestDetent = true
          handoff.pin.stopPinning()
          return
        }
        // The sheet is at its largest detent. As in a native sheet, the rest of
        // the scrub goes to the content: it stretches past its start and the
        // indicator squeezes against the top. Pinned instead, the indicator
        // is laid out for the stretch UIKit asked for and then left there as
        // the pin undoes it, drawn above the list.
        if !handoff.releasedAtLargestDetent {
          drag.handoff?.releasedAtLargestDetent = true
          handoff.pin.stopPinning()
          setSheetTranslation(handoff.range.minTy)
          refitScrollIndicators()
          emitPosition()
        }
        return
      }
      if handoff.releasedAtLargestDetent {
        // Back below that point. A content still stretched past its start
        // unwinds with the finger first; then the sheet follows it down.
        drag.handoff?.releasedAtLargestDetent = false
        let stretch = startY - scrollView.contentOffset.y
        if stretch > 0.5 {
          drag.handoff?.lag += y - handoff.largestDetentY + stretch
          drag.handoff?.unwindingStretch = stretch
          handoff.pin.startPinning(at: scrollView.contentOffset)
          return
        }
        handoff.pin.startPinning(at: CGPoint(x: scrollView.contentOffset.x, y: startY))
      } else if handoff.unwindingStretch != nil {
        // Unwound: the content is at its start, and the sheet follows.
        drag.handoff?.unwindingStretch = nil
        handoff.pin.pinnedOffset.y = startY
      }
      handoff.pin.restorePinnedOffset()
      let newTy = max(handoff.range.minTy, handoff.anchorTy + y - handoff.effectiveAnchorY)
      if abs(newTy - sheetContainer.transform.ty) > .ulpOfOne {
        setSheetTranslation(newTy)
        refitScrollIndicators()
        emitPosition()
      }

    case .ended, .cancelled, .failed:
      guard let drag = indicatorDrag, drag.scrollView === scrollView else { return }
      guard let handoff = drag.handoff else {
        // The sheet gave the scrub back where it took over. If taking over
        // stopped a settle, finish it, as a pan that never moved the sheet does.
        endIndicatorDrag(settle: true)
        return
      }
      indicatorDrag = nil
      handoff.pin.stopPinning()
      isPanning = false
      // Left to the content at the largest detent, the sheet stands still: the
      // finger's velocity is the content's, not the sheet's.
      let velocity = handoff.contentOwnsScrub ? 0 : drag.releaseVelocity
      let currentHeight = sheetContainerHeight - sheetContainer.transform.ty
      let index = handoff.detentSpecs.count == detentSpecs.count
        ? snapshotBestSnapIndex(
          for: currentHeight,
          velocity: velocity,
          including: handoff.startingIndex,
          in: handoff.detentSpecs
        )
        : bestSnapIndex(for: currentHeight, velocity: velocity, including: handoff.startingIndex)
      snapToIndex(index, velocity: velocity)

    default:
      break
    }
  }

  /// Whether a scrub at the content's start may expand the sheet. `handoff`
  /// lets any scrub take the sheet; `initial` only one that began at the
  /// start, as a drag elsewhere in the content picks its owner at touch-down.
  /// A settle toward the largest detent can still be caught: the visual
  /// position counts, not the settle's target.
  private func canExpandFromIndicator(_ drag: IndicatorDragState) -> Bool {
    switch coordinationLevel(forVerticalVelocity: -1) {
    case .none:
      return false
    case .initial:
      guard drag.startedAtStart else { return false }
    case .handoff:
      break
    }
    let range = draggableRange(including: targetIndex)
    return currentTranslationY > range.minTy + 0.5
  }

  private func beginIndicatorHandoff(_ drag: IndicatorDragState, scrollView: UIScrollView, y: CGFloat) {
    if activeSpring != nil {
      if drag.interruptedSettleEmitsSettle == nil {
        drag.interruptedSettleEmitsSettle = activeSpringEmitsSettle
      }
      cancelActiveSpring()
    }
    isPanning = true
    pendingIndex = nil
    scrimPinnedFull = false
    let pin = ActiveScrollViewState(scrollView: scrollView, inverted: false, wasAtStartWhenTouched: true)
    pin.startPinning(at: scrollStartOffset(for: scrollView, inverted: false))
    drag.handoff = IndicatorDragState.Handoff(
      anchorY: y,
      anchorTy: sheetContainer.transform.ty,
      startingIndex: targetIndex,
      detentSpecs: detentSpecs,
      range: snapshotDraggableRange(including: targetIndex, in: detentSpecs),
      pin: pin
    )
  }

  func coordinationLevel(forVerticalVelocity velocityY: CGFloat) -> ScrollCoordinationLevel {
    let rawValue = velocityY < 0 ? scrollExpandCoordination : scrollCollapseCoordination
    return ScrollCoordinationLevel(clamping: rawValue)
  }

  /// The pan's vertical direction, negative up: the one its hold decided when
  /// the start was held, else the velocity's.
  func panVerticalDirection(_ gesture: UIPanGestureRecognizer) -> CGFloat {
    panGesture.holdDecidedDirection ? panGesture.decidedVerticalDirection : gesture.velocity(in: self).y
  }

  var hasAnyScrollCoordination: Bool {
    ScrollCoordinationLevel(clamping: scrollExpandCoordination) != .none
      || ScrollCoordinationLevel(clamping: scrollCollapseCoordination) != .none
  }
}
