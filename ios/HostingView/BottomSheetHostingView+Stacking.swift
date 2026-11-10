import UIKit

/// How much narrower a fully pushed-back card is drawn, across both sides.
/// Matches `STACK_WIDTH_INSET` on Android and web.
private let stackWidthInset: CGFloat = 32
/// How far a pushed-back card's top edge shows above the sheet covering it.
/// Matches `STACK_PEEK` on Android and web.
let stackPeek: CGFloat = 10

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
extension BottomSheetHostingView {
  /// Every hosting view in a window. Few enough that each update walks them all.
  private static let stackSheets = NSHashTable<BottomSheetHostingView>.weakObjects()

  private static let stackAnimationKey = "bottomSheetStack"
  /// Samples per second of the stack animation, as the settle animation has.
  private static let stackSampleRate: CFTimeInterval = 120

  func updateStackRegistration() {
    if window != nil {
      Self.stackSheets.add(self)
    } else {
      Self.stackSheets.remove(self)
    }
    Self.updateStacks()
  }

  /// Finds every sheet's candidates again and brings its transform up to date.
  /// Cheap enough to run on every move: without a stacked sheet it only finds
  /// each sheet at the identity it already has.
  static func updateStacks() {
    let sheets = stackSheets.allObjects
    let participants = sheets.filter(\.isStackParticipant)
    for sheet in sheets {
      sheet.followStack(sheet.isStackParticipant ? sheet.stackCandidates(among: participants) : [])
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

  /// Where the sheet's model puts its card's top: the settle target while a
  /// spring runs.
  private var modelPosition: CGFloat {
    sheetContainerHeight - sheetContainer.transform.ty
  }

  /// The sheets that may push this one back: every `stacked` one above it.
  /// A sheet that is closed pushes nothing, and one that is closing fades its
  /// push out as it goes, so none needs passing over.
  private func stackCandidates(among sheets: [BottomSheetHostingView]) -> [BottomSheetHostingView] {
    guard restingPosition > 0 else { return [] }
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
    let transform = stackTransform(under: candidates, at: candidates.map(\.modelPosition))
    if stackView.transform != transform {
      stackView.transform = transform
    }

    // A new settle starts its own spring; the curves of those still running
    // are analytic, so rebuilding mid-flight picks them up seamlessly.
    let sources = candidates.compactMap(\.settleAnimation)
    guard !sources.elementsEqual(stackAnimationSources, by: ===) else { return }
    stackAnimationSources = sources
    stackView.layer.removeAnimation(forKey: Self.stackAnimationKey)
    let springs = candidates.map(\.activeSpring)
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
      let transform = stackTransform(under: candidates, at: positions)
      return NSValue(caTransform3D: CATransform3DMakeAffineTransform(transform))
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

  /// This sheet's transform with each candidate at the matching position. Only
  /// a candidate's tallest detent can cover this sheet's top edge, as only a
  /// native sheet's large detent pushes back the one beneath, and its push is
  /// interpolated between detents by its position, like a scrim opacity. The
  /// candidate that covers most wins, in progress and in shift alike.
  private func stackTransform(
    under candidates: [BottomSheetHostingView],
    at positions: [CGFloat]
  ) -> CGAffineTransform {
    let resting = restingPosition
    var progress: CGFloat = 0
    var shift: CGFloat = 0
    for (candidate, position) in zip(candidates, positions) {
      // How far each of the candidate's detents reaches into the strip this
      // sheet's edge peeks out of. Negative leaves the edge uncovered.
      let tallest = candidate.detentSpecs.map(\.position).max() ?? 0
      let reaches = candidate.detentSpecs.map {
        $0.position > 0 && $0.position == tallest ? $0.position - (resting - stackPeek) : -1
      }
      let progresses: [CGFloat] = reaches.map { $0 >= 0 ? 1 : 0 }
      let shifts = reaches.map { min(max($0, 0), stackPeek) }
      progress = max(progress, candidate.interpolate(forPosition: position, values: progresses))
      shift = max(shift, candidate.interpolate(forPosition: position, values: shifts))
    }
    progress = easedStackProgress(progress)
    guard progress > 0 || shift > 0 else { return .identity }

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

  func resetStack() {
    stackAnimationSources = []
    stackView.layer.removeAnimation(forKey: Self.stackAnimationKey)
    stackView.transform = .identity
  }
}
