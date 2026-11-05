import UIKit
import UIKit.UIGestureRecognizerSubclass

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
final class SheetPanGestureRecognizer: UIPanGestureRecognizer {
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
