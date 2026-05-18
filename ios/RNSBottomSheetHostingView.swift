import UIKit

@objc public protocol RNSBottomSheetHostingViewDelegate: AnyObject {
  func bottomSheetHostingView(_ view: RNSBottomSheetHostingView, didChangeIndex index: Int)
  func bottomSheetHostingView(_ view: RNSBottomSheetHostingView, didSettle index: Int)
  func bottomSheetHostingView(_ view: RNSBottomSheetHostingView, didChangePosition position: CGFloat)
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

@objcMembers
public final class RNSBottomSheetHostingView: UIView {
  public weak var eventDelegate: RNSBottomSheetHostingViewDelegate?
  public var modal: Bool = false {
    didSet { updateScrim() }
  }

  public var scrimColor: UIColor? = .clear {
    didSet { scrimView.backgroundColor = scrimColor }
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

  public let sheetContainer = UIView()
  private let scrimView = UIControl()
  private var panGesture: UIPanGestureRecognizer!
  private var activeAnimator: UIViewPropertyAnimator?
  private var activeAnimatorEmitsSettle = false
  private var displayLink: CADisplayLink?
  private var pendingIndex: Int?
  private var hasLaidOut = false
  private var isPanning = false
  private var isContentInteractionDisabled = false
  private weak var contentHeightMarker: UIView?

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
          return
        }
      }
      current = view.superview
    }
  }

  override public func layoutSubviews() {
    super.layoutSubviews()
    guard bounds.width > 0, bounds.height > 0 else { return }

    scrimView.frame = bounds
    refreshDetentsFromLayout()
    let maxHeight = maximumResolvedDetentHeight ?? resolvedMaxDetentHeight
    sheetContainer.bounds = CGRect(x: 0, y: 0, width: bounds.width, height: maxHeight)
    sheetContainer.center = CGPoint(x: bounds.width / 2, y: bounds.height - maxHeight / 2)

    if !hasLaidOut && !detentSpecs.isEmpty {
      let indexToApply = pendingIndex ?? targetIndex
      let clampedIndex = max(0, min(detentSpecs.count - 1, indexToApply))

      if animateIn, isInvalidContentDetentTarget(clampedIndex) {
        targetIndex = clampedIndex
        pendingIndex = clampedIndex
        let closedTy = maximumResolvedDetentHeight ?? bounds.height
        sheetContainer.transform = CGAffineTransform(translationX: 0, y: closedTy)
        emitPosition()
        return
      }

      hasLaidOut = true
      pendingIndex = nil
      targetIndex = clampedIndex

      if animateIn {
        let closedTy = maximumResolvedDetentHeight ?? bounds.height
        sheetContainer.transform = CGAffineTransform(translationX: 0, y: closedTy)
        emitPosition()
        snapToIndex(targetIndex, velocity: 0, emitIndexChange: false, emitSettle: false)
      } else {
        sheetContainer.transform = CGAffineTransform(translationX: 0, y: translationY(for: targetIndex))
        emitPosition()
      }
      return
    }

    if activeAnimator != nil || isPanning { return }
    sheetContainer.transform = CGAffineTransform(translationX: 0, y: translationY(for: targetIndex))
    updateScrim()
  }

  private var presentedSheetFrame: CGRect {
    if activeAnimator != nil, let presentation = sheetContainer.layer.presentation() {
      return presentation.frame
    }
    return sheetContainer.frame
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

  public func resetSheetState() {
    activeAnimator?.stopAnimation(true)
    activeAnimator = nil
    stopDisplayLink()
    rawDetentSpecs = []
    detentSpecs = []
    targetIndex = 0
    pendingIndex = nil
    hasLaidOut = false
    isPanning = false
    setContentInteractionEnabled(true)
    contentHeightMarker = nil
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
    let maxHeight = maximumResolvedDetentHeight ?? resolvedMaxDetentHeight
    let snapHeight = detent(at: index).height
    return maxHeight - snapHeight
  }

  private var draggableRange: (minTy: CGFloat, maxTy: CGFloat) {
    let draggable = detentSpecs.enumerated().filter { !$0.element.programmatic }
    let highestIndex = draggable.last?.offset ?? 0
    let lowestIndex = draggable.first?.offset ?? 0
    return (minTy: translationY(for: highestIndex), maxTy: translationY(for: lowestIndex))
  }

  private var closedIndex: Int? {
    detentSpecs.firstIndex(where: { $0.height == 0 })
  }

  private var firstNonZeroDetentHeight: CGFloat {
    detentSpecs.first(where: { $0.height > 0 })?.height ?? 0
  }

  private var currentSheetHeight: CGFloat {
    let maxHeight = maximumResolvedDetentHeight ?? resolvedMaxDetentHeight
    let ty = currentTranslationY
    return maxHeight - ty
  }

  public var currentContentOffsetY: CGFloat {
    let maxHeight = maximumResolvedDetentHeight ?? resolvedMaxDetentHeight
    let containerTop = bounds.height - maxHeight
    let ty = currentTranslationY
    return containerTop + ty
  }

  private var isScrimVisible: Bool {
    modal && !scrimView.isHidden
  }

  private func emitPosition() {
    let maxHeight = maximumResolvedDetentHeight ?? resolvedMaxDetentHeight
    let ty = currentTranslationY
    let position = maxHeight - ty
    updateScrim(forPosition: position)
    updateSheetVisibility(forPosition: position)
    updateInteractionState()
    eventDelegate?.bottomSheetHostingView(self, didChangePosition: position)
  }

  private func startDisplayLink() {
    guard displayLink == nil else { return }
    let link = CADisplayLink(target: self, selector: #selector(displayLinkFired))
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

  @objc private func displayLinkFired() {
    emitPosition()
  }

  @objc private func handleScrimPress() {
    guard
      modal,
      let closedIndex,
      targetIndex != closedIndex,
      activeAnimator == nil || currentSheetHeight > 0.5
    else {
      return
    }

    snapToIndex(closedIndex, velocity: 0)
  }

  private func snapToIndex(
    _ index: Int,
    velocity: CGFloat,
    emitIndexChange: Bool = true,
    emitSettle: Bool = true
  ) {
    guard index >= 0, index < detentSpecs.count else { return }
    targetIndex = index

    let currentTy = sheetContainer.transform.ty
    let targetTy = translationY(for: index)
    let distance = targetTy - currentTy
    let velocityRatio = distance != 0 ? velocity / distance : 0
    let clampedRatio = min(max(velocityRatio, -5), 5)
    let initialVelocity = CGVector(dx: 0, dy: clampedRatio)

    activeAnimatorEmitsSettle = emitSettle
    activeAnimator?.stopAnimation(true)

    let spring = UISpringTimingParameters(dampingRatio: 1.0, initialVelocity: initialVelocity)
    let animator = UIViewPropertyAnimator(duration: 0.45, timingParameters: spring)

    animator.addAnimations {
      self.sheetContainer.transform = CGAffineTransform(translationX: 0, y: targetTy)
    }
    animator.addCompletion { [weak self] position in
      guard let self, position == .end else { return }
      self.stopDisplayLink()
      self.emitPosition()
      self.activeAnimator = nil
      self.activeAnimatorEmitsSettle = false
      self.setContentInteractionEnabled(true)
      self.updateInteractionState()
      if emitIndexChange {
        self.eventDelegate?.bottomSheetHostingView(self, didChangeIndex: index)
      }
      if emitSettle {
        self.eventDelegate?.bottomSheetHostingView(self, didSettle: index)
      }
    }
    animator.startAnimation()
    activeAnimator = animator
    startDisplayLink()
  }

  @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
    let maxHeight = maximumResolvedDetentHeight ?? resolvedMaxDetentHeight

    switch gesture.state {
    case .began:
      isPanning = true
      sheetContainer.endEditing(true)
      setContentInteractionEnabled(false)
      if let handler = surfaceTouchHandler {
        handler.isEnabled = false
        handler.isEnabled = true
      }
      gesture.setTranslation(.zero, in: self)
      if let animator = activeAnimator {
        stopDisplayLink()
        let visual = sheetContainer.layer.presentation()?.affineTransform() ?? sheetContainer.transform
        animator.stopAnimation(true)
        sheetContainer.transform = visual
        activeAnimator = nil
      }

    case .changed:
      let delta = gesture.translation(in: self).y
      gesture.setTranslation(.zero, in: self)
      let minTy = draggableRange.minTy
      let maxTy = draggableRange.maxTy
      let newTy = max(minTy, min(maxTy, sheetContainer.transform.ty + delta))
      sheetContainer.transform = CGAffineTransform(translationX: 0, y: newTy)
      emitPosition()

    case .ended:
      isPanning = false
      let velocity = gesture.velocity(in: self).y
      let currentHeight = maxHeight - sheetContainer.transform.ty
      let index = bestSnapIndex(for: currentHeight, velocity: velocity)
      snapToIndex(index, velocity: velocity)

    case .cancelled:
      isPanning = false
      setContentInteractionEnabled(true)
      let cancelVelocity = gesture.velocity(in: self).y
      let cancelHeight = maxHeight - sheetContainer.transform.ty
      let cancelIndex = bestSnapIndex(for: cancelHeight, velocity: cancelVelocity)
      snapToIndex(cancelIndex, velocity: cancelVelocity)

    case .failed:
      isPanning = false
      setContentInteractionEnabled(true)

    default:
      break
    }
  }

  private func bestSnapIndex(for height: CGFloat, velocity: CGFloat) -> Int {
    let draggable = detentSpecs.enumerated().filter { !$0.element.programmatic }
    guard !draggable.isEmpty else { return targetIndex }

    let flickThreshold: CGFloat = 600

    if velocity < -flickThreshold {
      return draggable.first(where: { $0.element.height > height })?.offset
        ?? draggable.last?.offset ?? targetIndex
    }
    if velocity > flickThreshold {
      return draggable.last(where: { $0.element.height < height })?.offset
        ?? draggable.first?.offset ?? targetIndex
    }

    return draggable.min(by: {
      abs($0.element.height - height) < abs($1.element.height - height)
    })?.offset ?? targetIndex
  }

  private func isVerticallyScrollable(_ scrollView: UIScrollView) -> Bool {
    let verticalInset = scrollView.adjustedContentInset.top + scrollView.adjustedContentInset.bottom
    let visibleHeight = max(0, scrollView.bounds.height - verticalInset)
    return scrollView.alwaysBounceVertical || scrollView.contentSize.height > visibleHeight
  }

  private func isViewInverted(_ view: UIView) -> Bool {
    var current: UIView? = view
    while let v = current, v !== sheetContainer {
      if v.transform.d < 0 { return true }
      current = v.superview
    }
    return false
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

    let draggable = detentSpecs.enumerated().filter { !$0.element.programmatic }
    guard draggable.count > 1 else { return false }

    if disableScrollableNegotiation {
      let locationInContainer = panGesture.location(in: sheetContainer)
      if scrollView(containing: locationInContainer, in: sheetContainer) != nil {
        return false
      }
    }

    let maxDraggableIndex = draggable.last?.offset ?? 0
    // Below max: allow drag in either direction to reach other detents.
    guard targetIndex >= maxDraggableIndex else { return true }
    // At max: only allow downward drag, and only when the scroll view (if any)
    // is at its top edge — otherwise the scroll view should handle the gesture.
    if velocity.y < 0 {
      return false
    }

    let locationInContainer = panGesture.location(in: sheetContainer)
    guard let scrollView = scrollView(containing: locationInContainer, in: sheetContainer) else {
      return true
    }
    let inverted = isViewInverted(scrollView)
    if inverted {
      let maxOffsetY = scrollView.contentSize.height - scrollView.bounds.height + scrollView.adjustedContentInset.bottom
      return scrollView.contentOffset.y >= maxOffsetY
    }
    return scrollView.contentOffset.y <= 0
  }

  private var resolvedMaxDetentHeight: CGFloat {
    if !maxDetentHeight.isFinite || maxDetentHeight <= 0 {
      return bounds.height
    }
    return min(max(0, maxDetentHeight), bounds.height)
  }

  private var maximumResolvedDetentHeight: CGFloat? {
    detentSpecs.map(\.height).max()
  }

  private func resolveDetentSpecs() -> [DetentSpec] {
    let maxHeight = resolvedMaxDetentHeight
    let measuredContentHeight = maxHeight > 0 ? validContentHeight.map { min($0, maxHeight) } : nil
    let contentHeight = measuredContentHeight ?? maxHeight
    return rawDetentSpecs.enumerated().map { index, spec in
      let height: CGFloat
      switch spec.kind {
      case .points:
        if measuredContentHeight != nil, spec.value > contentHeight {
          NSException(
            name: NSExceptionName.invalidArgumentException,
            reason:
            "Invalid bottom sheet detent at index \(index): fixed detent \(spec.value) exceeds measured content height \(contentHeight).",
            userInfo: nil
          ).raise()
        }
        height = spec.value
      case .content:
        height = contentHeight
      }
      return DetentSpec(height: min(max(0, height), maxHeight), programmatic: spec.programmatic)
    }
  }

  private func refreshDetentsFromLayout() {
    refreshContentHeightMarker()
    if hasLaidOut, isInvalidContentDetentTarget(targetIndex) {
      updateScrim()
      return
    }

    let resolvedDetents = resolveDetentSpecs()
    guard resolvedDetents != detentSpecs else {
      updateScrim()
      return
    }

    detentSpecs = resolvedDetents

    guard bounds.width > 0, bounds.height > 0, !detentSpecs.isEmpty else {
      return
    }

    if hasLaidOut, !isPanning {
      targetIndex = max(0, min(detentSpecs.count - 1, targetIndex))

      if let animator = activeAnimator {
        stopDisplayLink()
        let visualTy = sheetContainer.layer.presentation()?.affineTransform().ty ?? sheetContainer.transform.ty
        let shouldEmitSettle = activeAnimatorEmitsSettle
        animator.stopAnimation(true)
        activeAnimator = nil
        activeAnimatorEmitsSettle = false
        sheetContainer.transform = CGAffineTransform(
          translationX: 0,
          y: min(max(visualTy, 0), maximumResolvedDetentHeight ?? visualTy)
        )
        emitPosition()
        snapToIndex(targetIndex, velocity: 0, emitIndexChange: false, emitSettle: shouldEmitSettle)
      } else {
        let targetTy = translationY(for: targetIndex)
        let currentTy = currentTranslationY
        if abs(targetTy - currentTy) <= 0.5 {
          sheetContainer.transform = CGAffineTransform(translationX: 0, y: targetTy)
          emitPosition()
        } else {
          snapToIndex(targetIndex, velocity: 0, emitIndexChange: false, emitSettle: false)
        }
      }
    }
  }

  private func refreshContentHeightMarker() {
    contentHeightMarker = findContentHeightMarker()
  }

  private func findContentHeightMarker() -> UIView? {
    guard let contentView = sheetContainer.subviews.first else { return nil }
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

extension RNSBottomSheetHostingView: UIGestureRecognizerDelegate {
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

private extension RNSBottomSheetHostingView {
  var currentTranslationY: CGFloat {
    if activeAnimator != nil, let presentation = sheetContainer.layer.presentation() {
      return presentation.affineTransform().ty
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

    // If we're settled on the closed detent, dynamic detent/content updates can
    // momentarily report a stale non-zero position. Keep scrim fully hidden.
    if
      let closedIndex,
      targetIndex == closedIndex,
      activeAnimator == nil,
      !isPanning
    {
      scrimView.alpha = 0
      scrimView.isHidden = true
      return
    }

    let threshold = firstNonZeroDetentHeight
    let progress: CGFloat
    if threshold <= 0 {
      progress = 0
    } else {
      progress = min(1, max(0, position / threshold))
    }
    scrimView.alpha = progress
    scrimView.isHidden = progress <= 0.001
  }

  func updateInteractionState() {
    scrimView.isUserInteractionEnabled = modal && (closedIndex != nil) && !scrimView.isHidden
  }
}
