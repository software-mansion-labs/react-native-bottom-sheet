import UIKit

extension BottomSheetHostingView {
  func detent(at index: Int) -> DetentSpec {
    guard detentSpecs.indices.contains(index) else {
      return DetentSpec(position: 0, programmatic: false, insets: DetentInsets())
    }
    return detentSpecs[index]
  }

  func translationY(for index: Int) -> CGFloat {
    sheetContainerHeight - detent(at: index).position
  }

  func snapCandidateIndices(including index: Int? = nil) -> [Int] {
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

  func draggableRange(including index: Int? = nil) -> (minTy: CGFloat, maxTy: CGFloat) {
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

  func snapshotDraggableRange(
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

  var closedIndex: Int? {
    detentSpecs.firstIndex(where: { $0.position == 0 })
  }

  var scrimDismissIndex: Int? {
    guard let closedIndex, !detentSpecs[closedIndex].programmatic else {
      return nil
    }
    return closedIndex
  }

  var firstOpenDetentPosition: CGFloat {
    detentSpecs.first(where: { $0.position > 0 })?.position ?? 0
  }

  var currentSheetHeight: CGFloat {
    let maxHeight = sheetContainerHeight
    let ty = currentTranslationY
    return maxHeight - ty
  }

  @objc func displayLinkFired(_ link: CADisplayLink) {
    // `targetTimestamp` is the predicted display time of the next frame.
    // Ideal synchronization with animations running on the render server
    // hasn't been achieved. Render server is a black box and it's hard to
    // find out why exactly. This approach however gives better results
    // than lagging one frame behind.
    let frameDuration = link.targetTimestamp - link.timestamp
    stepSpring(targetTime: link.targetTimestamp + Self.followerPredictionBiasFrames * frameDuration)
  }

  func snapToIndex(
    _ index: Int,
    velocity: CGFloat,
    emitIndexChange: Bool = true,
    emitSettle: Bool = true,
    preserveScrimPin: Bool = false
  ) {
    guard index >= 0, index < detentSpecs.count else { return }
    // A newer snap supersedes the one a pan stopped.
    interruptedSpringEmitsSettle = nil
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

    targetIndex = index
    if !preserveScrimPin {
      scrimPinnedFull = false
    }

    let currentTy: CGFloat
    if activeSpring != nil {
      currentTy = cancelActiveSpring()
    } else {
      currentTy = sheetContainer.transform.ty
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
    fitScrollIndicators(of: initialScrollViewStates)
    sheetContainer.layer.add(animation, forKey: Self.springAnimationKey)
    addCardAnimations(matching: animation)
    activeSpring = spring

    startDisplayLink()

    // Report the index change as soon as the snap is committed, not when it
    // finishes: `targetIndex` is already set, and a programmatic snap's start is
    // known to the caller. `onSettle` remains the signal for movement end.
    if emitIndexChange {
      eventDelegate?.bottomSheetHostingView(self, didChangeIndex: index)
    }
  }

  func flushPendingSnapIfNeeded() {
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
    fitScrollIndicators(of: initialScrollViewStates)
    emitPosition()
    scrimPinnedFull = false
    setContentInteractionEnabled(true)
    updateInteractionState()
    if emitSettle {
      eventDelegate?.bottomSheetHostingView(self, didSettle: index)
    }
  }

  /// Restarts the snap a pan stopped as it began, when the content kept the
  /// touch and the sheet never moved: it would otherwise rest wherever the
  /// spring stopped, and `onSettle` would never arrive.
  func resumeInterruptedSpring() {
    guard let emitSettle = interruptedSpringEmitsSettle else { return }
    interruptedSpringEmitsSettle = nil
    snapToIndex(targetIndex, velocity: 0, emitIndexChange: false, emitSettle: emitSettle)
  }

  @discardableResult
  func cancelActiveSpring() -> CGFloat {
    let visualTy = currentTranslationY
    activeSpring = nil
    activeSpringEmitsSettle = false
    stopDisplayLink()
    removeSettleAnimations()
    setSheetTranslation(visualTy)
    return visualTy
  }

  func bestSnapIndex(
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

  func snapshotBestSnapIndex(
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
}

extension BottomSheetHostingView: CAAnimationDelegate {
  public func animationDidStop(_: CAAnimation, finished: Bool) {
    guard finished, activeSpring != nil else { return }
    finishSpring()
  }
}
