import UIKit

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
  /// Fired after the scrim and sheet visibility are re-evaluated without the
  /// sheet moving (e.g., a `scrimOpacity` or `modal` change), so the component
  /// layer can follow `isPresented` and `isModalAccessibilityActive`. Movement
  /// reports through `didChangePosition` instead.
  func bottomSheetHostingViewDidUpdateScrim(_ view: BottomSheetHostingView)
}

/// The scrim control, exposed to VoiceOver as a dismiss button while a
/// dismissible modal sheet is open. VoiceOver's default activation simulates
/// a tap at the activation point; dismissing directly keeps activation
/// reliable even when the sheet overlaps that point mid-settle.
final class BottomSheetScrimControl: UIControl {
  /// The part of the scrim VoiceOver frames as the button, in this control's
  /// coordinates. The scrim spans the host, but the sheet covers all of it
  /// below the sheet's top edge.
  var accessibilityRegion: (() -> CGRect)?

  override var accessibilityFrame: CGRect {
    get {
      guard let region = accessibilityRegion?() else { return super.accessibilityFrame }
      return UIAccessibility.convertToScreenCoordinates(region, in: self)
    }
    set { super.accessibilityFrame = newValue }
  }

  /// Performs the dismissal, returning whether one was performed, so VoiceOver
  /// can tell an activation that did nothing.
  var onAccessibilityActivate: (() -> Bool)?

  override func accessibilityActivate() -> Bool {
    onAccessibilityActivate?() ?? false
  }
}

@objcMembers
public final class BottomSheetHostingView: UIView {
  public weak var eventDelegate: BottomSheetHostingViewDelegate?
  public var modal: Bool = false {
    didSet { updateScrim() }
  }

  public var scrimColor: UIColor? = .clear {
    didSet { scrimPaintView.backgroundColor = scrimColor }
  }

  /// The label VoiceOver announces for the scrim while it is exposed as a
  /// dismiss button.
  public var scrimAccessibilityLabel: String? {
    get { scrimView.accessibilityLabel }
    set { scrimView.accessibilityLabel = newValue ?? "Dismiss" }
  }

  /// Scrim opacity per detent index, from each detent’s `scrimOpacity`, and
  /// linearly interpolated between detents. Kept apart from `rawDetentSpecs`,
  /// so a scrim change only repaints the scrim.
  var scrimOpacities: [CGFloat] = [] {
    didSet {
      if scrimOpacities != oldValue {
        updateScrim()
      }
    }
  }

  /// Physical corner radii of the card: top-left, top-right, bottom-right,
  /// bottom-left. With all zero and no gaps around the card, the sheet stays
  /// unclipped, as before cards existed, so content drawn outside the sheet
  /// (e.g., a surface's own shadow) stays visible.
  var cornerRadii: [CGFloat] = [0, 0, 0, 0]
  var cornerCurve: CALayerCornerCurve = .circular

  /// Outset shadows cast by the card, each one drawn by its own layer below
  /// the clipping container so the clip never cuts it.
  var cardShadows: [CardShadow] = []

  /// The height the container geometry and translations are currently anchored
  /// to; used to detect height-only geometry changes and to re-anchor against
  /// the value the current translation was computed with.
  var lastAppliedMaxDetentHeight: CGFloat = .nan

  public var scrollExpandCoordination: Int = ScrollCoordinationLevel.handoff.rawValue
  public var scrollCollapseCoordination: Int = ScrollCoordinationLevel.initial.rawValue

  /// Duration of the spring that carries the sheet to the detent a release
  /// resolved to. `0` (the default) uses `defaultSettleDuration`.
  public var settleDuration: CFTimeInterval = 0

  var rawDetentSpecs: [RawDetentSpec] = []
  /// Whether any detent keeps a gap at the card's sides or bottom, so the card
  /// is narrower or shorter than the surface and has to clip it. A top inset
  /// only lowers the detent's height, which needs no clip.
  var hasCardGaps = false
  /// The sum of the side insets of the widest card, which the content is laid
  /// out against.
  var widestCardSideGaps: CGFloat = 0
  var detentSpecs: [DetentSpec] = [] {
    didSet {
      setNeedsLayout()
      updateScrim()
    }
  }

  var targetIndex: Int = 0
  public var animateIn: Bool = true
  public var animateContentHeight: Bool = true

  /// Whether this modal sheet pushes back the open modal sheet beneath it.
  public var stacked: Bool = false {
    didSet {
      if stacked != oldValue {
        // It changes how tall the card may get, which bounds the detents.
        setNeedsLayout()
        updateStackRegistration()
      }
    }
  }

  /// When the sheet last opened, relative to other sheets.
  public var openOrder: Int = 0 {
    didSet {
      if openOrder != oldValue {
        Self.applyStacks()
      }
    }
  }

  /// Whether the sheet is presented in the native overlay, above portal sheets.
  public var nativeOverlay: Bool = false {
    didSet {
      if nativeOverlay != oldValue {
        Self.applyStacks()
      }
    }
  }

  /// Holds the sheet, and carries the transform that pushes it back while a
  /// stacked sheet covers it. Apart from the scrim, which stays put, and from
  /// the sheet container, whose transform stays the sheet's translation alone.
  let stackView = UIView()
  /// When the candidates' springs that the running stack animation follows
  /// started, so it is rebuilt only when one starts or ends.
  var stackAnimationSprings: [CFTimeInterval] = []
  /// The `stacked` sheets above this one, which push it back and dim it.
  var stackAbove: [BottomSheetHostingView] = []
  /// Dims the card while stacked sheets cover it. A layer of the clipping
  /// container, above the surface and content, so it is cut to the card.
  let stackDimLayer = CALayer()
  public let sheetContainer = UIView()
  let clipContainer = CardClipView()
  /// Casts the card's shadows. A sibling below the clipping container, so the
  /// clip never cuts the shadows.
  let shadowView = UIView()
  var shadowLayers: [CALayer] = []
  /// Masks the card when its corners differ in radius, which the layer's own
  /// corner radius cannot express. Nil while the layer's corners suffice.
  var cardMask: CAShapeLayer?
  let scrimView = BottomSheetScrimControl()
  /// Paints the scrim. Apart from the scrim control, whose alpha also decides
  /// whether it takes touches, so a stacked sheet can leave the background to
  /// the sheet beneath it and still close on a tap there.
  let scrimPaintView = UIView()
  var panGesture: SheetPanGestureRecognizer!
  var activeSpring: CriticalSpring?
  var activeSpringTargetIndex: Int = 0
  var activeSpringEmitsSettle = false
  var scrimPinnedFull = false
  private var displayLink: CADisplayLink?
  var pendingIndex: Int?
  var pendingSnapRequest: PendingSnapRequest?
  var hasLaidOut = false
  var isPanning = false
  var lastReportedInvalidDetentMessage: String?
  var panStartingIndex: Int?
  var initialScrollViewStates: [ActiveScrollViewState] = []
  let scrollIndicatorFits = NSMapTable<UIScrollView, ScrollIndicatorFit>.weakToStrongObjects()
  /// The scroll views' indicator-drag recognizers the sheet listens to.
  let observedIndicatorDrags = NSHashTable<UIGestureRecognizer>.weakObjects()
  var indicatorDrag: IndicatorDragState?
  /// Whether every scrollable under the touch was at its visual start as the
  /// touch landed: the `initial` owner selection at the largest detent.
  var initialScrollableChainAtStart = true
  var activeDragRange: (minTy: CGFloat, maxTy: CGFloat)?
  var activeDragDetentSpecs: [DetentSpec]?
  var activeScrollViewStates: [ActiveScrollViewState] = []
  var panCoordinationMode: PanCoordinationMode = .sheet
  var activeScrollCoordinationLevel: ScrollCoordinationLevel = .none
  var didMoveSheetDuringPan = false
  /// Whether the spring a pan stopped as it began emitted `onSettle`; nil when
  /// no spring was running.
  var interruptedSpringEmitsSettle: Bool?
  var didCancelTouchesForPan = false
  var activeScrollViewsLocked = false
  var scrollViewOwnsLowerBoundary = false
  var isContentInteractionDisabled = false
  // The `nativeID` the JS side gives the zero-height marker it appends to the
  // content wrapper. Mirrored in `bottomSheetUtils.ts` and `BottomSheetHostView.kt`;
  // keep the three in sync.
  static let contentHeightMarkerNativeID = "swm-bottom-sheet-content-height-marker"
  var contentHeightMarker: UIView?
  weak var surfaceView: UIView?
  static var markerObservationContext = 0
  /// Whether a detents change moved the content region while Fabric has yet
  /// to lay the content out against it. Resolving a `content` detent from the
  /// stale measurement could trip the ascending-order check: a filling
  /// scrollable measured in a 300pt region cannot sit above a 500pt detent
  /// that the same update introduced.
  var isContentRegionChangePending = false
  /// The content-region inset of the state Fabric last committed, recorded
  /// mid-transaction. NaN before the first commit.
  var committedContentRegionInset: CGFloat = .nan
  /// The committed inset once its mounting transaction has fully mounted, so
  /// the content — and its marker — is laid out against it. Only this value
  /// vouches for a content measurement: mid-transaction, the host's own frame
  /// (and thus its safe-area insets) can change before the marker's layout
  /// mutation runs. NaN before the first transaction completes.
  var appliedContentRegionInset: CGFloat = .nan
  /// Settle duration used when `settleDuration` is left at 0.
  static let defaultSettleDuration: CFTimeInterval = 0.45
  static let springAnimationKey = "bottomSheetSettle"
  static let cardBoundsAnimationKey = "bottomSheetCardBounds"
  static let cardPositionAnimationKey = "bottomSheetCardPosition"
  static let cardMaskAnimationKey = "bottomSheetCardMask"
  static let cardShadowAnimationKey = "bottomSheetCardShadow"
  static let cardShadowMaskAnimationKey = "bottomSheetCardShadowMask"
  static let cardContentAnimationKey = "bottomSheetCardContent"
  /// The settle animation the card is following, kept so the content's keyframes
  /// can be rebuilt when a layout moves the content mid-settle.
  var cardSettleAnimation: CAKeyframeAnimation?
  /// The centers and sizes that the running content keyframes were built from.
  var contentAnimationLayouts: [CGRect] = []

  /// Extra lead applied to the spring prediction when emitting follower
  /// positions, in frames (scaled by the display link's actual frame duration,
  /// so it means the same thing at 60Hz and 120Hz). `targetTimestamp` alone
  /// still trails the sheet slightly: the render server's sampling phase for
  /// the keyframe animation is undocumented, and the follower's transform
  /// commits one runloop turn after this callback. Tune by bisecting on a
  /// physical device: 0 = no bias, 1 = predict one extra frame ahead. Too high
  /// and followers visibly lead the sheet at the start of a settle.
  static let followerPredictionBiasFrames: CFTimeInterval = 1.0

  override public init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    clipsToBounds = false

    scrimView.backgroundColor = .clear
    scrimPaintView.backgroundColor = scrimColor
    scrimPaintView.isUserInteractionEnabled = false
    scrimView.addSubview(scrimPaintView)
    scrimView.alpha = 0
    scrimView.isHidden = true
    scrimView.addTarget(self, action: #selector(handleScrimPress), for: .touchUpInside)
    scrimView.isAccessibilityElement = false
    scrimView.accessibilityTraits = .button
    scrimView.accessibilityLabel = "Dismiss"
    scrimView.accessibilityRegion = { [weak self] in self?.scrimDismissRegion ?? .zero }
    scrimView.onAccessibilityActivate = { [weak self] in self?.attemptScrimDismissal() ?? false }
    addSubview(scrimView)

    sheetContainer.backgroundColor = .clear
    sheetContainer.clipsToBounds = false
    stackView.backgroundColor = .clear
    addSubview(stackView)
    stackView.addSubview(sheetContainer)

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
    stackDimLayer.backgroundColor = UIColor.black.cgColor
    stackDimLayer.opacity = 0
    // Above the surface and content, whatever order Fabric mounts them in.
    stackDimLayer.zPosition = 1
    clipContainer.layer.addSublayer(stackDimLayer)

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
        Self.updateStackDimming()
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
      Self.updateStackDimming()
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
  weak var surfaceTouchHandler: UIGestureRecognizer?

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
    updateStackRegistration()
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
    guard bounds.width > 0, bounds.height > 0 else { return }
    // See refreshDetentsFromLayout: without a window the status bar's overlap
    // falls back to zero, which must not be applied to the container geometry.
    if hasLaidOut, window == nil {
      return
    }

    scrimView.frame = bounds
    scrimPaintView.frame = scrimView.bounds
    // Not through `frame`, which is undefined under the stack transform.
    stackView.bounds = bounds
    stackView.center = CGPoint(x: bounds.midX, y: bounds.midY)
    refreshDetentsFromLayout()
    let maxHeight = sheetContainerHeight
    lastAppliedMaxDetentHeight = maxHeight
    sheetContainer.bounds = CGRect(x: 0, y: 0, width: bounds.width, height: maxHeight)
    sheetContainer.center = CGPoint(x: bounds.width / 2, y: bounds.height - maxHeight / 2)

    // The surface fills the full container so it always covers the visible sheet
    // (the container is translated to the current sheet position), regardless of
    // how short the content becomes. Sized from the top via frame — never via
    // anchorPoint.
    shadowView.frame = sheetContainer.bounds
    // In sheet-container coordinates, which the clipping container keeps. It
    // spans the whole container, and the clip cuts it to the card.
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    stackDimLayer.frame = sheetContainer.bounds
    CATransaction.commit()
    // From the model translation: mid-settle the card animation drives the
    // presented geometry and the model already holds the target.
    applyCardGeometry(forPosition: maxHeight - sheetContainer.transform.ty)

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
      }
      return
    }

    if activeSpring != nil || isPanning {
      // Every other path moves the sheet, which updates the stack. The host's
      // size and the detents shape it too.
      Self.updateStacks()
      return
    }
    setSheetTranslation(translationY(for: targetIndex))
    updateScrim()
  }

  /// The card as it is drawn, in host coordinates. Mid-settle the translation
  /// comes from the spring instead of the presentation layer, which lags one
  /// commit behind right after a snap starts. The container's transform is
  /// translation-only.
  var presentedCardFrame: CGRect {
    let ty = currentTranslationY
    let size = sheetContainer.bounds.size
    let center = sheetContainer.center
    return cardRect(forPosition: sheetContainerHeight - ty).offsetBy(
      dx: center.x - size.width / 2,
      dy: center.y - size.height / 2 + ty
    )
  }

  /// Whether `point` (in host coordinates) falls on the card as it is drawn.
  /// Mid-settle the translation comes from the spring instead of the
  /// presentation layer, which lags one commit behind right after a snap
  /// starts. The container's transform is translation-only.
  private func presentedCardContains(_ point: CGPoint) -> Bool {
    // The card is drawn where the stack transform puts it.
    let point = convert(point, to: stackView)
    let card = presentedCardFrame
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
    // Entries without a value are dropped up front, so the specs and the scrim
    // opacities below stay aligned by index.
    let entries = raw.filter { $0["value"] is NSNumber }
    rawDetentSpecs = entries.map { dict in
      let value = (dict["value"] as! NSNumber).doubleValue
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
    // Set once the detents are re-resolved, so the scrim isn’t repainted
    // against the previous ones.
    scrimOpacities = entries.map { CGFloat(truncating: $0["scrimOpacity"] as? NSNumber ?? 1) }
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
      interruptedSpringEmitsSettle = nil
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
  func replayPendingIndexIfResolved() {
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
    interruptedSpringEmitsSettle = nil
    scrimPinnedFull = false
    lastAppliedMaxDetentHeight = .nan
    stopDisplayLink()
    removeSettleAnimations()
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
    detachIndicatorDrags()
    releaseScrollIndicatorFits()
    finishScrollViewCoordination()
    setContentInteractionEnabled(true)
    stopObservingContentHeightMarker()
    isContentRegionChangePending = false
    committedContentRegionInset = .nan
    appliedContentRegionInset = .nan
    surfaceView = nil
    resetStack()
    sheetContainer.transform = .identity
    scrimView.alpha = 0
    scrimView.isHidden = true
    for subview in clipContainer.subviews {
      subview.layer.removeAnimation(forKey: Self.cardContentAnimationKey)
      subview.layer.transform = CATransform3DIdentity
      subview.removeFromSuperview()
    }
  }

  /// `overrideTy` is the spring's predicted translationY for the upcoming frame
  /// (passed during a settle). Without it we read the current on-screen value.
  func emitPosition(overrideTy: CGFloat? = nil) {
    let maxHeight = sheetContainerHeight
    let ty = overrideTy ?? currentTranslationY
    let position = maxHeight - ty
    updateScrim(forPosition: position)
    updateSheetVisibility(forPosition: position)
    updateInteractionState()
    Self.updateStackDimming()
    eventDelegate?.bottomSheetHostingView(
      self, didChangePosition: position, index: detentIndex(forPosition: position)
    )
  }

  func startDisplayLink() {
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

  func stopDisplayLink() {
    displayLink?.invalidate()
    displayLink = nil
  }

  override public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
    guard gestureRecognizer === panGesture else { return true }
    return shouldBeginSheetPan()
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
    for recognizer in observedIndicatorDrags.allObjects {
      recognizer.removeTarget(self, action: nil)
    }
    stopObservingContentHeightMarker()
    displayLink?.invalidate()
    removeSettleAnimations()
  }
}
