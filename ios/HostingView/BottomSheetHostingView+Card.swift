import UIKit

/// Hosts the surface and content and clips them to the card shape. The host
/// gates hit testing against the presented card, so this view accepts every
/// point: mid-settle its model bounds already hold the target geometry and
/// would reject touches on the card as it is drawn.
final class CardClipView: UIView {
  override func point(inside _: CGPoint, with _: UIEvent?) -> Bool {
    true
  }
}

/// An outset shadow of the card, as `boxShadow` or the `shadow*` props describe it.
struct CardShadow: Equatable {
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
enum ContinuousCorner {
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

// MARK: - Card geometry

extension BottomSheetHostingView {
  public func setSurfaceCornerRadii(_ values: [NSNumber]) {
    var radii = values.prefix(4).map { max(0, CGFloat(truncating: $0)) }
    radii += Array(repeating: 0, count: 4 - radii.count)
    guard radii != cornerRadii else { return }
    cornerRadii = radii
    updateCardShape()
  }

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

  var isCardShaped: Bool {
    hasCardGaps || cornerRadii.contains { $0 > 0 }
  }

  /// Moves the sheet and keeps the card in step with it.
  func setSheetTranslation(_ ty: CGFloat) {
    sheetContainer.transform = CGAffineTransform(translationX: 0, y: ty)
    applyCardGeometry(forPosition: sheetContainerHeight - ty)
    Self.updateStacks()
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
  private func cardInsets(forPosition position: CGFloat) -> DetentInsets {
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
  private func shadowMaskPath(in cardRect: CGRect, for shadow: CardShadow) -> CGPath {
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
  private func contentTransform(of contentView: UIView, in rect: CGRect) -> CATransform3D {
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
  private func updateCardShadows() {
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
  func addContentAnimation() {
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

  var isContentAnimationStale: Bool {
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
