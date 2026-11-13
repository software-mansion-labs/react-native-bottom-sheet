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
// See packages/exact-bottom-sheet/README.md ("How content is adopted") for the rules an
// author follows so Exact never takes it back.
import ExactPackage
import Foundation
import UIKit

public final class ExactBottomSheetModule: ExactPackage {
  private let context: ExactPackageContext
  /// What each sheet discloses to Exact's agent, by the sheet's id (see
  /// `ExactBottomSheetView.stateKey`): `state.extensions`, under the agent
  /// only, so a test can read a sheet without the app keeping its own copy.
  private var sheets: [String: [String: Any]] = [:]

  public init(context: ExactPackageContext) {
    self.context = context
  }

  /// Replaces one sheet's entry (nil removes it) and publishes them all, as
  /// `context.state` replaces a field whole.
  func publish(_ key: String, _ entry: [String: Any]?) {
    guard context.agent else { return }
    sheets[key] = entry
    context.state(["sheets": sheets])
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
    let sheet = ExactBottomSheetView(
      id: id, events: events, log: { [context] in context.log($0) },
      publish: { [weak self] key, entry in self?.publish(key, entry) }
    )
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
/// sits at the adopted node's own end marker (see `endMarkerId`) when the node
/// has one, else at the node's bottom.
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

/// A view that holds adopted nodes and says when one leaves it: Exact removes
/// a node it unmounts (a lazy sheet's content and surface) without changing
/// its frame, so no frame observation sees it go.
final class AdoptionContainer: UIView {
  var subviewRemoved: () -> Void = {}

  override func willRemoveSubview(_ subview: UIView) {
    super.willRemoveSubview(subview)
    subviewRemoved()
  }
}

/// A Contract node, found by its `testId`, moved into a view of the sheet and
/// given back where it was. Exact puts a node back into its parent when that
/// parent's children change, so `moved` is told whenever the node's frame or
/// parent changes, for the owner to adopt it again on the next turn.
final class NodeAdoption {
  var id = ""
  private(set) weak var node: UIView?
  private weak var home: UIView?
  private weak var next: UIView?
  private var place = 0
  private var observations: [NSKeyValueObservation] = []
  private let moved: () -> Void

  init(moved: @escaping () -> Void) {
    self.moved = moved
  }

  /// Keeps the node with `id` in `container` (found with `find`), letting go
  /// of one that left the tree or no longer carries the id. True when the
  /// node changed or was put back.
  @discardableResult
  func reconcile(into container: UIView, at index: Int = 0, find: (String) -> UIView?) -> Bool {
    var changed = false
    if let node, node.superview == nil || node.accessibilityIdentifier != id {
      release()
      changed = true
    }
    if node == nil, !id.isEmpty, let found = find(id) {
      node = found
      observations = [
        found.layer.observe(\.bounds) { [weak self] _, _ in self?.moved() },
        found.layer.observe(\.position) { [weak self] _, _ in self?.moved() },
      ]
      changed = true
    }
    guard let node, node.superview !== container else { return changed }
    if let parent = node.superview {
      home = parent
      place = parent.subviews.firstIndex(of: node) ?? parent.subviews.count
      next = place + 1 < parent.subviews.count ? parent.subviews[place + 1] : nil
    }
    container.insertSubview(node, at: min(index, container.subviews.count))
    return true
  }

  /// Whether the node is in `container`.
  func isAdopted(in container: UIView) -> Bool {
    node?.superview === container
  }

  /// Gives the node back to its parent, at its place, if it is still adopted.
  func release(from container: UIView? = nil) {
    observations = []
    guard let node else { return }
    self.node = nil
    if container == nil || node.superview === container, node.superview != nil, let home {
      let at: Int
      if let next, next.superview === home, let i = home.subviews.firstIndex(of: next) {
        at = i
      } else {
        at = min(place, home.subviews.count)
      }
      home.insertSubview(node, at: at)
    }
    home = nil
    next = nil
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
  private let publish: (String, [String: Any]?) -> Void
  /// The entry last published, and the sheet's state it holds.
  private var publishedKey: String?
  private var publishedAdoption: [Bool] = []
  /// Whether the content and the surface nodes are adopted.
  private var adoption: [Bool] {
    [contentNode?.superview === contentWrapper, surfaceNode.isAdopted(in: surface)]
  }
  private var settledIndex: Int?
  private var lastPosition: Double = 0

  /// Plays the content wrapper's part (`BottomSheetContentWrapperView`): sized
  /// to the content region, it holds the adopted node and the marker.
  private let contentWrapper = AdoptionContainer()
  private let marker = ExactContentHeightMarker(frame: .zero)
  /// Plays the surface's part (`BottomSheetSurfaceView`): it holds the
  /// adopted surface node (`surfaceId`, the `BottomSheet` component's
  /// `${id}-surface`, drawn by Contract), or, with the raw tag, is filled with
  /// `surfaceColor`.
  private let surface = AdoptionContainer()
  private var surfaceMounted = false
  private var surfaceColor: UIColor?
  private lazy var surfaceNode = NodeAdoption { [weak self] in self?.surfaceNodeDidMove() }
  private var lastSurfaceHeight: CGFloat = 0

  private var contentId = ""
  private weak var contentNode: UIView?
  /// Where the node was before it was adopted, to give it back on destroy.
  private weak var contentHome: UIView?
  /// The node's next sibling and index in `contentHome` then, to put it back
  /// at its place in z-order.
  private weak var contentNext: UIView?
  private var contentPlace = 0
  private var nodeObservations: [NSKeyValueObservation] = []
  /// The adopted node's end marker, a descendant whose testId is
  /// `endMarkerId`: the `BottomSheet` component's zero-height view after its
  /// children, as the content-height marker after `children` in
  /// `BottomSheet.tsx`. Its top is the content's height.
  private weak var endMarker: UIView?
  private var endMarkerObservations: [NSKeyValueObservation] = []

  private var lastIndex: Int?
  private var widestLeft: CGFloat = 0
  private var widestRight: CGFloat = 0
  private var lastRegion: CGSize = .zero
  /// Which detents are closed, and whether the sheet rests at one: the
  /// `BottomSheet` component's `lazy` keeps the content mounted until it does,
  /// as `BottomSheet.tsx`'s `isSheetActive` (an open index clears it at once,
  /// a settle at a closed detent sets it).
  private var closedDetents: [Bool] = []
  private var restingClosed: Bool?
  private var lastInset: CGFloat = .nan
  private var lastSurfaceOverflow: CGFloat = 0
  private var props: [String: String] = [:]
  /// Whether `positionchange` is sent: Exact can't tell a view which events
  /// are bound, and the event runs every frame the sheet moves, so it goes
  /// out only when the app asks (`trackPosition`, which `TrackedBottomSheet`
  /// sets).
  private var tracksPosition = false
  /// Whether the sheet is in Exact's top layer (the `BottomSheet` component's
  /// `nativeOverlay`), and the ancestors marked `accessibilityViewIsModal`
  /// while its scrim is shown, each with the value it had.
  private var nativeOverlay = false
  private var modalAncestors: [(view: UIView, wasModal: Bool)] = []
  /// Set by `destroy`: nothing is emitted, adopted or laid out after it.
  private var destroyed = false

  init(
    id: String, events: ExactPackageEvents, log: @escaping (String) -> Void,
    publish: @escaping (String, [String: Any]?) -> Void
  ) {
    self.id = id
    self.events = events
    self.log = log
    self.publish = publish
    super.init()
    host.eventDelegate = self
    contentWrapper.backgroundColor = .clear
    contentWrapper.addSubview(marker)
    surface.isUserInteractionEnabled = true
    host.mountChildComponentView(contentWrapper, atIndex: 0)
    root.onLayout = { [weak self] in
      self?.reconcile()
      self?.updateOverlayAccessibility()
    }
    // A node leaving (Exact unmounting or taking it back) is reconciled on
    // the next turn, outside the batch, which republishes the agent's state.
    let removed = { [weak self] in
      DispatchQueue.main.async { self?.reconcile() }
    }
    contentWrapper.subviewRemoved = removed
    surface.subviewRemoved = removed
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
    tracksPosition = Self.bool(next["trackPosition"]) ?? false
    nativeOverlay = Self.bool(next["nativeOverlay"]) ?? false

    widestLeft = detents.widestLeft
    widestRight = detents.widestRight
    // Always pushed, as the Fabric glue pushes detents on every props update.
    host.setDetents(detents.dictionaries)

    closedDetents = detents.dictionaries.map {
      ($0["kind"] as? String) != "content" && (($0["value"] as? NSNumber)?.doubleValue ?? 0) == 0
    }
    if index != lastIndex {
      lastIndex = index
      if !first, !closedDetents[index], contentNode?.superview == nil, !contentId.isEmpty {
        // A lazy sheet's content (the `BottomSheet` component's `lazy`) is
        // created in the same Exact batch as the open index, after these
        // props. Adopt it once the batch is applied, then open, so the sheet
        // opens to the content's height as `BottomSheet.tsx`'s does (its
        // content mounts in the same commit as the index).
        DispatchQueue.main.async { [weak self] in
          guard let self, !self.destroyed, self.lastIndex == index else { return }
          self.reconcile()
          self.host.setDetentIndex(index)
        }
      } else {
        host.setDetentIndex(index)
      }
    }
    if restingClosed == nil || !closedDetents[index] {
      setRestingClosed(closedDetents[index])
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
      surfaceColor = Self.color(next["surfaceColor"])
    }
    surfaceNode.id = Self.string(next["surfaceId"]) ?? ""
    updateSurface()

    syncGeometry()
    reconcile()
    updateOverlayAccessibility()
    publishState()
  }

  /// The sheet's key in the agent's state: its id in the `BottomSheet`
  /// component (`contentId` without `-content`), else the view's.
  private var stateKey: String {
    contentId.hasSuffix("-content") ? String(contentId.dropLast("-content".count)) : (contentId.isEmpty ? id : contentId)
  }

  /// Discloses the sheet to Exact's agent (`state.extensions`): its target
  /// and settled index, whether it rests closed, its position when it last
  /// settled, and whether its content and surface nodes are adopted. Not per
  /// frame: the position is the one at rest.
  private func publishState() {
    guard !destroyed else { return }
    let key = stateKey
    if let old = publishedKey, old != key {
      publish(old, nil)
    }
    publishedKey = key
    publishedAdoption = adoption
    publish(key, [
      "index": lastIndex.map { $0 as Any } ?? NSNull(),
      "settled": settledIndex.map { $0 as Any } ?? NSNull(),
      "restingClosed": restingClosed ?? true,
      "position": lastPosition,
      "content": contentNode?.superview === contentWrapper,
      "surface": surfaceNode.isAdopted(in: surface),
      "modal": !modalAncestors.isEmpty,
    ])
  }

  /// `BottomSheetComponentView.mm`'s `updateOverlayAccessibilityState`: while
  /// an overlay sheet's scrim is shown, VoiceOver keeps to the sheet. React
  /// Native's overlay is a direct child of the window, so marking it modal
  /// hides the rest of the app (UIKit hides a modal view's siblings). Here
  /// the sheet sits in Exact's popover layer, whose node the component marks
  /// `aria-modal` (Exact moves VoiceOver into it), but that hides only the
  /// layer's siblings, other popovers; so every ancestor up to the window is
  /// marked too, which leaves out everything off the sheet's own branch, as
  /// React Native's does. Each keeps its own value to go back to.
  private func updateOverlayAccessibility() {
    let active = !destroyed && nativeOverlay && host.isModalAccessibilityActive && root.window != nil
    var chain: [UIView] = []
    if active {
      var at = root.superview
      while let view = at, !(view is UIWindow) {
        chain.append(view)
        at = view.superview
      }
    }
    if chain.count == modalAncestors.count, zip(chain, modalAncestors).allSatisfy({ $0 === $1.view }) {
      return
    }
    for (view, wasModal) in modalAncestors.reversed() where view.accessibilityViewIsModal != wasModal {
      view.accessibilityViewIsModal = wasModal
    }
    modalAncestors = chain.map { ($0, $0.accessibilityViewIsModal) }
    for view in chain where !view.accessibilityViewIsModal {
      view.accessibilityViewIsModal = true
    }
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

  /// Mounts the surface while it has something to show: the adopted node, or
  /// the raw tag's `surfaceColor` (unused while a node is adopted).
  private func updateSurface() {
    if !destroyed {
      surfaceNode.reconcile(into: surface, find: findNode)
    }
    let adopted = surfaceNode.isAdopted(in: surface)
    surface.backgroundColor = adopted ? nil : surfaceColor
    let shows = adopted || surfaceColor != nil
    if shows, !surfaceMounted {
      surfaceMounted = true
      host.mountSurfaceComponentView(surface, atIndex: 0)
    } else if !shows, surfaceMounted {
      surfaceMounted = false
      host.unmountSurfaceComponentView(surface)
    }
  }

  private func surfaceNodeDidMove() {
    guard !destroyed, !surfaceNode.isAdopted(in: surface) else { return }
    // Taken back by Exact (or gone); adopt it again outside the batch.
    DispatchQueue.main.async { [weak self] in
      guard let self, !self.destroyed else { return }
      self.updateSurface()
      self.syncGeometry()
      self.publishState()
    }
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
    let surfaceHeight = size.height + lastSurfaceOverflow
    if inset != lastInset || region != lastRegion || surfaceHeight != lastSurfaceHeight {
      lastInset = inset
      lastRegion = region
      lastSurfaceHeight = surfaceHeight
      host.contentRegionStateDidCommit(inset)
      host.contentRegionLayoutDidApply()
      events.emit("regionchange", [["width": Double(region.width), "height": Double(region.height)]])
      emitChange()
    }
  }

  /// The built-in `change` event, "<width>,<height>,<resting closed: 0|1>,
  /// <surface height>", which a child component can handle with its own
  /// action (a typed event must name a root action): the `BottomSheet`
  /// component lays its content node out with the region, its surface node
  /// with the region's width and the surface height (the sheet plus
  /// `surfaceOverflow`), and unmounts a `lazy` sheet once it rests closed.
  private func emitChange() {
    events.change(
      "\(Double(lastRegion.width)),\(Double(lastRegion.height)),\(restingClosed == true ? 1 : 0),\(Double(lastSurfaceHeight))"
    )
  }

  private func setRestingClosed(_ closed: Bool) {
    guard restingClosed != closed else { return }
    restingClosed = closed
    emitChange()
    publishState()
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
    var bottom: CGFloat = 0
    if let node = contentNode, node.superview === contentWrapper {
      bottom = node.frame.maxY
      if let end = endMarker, end.isDescendant(of: node) {
        // In the node's own coordinates, which the card's scale (applied
        // above the wrapper) leaves unscaled.
        bottom = node.frame.minY + node.convert(end.bounds.origin, from: end).y
      }
    }
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
    updateSurface()
    // Republished when what is adopted differs from what was published:
    // a node can be gone before this runs, so "before" isn't this call's.
    defer {
      if publishedAdoption != adoption {
        publishState()
      }
    }
    if let node = contentNode, node.superview == nil || node.accessibilityIdentifier != contentId {
      release(node)
    }
    if contentNode == nil, !contentId.isEmpty, let node = findNode(contentId) {
      contentNode = node
      nodeObservations = [
        node.layer.observe(\.bounds) { [weak self] _, _ in self?.nodeDidMove() },
        node.layer.observe(\.position) { [weak self] _, _ in self?.nodeDidMove() },
      ]
      log("swm-bottom-sheet \(id): adopted content \(contentId)")
    }
    guard let node = contentNode else { return }
    let marked = refreshEndMarker(in: node)
    if node.superview !== contentWrapper {
      if let home = node.superview {
        remember(node, in: home)
      }
      contentWrapper.insertSubview(node, at: 0)
      nodeDidMove()
    } else if marked {
      nodeDidMove()
    }
  }

  /// The testId of the adopted node's end marker: `contentId` without a
  /// trailing `-content`, then `-end` (the component's `${id}-content` holds
  /// `${id}-end`).
  private var endMarkerId: String {
    let base = contentId.hasSuffix("-content") ? String(contentId.dropLast("-content".count)) : contentId
    return "\(base)-end"
  }

  /// Finds the node's end marker when it has none or lost it, and observes
  /// its frame, so a `content` detent follows the children growing and
  /// shrinking. True when the marker changed.
  private func refreshEndMarker(in node: UIView) -> Bool {
    if let end = endMarker, end.isDescendant(of: node), end.accessibilityIdentifier == endMarkerId {
      return false
    }
    let found = Self.descendant(of: node, id: endMarkerId, depth: 3)
    if found === endMarker, found == nil {
      return false
    }
    endMarker = found
    endMarkerObservations = found.map { end in
      [
        end.layer.observe(\.bounds) { [weak self] _, _ in self?.nodeDidMove() },
        end.layer.observe(\.position) { [weak self] _, _ in self?.nodeDidMove() },
      ]
    } ?? []
    return true
  }

  /// The first view whose accessibility identifier is `id`, breadth first,
  /// at most `depth` levels under `view` (Exact may hold a node's children
  /// in a container view).
  private static func descendant(of view: UIView, id: String, depth: Int) -> UIView? {
    var level = view.subviews
    for _ in 0 ..< depth {
      if let found = level.first(where: { $0.accessibilityIdentifier == id }) {
        return found
      }
      level = level.flatMap(\.subviews)
    }
    return nil
  }

  private func nodeDidMove() {
    guard let node = contentNode else { return }
    if node.superview !== contentWrapper {
      // Taken back by Exact; adopt it again on the next turn, outside the
      // batch that moved it.
      DispatchQueue.main.async { [weak self] in self?.reconcile() }
      return
    }
    // The end marker may be patched in after the node was adopted.
    _ = refreshEndMarker(in: node)
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
    endMarkerObservations = []
    endMarker = nil
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

  /// The view whose accessibility identifier (Exact's `testId`) is `id`
  /// (the content's or the surface's), searched among the siblings of the
  /// tag's node and then of each ancestor, never inside this sheet.
  private func findNode(_ id: String) -> UIView? {
    var level: UIView? = root.superview
    while let current = level, let parent = current.superview {
      if let found = search(parent, skipping: current, id: id) {
        return found
      }
      level = parent
    }
    return nil
  }

  private func search(_ view: UIView, skipping: UIView, id: String) -> UIView? {
    for child in view.subviews where child !== skipping {
      if child.accessibilityIdentifier == id {
        return child
      }
    }
    for child in view.subviews where child !== skipping && !(child is ExactSheetRootView) {
      if let found = searchDeep(child, depth: 2, id: id) {
        return found
      }
    }
    return nil
  }

  private func searchDeep(_ view: UIView, depth: Int, id: String) -> UIView? {
    guard depth > 0 else { return nil }
    for child in view.subviews {
      if child.accessibilityIdentifier == id {
        return child
      }
      if child is ExactSheetRootView {
        continue
      }
      if let found = searchDeep(child, depth: depth - 1, id: id) {
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
    surfaceNode.release(from: surface)
    nodeObservations = []
    endMarkerObservations = []
    updateOverlayAccessibility()
    if let key = publishedKey {
      publish(key, nil)
    }
    host.resetSheetState()
  }

  // MARK: BottomSheetHostingViewDelegate (BottomSheetComponentView's)

  func bottomSheetHostingView(_: BottomSheetHostingView, didChangeIndex index: Int) {
    guard !destroyed else { return }
    lastIndex = index
    events.emit("indexchange", [index])
    publishState()
  }

  func bottomSheetHostingView(_: BottomSheetHostingView, didSettle index: Int) {
    guard !destroyed else { return }
    events.emit("settle", [index])
    settledIndex = index
    if index < closedDetents.count {
      setRestingClosed(closedDetents[index])
    }
    updateOverlayAccessibility()
    publishState()
  }

  func bottomSheetHostingView(
    _: BottomSheetHostingView, didChangePosition position: CGFloat, index: CGFloat
  ) {
    guard !destroyed else { return }
    lastPosition = Double(position)
    // As the Fabric glue, on every move: the scrim shows and hides with it.
    updateOverlayAccessibility()
    guard tracksPosition else { return }
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

  /// `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`, `rgb()`, `rgba()`, a few names,
  /// and CSS `light-dark(light, dark)` (Exact's way to write a colour for both
  /// schemes): a dynamic colour, which the hosting view re-resolves when the
  /// appearance changes, as React Native's `DynamicColorIOS`.
  static func color(_ raw: String?) -> UIColor? {
    guard let text = string(raw)?.trimmingCharacters(in: .whitespaces).lowercased(), !text.isEmpty else {
      return nil
    }
    if text.hasPrefix("light-dark("), text.hasSuffix(")") {
      let inner = text.dropFirst("light-dark(".count).dropLast()
      // The comma between the two colours, outside any `rgb(…)`.
      var depth = 0
      var split: String.Index?
      for i in inner.indices {
        switch inner[i] {
        case "(": depth += 1
        case ")": depth -= 1
        case "," where depth == 0: split = i
        default: break
        }
        if split != nil { break }
      }
      guard let split, let light = color(String(inner[..<split])),
            let dark = color(String(inner[inner.index(after: split)...]))
      else { return nil }
      return UIColor { $0.userInterfaceStyle == .dark ? dark : light }
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
