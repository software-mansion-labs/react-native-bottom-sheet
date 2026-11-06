import UIKit

/// The space kept around the card at a detent.
struct DetentInsets: Equatable {
  /// Nil for the status bar's overlap with the host, which can change.
  var top: CGFloat?
  var left: CGFloat = 0
  var right: CGFloat = 0
  var bottom: CGFloat = 0
}

struct DetentSpec: Equatable {
  /// How far the card's top rests above the host's bottom: the card's height
  /// plus its bottom inset. Zero when closed, whatever the inset.
  let position: CGFloat
  let programmatic: Bool
  let insets: DetentInsets
}

enum DetentKind {
  case points
  case percentage
  case content
}

struct RawDetentSpec: Equatable {
  let value: CGFloat
  let kind: DetentKind
  let programmatic: Bool
  let insets: DetentInsets
  /// The widest open card, which the content is laid out against. Flagged in JS,
  /// which lays the content out.
  let widestCard: Bool
}

struct PendingSnapRequest {
  let index: Int
  let velocity: CGFloat
  let emitIndexChange: Bool
  let emitSettle: Bool
  let preserveScrimPin: Bool
}

enum PanCoordinationMode {
  case sheet
  case scrollView
}

enum ScrollableNegotiationLevel: Int {
  case none = 0
  case initial = 1
  case handoff = 2

  init(clamping value: Int) {
    self = ScrollableNegotiationLevel(rawValue: min(max(value, 0), 2)) ?? .none
  }
}
