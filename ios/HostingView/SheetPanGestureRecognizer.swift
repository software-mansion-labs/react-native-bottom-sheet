import UIKit
import UIKit.UIGestureRecognizerSubclass

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
final class SheetPanGestureRecognizer: UIPanGestureRecognizer {
  /// The vertical directions the sheet can use at its detent, as the touch lands.
  struct UsableDirections {
    var up: Bool
    var down: Bool
  }

  /// Vertical travel that decides a held start's direction, matching the
  /// threshold UIKit's pan recognizers apply to total travel.
  private static let verticalSlop: CGFloat = 10

  /// Whether the start is held: a touch inside a vertical scrollable the sheet
  /// coordinates with.
  private(set) var isHoldingStart = false
  /// The contact the hold follows. A pull toward the edge of the screen can land
  /// a second contact and end the first; the gesture continues on the one left,
  /// as the scrollable's own pan does.
  private var heldTouch: UITouch?
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
    heldTouch = touch
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

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
    if isHoldingStart, state == .possible {
      guard let heldTouch, touches.contains(heldTouch), shouldDeliver(moveAt: holdY(of: heldTouch)) else {
        return
      }
    }
    super.touchesMoved(touches, with: event)
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
    holdNextTouch(after: touches, with: event)
    super.touchesEnded(touches, with: event)
  }

  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
    holdNextTouch(after: touches, with: event)
    super.touchesCancelled(touches, with: event)
  }

  /// Follows a remaining contact when the held one lifts. Its position is not
  /// travel, so the far point moves to it and the decided direction stands.
  private func holdNextTouch(after touches: Set<UITouch>, with event: UIEvent) {
    guard let heldTouch, touches.contains(heldTouch) else { return }
    let remaining = event.touches(for: self)?.first { !touches.contains($0) }
    self.heldTouch = remaining
    if let remaining {
      farY = holdY(of: remaining)
    }
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
    heldTouch = nil
    usableDirections = UsableDirections(up: false, down: false)
    handoffDirections = UsableDirections(up: false, down: false)
    farY = 0
    decidedVerticalDirection = 0
    super.reset()
  }
}
