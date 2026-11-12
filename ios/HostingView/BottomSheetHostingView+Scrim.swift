import UIKit

extension BottomSheetHostingView {
  public var isModalAccessibilityActive: Bool {
    isScrimVisible
  }

  /// Whether the sheet is on screen: placed by its first layout and not hidden
  /// at its closed position (see `updateSheetVisibility`). Independent of the
  /// scrim, so a sheet at a transparent detent is still presented.
  public var isPresented: Bool {
    hasLaidOut && sheetContainer.alpha > 0
  }

  var isScrimVisible: Bool {
    modal && !scrimView.isHidden
  }

  @objc func handleScrimPress() {
    attemptScrimDismissal()
  }

  /// The closed detent a scrim tap, the scrim's dismiss button, or VoiceOver's
  /// escape gesture snaps the sheet to, while one would. Requiring an open
  /// target also ignores dismissals once a close is running, including one to
  /// another zero-height detent.
  var scrimDismissTargetIndex: Int? {
    guard
      isScrimVisible,
      let closedIndex = scrimDismissIndex,
      detentSpecs.indices.contains(targetIndex),
      detentSpecs[targetIndex].position > 0,
      activeSpring == nil || currentSheetHeight > 0.5
    else {
      return nil
    }
    return closedIndex
  }

  /// Dismisses a modal sheet to its closed detent through the exact path a
  /// scrim tap takes, returning whether a dismissal was actually performed.
  @discardableResult
  func attemptScrimDismissal() -> Bool {
    guard let closedIndex = scrimDismissTargetIndex else { return false }
    snapToIndex(closedIndex, velocity: 0)
    return true
  }

  /// The part of the scrim above the sheet, in host coordinates, where VoiceOver
  /// frames the scrim's dismiss button. Empty while the sheet fills the host.
  var scrimDismissRegion: CGRect {
    let top = min(max(presentedCardFrame.minY, 0), bounds.height)
    return CGRect(x: 0, y: 0, width: bounds.width, height: top)
  }

  /// VoiceOver's escape gesture (two-finger Z scrub) dismisses a modal sheet,
  /// mirroring a scrim tap. While the scrim blocks the background, the gesture
  /// stays with the sheet even when it can't dismiss (e.g., a programmatic-only
  /// closed detent): bubbling would act on the screen behind it, like a
  /// navigation controller popping it.
  override public func accessibilityPerformEscape() -> Bool {
    guard isScrimVisible else { return false }
    attemptScrimDismissal()
    return true
  }

  /// Reframes the dismiss button for VoiceOver after the sheet settles, if it
  /// holds focus. VoiceOver reads frames lazily and would keep the old one.
  func refreshScrimAccessibilityFrameIfFocused() {
    guard scrimView.isAccessibilityElement, scrimView.accessibilityElementIsFocused() else { return }
    UIAccessibility.post(notification: .layoutChanged, argument: scrimView)
  }

  func updateScrim() {
    updateScrim(forPosition: currentSheetHeight)
    updateSheetVisibility(forPosition: currentSheetHeight)
    updateInteractionState()
    eventDelegate?.bottomSheetHostingViewDidUpdateScrim(self)
  }

  func updateSheetVisibility(forPosition position: CGFloat) {
    sheetContainer.alpha = position <= 0.5 ? 0 : 1
  }

  func updateScrim(forPosition position: CGFloat) {
    guard modal else {
      scrimView.alpha = 0
      scrimView.isHidden = true
      return
    }

    // Until the first layout places the sheet, the identity transform reads as
    // a fully-open sheet; keep the scrim hidden until the sheet is positioned.
    if !hasLaidOut {
      scrimView.alpha = 0
      scrimView.isHidden = true
      return
    }

    // If we're settled on the closed detent, dynamic detent/content updates can
    // momentarily report a stale non-zero position. Keep scrim fully hidden.
    if
      let closedIndex,
      targetIndex == closedIndex,
      activeSpring == nil,
      !isPanning
    {
      scrimView.alpha = 0
      scrimView.isHidden = true
      return
    }

    // While the sheet is fully open and only its content/detent geometry is
    // resizing, the position momentarily lags the grown detent height. Keep the
    // scrim pinned to the fully-open opacity instead of dipping it until the
    // re-anchor settles.
    if scrimPinnedFull {
      let opacity = fullyOpenScrimOpacity
      scrimView.alpha = opacity
      scrimView.isHidden = opacity <= 0.001
      return
    }

    let progress = scrimOpacity(forPosition: position)
    scrimView.alpha = progress
    scrimView.isHidden = progress <= 0.001
  }

  /// The opacity at the tallest detent, held while the sheet re-anchors.
  private var fullyOpenScrimOpacity: CGFloat {
    guard let maxPosition = detentSpecs.map({ $0.position }).max() else { return 1 }
    return scrimOpacity(forPosition: maxPosition)
  }

  /// Interpolates the scrim opacity for a sheet height by bracketing it between
  /// adjacent detent heights and lerping each detent index's configured value.
  func scrimOpacity(forPosition position: CGFloat) -> CGFloat {
    interpolate(
      forPosition: position,
      values: detentSpecs.indices.map {
        clampOpacity(scrimOpacities.indices.contains($0) ? scrimOpacities[$0] : 1)
      }
    )
  }

  /// Fractional detent index in 0...(detentSpecs.count - 1): 0 at the shortest
  /// detent, 1 at the next, and so on, interpolated by position in between. The
  /// continuous counterpart of `onIndexChange`, so consumers can drive a backdrop
  /// or animate per detent without knowing the sheet's height.
  func detentIndex(forPosition position: CGFloat) -> CGFloat {
    interpolate(forPosition: position, values: detentSpecs.indices.map { CGFloat($0) })
  }

  /// Interpolates a per-detent value (one per detent, by index) by the sheet
  /// position, using each detent's resolved position as the breakpoint.
  private func interpolate(forPosition position: CGFloat, values: [CGFloat]) -> CGFloat {
    let pairs = zip(detentSpecs.map(\.position), values)
      .map { (position: $0, value: $1) }
      .sorted { $0.position < $1.position }

    guard let first = pairs.first, let last = pairs.last else { return 0 }
    if position <= first.position {
      return first.value
    }
    if position >= last.position {
      return last.value
    }

    for i in 1 ..< pairs.count where position <= pairs[i].position {
      let lower = pairs[i - 1]
      let upper = pairs[i]
      let span = upper.position - lower.position
      let t = span <= 0 ? 1 : (position - lower.position) / span
      return lower.value + (upper.value - lower.value) * t
    }
    return last.value
  }

  private func clampOpacity(_ value: CGFloat) -> CGFloat {
    min(1, max(0, value))
  }

  func updateInteractionState() {
    scrimView.isUserInteractionEnabled = modal && (closedIndex != nil) && !scrimView.isHidden
    // Expose the scrim to VoiceOver only while tapping it would dismiss the
    // sheet; otherwise it would be an inert, unlabeled stop in the
    // accessibility tree (a scrim over a programmatic-only close detent is
    // decorative, not actionable). A sheet that fills the host leaves no scrim
    // to frame; VoiceOver's escape gesture still dismisses it.
    scrimView.isAccessibilityElement =
      scrimDismissTargetIndex != nil && scrimDismissRegion.height >= 1
  }
}
