import UIKit

extension BottomSheetHostingView {
  /// The default top inset: the part of the window's top (status bar / notch)
  /// inset that actually overlaps this view. Measured from real window geometry
  /// in every mode — inline, portal, and overlay — so no JS-estimated dimensions
  /// are involved. Before the view is in a window, nothing overlaps it.
  private var statusBarOverlap: CGFloat {
    guard let window else { return 0 }
    let originYInWindow = convert(CGPoint.zero, to: window).y
    return min(max(0, window.safeAreaInsets.top - originYInWindow), bounds.height)
  }

  /// How much the content is scaled for a card this wide: its width over the
  /// width of the widest card, which the content is laid out against.
  func contentScale(forCardWidth width: CGFloat) -> CGFloat {
    let widest = placeCard(insets: widestCardInsets, hostWidth: bounds.width).width
    return widest > 0 ? width / widest : 1
  }

  func contentScale(for insets: DetentInsets) -> CGFloat {
    contentScale(forCardWidth: placeCard(insets: insets, hostWidth: bounds.width).width)
  }

  /// The card's horizontal span for its side insets: the space they leave,
  /// capped at the maximum width, where the placement puts it in the whole
  /// host, moved only as far as needed to keep clear of the insets. Mirrors
  /// `placeCard` in `cardPlacement.ts`.
  func placeCard(insets: DetentInsets, hostWidth: CGFloat) -> (minX: CGFloat, width: CGFloat) {
    let available = max(0, hostWidth - insets.left - insets.right)
    let width = cardMaxWidth > 0 ? min(cardMaxWidth, available) : available
    let preferred = cardPlacementBias * (hostWidth - width)
    let rightmost = max(insets.left, hostWidth - insets.right - width)
    return (min(max(preferred, insets.left), rightmost), width)
  }

  /// The height of the content region: the Yoga box the sheet's in-flow
  /// children (the `flex: 1` content wrapper) are laid out in. It is the
  /// tallest height the sheet can reach — the card's height at the tallest
  /// detent, clamped to the space its insets leave — rather than the host's,
  /// so a scrollable under a fixed or percentage tallest detent is laid out
  /// against the height it can actually show instead of leaving its last rows
  /// below the card's edge. In layout units, so divided by the scale at that
  /// detent: a narrow card's region can outgrow the sheet's node.
  private var contentRegionHeight: CGFloat {
    guard let tallest = rawDetentSpecs.last else { return bounds.height }
    let height = cardHeightBound(for: tallest)
    let scale = contentScale(for: tallest.insets)
    return scale > 0 ? height / scale : height
  }

  /// The tallest the card gets at a detent. A `content` detent is bounded by the
  /// space its insets leave alone: its resolved height is measured from the
  /// content region, so bounding the region by it would be circular.
  private func cardHeightBound(for spec: RawDetentSpec) -> CGFloat {
    let available = availableCardHeight(for: spec)
    let bound: CGFloat
    switch spec.kind {
    case .points:
      bound = spec.value
    case .percentage:
      bound = available * spec.value
    case .content:
      bound = available
    }
    return min(max(0, bound), available)
  }

  /// The tallest the card can be at a detent: the host's height, less the
  /// detent's top and bottom insets. Percentages resolve against it, so `100%`
  /// fits.
  private func availableCardHeight(for spec: RawDetentSpec) -> CGFloat {
    max(0, bounds.height - (spec.insets.top ?? statusBarOverlap) - spec.insets.bottom)
  }

  /// The natively measured inset of the content region: the gap between the
  /// sheet's height and `contentRegionHeight`, negative when the region outgrows
  /// the sheet. Reported into the shadow tree, where it sizes the content wrapper.
  public var contentRegionInset: CGFloat {
    bounds.height - contentRegionHeight
  }

  /// How far the surface extends below the sheet, in layout units, so that once
  /// scaled it still covers the card. A card's height and width are linear in the
  /// sheet's position between detents, so their ratio peaks at a detent. A detents
  /// change can leave the card above all of them, so its current height counts too,
  /// and `previous`, the overflow last reported, stays until the sheet settles.
  /// A `content` detent counts at its bound rather than its measured height on
  /// purpose: a surface following the measurement would be a commit behind a card
  /// that grows, showing a gap, while one that's too tall is just clipped.
  /// Reported into the shadow tree.
  public func surfaceOverflow(holding previous: CGFloat) -> CGFloat {
    var current: CGFloat = 0
    if hasLaidOut {
      let card = cardRect(forPosition: sheetContainerHeight - currentTranslationY)
      let scale = contentScale(forCardWidth: card.width)
      current = scale > 0 ? card.height / scale : card.height
    }
    let tallest = rawDetentSpecs.reduce(max(bounds.height, current)) { tallest, spec in
      let scale = contentScale(for: spec.insets)
      return scale > 0 ? max(tallest, cardHeightBound(for: spec) / scale) : tallest
    }
    let overflow = tallest - bounds.height
    return activeSpring != nil || isPanning ? max(overflow, previous) : overflow
  }

  /// Stable coordinate base for the sheet container. The container is sized to
  /// the full available height rather than the tallest detent, so it stays a
  /// fixed-size canvas: when content — and thus the `content` detent — shrinks,
  /// the container does not collapse underneath the sheet, leaving room to
  /// animate the sheet down to its new height. The surface fills this canvas, so
  /// the area below the shrunken content stays covered throughout.
  var sheetContainerHeight: CGFloat {
    bounds.height
  }

  private func resolveDetentSpecs() -> [DetentSpec]? {
    let maxHeight = sheetContainerHeight
    let measuredContentHeight = maxHeight > 0 ? validContentHeight : nil
    var resolvedDetents: [DetentSpec] = []
    resolvedDetents.reserveCapacity(rawDetentSpecs.count)

    for (index, spec) in rawDetentSpecs.enumerated() {
      // Checked against the host's measured width, as positions are against its
      // height. A closed detent's insets are 0.
      let sideGaps = spec.insets.left + spec.insets.right
      if bounds.width > 0, sideGaps >= bounds.width {
        reportInvalidDetent(
          "Invalid bottom sheet detent at index \(index): `insets.left` and `insets.right` add up to \(sideGaps), which leaves no room for the sheet in its \(bounds.width)-point-wide host. Keep their sum below the host's width."
        )
        return nil
      }
      let available = availableCardHeight(for: spec)
      let height: CGFloat
      switch spec.kind {
      case .points:
        height = spec.value
      case .percentage:
        height = available * spec.value
      case .content:
        height =
          measuredContentHeight.map { $0 * contentScale(for: spec.insets) }
            ?? unresolvedContentDetentHeight(after: index, maxHeight: maxHeight)
      }
      let resolvedHeight = min(max(0, height), available)
      let position = resolvedHeight > 0 ? resolvedHeight + spec.insets.bottom : 0
      if let previous = resolvedDetents.last, position < previous.position {
        reportInvalidDetent(
          "Invalid bottom sheet detent at index \(index): resolved position \(position) is lower than previous detent position \(previous.position). Detents must be passed in ascending order."
        )
        return nil
      }
      resolvedDetents.append(
        DetentSpec(position: position, programmatic: spec.programmatic, insets: spec.insets)
      )
    }

    lastReportedInvalidDetentMessage = nil
    return resolvedDetents
  }

  /// Reports a detent that can't be resolved, once until the detents resolve again.
  private func reportInvalidDetent(_ message: String) {
    guard lastReportedInvalidDetentMessage != message else { return }
    lastReportedInvalidDetentMessage = message
    eventDelegate?.bottomSheetHostingView(self, didReportError: message)
  }

  private func unresolvedContentDetentHeight(after index: Int, maxHeight: CGFloat) -> CGFloat {
    guard index + 1 < rawDetentSpecs.count else {
      return maxHeight
    }
    for spec in rawDetentSpecs[(index + 1)...] {
      let available = availableCardHeight(for: spec)
      let height: CGFloat
      switch spec.kind {
      case .points:
        height = spec.value
      case .percentage:
        height = available * spec.value
      case .content:
        continue
      }
      // Whatever its inset, this detent must not rise above the next one's top.
      return min(max(0, height), available) + spec.insets.bottom
        - rawDetentSpecs[index].insets.bottom
    }
    return maxHeight
  }

  func refreshDetentsFromLayout() {
    resolveDetentsFromLayout()
    replayPendingIndexIfResolved()
  }

  private func resolveDetentsFromLayout() {
    // While detached from a window (e.g. mid-commit, when Fabric reparents the
    // host as ancestor view flattening changes), the status bar's overlap is not
    // computable — its zero fallback would register as a detents change and
    // trigger a spurious re-anchor snap that clobbers any queued snap request.
    // Stay inert; didMoveToWindow refreshes geometry on reattach.
    if hasLaidOut, window == nil {
      return
    }
    refreshContentHeightMarker()
    if !isPanning {
      activeDragRange = nil
      activeDragDetentSpecs = nil
    }
    if hasLaidOut, isInvalidContentDetentTarget(targetIndex) {
      updateScrim()
      return
    }

    guard let resolvedDetents = resolveDetentSpecs() else {
      updateScrim()
      return
    }
    // Also fall through when only the height changed: the specs store positions,
    // but translations derive from it, so they must be re-anchored even
    // when the resolved detents are unchanged.
    guard resolvedDetents != detentSpecs || sheetContainerHeight != lastAppliedMaxDetentHeight
    else {
      updateScrim()
      return
    }

    // The re-anchor math below preserves the on-screen sheet height across the
    // geometry change, so it must relate the current translation to the height it
    // was computed against — not the freshly resolved one.
    let previousMaxHeight =
      lastAppliedMaxDetentHeight.isFinite ? lastAppliedMaxDetentHeight : sheetContainerHeight
    // Whether the scrim is currently fully opaque, i.e. the sheet is settled at
    // or above the first non-zero detent. If so, a detent resize must not dip
    // the scrim while the sheet re-anchors to the new geometry.
    let wasScrimFull = hasLaidOut
      && !isPanning
      && modal
      && firstOpenDetentPosition > 0
      && currentSheetHeight + 0.5 >= firstOpenDetentPosition
    scrimPinnedFull = scrimPinnedFull || wasScrimFull
    detentSpecs = resolvedDetents

    guard bounds.width > 0, bounds.height > 0, !detentSpecs.isEmpty else {
      return
    }

    if hasLaidOut, !isPanning {
      targetIndex = max(0, min(detentSpecs.count - 1, targetIndex))
      let newMaxHeight = sheetContainerHeight
      let targetTy = translationY(for: targetIndex)

      if activeSpring != nil {
        let shouldEmitSettle = activeSpringEmitsSettle
        let visualTy = cancelActiveSpring()
        // Re-anchor the in-flight position to the new container height so the
        // sheet surface keeps the same on-screen height across the resize.
        let visibleHeight = previousMaxHeight - visualTy
        let reanchoredTy = min(max(newMaxHeight - visibleHeight, 0), newMaxHeight)
        setSheetTranslation(reanchoredTy)
        emitPosition()
        snapToIndex(
          targetIndex,
          velocity: 0,
          emitIndexChange: false,
          emitSettle: shouldEmitSettle,
          preserveScrimPin: true
        )
      } else {
        let currentVisibleHeight = previousMaxHeight - currentTranslationY
        let targetHeight = detent(at: targetIndex).position
        let shouldAnimateHeight = shouldAnimateContentHeight(at: targetIndex)
        if abs(targetHeight - currentVisibleHeight) <= 0.5 {
          // No meaningful change.
          setSheetTranslation(targetTy)
          emitPosition()
          scrimPinnedFull = false
        } else if !shouldAnimateHeight {
          setSheetTranslation(targetTy)
          emitPosition()
          scrimPinnedFull = false
        } else {
          // The content detent changed (grew or shrank): re-anchor at the
          // current visible height, then animate to the new target. The surface
          // covers the full sheet, so a shrink no longer exposes blank space.
          let startTy = min(max(newMaxHeight - currentVisibleHeight, 0), newMaxHeight)
          setSheetTranslation(startTy)
          emitPosition()
          snapToIndex(
            targetIndex,
            velocity: 0,
            emitIndexChange: false,
            emitSettle: false,
            preserveScrimPin: true
          )
        }
      }
    }
  }

  private func shouldAnimateContentHeight(at index: Int) -> Bool {
    guard rawDetentSpecs.indices.contains(index) else {
      return animateContentHeight
    }
    return animateContentHeight || rawDetentSpecs[index].kind != .content
  }

  func refreshContentHeightMarker() {
    let marker = findContentHeightMarker()
    guard marker !== contentHeightMarker else { return }
    stopObservingContentHeightMarker()
    contentHeightMarker = marker
    if let marker {
      // The marker's frame is updated by React Native when content above it
      // resizes; observe its layer so we can re-resolve detents immediately
      // instead of waiting for an unrelated layout pass. This is the iOS
      // counterpart to Android's OnLayoutChangeListener on the marker.
      marker.layer.addObserver(
        self, forKeyPath: "position", options: [], context: &Self.markerObservationContext
      )
      marker.layer.addObserver(
        self, forKeyPath: "bounds", options: [], context: &Self.markerObservationContext
      )
    }
  }

  func stopObservingContentHeightMarker() {
    guard let marker = contentHeightMarker else { return }
    marker.layer.removeObserver(
      self, forKeyPath: "position", context: &Self.markerObservationContext
    )
    marker.layer.removeObserver(
      self, forKeyPath: "bounds", context: &Self.markerObservationContext
    )
    contentHeightMarker = nil
  }

  /// Called once a Fabric mounting transaction has finished applying layout,
  /// so the content wrapper and its marker both reflect the committed inset,
  /// which becomes the applied one. A `content` detent held for that layout
  /// (see isContentRegionChangePending) can now be resolved. Per-view layout
  /// hooks cannot do this: they fire mid-transaction, when the host or the
  /// wrapper may already have its new geometry while the marker still has its
  /// old position. The marker alone is not enough either: it does not move
  /// for intrinsic content.
  public func contentRegionLayoutDidApply() {
    appliedContentRegionInset = committedContentRegionInset
    // The transform pins the content by its center, which this layout may move.
    applyCardGeometry(forPosition: sheetContainerHeight - sheetContainer.transform.ty)
    // So does a running settle's copy of it: the settle usually starts before
    // the layout of the same transaction lands. Commits run per frame, so only
    // rebuild it when the content actually moved.
    if isContentAnimationStale {
      addContentAnimation()
    }
    guard isContentRegionChangePending else { return }
    refreshDetentsFromLayout()
  }

  /// The content wrapper: the surface is its sibling, so skip it and the
  /// wrapper is whatever else the sheet mounts.
  func findContentView() -> UIView? {
    clipContainer.subviews.first(where: { $0 !== surfaceView })
  }

  private func findContentHeightMarker() -> UIView? {
    // Always read the marker from the content, never from the surface.
    guard let contentView = findContentView() else { return nil }
    // The marker is identified by the nativeID the JS side gives it, not by
    // its position: Fabric mounts siblings sorted by zIndex, so any content
    // child with a positive zIndex lands after the marker in the wrapper.
    return contentView.subviews.first { nativeID(of: $0) == Self.contentHeightMarkerNativeID }
  }

  private func nativeID(of view: UIView) -> String? {
    // Fabric stores the `nativeID` prop in RCTViewComponentView's `nativeId`
    // property. Its header is C++ and cannot be imported here, so read the
    // property by name; views that do not declare it are never the marker.
    guard view.responds(to: NSSelectorFromString("nativeId")) else { return nil }
    return view.value(forKey: "nativeId") as? String
  }

  private var currentContentHeight: CGFloat? {
    guard let marker = contentHeightMarker else { return nil }
    return marker.frame.minY.isFinite ? marker.frame.minY : nil
  }

  private var validContentHeight: CGFloat? {
    if isContentRegionChangePending {
      // The content — and thus its marker — is laid out against the applied
      // inset. Until that is the inset the current detents call for, the
      // measurement describes the previous region and is treated as
      // unavailable.
      let isAppliedInsetCurrent = abs(appliedContentRegionInset - contentRegionInset) <= 0.01
      guard appliedContentRegionInset.isFinite, isAppliedInsetCurrent else {
        return nil
      }
      isContentRegionChangePending = false
    }
    guard let height = currentContentHeight, height.isFinite, height > 0 else {
      return nil
    }
    return height
  }

  func isInvalidContentDetentTarget(_ index: Int) -> Bool {
    guard rawDetentSpecs.indices.contains(index) else {
      return false
    }
    switch rawDetentSpecs[index].kind {
    case .points, .percentage:
      return false
    case .content:
      return validContentHeight == nil
    }
  }
}
