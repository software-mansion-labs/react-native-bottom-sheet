import UIKit

/// How much narrower a fully pushed-back card is drawn, across both sides.
/// Matches `STACK_WIDTH_INSET` on Android and web.
private let stackWidthInset: CGFloat = 32
/// How far a pushed-back card's top edge shows above the sheet covering it.
/// Matches `STACK_PEEK` on Android and web.
let stackPeek: CGFloat = 10

/// How dark a covered card is dimmed, in black, under one stacked sheet and
/// under two or more, in light and dark appearance: what native iOS sheets draw
/// over a covered sheet. Matches `stackDimming` on Android and web.
private func stackDimming(dark: Bool) -> (single: CGFloat, deep: CGFloat) {
  dark ? (0.29, 0.38) : (0.12, 0.16)
}

/// How far a sheet is pushed back for `progress`, the covering sheet's linear
/// progress towards its tallest detent: the curve measured on native iOS
/// sheets, which push back quickly at first and ease into the end.
private func easedStackProgress(_ progress: CGFloat) -> CGFloat {
  1.38 * progress - 0.38 * progress * progress
}

// MARK: - Stacking

/// A `stacked` modal sheet pushes back the open modal sheets beneath it, as
/// native iOS sheets do: each is scaled down about its top edge and shifted up,
/// so the edge peeks out above the sheet covering it. A sheet is pushed back
/// as far as the `stacked` sheet above it that covers it most, whatever sits
/// between them.
///
/// The sheet beneath never moves on its own clock. Its transform is a function
/// of the positions of the sheets above, its candidates: set directly as they
/// are dragged, and animated along their springs as they settle, so the sheets
/// stay together frame for frame on the render server.
///
/// Dimming follows native sheets too: a covered card is dimmed on its own, and
/// a stacked sheet leaves the background to the scrim of the sheet beneath it,
/// so the background is dimmed once however many sheets are open.
extension BottomSheetHostingView {
  /// Every hosting view in a window. Few enough that each update walks them all.
  private static let stackSheets = NSHashTable<BottomSheetHostingView>.weakObjects()
  /// Those of them that are `stacked`. While there is none, a moving sheet has
  /// nothing to update.
  private static let stackedSheets = NSHashTable<BottomSheetHostingView>.weakObjects()

  private static let stackAnimationKey = "bottomSheetStack"
  /// Samples per second of the stack animation, as the settle animation has.
  private static let stackSampleRate: CFTimeInterval = 120

  func updateStackRegistration() {
    if window != nil {
      Self.stackSheets.add(self)
    } else {
      Self.stackSheets.remove(self)
    }
    if window != nil, stacked {
      Self.stackedSheets.add(self)
    } else {
      Self.stackedSheets.remove(self)
    }
    if window == nil {
      stackAbove = []
    }
    Self.applyStacks()
  }

  /// Brings the stack up to date after a sheet moved. Free while no sheet is
  /// stacked.
  static func updateStacks() {
    guard stackedSheets.anyObject != nil else { return }
    applyStacks()
  }

  /// Finds the stacked sheets above every sheet again and brings its transform
  /// and dimming up to date. Also run when the last stacked sheet goes, to put
  /// the others back.
  static func applyStacks() {
    let sheets = stackSheets.allObjects
    let participants = sheets.filter(\.isStackParticipant)
    for sheet in sheets {
      sheet.stackAbove = sheet.isStackParticipant ? sheet.stackedSheets(among: participants) : []
      // A sheet that is closing is not pushed: the push reads where it rests.
      sheet.followStack(sheet.restingPosition > 0 ? sheet.stackAbove : [])
    }
    applyStackDimming(to: sheets)
  }

  /// Brings the dimming up to date on a frame a sheet moved in, as a scrim
  /// follows its sheet. Free while no sheet is stacked.
  static func updateStackDimming() {
    guard stackedSheets.anyObject != nil else { return }
    applyStackDimming(to: stackSheets.allObjects)
  }

  /// Dims each card under the stacked sheets above it, and fades out a stacked
  /// sheet's scrim as far as the sheets beneath it are open, as they already
  /// dim the background. Both follow how open the sheets are, so they fade in
  /// and out with them.
  private static func applyStackDimming(to sheets: [BottomSheetHostingView]) {
    var openBeneath: [ObjectIdentifier: CGFloat] = [:]
    for sheet in sheets {
      let open = sheet.openProgress
      for above in sheet.stackAbove {
        openBeneath[ObjectIdentifier(above), default: 0] = max(openBeneath[ObjectIdentifier(above)] ?? 0, open)
      }
      let covering = sheet.stackAbove.map(\.openProgress).sorted(by: >)
      let dimming = stackDimming(dark: sheet.traitCollection.userInterfaceStyle == .dark)
      sheet.setStackDimming(
        dimming.single * (covering.first ?? 0)
          + (dimming.deep - dimming.single) * (covering.dropFirst().first ?? 0)
      )
    }
    for sheet in sheets {
      sheet.scrimPaintView.alpha = 1 - (openBeneath[ObjectIdentifier(sheet)] ?? 0)
    }
  }

  private var isStackParticipant: Bool {
    modal && window != nil && hasLaidOut && !detentSpecs.isEmpty
  }

  /// Where the sheet sits in its window: overlays above portal sheets, then by
  /// when each last opened.
  private var stackRank: (Int, Int) {
    (nativeOverlay ? 1 : 0, openOrder)
  }

  /// Where the card's top rests once the sheet settles. The push reads this
  /// rather than the moving position, so two sheets never chase each other.
  private var restingPosition: CGFloat {
    detent(at: targetIndex).position
  }

  /// How open the sheet is, from closed (0) to any open detent (1), and in
  /// between as it moves: how far it dims what it covers.
  private var openProgress: CGFloat {
    interpolate(forPosition: currentSheetHeight, values: detentSpecs.map { $0.position > 0 ? 1 : 0 })
  }

  /// Where the sheet's model puts its card's top: the settle target while a
  /// spring runs.
  private var modelPosition: CGFloat {
    sheetContainerHeight - sheetContainer.transform.ty
  }

  /// The sheets that push this one back and dim it: every `stacked` one above
  /// it. A sheet that is closed does neither, and one that is closing fades out
  /// as it goes, so none needs passing over.
  private func stackedSheets(among sheets: [BottomSheetHostingView]) -> [BottomSheetHostingView] {
    let frame = convert(bounds, to: nil)
    return sheets.filter { sheet in
      guard sheet !== self, sheet.stacked, sheet.window === window, sheet.stackRank > stackRank
      else { return false }
      // Only sheets that fill the same area stack; positions compare directly.
      let other = sheet.convert(sheet.bounds, to: nil)
      return abs(other.minX - frame.minX) < 0.5 && abs(other.maxX - frame.maxX) < 0.5
        && abs(other.minY - frame.minY) < 0.5 && abs(other.maxY - frame.maxY) < 0.5
    }
  }

  /// Sets the model transform for where the candidates rest, and animates it
  /// along their springs on the render server while any of them settles.
  private func followStack(_ candidates: [BottomSheetHostingView]) {
    let pushes = candidates.map(stackPush(from:))
    let transform = stackTransform(pushes, at: candidates.map(\.modelPosition))
    if stackView.transform != transform {
      stackView.transform = transform
    }

    // A new settle starts its own spring; the curves of those still running
    // are analytic, so rebuilding mid-flight picks them up seamlessly.
    let springs = candidates.map(\.activeSpring)
    let starts = springs.compactMap { $0?.startTime }
    guard starts != stackAnimationSprings else { return }
    stackAnimationSprings = starts
    stackView.layer.removeAnimation(forKey: Self.stackAnimationKey)
    guard let end = springs.compactMap({ $0 }).map({ $0.startTime + $0.duration }).max() else { return }
    let now = CACurrentMediaTime()
    let duration = end - now
    guard duration > 0 else { return }
    let sampleCount = max(Int((duration * Self.stackSampleRate).rounded()), 1)
    let values = (0 ... sampleCount).map { i in
      let time = now + duration * CFTimeInterval(i) / CFTimeInterval(sampleCount)
      let positions = zip(candidates, springs).map { candidate, spring in
        spring.map { candidate.sheetContainerHeight - $0.value(at: time) } ?? candidate.modelPosition
      }
      return NSValue(caTransform3D: CATransform3DMakeAffineTransform(stackTransform(pushes, at: positions)))
    }
    let animation = CAKeyframeAnimation(keyPath: "transform")
    animation.values = values
    animation.keyTimes = (0 ... sampleCount).map {
      NSNumber(value: Double($0) / Double(sampleCount))
    }
    animation.duration = duration
    animation.calculationMode = .linear
    animation.beginTime = stackView.layer.convertTime(now, from: nil)
    animation.isRemovedOnCompletion = false
    animation.fillMode = .forwards
    stackView.layer.add(animation, forKey: Self.stackAnimationKey)
  }

  /// How far a candidate pushes this sheet back at each of its detents, to be
  /// interpolated by its position like a scrim opacity. Only its tallest detent
  /// can cover this sheet's top edge, as only a native sheet's large detent
  /// pushes back the one beneath.
  private struct StackPush {
    let candidate: BottomSheetHostingView
    let progresses: [CGFloat]
    let shifts: [CGFloat]
  }

  private func stackPush(from candidate: BottomSheetHostingView) -> StackPush {
    // How far each of the candidate's detents reaches into the strip this
    // sheet's edge peeks out of. Negative leaves the edge uncovered.
    let tallest = candidate.detentSpecs.map(\.position).max() ?? 0
    let reaches = candidate.detentSpecs.map {
      $0.position > 0 && $0.position == tallest ? $0.position - (restingPosition - stackPeek) : -1
    }
    return StackPush(
      candidate: candidate,
      progresses: reaches.map { $0 >= 0 ? 1 : 0 },
      shifts: reaches.map { min(max($0, 0), stackPeek) }
    )
  }

  /// This sheet's transform with each candidate at the matching position. The
  /// candidate that covers most wins, in progress and in shift alike.
  private func stackTransform(_ pushes: [StackPush], at positions: [CGFloat]) -> CGAffineTransform {
    var progress: CGFloat = 0
    var shift: CGFloat = 0
    for (push, position) in zip(pushes, positions) {
      progress = max(progress, push.candidate.interpolate(forPosition: position, values: push.progresses))
      shift = max(shift, push.candidate.interpolate(forPosition: position, values: push.shifts))
    }
    progress = easedStackProgress(progress)
    guard progress > 0 || shift > 0 else { return .identity }

    let resting = restingPosition
    let card = cardRect(forPosition: resting)
    let pivot = CGPoint(x: card.midX, y: bounds.height - resting)
    let scale = card.width > stackWidthInset ? 1 - progress * stackWidthInset / card.width : 1
    // Scales about the top center of the card, where it rests.
    return CGAffineTransform(
      a: scale, b: 0, c: 0, d: scale,
      tx: (1 - scale) * (pivot.x - bounds.midX),
      ty: (1 - scale) * (pivot.y - bounds.midY) - shift
    )
  }

  private func setStackDimming(_ value: CGFloat) {
    let opacity = Float(value)
    guard stackDimLayer.opacity != opacity else { return }
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    stackDimLayer.opacity = opacity
    CATransaction.commit()
  }

  func resetStack() {
    stackAbove = []
    stackAnimationSprings = []
    stackView.layer.removeAnimation(forKey: Self.stackAnimationKey)
    stackView.transform = .identity
    setStackDimming(0)
    scrimPaintView.alpha = 1
  }
}
