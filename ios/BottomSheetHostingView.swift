import UIKit
import UIKit.UIGestureRecognizerSubclass

@objc public protocol BottomSheetHostingViewDelegate: AnyObject {
  func bottomSheetHostingView(_ view: BottomSheetHostingView, didChangeIndex index: Int)
  func bottomSheetHostingView(_ view: BottomSheetHostingView, didSettle index: Int)
  func bottomSheetHostingView(
    _ view: BottomSheetHostingView, didChangePosition position: CGFloat, index: CGFloat
  )
  func bottomSheetHostingView(_ view: BottomSheetHostingView, didReportError message: String)
  /// Fired after each layout pass with fresh native geometry, so the component
  /// layer can push the content wrapper's target size (and, in overlay mode,
  /// the sheet frame) into the shadow tree.
  func bottomSheetHostingViewDidLayout(_ view: BottomSheetHostingView)
}

private struct DetentSpec: Equatable {
  let height: CGFloat
  let programmatic: Bool
}

private enum DetentKind {
  case points
  case percentage
  case content
}

private struct RawDetentSpec: Equatable {
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

private enum PanCoordinationMode {
  case sheet
  case scrollView
}

private enum ScrollableNegotiationLevel: Int {
  case none = 0
  case initial = 1
  case handoff = 2

  init(clamping value: Int) {
    self = ScrollableNegotiationLevel(rawValue: min(max(value, 0), 2)) ?? .none
  }
}

/// The sheet's pan. UIKit's pan recognizers decide once, at a translation
/// threshold, and the sheet rejects a start there that it cannot take: a
/// sideways one, so horizontal recognizers under the touch keep it, or a
/// vertical one in a direction it has no detent for. A vertical scrollable's
/// own pan is not decided that way: it stays possible through sideways travel
/// and begins once vertical travel passes its slop, in either direction, so a
/// start the sheet rejected would later scroll content the sheet owns at this
/// detent. Inside a vertical scrollable with no horizontal recognizer under the
/// touch, the start is therefore held: while the pan is possible, a move
/// reaches UIKit's pan machinery only along a vertical direction the hold has
/// decided and the sheet can use. The direction is decided once vertical travel
/// passes the slop, and decided anew whenever the travel turns around by the
/// slop from its far point. Travel the sheet cannot use belongs to the scrollable;
/// only handoff may take the touch back on a reversal. While held, the sheet
/// yields to pans above it the way the scrollable would: see
/// `gestureRecognizer(_:shouldBeRequiredToFailBy:)`.
private final class SheetPanGestureRecognizer: UIPanGestureRecognizer {
  /// The vertical directions the sheet can use at its detent, as the touch lands.
  struct UsableDirections {
    var up: Bool
    var down: Bool
  }

  /// Vertical travel that decides a held start's direction, matching the
  /// threshold UIKit's pan recognizers apply to total travel.
  private static let verticalSlop: CGFloat = 10

  /// Whether the start is held: a single touch inside a vertical scrollable
  /// the sheet negotiates with.
  private(set) var isHoldingStart = false
  private var usableDirections = UsableDirections(up: false, down: false)
  private var handoffDirections = UsableDirections(up: false, down: false)
  /// The far point of travel in the decided direction, the touch-down point
  /// while undecided: a reversal is measured from it.
  private var farY: CGFloat = 0

  /// The vertical direction the hold decided, negative up and positive down.
  /// Zero while undecided.
  private(set) var decidedVerticalDirection: CGFloat = 0

  /// Whether the hold decided a direction the sheet can use.
  var holdDecidedDirection: Bool {
    isHoldingStart && decidedDirectionIsUsable
  }

  private var decidedDirectionIsUsable: Bool {
    if decidedVerticalDirection < 0 {
      return usableDirections.up
    }
    if decidedVerticalDirection > 0 {
      return usableDirections.down
    }
    return false
  }

  /// Arms the hold for a first touch. Called by the host from
  /// `gestureRecognizer(_:shouldReceive:)`, before UIKit delivers the touch,
  /// so `isHoldingStart` is already settled whenever UIKit asks for failure
  /// requirements.
  func prepareHold(
    for touch: UITouch,
    usable directions: UsableDirections,
    handoff: UsableDirections
  ) {
    usableDirections = directions
    handoffDirections = handoff
    farY = holdY(of: touch)
    decidedVerticalDirection = 0
    isHoldingStart = directions.up || directions.down
  }

  /// The touch's vertical position in the host's coordinates, the pan's own
  /// translation axis. The sheet container can move under a held touch, by a
  /// programmatic snap or a content re-anchor, and that movement is not travel.
  private func holdY(of touch: UITouch) -> CGFloat {
    touch.location(in: view?.superview).y
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
    if isHoldingStart, numberOfTouches + touches.count > 1 {
      // Only a single finger is held.
      isHoldingStart = false
    }
    super.touchesBegan(touches, with: event)
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
    if isHoldingStart {
      if numberOfTouches != 1 {
        isHoldingStart = false
      } else if state == .possible, let touch = touches.first {
        guard shouldDeliver(moveAt: holdY(of: touch)) else { return }
      }
    }
    super.touchesMoved(touches, with: event)
  }

  /// Whether a move, while the pan is possible, reaches UIKit: only moves along
  /// a decided direction the sheet can use do. Travel the sheet cannot use, and
  /// travel back within the slop, is withheld; a reversal by the slop from the
  /// far point decides the direction anew. The scrollable's own pan may begin
  /// earlier, on a sideways move with a trace of vertical travel, and scroll by
  /// that trace until the slop decides; a trace is not a direction.
  private func shouldDeliver(moveAt y: CGFloat) -> Bool {
    if decidedVerticalDirection != 0 {
      let advance = (y - farY) * decidedVerticalDirection
      if advance >= 0 {
        farY = y
        return decidedDirectionIsUsable
      }
      guard -advance >= Self.verticalSlop else { return false }
    } else {
      guard abs(y - farY) >= Self.verticalSlop else { return false }
    }
    decidedVerticalDirection = y < farY ? -1 : 1
    farY = y
    guard decidedDirectionIsUsable else {
      // A meaningful start the sheet cannot use selects the scrollable. An
      // initial-only direction cannot take ownership later in the same touch.
      usableDirections = handoffDirections
      if !usableDirections.up, !usableDirections.down {
        isHoldingStart = false
        state = .failed
      }
      return false
    }
    return true
  }

  override func reset() {
    isHoldingStart = false
    usableDirections = UsableDirections(up: false, down: false)
    handoffDirections = UsableDirections(up: false, down: false)
    farY = 0
    decidedVerticalDirection = 0
    super.reset()
  }
}

/// Keeps the caller's preference separate from temporary sheet suppression.
private final class ScrollIndicatorState {
  weak var scrollView: UIScrollView?
  private var requestedVisibility: Bool
  private var isSuppressed = false
  private var isApplyingVisibility = false
  private var observation: NSKeyValueObservation?

  init(scrollView: UIScrollView) {
    self.scrollView = scrollView
    requestedVisibility = scrollView.showsVerticalScrollIndicator
    observation = scrollView.observe(\.showsVerticalScrollIndicator, options: [.new]) { [weak self] _, change in
      guard let self, !self.isApplyingVisibility, let visible = change.newValue else { return }
      self.requestedVisibility = visible
      self.applyVisibility()
    }
  }

  func setSuppressed(_ suppressed: Bool) {
    isSuppressed = suppressed
    applyVisibility()
  }

  func restore() {
    observation?.invalidate()
    observation = nil
    scrollView?.showsVerticalScrollIndicator = requestedVisibility
  }

  private func applyVisibility() {
    guard let scrollView else { return }
    let visible = requestedVisibility && !isSuppressed
    guard scrollView.showsVerticalScrollIndicator != visible else { return }
    isApplyingVisibility = true
    scrollView.showsVerticalScrollIndicator = visible
    isApplyingVisibility = false
  }
}

private final class ActiveScrollViewState {
  weak var scrollView: UIScrollView?
  var pinnedOffset: CGPoint
  let inverted: Bool
  /// Whether the scroll view was at or before its visual start as the touch
  /// landed. A held start lets it scroll before the sheet's pan begins; the
  /// lock that begins the pan pins it back.
  let wasAtStartWhenTouched: Bool
  /// Whether the scroll view reached or passed its visual start while the
  /// sheet, at its lowest detent, had released the touch to it. The lock that
  /// takes the touch back pins it there: the reversal update may already have
  /// scrolled it by a few points.
  var reachedStartWhileReleased = false
  /// The caller's preferences, captured as the sheet locks the scroll view and
  /// restored when it unlocks.
  var bounces: Bool
  var directionalLockEnabled: Bool
  private var contentOffsetObservation: NSKeyValueObservation?
  private var isRestoringPinnedOffset = false

  init(scrollView: UIScrollView, inverted: Bool, wasAtStartWhenTouched: Bool) {
    self.scrollView = scrollView
    pinnedOffset = scrollView.contentOffset
    self.inverted = inverted
    self.wasAtStartWhenTouched = wasAtStartWhenTouched
    bounces = scrollView.bounces
    directionalLockEnabled = scrollView.isDirectionalLockEnabled
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

  /// The cap value the container geometry and translations are currently
  /// anchored to; used to detect cap-only geometry changes and to re-anchor
  /// against the value the current translation was computed with.
  private var lastAppliedMaxDetentHeight: CGFloat = .nan

  /// Whether full-height detents may extend under the status bar. Feeds the
  /// natively computed detent cap; there is no JS-provided cap anymore.
  public var extendUnderStatusBar: Bool = false {
    didSet {
      guard extendUnderStatusBar != oldValue else { return }
      refreshDetentsFromLayout()
      setNeedsLayout()
    }
  }

  public var scrollableExpandNegotiation: Int = ScrollableNegotiationLevel.handoff.rawValue
  public var scrollableCollapseNegotiation: Int = ScrollableNegotiationLevel.initial.rawValue

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
  private var panGesture: SheetPanGestureRecognizer!
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
  private var initialScrollViewStates: [ActiveScrollViewState] = []
  /// Whether every scrollable under the touch was at its visual start as the
  /// touch landed: the `initial` owner selection at the largest detent.
  private var initialScrollableChainAtStart = true
  private var activeDragRange: (minTy: CGFloat, maxTy: CGFloat)?
  private var activeDragDetentSpecs: [DetentSpec]?
  private var activeScrollViewStates: [ActiveScrollViewState] = []
  private var scrollIndicatorStates: [ObjectIdentifier: ScrollIndicatorState] = [:]
  private var scrollIndicatorParentTags: Set<Int> = []
  private var lastIndicatorTouchTimestamp: TimeInterval?
  private var panCoordinationMode: PanCoordinationMode = .sheet
  private var activeScrollableNegotiationLevel: ScrollableNegotiationLevel = .none
  private var didMoveSheetDuringPan = false
  private var didCancelTouchesForPan = false
  private var activeScrollViewsLocked = false
  private var scrollViewOwnsLowerBoundary = false
  private var isContentInteractionDisabled = false
  // The `nativeID` the JS side gives the zero-height marker it appends to the
  // content wrapper. Mirrored in `bottomSheetUtils.ts` and `BottomSheetHostView.kt`;
  // keep the three in sync.
  private static let contentHeightMarkerNativeID = "swm-bottom-sheet-content-height-marker"
  private var contentHeightMarker: UIView?
  private weak var surfaceView: UIView?
  private static var markerObservationContext = 0
  /// Whether a detents change moved the content region while Fabric has yet
  /// to lay the content out against it. Resolving a `content` detent from the
  /// stale measurement could trip the ascending-order check: a filling
  /// scrollable measured in a 300pt region cannot sit above a 500pt detent
  /// that the same update introduced.
  private var isContentRegionChangePending = false
  /// The content-region inset of the state Fabric last committed, recorded
  /// mid-transaction. NaN before the first commit.
  private var committedContentRegionInset: CGFloat = .nan
  /// The committed inset once its mounting transaction has fully mounted, so
  /// the content — and its marker — is laid out against it. Only this value
  /// vouches for a content measurement: mid-transaction, the host's own frame
  /// (and thus its safe-area insets) can change before the marker's layout
  /// mutation runs. NaN before the first transaction completes.
  private var appliedContentRegionInset: CGFloat = .nan
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

    panGesture = SheetPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
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

  override public func safeAreaInsetsDidChange() {
    super.safeAreaInsetsDidChange()
    // The native detent cap depends on the window's top inset.
    refreshDetentsFromLayout()
    setNeedsLayout()
  }

  override public func didMoveToWindow() {
    super.didMoveToWindow()
    surfaceTouchHandler = nil
    guard window != nil else {
      restoreScrollIndicators()
      return
    }
    // The native detent cap becomes computable once a window exists.
    refreshDetentsFromLayout()
    setNeedsLayout()
    var current: UIView? = superview
    while let view = current {
      for gr in view.gestureRecognizers ?? [] {
        if NSStringFromClass(type(of: gr)).contains("TouchHandler") {
          surfaceTouchHandler = gr
          break
        }
      }
      if surfaceTouchHandler != nil {
        break
      }
      current = view.superview
    }
    flushPendingSnapIfNeeded()
    refreshScrollIndicatorViews()
  }

  override public func layoutSubviews() {
    super.layoutSubviews()
    defer { updateScrollIndicators() }
    guard bounds.width > 0, bounds.height > 0 else { return }
    // See refreshDetentsFromLayout: without a window the detent cap falls back
    // to the full height, which must not be applied to the container geometry.
    if hasLaidOut, window == nil {
      return
    }

    scrimView.frame = bounds
    refreshDetentsFromLayout()
    let maxHeight = sheetContainerHeight
    lastAppliedMaxDetentHeight = maxHeight
    sheetContainer.bounds = CGRect(x: 0, y: 0, width: bounds.width, height: maxHeight)
    sheetContainer.center = CGPoint(x: bounds.width / 2, y: bounds.height - maxHeight / 2)

    // The surface fills the full container so it always covers the visible sheet
    // (the container is translated to the current sheet position), regardless of
    // how short the content becomes. Sized from the top via frame — never via
    // anchorPoint.
    surfaceView?.frame = sheetContainer.bounds

    // Report fresh native geometry so the component layer can push the content
    // wrapper's target size (and the overlay frame) into the shadow tree.
    eventDelegate?.bottomSheetHostingViewDidLayout(self)

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

    if activeSpring != nil || isPanning {
      return
    }
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
    // Native descendants can appear outside a Fabric transaction. Discover
    // them before UIKit can hit-test their scroll indicators, at most once
    // per touch event. Repeated hit-tests and nil-event geometry queries use
    // the cache rather than walking the subtree again.
    if let event, event.type == .touches, lastIndicatorTouchTimestamp != event.timestamp {
      lastIndicatorTouchTimestamp = event.timestamp
      refreshScrollIndicatorViews()
    }
    return sheetContainer.hitTest(containerPoint, with: event)
  }

  public func setDetents(_ raw: [NSDictionary]) {
    let previousTallestDetent = rawDetentSpecs.last
    rawDetentSpecs = raw.compactMap { dict in
      guard let value = dict["value"] as? Double ?? (dict["value"] as? NSNumber)?.doubleValue else {
        return nil
      }
      let kindString =
        (dict["kind"] as? String) ?? ((dict["kind"] as? NSString) as String?) ?? "points"
      let kind: DetentKind
      switch kindString {
      case "content":
        kind = .content
      case "percentage":
        kind = .percentage
      default:
        kind = .points
      }
      let programmatic = (dict["programmatic"] as? Bool) ?? (dict["programmatic"] as? NSNumber)?.boolValue ?? false
      return RawDetentSpec(value: CGFloat(value), kind: kind, programmatic: programmatic)
    }
    // The tallest detent bounds the content region. The content stays laid out
    // against the previous region until Fabric commits the new inset; hold its
    // measurement until then. Decided from the detents alone so it also holds
    // while detached, when the cap is not computable.
    if hasLaidOut, rawDetentSpecs.last != previousTallestDetent {
      isContentRegionChangePending = true
    }
    refreshDetentsFromLayout()
  }

  /// Records the content-region inset of a state Fabric committed. The content
  /// is laid out against it once the same mounting transaction completes (see
  /// `contentRegionLayoutDidApply`), not before.
  public func contentRegionStateDidCommit(_ inset: CGFloat) {
    committedContentRegionInset = inset
  }

  public func setDetentIndex(_ newIndex: Int) {
    guard newIndex >= 0 else { return }

    if !hasLaidOut {
      pendingIndex = newIndex
      targetIndex = newIndex
      return
    }

    if window == nil {
      // Resolved detents intentionally stay stale while detached, but the raw
      // specs already reflect the latest props. Treat every detached index
      // update as authoritative so a later request can replace or cancel an
      // earlier queued snap — or a retained index — before the view is
      // reattached.
      pendingIndex = nil
      guard rawDetentSpecs.indices.contains(newIndex) else {
        pendingSnapRequest = nil
        return
      }

      if newIndex >= detentSpecs.count {
        // Not resolvable yet (see the attached path below); a queued snap
        // would be dropped at flush, so retain the index instead.
        pendingSnapRequest = nil
        pendingIndex = newIndex
        return
      }

      if newIndex == targetIndex {
        pendingSnapRequest = nil
        return
      }

      pendingSnapRequest = PendingSnapRequest(
        index: newIndex,
        velocity: 0,
        emitIndexChange: false,
        emitSettle: true,
        preserveScrimPin: false
      )
      return
    }

    guard newIndex < detentSpecs.count else {
      // The resolved detents lag the raw specs while a content measurement is
      // held (see isContentRegionChangePending): `detents` and `index` arrive
      // in one update, so an index that only the new detents cover is retained
      // and replayed once they resolve rather than dropped.
      if rawDetentSpecs.indices.contains(newIndex) {
        pendingIndex = newIndex
      }
      return
    }
    // A newer request supersedes a retained one.
    pendingIndex = nil
    guard newIndex != targetIndex else { return }
    snapToIndex(newIndex, velocity: 0, emitIndexChange: false)
  }

  /// Applies an index that was requested before the resolved detents covered
  /// it. Runs after every detent resolution; a drag in progress keeps it
  /// waiting, and the user's snap at the end of the drag drops it.
  private func replayPendingIndexIfResolved() {
    guard hasLaidOut, !isPanning, let index = pendingIndex, index < detentSpecs.count else {
      return
    }
    pendingIndex = nil
    setDetentIndex(index)
  }

  public func mountChildComponentView(_ childView: UIView, atIndex index: Int) {
    defer { refreshScrollIndicatorViews() }
    sheetContainer.insertSubview(childView, at: index)
    refreshContentHeightMarker()
    setNeedsLayout()
  }

  public func unmountChildComponentView(_ childView: UIView) {
    defer { refreshScrollIndicatorViews() }
    childView.removeFromSuperview()
    refreshContentHeightMarker()
    setNeedsLayout()
  }

  public func mountSurfaceComponentView(_ childView: UIView, atIndex index: Int) {
    defer { refreshScrollIndicatorViews() }
    surfaceView = childView
    sheetContainer.insertSubview(childView, at: index)
    refreshContentHeightMarker()
    setNeedsLayout()
  }

  public func unmountSurfaceComponentView(_ childView: UIView) {
    defer { refreshScrollIndicatorViews() }
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
    lastAppliedMaxDetentHeight = .nan
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
    initialScrollViewStates = []
    initialScrollableChainAtStart = true
    finishScrollViewCoordination()
    restoreScrollIndicators()
    setContentInteractionEnabled(true)
    stopObservingContentHeightMarker()
    isContentRegionChangePending = false
    committedContentRegionInset = .nan
    appliedContentRegionInset = .nan
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
    // The content's in-host displacement from its Yoga position: the container
    // offset plus the sheet's translation. The content-region inset shrinks
    // the content via Yoga BOTTOM padding, keeping the Yoga origin at zero, so
    // the full displacement is carried here.
    let maxHeight = sheetContainerHeight
    let containerTop = bounds.height - maxHeight
    return containerTop + currentTranslationY
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
    // Emit follower positions at the display's high refresh rate. Without an
    // explicit range a CADisplayLink runs at 60fps on ProMotion (even with
    // CADisableMinimumFrameDurationOnPhone set), so followers driven by
    // `onPositionChange` would update at half the rate of the sheet's
    // render-server animation.
    link.preferredFrameRateRange = CAFrameRateRange(minimum: 80, maximum: 120, preferred: 120)
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

  private func cancelContentTouchesForPanIfNeeded() {
    guard !didCancelTouchesForPan else { return }
    didCancelTouchesForPan = true
    if let handler = surfaceTouchHandler {
      handler.isEnabled = false
      handler.isEnabled = true
    }
  }

  /// Discover only when hierarchy changes or a new touch needs a native-view
  /// fallback. Layout and animation frames update the cached scroll views.
  private func refreshScrollIndicatorViews() {
    guard window != nil else {
      restoreScrollIndicators()
      return
    }
    var descendants: [ObjectIdentifier: UIScrollView] = [:]
    var parentTags: Set<Int> = []
    func collect(_ view: UIView) {
      // Nested sheets apply their own indicator policy.
      guard !(view is BottomSheetHostingView) else { return }
      if view.tag != 0 {
        parentTags.insert(view.tag)
      }
      if let scrollView = view as? UIScrollView {
        descendants[ObjectIdentifier(scrollView)] = scrollView
      }
      for child in view.subviews {
        collect(child)
      }
    }
    collect(sheetContainer)
    scrollIndicatorParentTags = parentTags
    for (id, state) in scrollIndicatorStates where state.scrollView !== descendants[id] {
      state.restore()
      scrollIndicatorStates.removeValue(forKey: id)
    }
    for (id, scrollView) in descendants where scrollIndicatorStates[id] == nil {
      scrollIndicatorStates[id] = ScrollIndicatorState(scrollView: scrollView)
    }
    updateScrollIndicators()
  }

  private func updateScrollIndicators(forPosition position: CGFloat? = nil) {
    guard window != nil else {
      restoreScrollIndicators()
      return
    }
    // A programmatic-only top is still full expansion. Use the actual
    // position, not an animation target or the available drag range.
    let fullyExpandedHeight = detentSpecs.lazy.map(\.height).max() ?? 0
    let partiallyExpanded = !detentSpecs.isEmpty
      && (!hasLaidOut || (position ?? currentSheetHeight) < fullyExpandedHeight - 0.5)
    for state in scrollIndicatorStates.values {
      guard let scrollView = state.scrollView else { continue }
      let lockedForPan = activeScrollViewsLocked && activeScrollViewStates.contains {
        $0.scrollView === scrollView
      }
      state.setSuppressed(partiallyExpanded || lockedForPan)
    }
  }

  private func restoreScrollIndicators() {
    for state in scrollIndicatorStates.values {
      state.restore()
    }
    scrollIndicatorStates.removeAll()
    scrollIndicatorParentTags.removeAll()
    lastIndicatorTouchTimestamp = nil
  }

  /// Pins the touched scroll views while the sheet moves. Each is pinned at
  /// its visual start when asked for all of them, when it reached that start
  /// while released at the lowest detent, or, for the lock that begins the
  /// pan, when it was there as the touch landed; otherwise where it rests now.
  private func lockActiveScrollViews(pinToStart: Bool = false, pinInitialStarts: Bool = false) {
    guard !activeScrollViewStates.isEmpty else { return }

    for state in activeScrollViewStates {
      guard let scrollView = state.scrollView else { continue }
      if !activeScrollViewsLocked {
        // Captured here rather than at touch-down: a held start can outlive a
        // prop change, which the unlock would otherwise overwrite.
        state.bounces = scrollView.bounces
        state.directionalLockEnabled = scrollView.isDirectionalLockEnabled
      }
      let pinnedToStart = pinToStart
        || state.reachedStartWhileReleased
        || (pinInitialStarts && state.wasAtStartWhenTouched)
      state.reachedStartWhileReleased = false
      let pinnedOffset = pinnedToStart
        ? scrollStartOffset(for: scrollView, inverted: state.inverted)
        : restingOffset(for: scrollView)
      scrollView.bounces = false
      scrollView.isDirectionalLockEnabled = true
      state.startPinning(at: pinnedOffset)
    }
    activeScrollViewsLocked = true
    updateScrollIndicators()
  }

  private func pinActiveScrollViews() {
    guard activeScrollViewsLocked else { return }
    for state in activeScrollViewStates {
      state.restorePinnedOffset()
    }
  }

  private func unlockActiveScrollViews() {
    guard activeScrollViewsLocked else { return }
    for state in activeScrollViewStates {
      state.stopPinning()
      guard let scrollView = state.scrollView else { continue }
      scrollView.bounces = state.bounces
      scrollView.isDirectionalLockEnabled = state.directionalLockEnabled
    }
    activeScrollViewsLocked = false
    updateScrollIndicators()
  }

  private func cancelActiveScrollViewPans() {
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

  private func finishScrollViewCoordination() {
    unlockActiveScrollViews()
    activeScrollViewStates = []
    panCoordinationMode = .sheet
    activeScrollableNegotiationLevel = .none
    didMoveSheetDuringPan = false
    didCancelTouchesForPan = false
    scrollViewOwnsLowerBoundary = false
  }

  /// Extra lead applied to the spring prediction when emitting follower
  /// positions, in frames (scaled by the display link's actual frame duration,
  /// so it means the same thing at 60Hz and 120Hz). `targetTimestamp` alone
  /// still trails the sheet slightly: the render server's sampling phase for
  /// the keyframe animation is undocumented, and the follower's transform
  /// commits one runloop turn after this callback. Tune by bisecting on a
  /// physical device: 0 = no bias, 1 = predict one extra frame ahead. Too high
  /// and followers visibly lead the sheet at the start of a settle.
  private static let followerPredictionBiasFrames: CFTimeInterval = 1.0

  @objc private func displayLinkFired(_ link: CADisplayLink) {
    // `targetTimestamp` is the predicted display time of the next frame.
    // Ideal synchronization with animations running on the render server
    // hasn't been achieved. Render server is a black box and it's hard to
    // find out why exactly. This approach however gives better results
    // than lagging one frame behind.
    let frameDuration = link.targetTimestamp - link.timestamp
    stepSpring(targetTime: link.targetTimestamp + Self.followerPredictionBiasFrames * frameDuration)
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
    if emitIndexChange {
      // A user-driven snap supersedes an index retained from props.
      pendingIndex = nil
    }
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
    updateScrollIndicators()

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
      activeScrollableNegotiationLevel = negotiationLevel(forVerticalVelocity: verticalDirection)

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
          activeScrollableNegotiationLevel == .handoff,
          isAtLargestDetent, verticalDirection < 0 || !activeScrollViewsAreAtStart()
        {
          panCoordinationMode = .scrollView
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
        activeScrollableNegotiationLevel == .handoff,
        !activeScrollViewStates.isEmpty,
        panCoordinationMode == .scrollView
      {
        if scrollViewOwnsLowerBoundary {
          // Once the sheet is fully collapsed, downward movement belongs to
          // native bounce/RefreshControl. On reversal, the sheet expands again
          // as soon as no scrollable is still pulled past its start: a quick
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
        sheetContainer.transform = CGAffineTransform(translationX: 0, y: newTy)
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
        noteReleasedScrollViewsAtStart()
        unlockActiveScrollViews()
      }

    case .ended:
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

    case .cancelled:
      isPanning = false
      setContentInteractionEnabled(true)
      let coordinatedScrollView = !activeScrollViewStates.isEmpty
      let sheetMoved = didMoveSheetDuringPan
      if sheetMoved, panCoordinationMode == .sheet {
        cancelActiveScrollViewPans()
      }
      unlockActiveScrollViews()
      let cancelVelocity = gesture.velocity(in: self).y
      if coordinatedScrollView, !sheetMoved {
        panStartingIndex = nil
        activeDragRange = nil
        activeDragDetentSpecs = nil
        finishScrollViewCoordination()
        return
      }
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
      finishScrollViewCoordination()
      snapToIndex(cancelIndex, velocity: cancelVelocity)

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

  private func scrollableAncestorChain(
    containing location: CGPoint
  ) -> [(scrollView: UIScrollView, inverted: Bool)] {
    scrollableAncestorChain(in: touchedViews(at: location))
  }

  private func scrollableAncestorChain(
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
  private func holdDeferralScrollViews(at location: CGPoint) -> [UIScrollView] {
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

  /// The scroll view's offset, clamped to where it can rest: a held start can
  /// leave it overscrolled, and pinning that offset would keep it there.
  private func restingOffset(for scrollView: UIScrollView) -> CGPoint {
    let minimumY = scrollStartOffset(for: scrollView, inverted: false).y
    let maximumY = scrollStartOffset(for: scrollView, inverted: true).y
    return CGPoint(
      x: scrollView.contentOffset.x,
      y: min(max(scrollView.contentOffset.y, minimumY), maximumY)
    )
  }

  /// Whether the scroll view is at or before its visual start.
  private func scrollViewIsAtStart(_ scrollView: UIScrollView, inverted: Bool) -> Bool {
    let edgeTolerance: CGFloat = 0.5
    let startY = scrollStartOffset(for: scrollView, inverted: inverted).y
    return inverted
      ? scrollView.contentOffset.y >= startY - edgeTolerance
      : scrollView.contentOffset.y <= startY + edgeTolerance
  }

  private func scrollViewIsAtStart(_ state: ActiveScrollViewState) -> Bool {
    guard let scrollView = state.scrollView else { return true }
    return scrollViewIsAtStart(scrollView, inverted: state.inverted)
  }

  private func activeScrollViewsAreAtStart() -> Bool {
    guard !activeScrollViewStates.isEmpty else { return false }
    return activeScrollViewStates.allSatisfy { scrollViewIsAtStart($0) }
  }

  /// Records which released scroll views are at or before their visual start,
  /// for the lock that takes the touch back.
  private func noteReleasedScrollViewsAtStart() {
    for state in activeScrollViewStates where scrollViewIsAtStart(state) {
      state.reachedStartWhileReleased = true
    }
  }

  /// Whether a vertical scrollable in the touched chain is overscrolled
  /// before its visual start, by a bounce or a refresh control pull.
  private func activeScrollViewsArePulledPastStart() -> Bool {
    let edgeTolerance: CGFloat = 0.5
    return activeScrollViewStates.contains { state in
      guard let scrollView = state.scrollView else { return false }
      let startY = scrollStartOffset(for: scrollView, inverted: state.inverted).y
      return state.inverted
        ? scrollView.contentOffset.y > startY + edgeTolerance
        : scrollView.contentOffset.y < startY - edgeTolerance
    }
  }

  private func negotiationLevel(forVerticalVelocity velocityY: CGFloat) -> ScrollableNegotiationLevel {
    let rawValue = velocityY < 0 ? scrollableExpandNegotiation : scrollableCollapseNegotiation
    return ScrollableNegotiationLevel(clamping: rawValue)
  }

  /// The pan's vertical direction, negative up: the one its hold decided when
  /// the start was held, else the velocity's.
  private func panVerticalDirection(_ gesture: UIPanGestureRecognizer) -> CGFloat {
    panGesture.holdDecidedDirection ? panGesture.decidedVerticalDirection : gesture.velocity(in: self).y
  }

  private var hasAnyScrollableNegotiation: Bool {
    ScrollableNegotiationLevel(clamping: scrollableExpandNegotiation) != .none
      || ScrollableNegotiationLevel(clamping: scrollableCollapseNegotiation) != .none
  }

  override public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
    guard gestureRecognizer === panGesture else { return true }

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

    let level = negotiationLevel(forVerticalVelocity: verticalDirection)
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

  private var resolvedMaxDetentHeight: CGFloat {
    // The detent cap is computed natively: the sheet's own height minus the
    // part of the window's top (status bar / notch) inset that actually
    // overlaps this view. Measured from real window geometry in every mode —
    // inline, portal, and overlay — so no JS-estimated dimensions are
    // involved. Before the view is in a window, fall back to full height.
    guard !extendUnderStatusBar, let window else {
      return bounds.height
    }
    let originYInWindow = convert(CGPoint.zero, to: window).y
    let topOverlap = max(0, window.safeAreaInsets.top - originYInWindow)
    return min(max(0, bounds.height - topOverlap), bounds.height)
  }

  /// The height of the content region: the Yoga box the sheet's in-flow
  /// children (the `flex: 1` content wrapper) are laid out in. It is the
  /// tallest height the sheet can reach — the tallest detent's upper bound,
  /// clamped to the cap — rather than the cap itself, so a scrollable under a
  /// fixed or percentage tallest detent is laid out against the height it can
  /// actually show instead of leaving its last rows below the screen edge.
  /// A trailing `content` detent is bounded by the cap alone:
  /// its resolved height is measured from this very region, so bounding
  /// the region by it would be circular.
  private var contentRegionHeight: CGFloat {
    let cap = resolvedMaxDetentHeight
    guard let tallest = rawDetentSpecs.last else { return cap }
    let bound: CGFloat
    switch tallest.kind {
    case .points:
      bound = tallest.value
    case .percentage:
      bound = cap * tallest.value
    case .content:
      bound = cap
    }
    return min(max(0, bound), cap)
  }

  /// The natively measured inset of the content region: the gap between the
  /// sheet's height and `contentRegionHeight`. Reported into the shadow tree,
  /// where it becomes Yoga bottom padding on the sheet node.
  public var contentRegionInset: CGFloat {
    max(0, bounds.height - contentRegionHeight)
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
      case .percentage:
        height = maxHeight * spec.value
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
    for spec in rawDetentSpecs[(index + 1)...] {
      switch spec.kind {
      case .points:
        return min(max(0, spec.value), maxHeight)
      case .percentage:
        return min(max(0, maxHeight * spec.value), maxHeight)
      case .content:
        continue
      }
    }
    return maxHeight
  }

  private func refreshDetentsFromLayout() {
    resolveDetentsFromLayout()
    replayPendingIndexIfResolved()
  }

  private func resolveDetentsFromLayout() {
    // While detached from a window (e.g. mid-commit, when Fabric reparents the
    // host as ancestor view flattening changes), the native detent cap is not
    // computable — its full-height fallback would register as a cap change and
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
    // Also fall through when only the cap changed: the specs store heights,
    // but translations derive from the cap, so they must be re-anchored even
    // when the resolved detents are unchanged.
    guard resolvedDetents != detentSpecs || sheetContainerHeight != lastAppliedMaxDetentHeight
    else {
      updateScrim()
      return
    }

    // The re-anchor math below preserves the on-screen sheet height across the
    // geometry change, so it must relate the current translation to the cap it
    // was computed against — not the freshly resolved one.
    let previousMaxHeight =
      lastAppliedMaxDetentHeight.isFinite ? lastAppliedMaxDetentHeight : sheetContainerHeight
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
    guard isContentRegionChangePending else { return }
    refreshDetentsFromLayout()
  }

  /// Only relevant insert/remove parents invalidate descendant discovery.
  /// Position-only Fabric updates keep using the cached scroll views.
  public func contentHierarchyDidChange(_ parentTags: [NSNumber], sheetTag: Int) {
    guard parentTags.contains(where: {
      $0.intValue == sheetTag || scrollIndicatorParentTags.contains($0.intValue)
    }) else { return }
    refreshScrollIndicatorViews()
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
    restoreScrollIndicators()
    stopObservingContentHeightMarker()
    displayLink?.invalidate()
    sheetContainer.layer.removeAnimation(forKey: Self.springAnimationKey)
  }

  /// The content wrapper: the surface is its sibling, so skip it and the
  /// wrapper is whatever else the sheet mounts.
  private func findContentView() -> UIView? {
    sheetContainer.subviews.first(where: { $0 !== surfaceView })
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

  private func isInvalidContentDetentTarget(_ index: Int) -> Bool {
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

extension BottomSheetHostingView: CAAnimationDelegate {
  public func animationDidStop(_: CAAnimation, finished: Bool) {
    guard finished, activeSpring != nil else { return }
    finishSpring()
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
      let canHold = !holdDeferralScrollViews(at: location).isEmpty
      let canBeginUp = canHold && canBeginPan(inDirection: -1)
      let canBeginDown = canHold && canBeginPan(inDirection: 1)
      panGesture.prepareHold(
        for: touch,
        usable: .init(up: canBeginUp, down: canBeginDown),
        handoff: .init(
          up: canBeginUp && negotiationLevel(forVerticalVelocity: -1) == .handoff,
          down: canBeginDown && negotiationLevel(forVerticalVelocity: 1) == .handoff
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
    updateScrollIndicators(forPosition: position)
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
    if position <= first.height {
      return first.value
    }
    if position >= last.height {
      return last.value
    }

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
