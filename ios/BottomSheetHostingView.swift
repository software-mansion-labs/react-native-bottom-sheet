import UIKit

@objc public protocol BottomSheetHostingViewDelegate: AnyObject {
  func bottomSheetHostingView(_ view: BottomSheetHostingView, didChangeIndex index: Int)
  func bottomSheetHostingView(_ view: BottomSheetHostingView, didSettle index: Int)
  func bottomSheetHostingView(
    _ view: BottomSheetHostingView, didChangePosition position: CGFloat, index: CGFloat
  )
  func bottomSheetHostingView(_ view: BottomSheetHostingView, didReportError message: String)
}

private struct DetentSpec: Equatable {
  let height: CGFloat
  let programmatic: Bool
}

private enum DetentKind {
  case points
  case content
}

private struct RawDetentSpec {
  let value: CGFloat
  let kind: DetentKind
  let programmatic: Bool
}

private struct PendingSnapRequest {
  let index: Int
  let velocity: CGFloat
  let emitIndexChange: Bool
  let emitSettle: Bool
  let preserveScrimPin: Bool
}

@objcMembers
public final class BottomSheetHostingView: UIView {
  public weak var eventDelegate: BottomSheetHostingViewDelegate?
  public var modal: Bool = false {
    didSet { updateScrim() }
  }

  public var scrimColor: UIColor? = .clear {
    didSet { scrimView.backgroundColor = scrimColor }
  }

  /// Scrim opacity per detent index. Linearly interpolated between detents and
  /// clamped to the last value for detents beyond the array. The JS layer always
  /// supplies a per-detent array; the fully-opaque fallback only guards against
  /// empty input (indexing requires a non-empty array).
  private var scrimOpacities: [CGFloat] = [1] {
    didSet { updateScrim() }
  }

  public func setScrimOpacities(_ values: [NSNumber]) {
    let mapped = values.map { CGFloat(truncating: $0) }
    scrimOpacities = mapped.isEmpty ? [1] : mapped
  }

  public var maxDetentHeight: CGFloat = .nan {
    didSet { refreshDetentsFromLayout() }
  }

  public var disableScrollableNegotiation: Bool = false

  private var rawDetentSpecs: [RawDetentSpec] = []
  private var detentSpecs: [DetentSpec] = [] {
    didSet {
      setNeedsLayout()
      updateScrim()
    }
  }

  private var targetIndex: Int = 0
  public var animateIn: Bool = true
  public var animateContentHeight: Bool = true

  public let sheetContainer = UIView()
  private let scrimView = UIControl()
  private var panGesture: UIPanGestureRecognizer!
  private var activeSpring: CriticalSpring?
  private var activeSpringTargetIndex: Int = 0
  private var activeSpringEmitsSettle = false
  private var scrimPinnedFull = false
  private var displayLink: CADisplayLink?
  private var pendingIndex: Int?
  private var pendingSnapRequest: PendingSnapRequest?
  private var hasLaidOut = false
  private var isPanning = false
  private var lastReportedInvalidDetentMessage: String?
  private var panStartingIndex: Int?
  private var activeDragRange: (minTy: CGFloat, maxTy: CGFloat)?
  private var activeDragDetentSpecs: [DetentSpec]?
  private var isContentInteractionDisabled = false
  private var contentHeightMarker: UIView?
  private weak var surfaceView: UIView?
  private static var markerObservationContext = 0
  private static let springAnimationKey = "bottomSheetSettle"

  override public init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    clipsToBounds = false

    scrimView.backgroundColor = scrimColor
    scrimView.alpha = 0
    scrimView.isHidden = true
    scrimView.addTarget(self, action: #selector(handleScrimPress), for: .touchUpInside)
    addSubview(scrimView)

    sheetContainer.backgroundColor = .clear
    sheetContainer.clipsToBounds = false
    addSubview(sheetContainer)

    panGesture = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
    panGesture.delegate = self
    panGesture.cancelsTouchesInView = true
    // Let child controls show immediate press feedback. If the interaction
    // becomes a sheet drag, handlePan(.began) cancels in-flight RN touches.
    panGesture.delaysTouchesBegan = false
    panGesture.delaysTouchesEnded = false
    sheetContainer.addGestureRecognizer(panGesture)
  }

  @available(*, unavailable)
  public required init?(coder _: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  /// RCTSurfaceTouchHandler dispatches touch events to JS independently of the
  /// pan gesture (it fires in touchesBegan: regardless of its recognizer state).
  /// We cache it here and toggle isEnabled in handlePan(.began) to force a
  /// touchesCancelled dispatch to JS, preventing Pressable from firing onPress
  /// during a sheet drag. This is the iOS equivalent of Android's
  /// NativeGestureUtil.notifyNativeGestureStarted.
  private weak var surfaceTouchHandler: UIGestureRecognizer?

  override public func didMoveToWindow() {
    super.didMoveToWindow()
    surfaceTouchHandler = nil
    guard window != nil else { return }
    var current: UIView? = superview
    while let view = current {
      for gr in view.gestureRecognizers ?? [] {
        if NSStringFromClass(type(of: gr)).contains("TouchHandler") {
          surfaceTouchHandler = gr
          break
        }
      }
      if surfaceTouchHandler != nil { break }
      current = view.superview
    }
    flushPendingSnapIfNeeded()
  }

  override public func layoutSubviews() {
    super.layoutSubviews()
    guard bounds.width > 0, bounds.height > 0 else { return }

    scrimView.frame = bounds
    refreshDetentsFromLayout()
    let maxHeight = sheetContainerHeight
    sheetContainer.bounds = CGRect(x: 0, y: 0, width: bounds.width, height: maxHeight)
    sheetContainer.center = CGPoint(x: bounds.width / 2, y: bounds.height - maxHeight / 2)

    // The surface fills the full container so it always covers the visible sheet
    // (the container is translated to the current sheet position), regardless of
    // how short the content becomes. Sized from the top via frame — never via
    // anchorPoint.
    surfaceView?.frame = sheetContainer.bounds

    if !hasLaidOut && !detentSpecs.isEmpty {
      let indexToApply = pendingIndex ?? targetIndex
      let clampedIndex = max(0, min(detentSpecs.count - 1, indexToApply))

      if animateIn, isInvalidContentDetentTarget(clampedIndex) {
        targetIndex = clampedIndex
        pendingIndex = clampedIndex
        let closedTy = sheetContainerHeight
        sheetContainer.transform = CGAffineTransform(translationX: 0, y: closedTy)
        emitPosition()
        return
      }

      hasLaidOut = true
      pendingIndex = nil
      targetIndex = clampedIndex

      if animateIn {
        let closedTy = sheetContainerHeight
        sheetContainer.transform = CGAffineTransform(translationX: 0, y: closedTy)
        emitPosition()
        snapToIndex(targetIndex, velocity: 0, emitIndexChange: false, emitSettle: true)
      } else {
        sheetContainer.transform = CGAffineTransform(translationX: 0, y: translationY(for: targetIndex))
        emitPosition()
      }
      return
    }

    if activeSpring != nil || isPanning { return }
    sheetContainer.transform = CGAffineTransform(translationX: 0, y: translationY(for: targetIndex))
    updateScrim()
  }

  private var presentedSheetFrame: CGRect {
    guard activeSpring != nil else { return sheetContainer.frame }
    // Mid-settle, derive the on-screen frame from the spring instead of the
    // presentation layer (which lags one commit behind right after a snap
    // starts). The container's transform is translation-only.
    let size = sheetContainer.bounds.size
    let center = sheetContainer.center
    return CGRect(
      x: center.x - size.width / 2,
      y: center.y - size.height / 2 + currentTranslationY,
      width: size.width,
      height: size.height
    )
  }

  override public func point(inside point: CGPoint, with _: UIEvent?) -> Bool {
    if presentedSheetFrame.contains(point) {
      return true
    }

    return isScrimVisible && bounds.contains(point)
  }

  override public func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
    guard self.point(inside: point, with: event) else { return nil }

    if isScrimVisible, !presentedSheetFrame.contains(point) {
      let scrimPoint = convert(point, to: scrimView)
      return scrimView.hitTest(scrimPoint, with: event)
    }

    let containerPoint = convert(point, to: sheetContainer)
    guard sheetContainer.bounds.contains(containerPoint) else { return nil }
    return sheetContainer.hitTest(containerPoint, with: event)
  }

  public func setDetents(_ raw: [NSDictionary]) {
    rawDetentSpecs = raw.compactMap { dict in
      guard let value = dict["value"] as? Double ?? (dict["value"] as? NSNumber)?.doubleValue else {
        return nil
      }
      let kindString = (dict["kind"] as? String) ?? ((dict["kind"] as? NSString) as String?) ?? "points"
      let kind: DetentKind = kindString == "content" ? .content : .points
      let programmatic = (dict["programmatic"] as? Bool) ?? (dict["programmatic"] as? NSNumber)?.boolValue ?? false
      return RawDetentSpec(value: CGFloat(value), kind: kind, programmatic: programmatic)
    }
    refreshDetentsFromLayout()
  }

  public func setDetentIndex(_ newIndex: Int) {
    guard newIndex >= 0 else { return }

    if !hasLaidOut {
      pendingIndex = newIndex
      targetIndex = newIndex
      return
    }

    guard newIndex < detentSpecs.count, newIndex != targetIndex else { return }
    snapToIndex(newIndex, velocity: 0, emitIndexChange: false)
  }

  public func mountChildComponentView(_ childView: UIView, atIndex index: Int) {
    sheetContainer.insertSubview(childView, at: index)
    refreshContentHeightMarker()
    setNeedsLayout()
  }

  public func unmountChildComponentView(_ childView: UIView) {
    childView.removeFromSuperview()
    refreshContentHeightMarker()
    setNeedsLayout()
  }

  public func mountSurfaceComponentView(_ childView: UIView, atIndex index: Int) {
    surfaceView = childView
    sheetContainer.insertSubview(childView, at: index)
    refreshContentHeightMarker()
    setNeedsLayout()
  }

  public func unmountSurfaceComponentView(_ childView: UIView) {
    if surfaceView === childView {
      surfaceView = nil
    }
    childView.removeFromSuperview()
    refreshContentHeightMarker()
    setNeedsLayout()
  }

  public func resetSheetState() {
    activeSpring = nil
    activeSpringEmitsSettle = false
    stopDisplayLink()
    sheetContainer.layer.removeAnimation(forKey: Self.springAnimationKey)
    rawDetentSpecs = []
    detentSpecs = []
    targetIndex = 0
    pendingIndex = nil
    pendingSnapRequest = nil
    hasLaidOut = false
    isPanning = false
    panStartingIndex = nil
    activeDragRange = nil
    activeDragDetentSpecs = nil
    setContentInteractionEnabled(true)
    stopObservingContentHeightMarker()
    surfaceView = nil
    sheetContainer.transform = .identity
    scrimView.alpha = 0
    scrimView.isHidden = true
    for subview in sheetContainer.subviews {
      subview.removeFromSuperview()
    }
  }

  private func detent(at index: Int) -> DetentSpec {
    guard detentSpecs.indices.contains(index) else {
      return DetentSpec(height: 0, programmatic: false)
    }
    return detentSpecs[index]
  }

  private func translationY(for index: Int) -> CGFloat {
    let maxHeight = sheetContainerHeight
    let snapHeight = detent(at: index).height
    return maxHeight - snapHeight
  }

  private func snapCandidateIndices(including index: Int? = nil) -> [Int] {
    var indices = detentSpecs.indices.filter { !detentSpecs[$0].programmatic }
    if
      let index,
      detentSpecs.indices.contains(index),
      detentSpecs[index].programmatic
    {
      indices.append(index)
    }
    return Array(Set(indices)).sorted {
      detentSpecs[$0].height < detentSpecs[$1].height
    }
  }

  private func draggableRange(including index: Int? = nil) -> (minTy: CGFloat, maxTy: CGFloat) {
    let candidates = snapCandidateIndices(including: index)
    guard !candidates.isEmpty else { return (minTy: 0, maxTy: 0) }
    return (
      minTy: candidates.map { translationY(for: $0) }.min() ?? 0,
      maxTy: candidates.map { translationY(for: $0) }.max() ?? 0
    )
  }

  private func snapshotTranslationY(for index: Int, in specs: [DetentSpec]) -> CGFloat {
    let maxHeight = sheetContainerHeight
    let snapHeight = specs.indices.contains(index) ? specs[index].height : 0
    return maxHeight - snapHeight
  }

  private func snapshotCandidateIndices(including index: Int?, in specs: [DetentSpec]) -> [Int] {
    var indices = specs.indices.filter { !specs[$0].programmatic }
    if
      let index,
      specs.indices.contains(index),
      specs[index].programmatic
    {
      indices.append(index)
    }
    return Array(Set(indices)).sorted {
      specs[$0].height < specs[$1].height
    }
  }

  private func snapshotDraggableRange(
    including index: Int?,
    in specs: [DetentSpec]
  ) -> (minTy: CGFloat, maxTy: CGFloat) {
    let candidates = snapshotCandidateIndices(including: index, in: specs)
    guard !candidates.isEmpty else { return (minTy: 0, maxTy: 0) }
    return (
      minTy: candidates.map { snapshotTranslationY(for: $0, in: specs) }.min() ?? 0,
      maxTy: candidates.map { snapshotTranslationY(for: $0, in: specs) }.max() ?? 0
    )
  }

  private var closedIndex: Int? {
    detentSpecs.firstIndex(where: { $0.height == 0 })
  }

  private var scrimDismissIndex: Int? {
    guard let closedIndex, !detentSpecs[closedIndex].programmatic else {
      return nil
    }
    return closedIndex
  }

  private var firstNonZeroDetentHeight: CGFloat {
    detentSpecs.first(where: { $0.height > 0 })?.height ?? 0
  }

  private var currentSheetHeight: CGFloat {
    let maxHeight = sheetContainerHeight
    let ty = currentTranslationY
    return maxHeight - ty
  }

  public var currentContentOffsetY: CGFloat {
    let maxHeight = sheetContainerHeight
    let containerTop = bounds.height - maxHeight
    let ty = currentTranslationY
    return containerTop + ty
  }

  public var isModalAccessibilityActive: Bool {
    isScrimVisible
  }

  private var isScrimVisible: Bool {
    modal && !scrimView.isHidden
  }

  /// `overrideTy` is the spring's predicted translationY for the upcoming frame
  /// (passed during a settle). Without it we read the current on-screen value.
  private func emitPosition(overrideTy: CGFloat? = nil) {
    let maxHeight = sheetContainerHeight
    let ty = overrideTy ?? currentTranslationY
    let position = maxHeight - ty
    updateScrim(forPosition: position)
    updateSheetVisibility(forPosition: position)
    updateInteractionState()
    eventDelegate?.bottomSheetHostingView(
      self, didChangePosition: position, index: detentIndex(forPosition: position)
    )
  }

  private func startDisplayLink() {
    guard displayLink == nil else { return }
    let link = CADisplayLink(target: self, selector: #selector(displayLinkFired(_:)))
    link.add(to: .main, forMode: .common)
    displayLink = link
  }

  private func stopDisplayLink() {
    displayLink?.invalidate()
    displayLink = nil
  }

  private func setContentInteractionEnabled(_ isEnabled: Bool) {
    if isContentInteractionDisabled == !isEnabled {
      return
    }

    for subview in sheetContainer.subviews {
      subview.isUserInteractionEnabled = isEnabled
    }
    isContentInteractionDisabled = !isEnabled
  }

  @objc private func displayLinkFired(_ link: CADisplayLink) {
    // `targetTimestamp` is the predicted display time of the next frame
    // Ideal synchronization with animations running on the render server
    // hasn't been achieved. Render server is a black box and it's hard to
    // find out why exactly. This approach however gives better results
    // than lagging one frame behind.
    stepSpring(targetTime: link.targetTimestamp)
  }

  @objc private func handleScrimPress() {
    guard
      modal,
      let closedIndex = scrimDismissIndex,
      targetIndex != closedIndex,
      activeSpring == nil || currentSheetHeight > 0.5
    else {
      return
    }

    snapToIndex(closedIndex, velocity: 0)
  }

  private func snapToIndex(
    _ index: Int,
    velocity: CGFloat,
    emitIndexChange: Bool = true,
    emitSettle: Bool = true,
    preserveScrimPin: Bool = false
  ) {
    guard index >= 0, index < detentSpecs.count else { return }
    if window == nil, activeSpring == nil {
      // Avoid starting a render-server animation before the layer is attached:
      // UIKit may briefly display the model transform before keyframes begin.
      pendingSnapRequest = PendingSnapRequest(
        index: index,
        velocity: velocity,
        emitIndexChange: emitIndexChange,
        emitSettle: emitSettle,
        preserveScrimPin: preserveScrimPin
      )
      return
    }

    targetIndex = index
    if !preserveScrimPin {
      scrimPinnedFull = false
    }

    let currentTy: CGFloat
    if activeSpring != nil {
      currentTy = cancelActiveSpring()
    } else {
      currentTy = sheetContainer.transform.ty
    }
    let targetTy = translationY(for: index)
    let distance = targetTy - currentTy

    let velocityRatio = distance != 0 ? velocity / distance : 0
    let clampedRatio = min(max(velocityRatio, -5), 5)
    let v0 = clampedRatio * distance

    let duration: CFTimeInterval = 0.45
    // Pick the stiffness so the sheet looks settled (within ~0.5% of target)
    // right at `duration`. For a critically-damped spring that point is
    // ω·t ≈ 8, so ω = 8 / duration.
    let omega = 8.0 / CGFloat(duration)
    activeSpringEmitsSettle = emitSettle
    activeSpringTargetIndex = index

    // The single instant both the modal animation and the follower curve are anchored to.
    let startTime = CACurrentMediaTime()
    let spring = CriticalSpring(
      from: currentTy,
      target: targetTy,
      v0: v0,
      omega: omega,
      startTime: startTime,
      duration: duration
    )

    // The sheet is animated on the render server from samples of the same spring
    // that feeds the follower position events.
    let animation = CAKeyframeAnimation(keyPath: "transform.translation.y")
    let sampleCount = max(Int((duration * 120).rounded()), 1)
    animation.values = spring.keyframeValues(count: sampleCount)
    animation.keyTimes = (0 ... sampleCount).map {
      NSNumber(value: Double($0) / Double(sampleCount))
    }
    animation.duration = duration
    animation.calculationMode = .linear
    animation.beginTime = sheetContainer.layer.convertTime(startTime, from: nil)
    animation.isRemovedOnCompletion = false
    animation.fillMode = .forwards
    animation.delegate = self

    sheetContainer.transform = CGAffineTransform(translationX: 0, y: targetTy)
    sheetContainer.layer.add(animation, forKey: Self.springAnimationKey)
    activeSpring = spring

    startDisplayLink()

    // Report the index change as soon as the snap is committed, not when it
    // finishes: `targetIndex` is already set, and a programmatic snap's start is
    // known to the caller. `onSettle` remains the signal for movement end.
    if emitIndexChange {
      eventDelegate?.bottomSheetHostingView(self, didChangeIndex: index)
    }
  }

  private func flushPendingSnapIfNeeded() {
    guard window != nil, let request = pendingSnapRequest else { return }
    pendingSnapRequest = nil
    snapToIndex(
      request.index,
      velocity: request.velocity,
      emitIndexChange: request.emitIndexChange,
      emitSettle: request.emitSettle,
      preserveScrimPin: request.preserveScrimPin
    )
  }

  private func stepSpring(targetTime: CFTimeInterval) {
    guard let spring = activeSpring else { return }
    emitPosition(overrideTy: spring.value(at: targetTime))
  }

  private func finishSpring() {
    guard activeSpring != nil else { return }
    let index = activeSpringTargetIndex
    let emitSettle = activeSpringEmitsSettle
    let targetTy = translationY(for: index)

    activeSpring = nil
    activeSpringEmitsSettle = false
    stopDisplayLink()
    sheetContainer.layer.removeAnimation(forKey: Self.springAnimationKey)

    sheetContainer.transform = CGAffineTransform(translationX: 0, y: targetTy)
    emitPosition()
    scrimPinnedFull = false
    setContentInteractionEnabled(true)
    updateInteractionState()
    if emitSettle {
      eventDelegate?.bottomSheetHostingView(self, didSettle: index)
    }
  }

  @discardableResult
  private func cancelActiveSpring() -> CGFloat {
    let visualTy = currentTranslationY
    activeSpring = nil
    activeSpringEmitsSettle = false
    stopDisplayLink()
    sheetContainer.layer.removeAnimation(forKey: Self.springAnimationKey)
    sheetContainer.transform = CGAffineTransform(translationX: 0, y: visualTy)
    return visualTy
  }

  @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
    let maxHeight = sheetContainerHeight

    switch gesture.state {
    case .began:
      isPanning = true
      scrimPinnedFull = false
      panStartingIndex = targetIndex
      activeDragDetentSpecs = detentSpecs
      activeDragRange = snapshotDraggableRange(including: targetIndex, in: detentSpecs)
      sheetContainer.endEditing(true)
      setContentInteractionEnabled(false)
      if let handler = surfaceTouchHandler {
        handler.isEnabled = false
        handler.isEnabled = true
      }
      gesture.setTranslation(.zero, in: self)
      if activeSpring != nil {
        cancelActiveSpring()
      }

    case .changed:
      let delta = gesture.translation(in: self).y
      gesture.setTranslation(.zero, in: self)
      let range = activeDragRange ?? draggableRange(including: panStartingIndex)
      let minTy = range.minTy
      let maxTy = range.maxTy
      let newTy = max(minTy, min(maxTy, sheetContainer.transform.ty + delta))
      sheetContainer.transform = CGAffineTransform(translationX: 0, y: newTy)
      emitPosition()

    case .ended:
      isPanning = false
      setContentInteractionEnabled(true)
      let velocity = gesture.velocity(in: self).y
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
      snapToIndex(index, velocity: velocity)

    case .cancelled:
      isPanning = false
      setContentInteractionEnabled(true)
      let cancelVelocity = gesture.velocity(in: self).y
      let cancelHeight = maxHeight - sheetContainer.transform.ty
      let cancelIndex =
        activeDragDetentSpecs.flatMap {
          $0.count == detentSpecs.count ? $0 : nil
        }.map {
          snapshotBestSnapIndex(
            for: cancelHeight,
            velocity: cancelVelocity,
            including: panStartingIndex,
            in: $0
          )
        } ?? bestSnapIndex(
          for: cancelHeight,
          velocity: cancelVelocity,
          including: panStartingIndex
        )
      panStartingIndex = nil
      activeDragRange = nil
      activeDragDetentSpecs = nil
      snapToIndex(cancelIndex, velocity: cancelVelocity)

    case .failed:
      isPanning = false
      panStartingIndex = nil
      activeDragRange = nil
      activeDragDetentSpecs = nil
      setContentInteractionEnabled(true)

    default:
      break
    }
  }

  private func bestSnapIndex(
    for height: CGFloat,
    velocity: CGFloat,
    including index: Int? = nil
  ) -> Int {
    let candidates = snapCandidateIndices(including: index)
    guard !candidates.isEmpty else { return targetIndex }

    let flickThreshold: CGFloat = 600

    if velocity < -flickThreshold {
      return candidates.first(where: { detentSpecs[$0].height > height })
        ?? candidates.last ?? targetIndex
    }
    if velocity > flickThreshold {
      return candidates.last(where: { detentSpecs[$0].height < height })
        ?? candidates.first ?? targetIndex
    }

    return candidates.min(by: {
      abs(detentSpecs[$0].height - height) < abs(detentSpecs[$1].height - height)
    }) ?? targetIndex
  }

  private func snapshotBestSnapIndex(
    for height: CGFloat,
    velocity: CGFloat,
    including index: Int?,
    in specs: [DetentSpec]
  ) -> Int {
    let candidates = snapshotCandidateIndices(including: index, in: specs)
    guard !candidates.isEmpty else { return targetIndex }

    let flickThreshold: CGFloat = 600

    if velocity < -flickThreshold {
      return candidates.first(where: { specs[$0].height > height })
        ?? candidates.last ?? targetIndex
    }
    if velocity > flickThreshold {
      return candidates.last(where: { specs[$0].height < height })
        ?? candidates.first ?? targetIndex
    }

    return candidates.min(by: {
      abs(specs[$0].height - height) < abs(specs[$1].height - height)
    }) ?? targetIndex
  }

  private func isVerticallyScrollable(_ scrollView: UIScrollView) -> Bool {
    guard scrollView.isScrollEnabled else { return false }
    let verticalInset = scrollView.adjustedContentInset.top + scrollView.adjustedContentInset.bottom
    let visibleHeight = max(0, scrollView.bounds.height - verticalInset)
    return scrollView.alwaysBounceVertical || scrollView.contentSize.height > visibleHeight
  }

  private func scrollView(containing location: CGPoint, in view: UIView) -> UIScrollView? {
    for subview in view.subviews.reversed() {
      let locationInSubview = view.convert(location, to: subview)
      guard subview.bounds.contains(locationInSubview) else { continue }

      if let found = scrollView(containing: locationInSubview, in: subview) {
        return found
      }

      if let scrollView = subview as? UIScrollView, isVerticallyScrollable(scrollView) {
        return scrollView
      }
    }
    return nil
  }

  override public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
    guard gestureRecognizer === panGesture else { return true }

    let velocity = panGesture.velocity(in: self)
    guard abs(velocity.y) > abs(velocity.x) else { return false }

    let candidates = snapCandidateIndices(including: targetIndex)
    guard candidates.count > 1 else { return false }

    if disableScrollableNegotiation {
      let locationInContainer = panGesture.location(in: sheetContainer)
      if scrollView(containing: locationInContainer, in: sheetContainer) != nil {
        return false
      }
    }

    let maxCandidateHeight = candidates.map { detentSpecs[$0].height }.max() ?? 0
    // Below max: allow drag in either direction to reach other detents.
    guard currentSheetHeight >= maxCandidateHeight - 0.5 else { return true }
    // At max: only allow a downward drag, and only when no scroller under the touch can
    // still absorb it (see the ancestor walk below) — otherwise a scroller handles it.
    if velocity.y < 0 {
      return false
    }

    let locationInContainer = panGesture.location(in: sheetContainer)
    guard let touchedScrollView = scrollView(containing: locationInContainer, in: sheetContainer) else {
      return true
    }

    // Collapse the sheet only when EVERY vertically-scrollable scroller under the touch is
    // already at its relevant edge; if any of them can still scroll, the gesture belongs to
    // that scroller. Inspecting only the innermost scroller breaks on a nested *horizontal*
    // carousel: it can pass isVerticallyScrollable(_:) via vertical bounce or a slightly
    // taller contentSize, yet its contentOffset.y is always 0 ("at top"), so the sheet
    // collapsed while the enclosing vertical list was still mid-scroll.
    //
    // Walk the touched scroller and its ancestors up to the sheet container, resolving
    // inversion top-down as we go: a view is inverted when it — or any ancestor up to the
    // container — has a flipped vertical axis. edgeTolerance absorbs a sub-pixel residual so
    // the sheet can still collapse when a list is effectively, if not exactly, at its edge.
    let edgeTolerance: CGFloat = 0.5

    var chain: [UIView] = []
    var node: UIView? = touchedScrollView
    while let view = node, view !== sheetContainer {
      chain.append(view)
      node = view.superview
    }

    var inverted = false
    for view in chain.reversed() {
      inverted = inverted || view.transform.d < 0
      guard let candidate = view as? UIScrollView, isVerticallyScrollable(candidate) else {
        continue
      }
      if inverted {
        let maxOffsetY =
          candidate.contentSize.height - candidate.bounds.height + candidate.adjustedContentInset.bottom
        if candidate.contentOffset.y < maxOffsetY - edgeTolerance { return false }
      } else if candidate.contentOffset.y > edgeTolerance {
        return false
      }
    }
    return true
  }

  private var resolvedMaxDetentHeight: CGFloat {
    if !maxDetentHeight.isFinite || maxDetentHeight <= 0 {
      return bounds.height
    }
    return min(max(0, maxDetentHeight), bounds.height)
  }

  /// Stable coordinate base for the sheet container. The container is sized to
  /// the full available height rather than the tallest detent, so it stays a
  /// fixed-size canvas: when content — and thus the `content` detent — shrinks,
  /// the container does not collapse underneath the sheet, leaving room to
  /// animate the sheet down to its new height. The surface fills this canvas, so
  /// the area below the shrunken content stays covered throughout.
  private var sheetContainerHeight: CGFloat {
    resolvedMaxDetentHeight
  }

  private func resolveDetentSpecs() -> [DetentSpec]? {
    let maxHeight = resolvedMaxDetentHeight
    let measuredContentHeight = maxHeight > 0 ? validContentHeight.map { min($0, maxHeight) } : nil
    var resolvedDetents: [DetentSpec] = []
    resolvedDetents.reserveCapacity(rawDetentSpecs.count)

    for (index, spec) in rawDetentSpecs.enumerated() {
      let height: CGFloat
      switch spec.kind {
      case .points:
        height = spec.value
      case .content:
        height =
          measuredContentHeight ?? unresolvedContentDetentHeight(after: index, maxHeight: maxHeight)
      }
      let resolvedHeight = min(max(0, height), maxHeight)
      if let previous = resolvedDetents.last, resolvedHeight < previous.height {
        let message =
          "Invalid bottom sheet detent at index \(index): resolved height \(resolvedHeight) is lower than previous detent height \(previous.height). Detents must be passed in ascending order."
        if lastReportedInvalidDetentMessage != message {
          lastReportedInvalidDetentMessage = message
          eventDelegate?.bottomSheetHostingView(self, didReportError: message)
        }
        return nil
      }
      resolvedDetents.append(DetentSpec(height: resolvedHeight, programmatic: spec.programmatic))
    }

    lastReportedInvalidDetentMessage = nil
    return resolvedDetents
  }

  private func unresolvedContentDetentHeight(after index: Int, maxHeight: CGFloat) -> CGFloat {
    guard index + 1 < rawDetentSpecs.count else {
      return maxHeight
    }
    let nextPointHeight = rawDetentSpecs[(index + 1)...]
      .first { $0.kind == .points }
      .map(\.value)
    return min(max(0, nextPointHeight ?? maxHeight), maxHeight)
  }

  private func refreshDetentsFromLayout() {
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
    guard resolvedDetents != detentSpecs else {
      updateScrim()
      return
    }

    let previousMaxHeight = sheetContainerHeight
    // Whether the scrim is currently fully opaque, i.e. the sheet is settled at
    // or above the first non-zero detent. If so, a detent resize must not dip
    // the scrim while the sheet re-anchors to the new geometry.
    let wasScrimFull = hasLaidOut
      && !isPanning
      && modal
      && firstNonZeroDetentHeight > 0
      && currentSheetHeight + 0.5 >= firstNonZeroDetentHeight
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
        sheetContainer.transform = CGAffineTransform(translationX: 0, y: reanchoredTy)
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
        let targetHeight = detent(at: targetIndex).height
        let shouldAnimateHeight = shouldAnimateContentHeight(at: targetIndex)
        if abs(targetHeight - currentVisibleHeight) <= 0.5 {
          // No meaningful change.
          sheetContainer.transform = CGAffineTransform(translationX: 0, y: targetTy)
          emitPosition()
          scrimPinnedFull = false
        } else if !shouldAnimateHeight {
          sheetContainer.transform = CGAffineTransform(translationX: 0, y: targetTy)
          emitPosition()
          scrimPinnedFull = false
        } else {
          // The content detent changed (grew or shrank): re-anchor at the
          // current visible height, then animate to the new target. The surface
          // covers the full sheet, so a shrink no longer exposes blank space.
          let startTy = min(max(newMaxHeight - currentVisibleHeight, 0), newMaxHeight)
          sheetContainer.transform = CGAffineTransform(translationX: 0, y: startTy)
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

  private func refreshContentHeightMarker() {
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

  private func stopObservingContentHeightMarker() {
    guard let marker = contentHeightMarker else { return }
    marker.layer.removeObserver(
      self, forKeyPath: "position", context: &Self.markerObservationContext
    )
    marker.layer.removeObserver(
      self, forKeyPath: "bounds", context: &Self.markerObservationContext
    )
    contentHeightMarker = nil
  }

  override public func observeValue(
    forKeyPath keyPath: String?,
    of object: Any?,
    change: [NSKeyValueChangeKey: Any]?,
    context: UnsafeMutableRawPointer?
  ) {
    if context == &Self.markerObservationContext {
      refreshDetentsFromLayout()
    } else {
      super.observeValue(forKeyPath: keyPath, of: object, change: change, context: context)
    }
  }

  deinit {
    stopObservingContentHeightMarker()
    displayLink?.invalidate()
    sheetContainer.layer.removeAnimation(forKey: Self.springAnimationKey)
  }

  private func findContentHeightMarker() -> UIView? {
    // The surface is a sibling of the content wrapper; skip it so the marker is
    // always read from the content, never from the surface.
    guard let contentView = sheetContainer.subviews.first(where: { $0 !== surfaceView })
    else { return nil }
    return contentView.subviews.last
  }

  private var currentContentHeight: CGFloat? {
    guard let marker = contentHeightMarker else { return nil }
    return marker.frame.minY.isFinite ? marker.frame.minY : nil
  }

  private var validContentHeight: CGFloat? {
    guard let height = currentContentHeight, height.isFinite, height > 0 else {
      return nil
    }
    return height
  }

  private func isInvalidContentDetentTarget(_ index: Int) -> Bool {
    guard rawDetentSpecs.indices.contains(index) else {
      return false
    }
    switch rawDetentSpecs[index].kind {
    case .points:
      return false
    case .content:
      return validContentHeight == nil
    }
  }
}

extension BottomSheetHostingView: CAAnimationDelegate {
  public func animationDidStop(_: CAAnimation, finished: Bool) {
    guard finished, activeSpring != nil else { return }
    finishSpring()
  }
}

extension BottomSheetHostingView: UIGestureRecognizerDelegate {
  public func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldBeRequiredToFailBy other: UIGestureRecognizer
  ) -> Bool {
    guard gestureRecognizer === panGesture else { return false }
    return other is UIPanGestureRecognizer || other is UITapGestureRecognizer
  }

  public func gestureRecognizer(
    _: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith _: UIGestureRecognizer
  ) -> Bool {
    return false
  }
}

private extension BottomSheetHostingView {
  var currentTranslationY: CGFloat {
    // During a settle the modal is driven by a keyframe animation on the render
    // server (the model `transform` already holds the final target), so read the
    // analytical spring the keyframes were sampled from. The presentation layer
    // is deliberately not used: right after a snap starts it still holds the
    // previously committed transform, and that stale value (e.g. the identity
    // transform from before the first layout) reads as a wide-open sheet — which
    // briefly flashed the scrim on mount. A drag assigns `transform` directly,
    // so outside a settle the model value is correct.
    if let spring = activeSpring {
      return spring.value(at: CACurrentMediaTime())
    }
    return sheetContainer.transform.ty
  }

  func updateScrim() {
    updateScrim(forPosition: currentSheetHeight)
    updateSheetVisibility(forPosition: currentSheetHeight)
    updateInteractionState()
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
    guard let maxHeight = detentSpecs.map({ $0.height }).max() else { return 1 }
    return scrimOpacity(forPosition: maxHeight)
  }

  /// Interpolates the scrim opacity for a sheet height by bracketing it between
  /// adjacent detent heights and lerping each detent index's configured value.
  private func scrimOpacity(forPosition position: CGFloat) -> CGFloat {
    interpolate(
      forPosition: position,
      values: detentSpecs.indices.map {
        clampOpacity(scrimOpacities[min($0, scrimOpacities.count - 1)])
      }
    )
  }

  /// Fractional detent index in 0...(detentSpecs.count - 1): 0 at the shortest
  /// detent, 1 at the next, and so on, interpolated by position in between. The
  /// continuous counterpart of `onIndexChange`, so consumers can drive a backdrop
  /// or animate per detent without knowing the sheet's height.
  private func detentIndex(forPosition position: CGFloat) -> CGFloat {
    interpolate(forPosition: position, values: detentSpecs.indices.map { CGFloat($0) })
  }

  /// Interpolates a per-detent value (one per detent, by index) by the sheet
  /// position, using each detent's resolved height as the breakpoint.
  private func interpolate(forPosition position: CGFloat, values: [CGFloat]) -> CGFloat {
    let pairs = zip(detentSpecs.map(\.height), values)
      .map { (height: $0, value: $1) }
      .sorted { $0.height < $1.height }

    guard let first = pairs.first, let last = pairs.last else { return 0 }
    if position <= first.height { return first.value }
    if position >= last.height { return last.value }

    for i in 1 ..< pairs.count where position <= pairs[i].height {
      let lower = pairs[i - 1]
      let upper = pairs[i]
      let span = upper.height - lower.height
      let t = span <= 0 ? 1 : (position - lower.height) / span
      return lower.value + (upper.value - lower.value) * t
    }
    return last.value
  }

  private func clampOpacity(_ value: CGFloat) -> CGFloat {
    min(1, max(0, value))
  }

  func updateInteractionState() {
    scrimView.isUserInteractionEnabled = modal && (closedIndex != nil) && !scrimView.isHidden
  }
}
