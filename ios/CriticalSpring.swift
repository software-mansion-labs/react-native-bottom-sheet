import QuartzCore


/// Critically damped (ζ = 1): reaches the target as fast as possible without overshooting.
struct CriticalSpring {
  let from: CGFloat
  let target: CGFloat
  /// Initial velocity (points/sec) — e.g. carried over from a finger flick.
  let v0: CGFloat
  /// Angular frequency (rad/sec) — the spring's stiffness/speed. Higher ω snaps faster.
  let omega: CGFloat
  /// Absolute media time the curve starts at — the same instant the modal's
  /// keyframe animation is pinned to via its `beginTime`, so `value(at:)` and the
  /// modal share one clock.
  let startTime: CFTimeInterval
  let duration: CFTimeInterval

  /// Position at an absolute `time`, from the closed-form solution of a
  /// critically-damped spring (ζ = 1):
  ///   x(t) = target + e^(−ω·t)·[A + (v0 + ω·A)·t]
  /// where A is the starting offset from the target. The `e^(−ω·t)` term decays
  /// the offset toward 0 (so x → target), and the linear `…·t` factor is what
  /// lets a critically-damped spring carry initial velocity without oscillating.
  func value(at time: CFTimeInterval) -> CGFloat {
    // Seconds since the spring started (clamped so a past `time` reads as t = 0).
    let t = CGFloat(max(0, time - startTime))
    // A: how far `from` is from `target` — the offset the spring must close.
    let a = from - target
    // Exponential envelope: 1 at t = 0, shrinking toward 0 as time passes.
    let decay = exp(-omega * t)
    return target + decay * (a + (v0 + omega * a) * t)
  }

  /// Samples this curve into `count + 1` evenly-spaced points over `[0, duration]`,
  /// for use as `CAKeyframeAnimation.values`. CA replays these (linearly
  /// interpolating between them) on the render server, so the modal traces this
  /// exact curve — identical to what `value(at:)` feeds the follower, by
  /// construction. The samples are relative to t = 0, so they don't depend on
  /// `startTime` (which is resolved only after the animation is committed).
  func keyframeValues(count: Int) -> [CGFloat] {
    let n = max(count, 1)
    return (0...n).map { i in
      let t = duration * CFTimeInterval(i) / CFTimeInterval(n)
      // Evaluate relative to the start: `startTime + t` minus `startTime` = t.
      return value(at: startTime + t)
    }
  }
}
