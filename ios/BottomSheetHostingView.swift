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

/// The space kept around the card at a detent.
private struct DetentInsets: Equatable {
  /// Nil for the status bar's overlap with the host, which can change.
  var top: CGFloat?
  var left: CGFloat = 0
  var right: CGFloat = 0
  var bottom: CGFloat = 0
}

private struct DetentSpec: Equatable {
  /// How far the card's top rests above the host's bottom: the card's height
  /// plus its bottom inset. Zero when closed, whatever the inset.
  let position: CGFloat
  let programmatic: Bool
  let insets: DetentInsets
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
  let insets: DetentInsets
  /// The widest open card, which the content is laid out against. Flagged in JS,
  /// which lays the content out.
  let widestCard: Bool
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
/// threshold, and the sheet rejects a sideways start there so horizontal
/// recognizers under the touch keep it. A vertical scrollable's own pan is
/// not decided by sideways travel: it stays possible and begins once vertical
/// travel passes its slop, so a start the sheet rejected would later scroll
/// content the sheet owns at this detent. Inside a vertical scrollable with no
/// horizontal recognizer under the touch, a sideways start is therefore held —
/// its moves are withheld from UIKit's pan machinery, leaving the sheet's pan
/// possible — until vertical travel passes the slop or the scrollable's pan
/// begins first. While held, the sheet yields to pans above it the way the
/// scrollable would: see `gestureRecognizer(_:shouldBeRequiredToFailBy:)`.
private final class SheetPanGestureRecognizer: UIPanGestureRecognizer {
  private enum Hold {
    /// No hold: the usual direction check applies.
    case none
    /// Moves are withheld until vertical travel passes the slop.
    case active
    /// The hold decided the direction, so the usual direction check does not apply.
    case released
  }

  /// Vertical travel that releases a held start, matching the threshold
  /// UIKit's pan recognizers apply to total travel.
  private static let verticalSlop: CGFloat = 10

  /// The vertical scrollables a sideways start defers to. Empty disables the hold.
  private var deferredScrollViews: [UIScrollView] = []
  private var hold = Hold.none
  private var initialLocation: CGPoint?

  var isHoldingSidewaysStart: Bool {
    hold == .active
  }

  var tookSidewaysStart: Bool {
    hold == .released
  }

  /// Whether a deferred scrollable's pan has recognized the touch.
  private var deferredScrollableAccepted: Bool {
    deferredScrollViews.contains { scrollView in
      let state = scrollView.panGestureRecognizer.state
      return state == .began || state == .changed
    }
  }

  /// Arms the hold for a first touch, deferring to `scrollViews`. Called by the
  /// host from `gestureRecognizer(_:shouldReceive:)`, before UIKit delivers the
  /// touch, so `isHoldingSidewaysStart` is already settled whenever UIKit asks
  /// for failure requirements.
  func prepareHold(for touch: UITouch, deferringTo scrollViews: [UIScrollView]) {
    deferredScrollViews = scrollViews
    initialLocation = touch.location(in: view)
    hold = scrollViews.isEmpty ? .none : .active
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
    if hold == .active, numberOfTouches + touches.count > 1 {
      // Only a single finger is held.
      hold = .none
    }
    super.touchesBegan(touches, with: event)
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
    if hold == .active {
      if numberOfTouches != 1 {
        hold = .none
      } else if let start = initialLocation, let touch = touches.first {
        let dy = touch.location(in: view).y - start.y
        guard abs(dy) >= Self.verticalSlop || deferredScrollableAccepted else { return }
        hold = .released
      }
    }
    super.touchesMoved(touches, with: event)
  }

  override func reset() {
    deferredScrollViews = []
    hold = .none
    initialLocation = nil
    super.reset()
  }
}

private final class ActiveScrollViewState {
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

/// The scrim control, exposed to VoiceOver as a dismiss button while a
/// dismissible modal sheet is open. VoiceOver's default activation simulates
/// a tap at the activation point; attempting dismissal directly keeps
/// activation reliable even when the sheet overlaps that point mid-settle.
private final class BottomSheetScrimControl: UIControl {
  // Keep the native hit area outside the sheet while drawing the scrim behind
  // the entire host, including the sheet's transparent rounded corners.
  private let backgroundView = UIView()
  var onAccessibilityActivate: (() -> Bool)?
  var containsPoint: ((CGPoint) -> Bool)?

  override var backgroundColor: UIColor? {
    get { backgroundView.backgroundColor }
    set { backgroundView.backgroundColor = newValue }
  }

  override init(frame: CGRect) {
    super.init(frame: frame)
    clipsToBounds = false
    backgroundView.isUserInteractionEnabled = false
    backgroundView.accessibilityElementsHidden = true
    addSubview(backgroundView)
  }

  @available(*, unavailable)
  required init?(coder _: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  func setBackgroundSize(_ size: CGSize) {
    backgroundView.frame = CGRect(origin: .zero, size: size)
  }

  override func point(inside point: CGPoint, with _: UIEvent?) -> Bool {
    containsPoint?(point) ?? bounds.contains(point)
  }

  override func accessibilityActivate() -> Bool {
    onAccessibilityActivate?() ?? false
  }
}

@objc(BottomSheetPresentationEscapeDecision)
final class PresentationEscapeDecision: NSObject {
  fileprivate enum Route {
    case passThrough
    case attemptLocal
    case consume
  }

  fileprivate let route: Route

  private init(route: Route) {
    self.route = route
  }

  @objc static func passThrough() -> PresentationEscapeDecision {
    PresentationEscapeDecision(route: .passThrough)
  }

  @objc static func attemptLocal() -> PresentationEscapeDecision {
    PresentationEscapeDecision(route: .attemptLocal)
  }

  @objc static func consume() -> PresentationEscapeDecision {
    PresentationEscapeDecision(route: .consume)
  }
}

/// Hosts the surface and content and clips them to the card shape. The host
/// gates hit testing against the presented card, so this view accepts every
/// point: mid-settle its model bounds already hold the target geometry and
/// would reject touches on the card as it is drawn.
private final class CardClipView: UIView {
  override func point(inside _: CGPoint, with _: UIEvent?) -> Bool {
    true
  }
}

/// An outset shadow of the card, as `boxShadow` or the `shadow*` props describe it.
private struct CardShadow: Equatable {
  let offset: CGSize
  let blurRadius: CGFloat
  let spreadDistance: CGFloat
  /// May be dynamic (`DynamicColorIOS`, `PlatformColor`); resolved against
  /// the host's trait collection when applied.
  let color: UIColor
  let opacity: CGFloat
  /// `boxShadow` leaves the card's interior unpainted; the `shadow*` props
  /// paint under it, as a layer's own shadow does.
  let excludesInterior: Bool
}

/// The continuous ("squircle") corner `CALayer` draws for
/// `cornerCurve = .continuous`, as three cubic Béziers per corner. The
/// coefficients scale with the radius and reproduce the layer's rendering to
/// within antialiasing.
private enum ContinuousCorner {
  /// How far along each edge the corner curve begins, in radii.
  static let extent: CGFloat = 1.528665

  /// Adds the top-right-shaped corner, mapped onto a corner by `transform`,
  /// starting with the line along the edge before it.
  static func add(to path: CGMutablePath, radius r: CGFloat, transform: CGAffineTransform) {
    func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
      CGPoint(x: x * r, y: y * r).applying(transform)
    }
    path.addLine(to: point(-extent, 0))
    path.addCurve(
      to: point(-0.631494, 0.074911),
      control1: point(-1.088493, 0),
      control2: point(-0.868407, 0)
    )
    path.addCurve(
      to: point(-0.074911, 0.631494),
      control1: point(-0.372824, 0.169060),
      control2: point(-0.169060, 0.372824)
    )
    path.addCurve(
      to: point(0, extent),
      control1: point(0, 0.868407),
      control2: point(0, 1.088493)
    )
  }
}

@objcMembers
public final class BottomSheetHostingView: UIView {
  public weak var eventDelegate: BottomSheetHostingViewDelegate?
  public var modal: Bool = false {
    didSet {
      updateScrim()
      updatePresentationActiveForCurrentState()
    }
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

  /// Physical corner radii of the card: top-left, top-right, bottom-right,
  /// bottom-left. With all zero and no gaps around the card, the sheet stays
  /// unclipped, as before cards existed, so content drawn outside the sheet
  /// (e.g., a surface's own shadow) stays visible.
  private var cornerRadii: [CGFloat] = [0, 0, 0, 0]
  private var cornerCurve: CALayerCornerCurve = .circular

  public func setSurfaceCornerRadii(_ values: [NSNumber]) {
    var radii = values.prefix(4).map { max(0, CGFloat(truncating: $0)) }
    radii += Array(repeating: 0, count: 4 - radii.count)
    guard radii != cornerRadii else { return }
    cornerRadii = radii
    updateCardShape()
  }

  /// Outset shadows cast by the card, each one drawn by its own layer below
  /// the clipping container so the clip never cuts it.
  private var cardShadows: [CardShadow] = []

  public func setSurfaceShadows(_ values: [NSDictionary]) {
    let shadows = values.map { value in
      let number = { (key: String) in CGFloat(truncating: value[key] as? NSNumber ?? 0) }
      return CardShadow(
        offset: CGSize(width: number("offsetX"), height: number("offsetY")),
        blurRadius: max(0, number("blurRadius")),
        spreadDistance: number("spreadDistance"),
        color: value["color"] as? UIColor ?? .black,
        opacity: number("opacity"),
        excludesInterior: (value["excludesInterior"] as? NSNumber)?.boolValue ?? false
      )
    }
    guard shadows != cardShadows else { return }
    cardShadows = shadows
    updateCardShadows()
  }

  public func setSurfaceCornerCurve(_ value: String) {
    let curve: CALayerCornerCurve = value == "continuous" ? .continuous : .circular
    guard curve != cornerCurve else { return }
    cornerCurve = curve
    updateCardShape()
  }

  /// The height the container geometry and translations are currently anchored
  /// to; used to detect height-only geometry changes and to re-anchor against
  /// the value the current translation was computed with.
  private var lastAppliedMaxDetentHeight: CGFloat = .nan

  public var scrollableExpandNegotiation: Int = ScrollableNegotiationLevel.handoff.rawValue
  public var scrollableCollapseNegotiation: Int = ScrollableNegotiationLevel.initial.rawValue

  private var rawDetentSpecs: [RawDetentSpec] = []
  /// Whether any detent keeps a gap at the card's sides or bottom, so the card
  /// is narrower or shorter than the surface and has to clip it. A top inset
  /// only lowers the detent's height, which needs no clip.
  private var hasCardGaps = false
  /// The sum of the side insets of the widest card, which the content is laid
  /// out against.
  private var widestCardSideGaps: CGFloat = 0
  private var detentSpecs: [DetentSpec] = [] {
    didSet {
      setNeedsLayout()
      updateScrim()
    }
  }

  private var targetIndex: Int = 0
  @objc(isPresentationActive)
  private(set) var presentationActive = false {
    didSet {
      updateAccessibilityPresentation()
      guard presentationActive != oldValue else { return }
      presentationActiveDidChange?(presentationActive)
    }
  }

  public var animateIn: Bool = true
  public var animateContentHeight: Bool = true

  public let sheetContainer = UIView()
  private let clipContainer = CardClipView()
  /// Casts the card's shadows. A sibling below the clipping container, so the
  /// clip never cuts the shadows.
  private let shadowView = UIView()
  private var shadowLayers: [CALayer] = []
  /// Masks the card when its corners differ in radius, which the layer's own
  /// corner radius cannot express. Nil while the layer's corners suffice.
  private var cardMask: CAShapeLayer?
  private let scrimView = BottomSheetScrimControl()
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
  private var initialTouchLocation: CGPoint?
  private var activeDragRange: (minTy: CGFloat, maxTy: CGFloat)?
  private var activeDragDetentSpecs: [DetentSpec]?
  private var activeScrollViewStates: [ActiveScrollViewState] = []
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
  private var dismissAccessibilityFrame: CGRect?
  private weak var dismissAccessibilityGeometryWindow: UIWindow?
  private var pendingStableDismissAccessibilityFrame: CGRect?
  private weak var surfaceView: UIView?
  var presentationActiveDidChange: ((Bool) -> Void)?
  var defersAccessibilityPresentation = false {
    didSet { updateAccessibilityPresentation() }
  }

  private var accessibilityPresentationCommitted = false
  var isAccessibilityTransitionRunning: Bool {
    activeSpring != nil || isPanning
  }

  func commitAccessibilityPresentation() {
    guard
      defersAccessibilityPresentation,
      presentationActive,
      !isAccessibilityTransitionRunning,
      !accessibilityPresentationCommitted
    else { return }
    accessibilityPresentationCommitted = true
    accessibilityElementsHidden = false
  }

  private func updateAccessibilityPresentation() {
    if !defersAccessibilityPresentation || !presentationActive {
      accessibilityPresentationCommitted = false
    }
    accessibilityElementsHidden = defersAccessibilityPresentation && !accessibilityPresentationCommitted
  }

  var presentationEscapePolicy: (() -> PresentationEscapeDecision)?
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
  private static let cardBoundsAnimationKey = "bottomSheetCardBounds"
  private static let cardPositionAnimationKey = "bottomSheetCardPosition"
  private static let cardMaskAnimationKey = "bottomSheetCardMask"
  private static let cardShadowAnimationKey = "bottomSheetCardShadow"
  private static let cardShadowMaskAnimationKey = "bottomSheetCardShadowMask"
  private static let cardContentAnimationKey = "bottomSheetCardContent"
  /// The settle animation the card is following, kept so the content's keyframes
  /// can be rebuilt when a layout moves the content mid-settle.
  private var cardSettleAnimation: CAKeyframeAnimation?
  /// The centers and sizes that the running content keyframes were built from.
  private var contentAnimationLayouts: [CGRect] = []

  override public init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    clipsToBounds = false

    scrimView.backgroundColor = scrimColor
    scrimView.alpha = 0
    scrimView.isHidden = true
    scrimView.addTarget(self, action: #selector(handleScrimPress), for: .touchUpInside)
    scrimView.onAccessibilityActivate = { [weak self] in
      self?.attemptScrimDismissal() ?? false
    }
    scrimView.containsPoint = { [weak self] point in
      guard let self, window != nil else { return false }
      let hostPoint = convert(point, from: scrimView)
      return bounds.contains(hostPoint) && !presentedCardContains(hostPoint)
    }
    scrimView.isAccessibilityElement = false
    scrimView.accessibilityTraits = .button
    scrimView.accessibilityLabel = "Dismiss"
    addSubview(scrimView)

    sheetContainer.backgroundColor = .clear
    sheetContainer.clipsToBounds = false
    addSubview(sheetContainer)

    // Anchored at its origin, so a card height change animates the bounds alone
    // and the origin stays put. Its bounds origin equals its frame origin, so
    // children keep sheet-container coordinates whatever the card geometry.
    clipContainer.backgroundColor = .clear
    clipContainer.layer.anchorPoint = .zero
    clipContainer.autoresizesSubviews = false
    shadowView.backgroundColor = .clear
    shadowView.isUserInteractionEnabled = false
    sheetContainer.addSubview(shadowView)
    sheetContainer.addSubview(clipContainer)

    panGesture = SheetPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
    panGesture.delegate = self
    panGesture.cancelsTouchesInView = true
    // Let child controls show immediate press feedback. If the interaction
    // becomes a sheet drag, handlePan(.began) cancels in-flight RN touches.
    panGesture.delaysTouchesBegan = false
    panGesture.delaysTouchesEnded = false
    sheetContainer.addGestureRecognizer(panGesture)

    if #available(iOS 17.0, *) {
      registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (self: Self, _) in
        self.updateCardShadowColors()
      }
    }
  }

  /// Deprecated itself so overriding the deprecated callback does not warn;
  /// iOS 17 and later observe the style with `registerForTraitChanges`.
  @available(iOS, deprecated: 17.0)
  override public func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
    super.traitCollectionDidChange(previousTraitCollection)
    if #available(iOS 17.0, *) {
      return
    }
    if traitCollection.hasDifferentColorAppearance(comparedTo: previousTraitCollection) {
      updateCardShadowColors()
    }
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
    // The default top inset depends on the window's. Resolved in the layout pass
    // rather than here: UIKit calls this from inside `setFrame:`, and the base
    // component view briefly shrinks this view to its padding-inset frame (see
    // `updateLayoutMetrics:`), which would read as a much smaller host.
    setNeedsLayout()
  }

  override public func didMoveToWindow() {
    super.didMoveToWindow()
    surfaceTouchHandler = nil
    dismissAccessibilityGeometryWindow = nil
    invalidateDismissAccessibilityGeometry()
    updateInteractionState()
    guard window != nil else { return }
    // The default top inset becomes computable once a window exists.
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
  }

  override public func layoutSubviews() {
    super.layoutSubviews()
    guard bounds.width > 0, bounds.height > 0 else {
      dismissAccessibilityGeometryWindow = nil
      invalidateDismissAccessibilityGeometry()
      updateInteractionState()
      return
    }
    // See refreshDetentsFromLayout: without a window the status bar's overlap falls back
    // to zero, which must not be applied to the container geometry.
    if hasLaidOut, window == nil {
      return
    }

    let commitsStableLayout = hasLaidOut && activeSpring == nil && !isPanning
    if commitsStableLayout {
      beginStableDismissGeometryTransition()
    }

    scrimView.setBackgroundSize(bounds.size)
    refreshDetentsFromLayout(commitsStableGeometry: false)
    let maxHeight = sheetContainerHeight
    lastAppliedMaxDetentHeight = maxHeight
    sheetContainer.bounds = CGRect(x: 0, y: 0, width: bounds.width, height: maxHeight)
    sheetContainer.center = CGPoint(x: bounds.width / 2, y: bounds.height - maxHeight / 2)

    // The surface fills the full container so it always covers the visible sheet
    // (the container is translated to the current sheet position), regardless of
    // how short the content becomes. Sized from the top via frame — never via
    // anchorPoint.
    shadowView.frame = sheetContainer.bounds
    // From the model translation: mid-settle the card animation drives the
    // presented geometry and the model already holds the target.
    applyCardGeometry(forPosition: maxHeight - sheetContainer.transform.ty)

    // Report fresh native geometry so the component layer can push the content
    // wrapper's target size (and the overlay frame) into the shadow tree.
    eventDelegate?.bottomSheetHostingViewDidLayout(self)

    // didMoveToWindow keeps Dismiss unavailable until this attachment has
    // completed a layout using its destination bounds and coordinate space.
    dismissAccessibilityGeometryWindow = window

    if !hasLaidOut && !detentSpecs.isEmpty {
      let indexToApply = pendingIndex ?? targetIndex
      let clampedIndex = max(0, min(detentSpecs.count - 1, indexToApply))

      if animateIn, isInvalidContentDetentTarget(clampedIndex) {
        targetIndex = clampedIndex
        pendingIndex = clampedIndex
        let closedTy = sheetContainerHeight
        setSheetTranslation(closedTy)
        emitPosition()
        return
      }

      hasLaidOut = true
      pendingIndex = nil
      targetIndex = clampedIndex

      if animateIn {
        let closedTy = sheetContainerHeight
        setSheetTranslation(closedTy)
        emitPosition()
        snapToIndex(targetIndex, velocity: 0, emitIndexChange: false, emitSettle: true)
      } else {
        setSheetTranslation(translationY(for: targetIndex))
        emitPosition()
        commitStableDismissGeometryIfNeeded()
      }
      return
    }

    if activeSpring != nil || isPanning {
      return
    }
    setSheetTranslation(translationY(for: targetIndex))
    updateScrim()
    if commitsStableLayout {
      commitStableDismissGeometryIfNeeded()
    }
  }

  /// Whether `point` (in host coordinates) falls on the card as it is drawn.
  /// Mid-settle the translation comes from the spring instead of the
  /// presentation layer, which lags one commit behind right after a snap
  /// starts. The container's transform is translation-only.
  private func presentedCardContains(_ point: CGPoint) -> Bool {
    let ty = currentTranslationY
    let size = sheetContainer.bounds.size
    let center = sheetContainer.center
    let card = cardRect(forPosition: sheetContainerHeight - ty).offsetBy(
      dx: center.x - size.width / 2,
      dy: center.y - size.height / 2 + ty
    )
    guard card.contains(point) else { return false }
    guard isCardShaped else { return true }
    return cardPath(in: card).contains(point)
  }

  override public func point(inside point: CGPoint, with _: UIEvent?) -> Bool {
    if presentedCardContains(point) {
      return true
    }

    return isScrimVisible && bounds.contains(point)
  }

  override public func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
    guard self.point(inside: point, with: event) else { return nil }

    if isScrimVisible, !presentedCardContains(point) {
      let scrimPoint = convert(point, to: scrimView)
      return scrimView.hitTest(scrimPoint, with: event)
    }

    let containerPoint = convert(point, to: sheetContainer)
    guard sheetContainer.bounds.contains(containerPoint) else { return nil }
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
      let widestCard = (dict["widestCard"] as? Bool) ?? (dict["widestCard"] as? NSNumber)?.boolValue ?? false
      let inset = { (key: String) in max(0, CGFloat(truncating: dict[key] as? NSNumber ?? 0)) }
      let usesStatusBarTopInset = (dict["usesStatusBarTopInset"] as? Bool)
        ?? (dict["usesStatusBarTopInset"] as? NSNumber)?.boolValue ?? false
      return RawDetentSpec(
        value: CGFloat(value),
        kind: kind,
        programmatic: programmatic,
        insets: DetentInsets(
          top: usesStatusBarTopInset ? nil : inset("insetTop"),
          left: inset("insetLeft"),
          right: inset("insetRight"),
          bottom: inset("insetBottom")
        ),
        widestCard: widestCard
      )
    }
    let previousWidestCardSideGaps = widestCardSideGaps
    widestCardSideGaps = rawDetentSpecs
      .first(where: \.widestCard)
      .map { $0.insets.left + $0.insets.right } ?? 0
    let hasGaps = rawDetentSpecs.contains {
      $0.insets.left > 0 || $0.insets.right > 0 || $0.insets.bottom > 0
    }
    if hasGaps != hasCardGaps {
      hasCardGaps = hasGaps
      updateCardShape()
    }
    // The tallest detent bounds the content region. The content stays laid out
    // against the previous region until Fabric commits the new inset; hold its
    // measurement until then. Decided from the detents alone so it also holds
    // while detached, when the status bar's overlap is not computable.
    if hasLaidOut, rawDetentSpecs.last != previousTallestDetent || widestCardSideGaps != previousWidestCardSideGaps {
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
    clipContainer.insertSubview(childView, at: index)
    refreshContentHeightMarker()
    setNeedsLayout()
  }

  public func unmountChildComponentView(_ childView: UIView) {
    // Fabric recycles the view, which must not keep the card's scale.
    childView.layer.removeAnimation(forKey: Self.cardContentAnimationKey)
    childView.layer.transform = CATransform3DIdentity
    childView.removeFromSuperview()
    refreshContentHeightMarker()
    setNeedsLayout()
  }

  public func mountSurfaceComponentView(_ childView: UIView, atIndex index: Int) {
    surfaceView = childView
    clipContainer.insertSubview(childView, at: index)
    refreshContentHeightMarker()
    setNeedsLayout()
  }

  public func unmountSurfaceComponentView(_ childView: UIView) {
    if surfaceView === childView {
      surfaceView = nil
    }
    // Fabric recycles the view, which must not keep the card's scale.
    childView.layer.removeAnimation(forKey: Self.cardContentAnimationKey)
    childView.layer.transform = CATransform3DIdentity
    childView.removeFromSuperview()
    refreshContentHeightMarker()
    setNeedsLayout()
  }

  public func resetSheetState() {
    activeSpring = nil
    activeSpringEmitsSettle = false
    lastAppliedMaxDetentHeight = .nan
    stopDisplayLink()
    removeSettleAnimations()
    rawDetentSpecs = []
    detentSpecs = []
    targetIndex = 0
    presentationActive = false
    pendingIndex = nil
    pendingSnapRequest = nil
    discardPendingStableDismissGeometry()
    dismissAccessibilityGeometryWindow = nil
    hasLaidOut = false
    isPanning = false
    panStartingIndex = nil
    activeDragRange = nil
    activeDragDetentSpecs = nil
    finishScrollViewCoordination()
    setContentInteractionEnabled(true)
    stopObservingContentHeightMarker()
    isContentRegionChangePending = false
    committedContentRegionInset = .nan
    appliedContentRegionInset = .nan
    surfaceView = nil
    sheetContainer.transform = .identity
    scrimView.alpha = 0
    scrimView.isHidden = true
    invalidateDismissAccessibilityGeometry()
    for subview in clipContainer.subviews {
      subview.layer.removeAnimation(forKey: Self.cardContentAnimationKey)
      subview.layer.transform = CATransform3DIdentity
      subview.removeFromSuperview()
    }
  }

  private func detent(at index: Int) -> DetentSpec {
    guard detentSpecs.indices.contains(index) else {
      return DetentSpec(position: 0, programmatic: false, insets: DetentInsets())
    }
    return detentSpecs[index]
  }

  private func translationY(for index: Int) -> CGFloat {
    sheetContainerHeight - detent(at: index).position
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
      detentSpecs[$0].position < detentSpecs[$1].position
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
    sheetContainerHeight - (specs.indices.contains(index) ? specs[index].position : 0)
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
      specs[$0].position < specs[$1].position
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
    detentSpecs.firstIndex(where: { $0.position == 0 })
  }

  private var accessibleDismissalIndex: Int? {
    guard
      hasLaidOut,
      isScrimVisible,
      let closedIndex,
      !detentSpecs[closedIndex].programmatic,
      detent(at: targetIndex).position > 0,
      activeSpring == nil || currentSheetHeight > 0.5
    else {
      return nil
    }
    return closedIndex
  }

  private var firstOpenDetentPosition: CGFloat {
    detentSpecs.first(where: { $0.position > 0 })?.position ?? 0
  }

  private var currentSheetHeight: CGFloat {
    let maxHeight = sheetContainerHeight
    let ty = currentTranslationY
    return maxHeight - ty
  }

  public var currentContentOffsetY: CGFloat {
    // The content's in-host displacement from its Yoga position: the sheet's
    // translation. The content-region inset shrinks
    // the content via Yoga BOTTOM padding, keeping the Yoga origin at zero, so
    // the full displacement is carried here.
    return currentTranslationY
  }

  /// How much the card scales the surface and content.
  public var currentContentScale: CGFloat {
    guard hasCardGaps else { return 1 }
    return contentScale(forCardWidth: cardRect(forPosition: currentSheetHeight).width)
  }

  /// The sheet coordinate space's horizontal displacement, which the scale applies
  /// to as well: it puts the widest card's left edge, scaled, on the card's.
  public var currentContentOffsetX: CGFloat {
    guard hasCardGaps else { return 0 }
    let card = cardRect(forPosition: currentSheetHeight)
    let widestLeft = findContentView().map { $0.center.x - $0.bounds.width / 2 } ?? 0
    return card.minX - contentScale(forCardWidth: card.width) * widestLeft
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
    updatePresentationActive(forPosition: position)
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

    for subview in clipContainer.subviews {
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

  private func lockActiveScrollViews(pinToStart: Bool = false) {
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
      scrollView.showsVerticalScrollIndicator = state.showsVerticalScrollIndicator
    }
    activeScrollViewsLocked = false
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
    attemptAccessibleDismissal()
  }

  /// Scrim taps and Dismiss activation require a positive outside-sheet area.
  @discardableResult
  private func attemptScrimDismissal() -> Bool {
    guard dismissAccessibilityFrame != nil else { return false }
    return attemptAccessibleDismissal()
  }

  /// Escape can directly dismiss an eligible sheet even when it fills the host
  /// and leaves no outside-sheet area for the spatial Dismiss control.
  @discardableResult
  private func attemptAccessibleDismissal() -> Bool {
    guard let closedIndex = accessibleDismissalIndex else {
      return false
    }

    snapToIndex(closedIndex, velocity: 0)
    return true
  }

  /// VoiceOver's escape gesture (two-finger Z scrub) follows the current
  /// per-window presentation route. Only Top may attempt local dismissal;
  /// lower presentations consume defensively, while no Top passes through.
  override public func accessibilityPerformEscape() -> Bool {
    guard let decision = presentationEscapePolicy?() else {
      return false
    }

    switch decision.route {
    case .passThrough:
      return false
    case .attemptLocal:
      attemptAccessibleDismissal()
      return true
    case .consume:
      return true
    }
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

    let currentTy: CGFloat
    if activeSpring != nil {
      currentTy = cancelActiveSpring()
    } else {
      currentTy = sheetContainer.transform.ty
    }
    updateDismissAccessibilityGeometry(forPosition: sheetContainerHeight - currentTy)
    beginStableDismissGeometryTransition()

    targetIndex = index
    updateInteractionState()
    if !preserveScrimPin {
      scrimPinnedFull = false
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

    setSheetTranslation(targetTy)
    sheetContainer.layer.add(animation, forKey: Self.springAnimationKey)
    addCardAnimations(matching: animation)
    activeSpring = spring

    // Publish ownership after motion is installed, so accessibility retains the
    // previous interface instead of projecting the target during this transition.
    if detent(at: index).position > 0.5 {
      updatePresentationActive(forPosition: detent(at: index).position)
    }
    presentationActiveDidChange?(presentationActive)

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
    removeSettleAnimations()

    setSheetTranslation(targetTy)
    emitPosition()
    if detent(at: index).position <= 0.5 {
      presentationActive = false
    }
    scrimPinnedFull = false
    setContentInteractionEnabled(true)
    updateInteractionState()
    commitStableDismissGeometryIfNeeded()
    // Opening/open-to-open can settle without changing neutral Active membership.
    presentationActiveDidChange?(presentationActive)
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
    removeSettleAnimations()
    setSheetTranslation(visualTy)
    discardPendingStableDismissGeometry()
    return visualTy
  }

  @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
    let maxHeight = sheetContainerHeight

    switch gesture.state {
    case .began:
      if activeSpring != nil {
        cancelActiveSpring()
      }
      updateDismissAccessibilityGeometry(forPosition: currentSheetHeight)
      beginStableDismissGeometryTransition(replacingPending: true)
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
        discardPendingStableDismissGeometry()
        presentationActiveDidChange?(presentationActive)
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
        discardPendingStableDismissGeometry()
        presentationActiveDidChange?(presentationActive)
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
      discardPendingStableDismissGeometry()
      presentationActiveDidChange?(presentationActive)

    default:
      break
    }
  }

  private func bestSnapIndex(
    for position: CGFloat,
    velocity: CGFloat,
    including index: Int? = nil
  ) -> Int {
    let candidates = snapCandidateIndices(including: index)
    guard !candidates.isEmpty else { return targetIndex }

    let flickThreshold: CGFloat = 600

    if velocity < -flickThreshold {
      return candidates.first(where: { detentSpecs[$0].position > position })
        ?? candidates.last ?? targetIndex
    }
    if velocity > flickThreshold {
      return candidates.last(where: { detentSpecs[$0].position < position })
        ?? candidates.first ?? targetIndex
    }

    return candidates.min(by: {
      abs(detentSpecs[$0].position - position) < abs(detentSpecs[$1].position - position)
    }) ?? targetIndex
  }

  private func snapshotBestSnapIndex(
    for position: CGFloat,
    velocity: CGFloat,
    including index: Int?,
    in specs: [DetentSpec]
  ) -> Int {
    let candidates = snapshotCandidateIndices(including: index, in: specs)
    guard !candidates.isEmpty else { return targetIndex }

    let flickThreshold: CGFloat = 600

    if velocity < -flickThreshold {
      return candidates.first(where: { specs[$0].position > position })
        ?? candidates.last ?? targetIndex
    }
    if velocity > flickThreshold {
      return candidates.last(where: { specs[$0].position < position })
        ?? candidates.first ?? targetIndex
    }

    return candidates.min(by: {
      abs(specs[$0].position - position) < abs(specs[$1].position - position)
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

  /// The vertical scrollables a sideways start under the touch defers to. A
  /// horizontal recognizer on the way to the sheet container — the pan of a
  /// scroll view that can scroll sideways, or any other pan — keeps sideways
  /// movement instead, so nothing is deferred and the sheet yields to it as
  /// before.
  private func sidewaysDeferralScrollViews(at location: CGPoint) -> [UIScrollView] {
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

  private func activeScrollViewsAreAtStart(requireExactOffset: Bool = false) -> Bool {
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

  private func negotiationLevel(forVerticalVelocity velocityY: CGFloat) -> ScrollableNegotiationLevel {
    let rawValue = velocityY < 0 ? scrollableExpandNegotiation : scrollableCollapseNegotiation
    return ScrollableNegotiationLevel(clamping: rawValue)
  }

  private var hasAnyScrollableNegotiation: Bool {
    ScrollableNegotiationLevel(clamping: scrollableExpandNegotiation) != .none
      || ScrollableNegotiationLevel(clamping: scrollableCollapseNegotiation) != .none
  }

  private func scrollableChainIsAtStart(
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

  override public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
    guard gestureRecognizer === panGesture else { return true }

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
  private func contentScale(forCardWidth width: CGFloat) -> CGFloat {
    let widest = bounds.width - widestCardSideGaps
    return widest > 0 ? width / widest : 1
  }

  private func contentScale(for insets: DetentInsets) -> CGFloat {
    contentScale(forCardWidth: max(0, bounds.width - insets.left - insets.right))
  }

  /// The height of the content region: the Yoga box the sheet's in-flow
  /// children (the `flex: 1` content wrapper) are laid out in. It is the
  /// tallest height the sheet can reach — the card's height at the tallest
  /// detent, clamped to the space its insets leave — rather than the host's,
  /// so a scrollable under a fixed or percentage tallest detent is laid out
  /// against the height it can actually show instead of leaving its last rows
  /// below the card's edge. A trailing `content` detent is bounded by that space
  /// alone: its resolved height is measured from this very region, so bounding
  /// the region by it would be circular. In layout units, so divided by the scale
  /// at that detent, and never taller than the sheet's node.
  private var contentRegionHeight: CGFloat {
    guard let tallest = rawDetentSpecs.last else { return bounds.height }
    let available = availableCardHeight(for: tallest)
    let bound: CGFloat
    switch tallest.kind {
    case .points:
      bound = tallest.value
    case .percentage:
      bound = available * tallest.value
    case .content:
      bound = available
    }
    let height = min(max(0, bound), available)
    let scale = contentScale(for: tallest.insets)
    return scale > 0 ? min(height / scale, bounds.height) : height
  }

  /// The tallest the card can be at a detent: the host's height, less the
  /// detent's top and bottom insets. Percentages resolve against it, so `100%`
  /// fits.
  private func availableCardHeight(for spec: RawDetentSpec) -> CGFloat {
    max(0, bounds.height - (spec.insets.top ?? statusBarOverlap) - spec.insets.bottom)
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
    bounds.height
  }

  private func resolveDetentSpecs() -> [DetentSpec]? {
    let maxHeight = sheetContainerHeight
    let measuredContentHeight = maxHeight > 0 ? validContentHeight.map { min($0, maxHeight) } : nil
    var resolvedDetents: [DetentSpec] = []
    resolvedDetents.reserveCapacity(rawDetentSpecs.count)

    for (index, spec) in rawDetentSpecs.enumerated() {
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
        let message =
          "Invalid bottom sheet detent at index \(index): resolved position \(position) is lower than previous detent position \(previous.position). Detents must be passed in ascending order."
        if lastReportedInvalidDetentMessage != message {
          lastReportedInvalidDetentMessage = message
          eventDelegate?.bottomSheetHostingView(self, didReportError: message)
        }
        return nil
      }
      resolvedDetents.append(
        DetentSpec(position: position, programmatic: spec.programmatic, insets: spec.insets)
      )
    }

    lastReportedInvalidDetentMessage = nil
    return resolvedDetents
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

  private func refreshDetentsFromLayout(commitsStableGeometry: Bool = true) {
    resolveDetentsFromLayout(commitsStableGeometry: commitsStableGeometry)
    replayPendingIndexIfResolved()
  }

  private func resolveDetentsFromLayout(commitsStableGeometry: Bool) {
    // While detached from a window (e.g. mid-commit, when Fabric reparents the
    // host as ancestor view flattening changes), the status bar's overlap is not
    // computable — its zero fallback would register as a detents change and
    // trigger a spurious re-anchor snap that clobbers any queued snap request.
    // Stay inert; didMoveToWindow refreshes geometry on reattach.
    if hasLaidOut, window == nil {
      return
    }
    let commitsStableTransition =
      commitsStableGeometry && hasLaidOut && activeSpring == nil && !isPanning
    if commitsStableTransition {
      beginStableDismissGeometryTransition()
    }
    refreshContentHeightMarker()
    if !isPanning {
      activeDragRange = nil
      activeDragDetentSpecs = nil
    }
    if hasLaidOut, isInvalidContentDetentTarget(targetIndex) {
      updateScrim()
      if commitsStableTransition {
        commitStableDismissGeometryIfNeeded()
      }
      return
    }

    guard let resolvedDetents = resolveDetentSpecs() else {
      updateScrim()
      if commitsStableTransition {
        commitStableDismissGeometryIfNeeded()
      }
      return
    }
    // Also fall through when only the height changed: the specs store positions,
    // but translations derive from it, so they must be re-anchored even
    // when the resolved detents are unchanged.
    guard resolvedDetents != detentSpecs || sheetContainerHeight != lastAppliedMaxDetentHeight
    else {
      updateScrim()
      if commitsStableTransition {
        commitStableDismissGeometryIfNeeded()
      }
      return
    }

    if hasLaidOut, activeSpring != nil, !isPanning {
      beginStableDismissGeometryTransition(replacingPending: true)
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
        let transitionStartFrame = pendingStableDismissAccessibilityFrame
        let visualTy = cancelActiveSpring()
        pendingStableDismissAccessibilityFrame = transitionStartFrame
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
          if commitsStableGeometry {
            commitStableDismissGeometryIfNeeded()
          }
        } else if !shouldAnimateHeight {
          setSheetTranslation(targetTy)
          emitPosition()
          scrimPinnedFull = false
          if commitsStableGeometry {
            commitStableDismissGeometryIfNeeded()
          }
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
    removeSettleAnimations()
  }

  /// The content wrapper: the surface is its sibling, so skip it and the
  /// wrapper is whatever else the sheet mounts.
  private func findContentView() -> UIView? {
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

  private func updatePresentationActiveForCurrentState() {
    updatePresentationActive(forPosition: currentSheetHeight)
  }

  private func updatePresentationActive(forPosition position: CGFloat) {
    if !modal {
      presentationActive = false
    } else if position > 0.5 {
      presentationActive = true
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

// MARK: - Card geometry

private extension BottomSheetHostingView {
  var isCardShaped: Bool {
    hasCardGaps || cornerRadii.contains { $0 > 0 }
  }

  /// Moves the sheet and keeps the card in step with it.
  func setSheetTranslation(_ ty: CGFloat) {
    sheetContainer.transform = CGAffineTransform(translationX: 0, y: ty)
    applyCardGeometry(forPosition: sheetContainerHeight - ty)
  }

  /// The card in sheet-container coordinates for a sheet position (the height
  /// of the sheet's top above the host's bottom). At or above the first open
  /// detent the card's bottom stays above the bottom gap and its side gaps blend
  /// between detents; below it, it keeps that detent's shape and slides down
  /// rigidly, so a closing sheet leaves through the gap with its shape intact.
  func cardRect(forPosition position: CGFloat) -> CGRect {
    let anchored = max(position, firstOpenDetentPosition, 0)
    let insets = cardInsets(forPosition: anchored)
    return CGRect(
      x: insets.left,
      y: 0,
      width: max(0, sheetContainer.bounds.width - insets.left - insets.right),
      height: min(max(0, anchored - insets.bottom), sheetContainerHeight)
    )
  }

  /// The card's gaps at a sheet position, blended between the detents around it.
  /// Resolved detents ascend by position.
  func cardInsets(forPosition position: CGFloat) -> DetentInsets {
    guard hasCardGaps,
          let upperIndex = detentSpecs.firstIndex(where: { position <= $0.position })
    else { return hasCardGaps ? detentSpecs.last?.insets ?? DetentInsets() : DetentInsets() }
    guard upperIndex > 0 else { return detentSpecs[upperIndex].insets }
    let lower = detentSpecs[upperIndex - 1]
    let upper = detentSpecs[upperIndex]
    let span = upper.position - lower.position
    let t = span <= 0 ? 1 : (position - lower.position) / span
    let blend = { (from: CGFloat, to: CGFloat) in from + (to - from) * t }
    return DetentInsets(
      left: blend(lower.insets.left, upper.insets.left),
      right: blend(lower.insets.right, upper.insets.right),
      bottom: blend(lower.insets.bottom, upper.insets.bottom)
    )
  }

  /// The card's outline in `rect`. A positive `outset` grows the outline and
  /// its rounded corners, as a `boxShadow` spread does; sharp corners stay
  /// sharp. Clip, shadows and hit testing all read this one outline, so they
  /// share the corner curve: continuous corners trace the same curve as
  /// `CALayer.cornerCurve = .continuous`.
  func cardPath(in cardRect: CGRect, outset: CGFloat = 0) -> CGPath {
    let rect = cardRect.insetBy(dx: -outset, dy: -outset)
    // A negative spread can leave no shape, which then casts no shadow.
    guard !rect.isNull else { return CGMutablePath() }
    let grown = cornerRadii.map { $0 > 0 ? max(0, $0 + outset) : 0 }
    // Corner transforms map the top-right corner's local frame (origin at the
    // corner, x leftward-negative along the top edge, y down the right edge)
    // onto each corner, clockwise from the top right.
    let corners: [(radius: CGFloat, transform: CGAffineTransform)] = [
      (grown[1], CGAffineTransform(a: 1, b: 0, c: 0, d: 1, tx: rect.maxX, ty: rect.minY)),
      (grown[2], CGAffineTransform(a: 0, b: 1, c: -1, d: 0, tx: rect.maxX, ty: rect.maxY)),
      (grown[3], CGAffineTransform(a: -1, b: 0, c: 0, d: -1, tx: rect.minX, ty: rect.maxY)),
      (grown[0], CGAffineTransform(a: 0, b: -1, c: 1, d: 0, tx: rect.minX, ty: rect.minY)),
    ]
    let path = CGMutablePath()
    if cornerCurve == .continuous {
      // Like the layer's own continuous corners, these are not clamped to the
      // rect: a radius past half the side overlaps, as it does there.
      path.move(to: CGPoint(x: rect.minX + ContinuousCorner.extent * grown[0], y: rect.minY))
      for corner in corners {
        ContinuousCorner.add(to: path, radius: corner.radius, transform: corner.transform)
      }
    } else {
      let limit = max(0, min(rect.width, rect.height) / 2)
      let clamped = corners.map { (min($0.radius, limit), $0.transform) }
      path.move(to: CGPoint(x: rect.minX + clamped[3].0, y: rect.minY))
      for (radius, transform) in clamped {
        path.addLine(to: CGPoint(x: -radius, y: 0).applying(transform))
        path.addArc(
          tangent1End: CGPoint.zero.applying(transform),
          tangent2End: CGPoint(x: 0, y: radius).applying(transform),
          radius: radius
        )
      }
    }
    path.closeSubpath()
    return path
  }

  /// Masks the card's interior out of a shadow: everything the shadow can
  /// reach, minus the card, with an even-odd fill.
  func shadowMaskPath(in cardRect: CGRect, for shadow: CardShadow) -> CGPath {
    // Past three sigma (1.5 blur radii) a Gaussian blur adds nothing visible.
    let reach = 1.5 * shadow.blurRadius + abs(shadow.spreadDistance) + 1
    let path = CGMutablePath()
    path.addRect(
      cardRect
        .offsetBy(dx: shadow.offset.width, dy: shadow.offset.height)
        .union(cardRect)
        .insetBy(dx: -reach, dy: -reach)
    )
    path.addPath(cardPath(in: cardRect))
    return path
  }

  /// Sets the model geometry of the card. Layer work only: the surface and
  /// content are never resized, so no Fabric layout runs.
  func applyCardGeometry(forPosition position: CGFloat) {
    let rect = cardRect(forPosition: position)
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    clipContainer.layer.position = rect.origin
    clipContainer.layer.bounds = rect
    cardMask?.path = cardPath(in: rect)
    for (layer, shadow) in zip(shadowLayers, cardShadows) {
      layer.shadowPath = cardPath(in: rect, outset: shadow.spreadDistance)
      (layer.mask as? CAShapeLayer)?.path = shadowMaskPath(in: rect, for: shadow)
    }
    for view in clipContainer.subviews {
      view.layer.transform = contentTransform(of: view, in: rect)
    }
    CATransaction.commit()
  }

  /// Scales a view (the surface or content) to the card's width and pins its
  /// top-left corner to the card's. Layer work only: the view keeps its layout.
  func contentTransform(of contentView: UIView, in rect: CGRect) -> CATransform3D {
    guard hasCardGaps else { return CATransform3DIdentity }
    let scale = contentScale(forCardWidth: rect.width)
    // The layer scales about its center, which Fabric's layout owns.
    let center = contentView.center
    let size = contentView.bounds.size
    let dx = rect.minX - (center.x - scale * size.width / 2)
    let dy = rect.minY - (center.y - scale * size.height / 2)
    return CATransform3DConcat(
      CATransform3DMakeScale(scale, scale, 1),
      CATransform3DMakeTranslation(dx, dy, 0)
    )
  }

  /// Matches the shadow layers to `cardShadows`, reusing existing layers.
  /// CSS blur radius is twice the Gaussian sigma, which is what
  /// `shadowRadius` approximates.
  func updateCardShadows() {
    while shadowLayers.count > cardShadows.count {
      shadowLayers.removeLast().removeFromSuperlayer()
    }
    while shadowLayers.count < cardShadows.count {
      let layer = CALayer()
      // Below the others, as the first listed shadow goes on top.
      shadowView.layer.insertSublayer(layer, at: 0)
      shadowLayers.append(layer)
    }
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    for (layer, shadow) in zip(shadowLayers, cardShadows) {
      layer.frame = shadowView.bounds
      layer.shadowOffset = shadow.offset
      layer.shadowRadius = shadow.blurRadius / 2
      layer.shadowOpacity = Float(shadow.opacity)
      if shadow.excludesInterior {
        let mask = layer.mask as? CAShapeLayer ?? CAShapeLayer()
        mask.fillRule = .evenOdd
        layer.mask = mask
      } else {
        layer.mask = nil
      }
    }
    CATransaction.commit()
    updateCardShadowColors()
    applyCardGeometry(forPosition: sheetContainerHeight - sheetContainer.transform.ty)
  }

  /// Resolves the shadow colors for the current appearance. Layers take plain
  /// `CGColor`s, so dynamic colors are re-resolved when the appearance flips.
  func updateCardShadowColors() {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    for (layer, shadow) in zip(shadowLayers, cardShadows) {
      layer.shadowColor = shadow.color.resolvedColor(with: traitCollection).cgColor
    }
    CATransaction.commit()
  }

  /// Chooses how the card is clipped. Corners sharing one radius use the
  /// layer's own corner rounding (which honors `cornerCurve`); differing radii
  /// fall back to a mask drawn from `cardPath`, in the same curve.
  func updateCardShape() {
    let rounded = cornerRadii.filter { $0 > 0 }
    clipContainer.clipsToBounds = isCardShaped
    if let radius = rounded.first, rounded.allSatisfy({ abs($0 - radius) < 0.01 }) {
      var corners: CACornerMask = []
      if cornerRadii[0] > 0 {
        corners.insert(.layerMinXMinYCorner)
      }
      if cornerRadii[1] > 0 {
        corners.insert(.layerMaxXMinYCorner)
      }
      if cornerRadii[2] > 0 {
        corners.insert(.layerMaxXMaxYCorner)
      }
      if cornerRadii[3] > 0 {
        corners.insert(.layerMinXMaxYCorner)
      }
      clipContainer.layer.cornerRadius = radius
      clipContainer.layer.maskedCorners = corners
      clipContainer.layer.cornerCurve = cornerCurve
      clipContainer.layer.mask = nil
      cardMask = nil
    } else if !rounded.isEmpty {
      clipContainer.layer.cornerRadius = 0
      let mask = cardMask ?? CAShapeLayer()
      mask.frame = .zero
      clipContainer.layer.mask = mask
      cardMask = mask
    } else {
      clipContainer.layer.cornerRadius = 0
      clipContainer.layer.mask = nil
      cardMask = nil
    }
    // A shape change mid-settle cannot retarget the running card animations;
    // they finish against the old shape, and the model geometry set here takes
    // over once they end.
    applyCardGeometry(forPosition: sheetContainerHeight - sheetContainer.transform.ty)
  }

  /// Animates the card along the same spring samples, timing and clock as the
  /// sheet's settle animation, so the card's height follows the sheet frame
  /// for frame on the render server.
  func addCardAnimations(matching sheetAnimation: CAKeyframeAnimation) {
    // An unclipped card without shadows draws nothing that follows its
    // geometry; the model geometry is enough.
    guard isCardShaped || !cardShadows.isEmpty else { return }
    let samples = (sheetAnimation.values ?? []).compactMap {
      ($0 as? CGFloat) ?? ($0 as? NSNumber).map { CGFloat(truncating: $0) }
    }
    guard !samples.isEmpty else { return }
    let rects = cardRects(forSamples: samples)
    let keyframes = { (keyPath: String, values: [Any]) in
      Self.cardKeyframes(keyPath, values, matching: sheetAnimation)
    }

    clipContainer.layer.add(
      keyframes("bounds", rects.map { NSValue(cgRect: $0) }),
      forKey: Self.cardBoundsAnimationKey
    )
    clipContainer.layer.add(
      keyframes("position", rects.map { NSValue(cgPoint: $0.origin) }),
      forKey: Self.cardPositionAnimationKey
    )
    cardSettleAnimation = sheetAnimation
    addContentAnimation()
    cardMask?.add(
      keyframes("path", rects.map { cardPath(in: $0) }),
      forKey: Self.cardMaskAnimationKey
    )
    for (layer, shadow) in zip(shadowLayers, cardShadows) {
      layer.add(
        keyframes("shadowPath", rects.map { cardPath(in: $0, outset: shadow.spreadDistance) }),
        forKey: Self.cardShadowAnimationKey
      )
      layer.mask?.add(
        keyframes("path", rects.map { shadowMaskPath(in: $0, for: shadow) }),
        forKey: Self.cardShadowMaskAnimationKey
      )
    }
  }

  /// Adds the surface's and content's keyframes for the running settle, or
  /// replaces them: the same `beginTime` keeps them on the settle's clock, so
  /// nothing visibly restarts.
  private func addContentAnimation() {
    guard hasCardGaps, let sheetAnimation = cardSettleAnimation else { return }
    let samples = (sheetAnimation.values ?? []).compactMap {
      ($0 as? CGFloat) ?? ($0 as? NSNumber).map { CGFloat(truncating: $0) }
    }
    let rects = cardRects(forSamples: samples)
    contentAnimationLayouts = currentContentLayouts
    for view in clipContainer.subviews {
      view.layer.add(
        Self.cardKeyframes(
          "transform",
          rects.map { NSValue(caTransform3D: contentTransform(of: view, in: $0)) },
          matching: sheetAnimation
        ),
        forKey: Self.cardContentAnimationKey
      )
    }
  }

  private var isContentAnimationStale: Bool {
    guard cardSettleAnimation != nil else { return false }
    return contentAnimationLayouts != currentContentLayouts
  }

  private var currentContentLayouts: [CGRect] {
    clipContainer.subviews.map { CGRect(origin: $0.center, size: $0.bounds.size) }
  }

  private func cardRects(forSamples samples: [CGFloat]) -> [CGRect] {
    let maxHeight = sheetContainerHeight
    return samples.map { cardRect(forPosition: maxHeight - $0) }
  }

  private static func cardKeyframes(
    _ keyPath: String,
    _ values: [Any],
    matching sheetAnimation: CAKeyframeAnimation
  ) -> CAKeyframeAnimation {
    let animation = CAKeyframeAnimation(keyPath: keyPath)
    animation.values = values
    animation.keyTimes = sheetAnimation.keyTimes
    animation.duration = sheetAnimation.duration
    animation.calculationMode = sheetAnimation.calculationMode
    animation.beginTime = sheetAnimation.beginTime
    animation.isRemovedOnCompletion = false
    animation.fillMode = .forwards
    return animation
  }

  func removeSettleAnimations() {
    cardSettleAnimation = nil
    contentAnimationLayouts = []
    sheetContainer.layer.removeAnimation(forKey: Self.springAnimationKey)
    clipContainer.layer.removeAnimation(forKey: Self.cardBoundsAnimationKey)
    clipContainer.layer.removeAnimation(forKey: Self.cardPositionAnimationKey)
    clipContainer.subviews.forEach { $0.layer.removeAnimation(forKey: Self.cardContentAnimationKey) }
    cardMask?.removeAnimation(forKey: Self.cardMaskAnimationKey)
    for layer in shadowLayers {
      layer.removeAnimation(forKey: Self.cardShadowAnimationKey)
      layer.mask?.removeAnimation(forKey: Self.cardShadowMaskAnimationKey)
    }
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
    updateDismissAccessibilityGeometry(forPosition: position)

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

  func updateDismissAccessibilityGeometry(forPosition position: CGFloat) {
    let hostBounds = bounds
    guard
      let window,
      dismissAccessibilityGeometryWindow === window,
      position.isFinite,
      hostBounds.minX.isFinite,
      hostBounds.minY.isFinite,
      hostBounds.width.isFinite,
      hostBounds.height.isFinite,
      hostBounds.width > 0,
      hostBounds.height > 0
    else {
      invalidateDismissAccessibilityGeometry()
      return
    }

    let sheetTop = min(max(hostBounds.maxY - position, hostBounds.minY), hostBounds.maxY)
    let outsideSheetRect = CGRect(
      x: hostBounds.minX,
      y: hostBounds.minY,
      width: hostBounds.width,
      height: sheetTop - hostBounds.minY
    )
    guard outsideSheetRect.height > 0 else {
      invalidateDismissAccessibilityGeometry()
      return
    }

    let screenFrame = UIAccessibility.convertToScreenCoordinates(outsideSheetRect, in: self)
    guard
      screenFrame.minX.isFinite,
      screenFrame.minY.isFinite,
      screenFrame.width.isFinite,
      screenFrame.height.isFinite,
      screenFrame.width > 0,
      screenFrame.height > 0
    else {
      invalidateDismissAccessibilityGeometry()
      return
    }

    dismissAccessibilityFrame = screenFrame
    scrimView.frame = outsideSheetRect
    scrimView.accessibilityFrame = screenFrame
  }

  func invalidateDismissAccessibilityGeometry() {
    discardPendingStableDismissGeometry()
    dismissAccessibilityFrame = nil
    scrimView.accessibilityFrame = .zero
    scrimView.frame = .zero
  }

  func beginStableDismissGeometryTransition(replacingPending: Bool = false) {
    if replacingPending {
      discardPendingStableDismissGeometry()
    }
    guard
      pendingStableDismissAccessibilityFrame == nil,
      scrimView.isAccessibilityElement,
      let dismissAccessibilityFrame
    else {
      return
    }
    pendingStableDismissAccessibilityFrame = dismissAccessibilityFrame
  }

  func discardPendingStableDismissGeometry() {
    pendingStableDismissAccessibilityFrame = nil
  }

  func commitStableDismissGeometryIfNeeded() {
    guard let initialFrame = pendingStableDismissAccessibilityFrame else { return }
    discardPendingStableDismissGeometry()

    guard
      let finalFrame = dismissAccessibilityFrame,
      initialFrame != finalFrame,
      scrimView.isAccessibilityElement,
      UIAccessibility.isVoiceOverRunning,
      scrimView.accessibilityElementIsFocused()
    else {
      return
    }

    UIAccessibility.post(notification: .layoutChanged, argument: nil)
  }

  func updateInteractionState() {
    scrimView.isUserInteractionEnabled = modal && (closedIndex != nil) && !scrimView.isHidden
    // Expose the scrim to VoiceOver only while tapping it would dismiss the
    // sheet; otherwise it would be an inert, unlabeled stop in the
    // accessibility tree (a scrim over a programmatic-only close detent is
    // decorative, not actionable).
    scrimView.isAccessibilityElement = accessibleDismissalIndex != nil && dismissAccessibilityFrame != nil
    if !scrimView.isAccessibilityElement {
      discardPendingStableDismissGeometry()
    }
  }
}
