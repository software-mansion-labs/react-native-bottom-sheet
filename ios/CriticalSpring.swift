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

  func value(at time: CFTimeInterval) -> CGFloat {
    let t = CGFloat(max(0, time - startTime))
    let a = from - target
    let decay = exp(-omega * t)
    return target + decay * (a + (v0 + omega * a) * t)
  }

  func isFinished(at time: CFTimeInterval) -> Bool {
    time - startTime >= duration
  }
}
