import QuartzCore

/// A spring we evaluate ourselves each frame instead of letting
/// `UIViewPropertyAnimator` run the settle. The animator runs on the "render
/// server", so its value is only readable one frame late via `presentation()` —
/// the cause of the follower view (fed by `onPositionChange`) trailing the
/// modal. Computing it in-process lets us emit the exact value we set.
/// Critically damped (ζ = 1): reaches the target as fast as possible without
/// overshooting.
struct CriticalSpring {
  let from: CGFloat
  let target: CGFloat
  /// Initial velocity (points/sec) — e.g. carried over from a finger flick.
  let v0: CGFloat
  /// Angular frequency (rad/sec) — the spring's stiffness/speed. Higher ω snaps faster;
  let omega: CGFloat
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

  func isFinished(at time: CFTimeInterval) -> Bool {
    time - startTime >= duration
  }
}
