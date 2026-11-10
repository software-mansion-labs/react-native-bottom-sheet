// The Exact adapter for the bottom sheet on iOS: the `swm-bottom-sheet` view of
// `@swmansion/exact-bottom-sheet`. It is the iOS counterpart of
// `ios/BottomSheetComponentView.mm` + `ios/BottomSheetContentView.mm` (the
// Fabric glue): it turns the tag's string props into the inputs those files
// feed `BottomSheetHostingView` (`ios/HostingView/*.swift`, compiled into this
// package's module unchanged), and the hosting view's delegate calls into the
// tag's typed events. Everything the sheet does — detents, gestures, the
// spring, the scrim, scroll coordination, the card — stays in the shared
// hosting view.
//
// A package view in Exact is a leaf: it has no Contract children. The sheet's
// content is therefore an ordinary Contract node placed next to the tag (with
// the `testId` the tag's `contentId` names); this view finds that node's
// UIView and adopts it into the sheet, where it moves with the card. Exact
// keeps laying the node out and drawing it; only its parent view changes.
// See packages/exact/README.md ("How content is adopted") for the rules an
// author follows so Exact never takes it back.
import ExactPackage
import Foundation
import UIKit

public final class ExactBottomSheetModule: ExactPackage {
  private let context: ExactPackageContext

  public init(context: ExactPackageContext) {
    self.context = context
  }

  public func later(
    _: String, _: [String: Any], preferred _: Any?, reply: @escaping ([String: Any]) -> Void
  ) {
    reply(["error": "NotSupportedError"])
  }

  public func view(
    _ tag: String, id: String, props: [String: String], events: ExactPackageEvents
  ) throws -> ExactPackageView {
    guard tag == "swm-bottom-sheet" else { throw ExactPackageRefusal("no view \(tag)") }
    let sheet = ExactBottomSheetView(id: id, events: events, log: { [context] in context.log($0) })
    do {
      try sheet.setProps(props)
    } catch {
      sheet.destroy()
      throw error
    }
    return sheet
  }
}

/// The marker the hosting view reads the content's height from: a zero-height
/// view directly in the content wrapper whose `nativeId` is the one the React
/// Native side gives its marker (`CONTENT_HEIGHT_MARKER_NATIVE_ID`). Its top
/// sits at the adopted node's bottom.
final class ExactContentHeightMarker: UIView {
  @objc var nativeId: NSString? = BottomSheetHostingView.contentHeightMarkerNativeID as NSString

  override init(frame: CGRect) {
    super.init(frame: frame)
    isUserInteractionEnabled = false
  }

  @available(*, unavailable)
  required init?(coder _: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }
}

/// The view Exact puts in the tag's box. It forwards hit testing to the
/// hosting view, which returns nil outside the card and the scrim, so a touch
/// there reaches whatever Exact drew under the tag.
final class ExactSheetRootView: UIView {
  let host = BottomSheetHostingView(frame: .zero)
  var onLayout: () -> Void = {}

  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    host.frame = bounds
    host.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    addSubview(host)
  }

  @available(*, unavailable)
  required init?(coder _: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    onLayout()
  }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    onLayout()
  }

  override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
    host.hitTest(convert(point, to: host), with: event)
  }

  override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
    host.point(inside: convert(point, to: host), with: event)
  }
}

final class ExactBottomSheetView: NSObject, ExactPackageView, BottomSheetHostingViewDelegate {
  private let root = ExactSheetRootView(frame: .zero)
  private var host: BottomSheetHostingView {
    root.host
  }

  private let id: String
  private let events: ExactPackageEvents
  private let log: (String) -> Void

  /// Plays the content wrapper's part (`BottomSheetContentWrapperView`): sized
  /// to the content region, it holds the adopted node and the marker.
  private let contentWrapper = UIView()
  private let marker = ExactContentHeightMarker(frame: .zero)
  /// Plays the surface's part when `surfaceColor` is set.
  private let surface = UIView()
  private var surfaceMounted = false

  private var contentId = ""
  private weak var contentNode: UIView?
  /// Where the node was before it was adopted, to give it back on destroy.
  private weak var contentHome: UIView?
  /// The node's next sibling and index in `contentHome` then, to put it back
  /// at its place in z-order.
  private weak var contentNext: UIView?
  private var contentPlace = 0
  private var nodeObservations: [NSKeyValueObservation] = []

  private var lastIndex: Int?
  private var widestLeft: CGFloat = 0
  private var widestRight: CGFloat = 0
  private var lastRegion: CGSize = .zero
  private var lastInset: CGFloat = .nan
  private var lastSurfaceOverflow: CGFloat = 0
  private var props: [String: String] = [:]
  /// Set by `destroy`: nothing is emitted, adopted or laid out after it.
  private var destroyed = false

  init(id: String, events: ExactPackageEvents, log: @escaping (String) -> Void) {
    self.id = id
    self.events = events
    self.log = log
    super.init()
    host.eventDelegate = self
    contentWrapper.backgroundColor = .clear
    contentWrapper.addSubview(marker)
    surface.isUserInteractionEnabled = true
    host.mountChildComponentView(contentWrapper, atIndex: 0)
    root.onLayout = { [weak self] in self?.reconcile() }
  }

  var view: ExactPackagePlatformView {
    root
  }

  // MARK: Props (BottomSheetComponentView.updateProps)

  func setProps(_ next: [String: String]) throws {
    guard !destroyed else { return }
    let modal = Self.bool(next["modal"]) ?? false
    // Validated as `BottomSheet.tsx` validates before rendering
    // (`validateIndex`, `validateSettleDuration`, `normalizeDetents`), before
    // anything is applied: a refused update changes nothing, and Exact keeps
    // the last accepted props.
    let index: Int
    let settleDuration: Double?
    let detents: NormalizedDetents
    do {
      let raw = (Self.json(next["detents"]) as? [Any]) ?? []
      index = try Self.validateIndex(next["index"], detentCount: raw.count)
      settleDuration = try Self.validateSettleDuration(next["settleDuration"])
      detents = try Self.normalizeDetents(raw, topInset: next["topInset"], modal: modal)
    } catch {
      log("swm-bottom-sheet \(id): \(error)")
      throw error
    }

    let previous = props
    let first = previous.isEmpty
    let changed = { (key: String) in first || previous[key] != next[key] }
    props = next
    contentId = next["contentId"] ?? ""

    widestLeft = detents.widestLeft
    widestRight = detents.widestRight
    // Always pushed, as the Fabric glue pushes detents on every props update.
    host.setDetents(detents.dictionaries)

    if index != lastIndex {
      lastIndex = index
      host.setDetentIndex(index)
    }
    let animateIn = Self.bool(next["animateIn"]) ?? true
    if host.animateIn != animateIn {
      host.animateIn = animateIn
    }
    let animateContentHeight = Self.bool(next["animateContentHeight"]) ?? true
    if host.animateContentHeight != animateContentHeight {
      host.animateContentHeight = animateContentHeight
    }
    if host.modal != modal {
      host.modal = modal
    }

    let expand = Self.coordination(next["scrollExpand"], fallback: 2)
    if host.scrollExpandCoordination != expand {
      host.scrollExpandCoordination = expand
    }
    let collapse = Self.coordination(next["scrollCollapse"], fallback: 1)
    if host.scrollCollapseCoordination != collapse {
      host.scrollCollapseCoordination = collapse
    }

    let settle = settleDuration ?? 0
    if host.settleDuration != settle {
      host.settleDuration = settle
    }

    if changed("scrimColor") {
      host.scrimColor = Self.color(next["scrimColor"])
    }
    if changed("scrimLabel") {
      let label = next["scrimLabel"].flatMap { $0.isEmpty ? nil : Self.string($0) }
      host.scrimAccessibilityLabel = label ?? "Dismiss"
    }

    let radii = (Self.json(next["surfaceCornerRadii"]) as? [NSNumber]) ?? []
    host.setSurfaceCornerRadii(radii)
    host.setSurfaceCornerCurve(Self.string(next["surfaceCornerCurve"]) == "continuous" ? "continuous" : "circular")
    let shadows = ((Self.json(next["surfaceShadows"]) as? [[String: Any]]) ?? []).map { shadow -> NSDictionary in
      [
        "offsetX": shadow["offsetX"] as? NSNumber ?? 0,
        "offsetY": shadow["offsetY"] as? NSNumber ?? 0,
        "blurRadius": shadow["blurRadius"] as? NSNumber ?? 0,
        "spreadDistance": shadow["spreadDistance"] as? NSNumber ?? 0,
        "color": Self.color(shadow["color"] as? String) ?? UIColor.black,
        "opacity": shadow["opacity"] as? NSNumber ?? 1,
        // `boxShadow`'s rule, which the web's shadows follow too.
        "excludesInterior": true,
      ]
    }
    host.setSurfaceShadows(shadows)

    if changed("surfaceColor") {
      updateSurface(Self.color(next["surfaceColor"]))
    }

    syncGeometry()
    reconcile()
  }

  struct NormalizedDetents {
    var dictionaries: [NSDictionary]
    var widestLeft: CGFloat
    var widestRight: CGFloat
  }

  /// The detents as `normalizeDetents` (src/bottomSheetUtils.ts) hands them to
  /// the native view, from the tag's `SheetDetent` list, with its checks and
  /// error messages. A `value` is a string: `"content"`, a number of points or
  /// a percentage (the same unsigned `0%`–`100%` grammar).
  static func normalizeDetents(_ raw: [Any], topInset rawTopInset: String?, modal: Bool) throws
    -> NormalizedDetents
  {
    var topInset: Double?
    if let text = present(rawTopInset) {
      guard let value = number(text), isFiniteNonNegative(value) else {
        throw ExactPackageRefusal(
          "Invalid bottom sheet `topInset`: received \(text). Expected a finite, non-negative number."
        )
      }
      topInset = value
    }
    var detents: [[String: Any]] = []
    for (index, entry) in raw.enumerated() {
      let config = entry as? [String: Any] ?? ["value": entry]
      let text = (config["value"] as? String) ?? (config["value"] as? NSNumber)?.stringValue ?? "undefined"
      let kind: String
      let value: Double
      if text == "content" {
        kind = "content"
        value = 0
      } else if matches(text, pointsPattern) {
        // A number, as React Native gets one.
        guard let points = Double(text), isFiniteNonNegative(points) else {
          throw ExactPackageRefusal(
            "Invalid bottom sheet detent at index \(index): received \(text). Expected a finite, non-negative number."
          )
        }
        kind = "points"
        value = points
      } else {
        guard matches(text, percentagePattern), let percent = Double(text.dropLast()), percent <= 100 else {
          throw ExactPackageRefusal(
            "Invalid bottom sheet detent at index \(index): `\(text)` is not a valid percentage. Expected an unsigned integer or decimal from 0% through 100%, without whitespace."
          )
        }
        kind = "percentage"
        value = percent / 100
      }
      let closed = kind != "content" && value == 0
      let insets = present(config["insets"]) as? [String: Any]
      var side: [String: Double] = [:]
      for name in ["left", "right", "bottom"] {
        let raw = present(insets?[name]) ?? NSNumber(value: 0)
        guard let number = numeric(raw), isFiniteNonNegative(number) else {
          throw optionError("insets.\(name)", raw, index, "a finite, non-negative number")
        }
        side[name] = number
      }
      // Only a modal sheet has a scrim, so an inline sheet ignores the option
      // rather than validating it.
      let rawScrim = modal ? present(config["scrimOpacity"]) : nil
      let scrimOpacity: Double? = rawScrim.map(numeric) ?? (closed ? 0 : 1)
      guard let scrimOpacity, isFiniteNonNegative(scrimOpacity), scrimOpacity <= 1 else {
        throw optionError("scrimOpacity", rawScrim as Any, index, "a number from 0 through 1")
      }
      var detent: [String: Any] = [
        "value": value,
        "kind": kind,
        "programmatic": (config["programmatic"] as? Bool) ?? false,
        "widestCard": false,
        "scrimOpacity": scrimOpacity,
      ]
      if closed {
        detent.merge(
          ["usesStatusBarTopInset": false, "insetTop": 0.0, "insetLeft": 0.0, "insetRight": 0.0, "insetBottom": 0.0]
        ) { $1 }
      } else {
        detent.merge([
          "usesStatusBarTopInset": topInset == nil,
          "insetTop": topInset ?? 0,
          "insetLeft": side["left"]!,
          "insetRight": side["right"]!,
          "insetBottom": side["bottom"]!,
        ]) { $1 }
      }
      detents.append(detent)
    }
    // `findWidestDetent`: the open detent with the tightest side insets, the
    // last of equals.
    var widest: Int?
    for (i, d) in detents.enumerated() {
      let kind = d["kind"] as! String
      if kind != "content", d["value"] as! Double == 0 {
        continue
      }
      let gaps = (d["insetLeft"] as! Double) + (d["insetRight"] as! Double)
      if let w = widest, gaps > (detents[w]["insetLeft"] as! Double) + (detents[w]["insetRight"] as! Double) {
        continue
      }
      widest = i
    }
    var result = NormalizedDetents(dictionaries: [], widestLeft: 0, widestRight: 0)
    if let widest {
      detents[widest]["widestCard"] = true
      result.widestLeft = CGFloat(detents[widest]["insetLeft"] as! Double)
      result.widestRight = CGFloat(detents[widest]["insetRight"] as! Double)
    }
    result.dictionaries = detents.map { $0 as NSDictionary }
    return result
  }

  /// `validateIndex` (src/bottomSheetUtils.ts).
  static func validateIndex(_ raw: String?, detentCount: Int) throws -> Int {
    if detentCount == 0 {
      throw ExactPackageRefusal(
        "Invalid bottom sheet detents: received an empty array. Expected at least one detent."
      )
    }
    let text = present(raw) ?? "undefined"
    guard let value = number(text), value.isFinite, value.rounded() == value else {
      throw ExactPackageRefusal(
        "Invalid bottom sheet index: received \(text). Expected a finite integer from 0 through \(detentCount - 1)."
      )
    }
    guard value >= 0, value < Double(detentCount) else {
      throw ExactPackageRefusal(
        "Invalid bottom sheet index: received \(text). Expected a value from 0 through \(detentCount - 1)."
      )
    }
    return Int(value)
  }

  /// `validateSettleDuration` (src/bottomSheetUtils.ts); nil when unset.
  static func validateSettleDuration(_ raw: String?) throws -> Double? {
    guard let text = present(raw) else { return nil }
    guard let value = number(text), value.isFinite, value > 0 else {
      throw ExactPackageRefusal(
        "Invalid bottom sheet settleDuration: received \(text). Expected a finite number of seconds greater than 0."
      )
    }
    return value
  }

  private static let pointsPattern = "^[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?$"
  private static let percentagePattern = "^[0-9]+(?:\\.[0-9]+)?%$"

  private static func matches(_ text: String, _ pattern: String) -> Bool {
    text.range(of: pattern, options: .regularExpression) != nil
  }

  private static func isFiniteNonNegative(_ value: Double) -> Bool {
    value.isFinite && value >= 0
  }

  /// An option's text, nil when unset (absent or `null`).
  private static func present(_ text: String?) -> String? {
    guard let text, !text.isEmpty, text != "null" else { return nil }
    return text
  }

  /// A JSON value, nil when unset (`null`).
  private static func present(_ value: Any?) -> Any? {
    value is NSNull ? nil : value
  }

  /// A JSON number (not a boolean) as a double.
  private static func numeric(_ value: Any) -> Double? {
    guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
    return number.doubleValue
  }

  private static func optionError(_ option: String, _ value: Any, _ index: Int, _ expected: String)
    -> ExactPackageRefusal
  {
    let text = (value as? NSNumber)?.stringValue ?? String(describing: value)
    return ExactPackageRefusal(
      "Invalid bottom sheet detent at index \(index): `\(option)` received \(text). Expected \(expected)."
    )
  }

  private func updateSurface(_ color: UIColor?) {
    surface.backgroundColor = color
    if color != nil, !surfaceMounted {
      surfaceMounted = true
      host.mountSurfaceComponentView(surface, atIndex: 0)
    } else if color == nil, surfaceMounted {
      surfaceMounted = false
      host.unmountSurfaceComponentView(surface)
    }
    syncGeometry()
  }

  // MARK: Geometry (pushNativeGeometry + the content wrapper's Yoga layout)

  /// Lays the content wrapper and surface out as Yoga lays them out under the
  /// Fabric glue: the wrapper fills the content region (the sheet's height
  /// less `contentRegionInset`) between the widest card's side insets; the
  /// surface spans the sheet plus `surfaceOverflow`. A changed inset is then
  /// committed and applied at once, since this layout is synchronous.
  private func syncGeometry() {
    guard !destroyed else { return }
    let size = host.bounds.size
    guard size.width > 0, size.height > 0 else { return }
    let inset = host.contentRegionInset
    let width = max(0, size.width - widestLeft - widestRight)
    let region = CGSize(width: width, height: max(0, size.height - inset))
    place(contentWrapper, x: widestLeft, size: region)
    lastSurfaceOverflow = host.surfaceOverflow(holding: lastSurfaceOverflow)
    if surfaceMounted {
      place(surface, x: widestLeft, size: CGSize(width: width, height: size.height + lastSurfaceOverflow))
    }
    layoutMarker()
    if inset != lastInset || region != lastRegion {
      lastInset = inset
      lastRegion = region
      host.contentRegionStateDidCommit(inset)
      host.contentRegionLayoutDidApply()
      events.emit("regionchange", [["width": Double(region.width), "height": Double(region.height)]])
    }
  }

  /// Sets a view's layout through bounds and center, which the card's layer
  /// transform leaves defined (as Fabric does).
  private func place(_ view: UIView, x: CGFloat, size: CGSize) {
    let bounds = CGRect(origin: .zero, size: size)
    let center = CGPoint(x: x + size.width / 2, y: size.height / 2)
    if view.bounds != bounds {
      view.bounds = bounds
    }
    if view.center != center {
      view.center = center
    }
  }

  private func layoutMarker() {
    let bottom = contentNode.map { $0.superview === contentWrapper ? $0.frame.maxY : 0 } ?? 0
    let frame = CGRect(x: 0, y: bottom, width: contentWrapper.bounds.width, height: 0)
    if marker.frame != frame {
      marker.frame = frame
    }
  }

  // MARK: Content adoption

  /// Finds the content node and keeps it in the wrapper. Exact puts a node
  /// back into its parent when that parent's children change, so this runs on
  /// every props change, layout, window change and node frame change.
  private func reconcile() {
    guard !destroyed else { return }
    if let node = contentNode, node.superview == nil || node.accessibilityIdentifier != contentId {
      release(node)
    }
    if contentNode == nil, !contentId.isEmpty, let node = findContentNode() {
      contentNode = node
      nodeObservations = [
        node.layer.observe(\.bounds) { [weak self] _, _ in self?.nodeDidMove() },
        node.layer.observe(\.position) { [weak self] _, _ in self?.nodeDidMove() },
      ]
      log("swm-bottom-sheet \(id): adopted content \(contentId)")
    }
    guard let node = contentNode else { return }
    if node.superview !== contentWrapper {
      if let home = node.superview {
        remember(node, in: home)
      }
      contentWrapper.insertSubview(node, at: 0)
      nodeDidMove()
    }
  }

  private func nodeDidMove() {
    guard let node = contentNode else { return }
    if node.superview !== contentWrapper {
      // Taken back by Exact; adopt it again on the next turn, outside the
      // batch that moved it.
      DispatchQueue.main.async { [weak self] in self?.reconcile() }
      return
    }
    layoutMarker()
    // As after a Fabric mounting transaction: the content moved, so the card
    // transform (pinned to the content's center) and any held `content`
    // detent are brought up to date.
    host.contentRegionLayoutDidApply()
  }

  /// Where the node sits before it is adopted, to give it back there.
  private func remember(_ node: UIView, in home: UIView) {
    contentHome = home
    let place = home.subviews.firstIndex(of: node) ?? home.subviews.count
    contentPlace = place
    contentNext = place + 1 < home.subviews.count ? home.subviews[place + 1] : nil
  }

  /// Lets the adopted node go, back in its parent at its place if it is still
  /// in the sheet (a `contentId` change, destroy).
  private func release(_ node: UIView) {
    nodeObservations = []
    contentNode = nil
    if node.superview === contentWrapper, let home = contentHome {
      let place: Int
      if let next = contentNext, next.superview === home, let at = home.subviews.firstIndex(of: next) {
        place = at
      } else {
        place = min(contentPlace, home.subviews.count)
      }
      home.insertSubview(node, at: place)
    }
    contentHome = nil
    contentNext = nil
    log("swm-bottom-sheet \(id): released content \(node.accessibilityIdentifier ?? "")")
  }

  /// The view whose accessibility identifier (Exact's `testId`) is
  /// `contentId`, searched among the siblings of the tag's node and then of
  /// each ancestor, never inside this sheet.
  private func findContentNode() -> UIView? {
    var level: UIView? = root.superview
    while let current = level, let parent = current.superview {
      if let found = search(parent, skipping: current) {
        return found
      }
      level = parent
    }
    return nil
  }

  private func search(_ view: UIView, skipping: UIView) -> UIView? {
    for child in view.subviews where child !== skipping {
      if child.accessibilityIdentifier == contentId {
        return child
      }
    }
    for child in view.subviews where child !== skipping && !(child is ExactSheetRootView) {
      if let found = searchDeep(child, depth: 2) {
        return found
      }
    }
    return nil
  }

  private func searchDeep(_ view: UIView, depth: Int) -> UIView? {
    guard depth > 0 else { return nil }
    for child in view.subviews {
      if child.accessibilityIdentifier == contentId {
        return child
      }
      if child is ExactSheetRootView {
        continue
      }
      if let found = searchDeep(child, depth: depth - 1) {
        return found
      }
    }
    return nil
  }

  func destroy() {
    guard !destroyed else { return }
    destroyed = true
    // As `prepareForRecycle`: no event after this, and a running settle
    // (its `CADisplayLink`) stops. The node goes back first, since the reset
    // removes what is mounted in the card.
    host.eventDelegate = nil
    root.onLayout = {}
    if let node = contentNode {
      release(node)
    }
    nodeObservations = []
    host.resetSheetState()
  }

  // MARK: BottomSheetHostingViewDelegate (BottomSheetComponentView's)

  func bottomSheetHostingView(_: BottomSheetHostingView, didChangeIndex index: Int) {
    guard !destroyed else { return }
    lastIndex = index
    events.emit("indexchange", [index])
  }

  func bottomSheetHostingView(_: BottomSheetHostingView, didSettle index: Int) {
    guard !destroyed else { return }
    events.emit("settle", [index])
  }

  func bottomSheetHostingView(
    _: BottomSheetHostingView, didChangePosition position: CGFloat, index: CGFloat
  ) {
    guard !destroyed else { return }
    events.emit("positionchange", [["position": Double(position), "index": Double(index)]])
  }

  func bottomSheetHostingView(_: BottomSheetHostingView, didReportError message: String) {
    // The Fabric glue raises this as a fatal error; here it is logged.
    log("swm-bottom-sheet \(id): \(message)")
  }

  func bottomSheetHostingViewDidLayout(_: BottomSheetHostingView) {
    syncGeometry()
  }

  // MARK: Prop parsing (props arrive as strings; non-strings as JSON text)

  private static func json(_ text: String?) -> Any? {
    guard let text, let data = text.data(using: .utf8) else { return nil }
    let value = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
    return value is NSNull ? nil : value
  }

  private static func string(_ text: String?) -> String? {
    guard let text else { return nil }
    if let decoded = json(text) as? String {
      return decoded
    }
    return text == "null" ? nil : text
  }

  private static func number(_ text: String?) -> Double? {
    (json(text) as? NSNumber)?.doubleValue ?? text.flatMap(Double.init)
  }

  private static func bool(_ text: String?) -> Bool? {
    switch text {
    case "true": return true
    case "false": return false
    default: return (json(text) as? NSNumber)?.boolValue
    }
  }

  private static func coordination(_ text: String?, fallback: Int) -> Int {
    switch string(text) {
    case "none": return 0
    case "initial": return 1
    case "handoff": return 2
    default: return fallback
    }
  }

  /// `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`, `rgb()`, `rgba()` and a few names.
  static func color(_ raw: String?) -> UIColor? {
    guard let text = string(raw)?.trimmingCharacters(in: .whitespaces).lowercased(), !text.isEmpty else {
      return nil
    }
    let named: [String: UIColor] = [
      "transparent": .clear, "white": .white, "black": .black, "red": .red, "green": .green,
      "blue": .blue, "gray": .gray, "grey": .gray,
    ]
    if let color = named[text] {
      return color
    }
    if text.hasPrefix("#") {
      var digits = String(text.dropFirst())
      if digits.count == 3 || digits.count == 4 {
        digits = digits.map { "\($0)\($0)" }.joined()
      }
      guard digits.count == 6 || digits.count == 8, let v = UInt64(digits, radix: 16) else { return nil }
      let hasAlpha = digits.count == 8
      let c = { (shift: UInt64) in CGFloat((v >> shift) & 0xFF) / 255 }
      return hasAlpha
        ? UIColor(red: c(24), green: c(16), blue: c(8), alpha: c(0))
        : UIColor(red: c(16), green: c(8), blue: c(0), alpha: 1)
    }
    if text.hasPrefix("rgb"), let open = text.firstIndex(of: "("), let close = text.lastIndex(of: ")") {
      let parts = text[text.index(after: open) ..< close]
        .split(whereSeparator: { $0 == "," || $0 == " " || $0 == "/" })
        .compactMap { Double($0) }
      guard parts.count >= 3 else { return nil }
      return UIColor(
        red: parts[0] / 255, green: parts[1] / 255, blue: parts[2] / 255,
        alpha: parts.count > 3 ? parts[3] : 1
      )
    }
    return nil
  }
}
