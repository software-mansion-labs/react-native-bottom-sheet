const SPRING_DURATION_MS = 450;
// Pick the stiffness so the sheet looks settled (within ~0.5% of target) right
// at the duration. For a critically-damped spring that point is ω·t ≈ 8.
const SPRING_OMEGA = 8 / (SPRING_DURATION_MS / 1000);

/** Critically damped (ζ = 1) spring; see `CriticalSpring.swift`. */
export class CriticalSpring {
  constructor(
    readonly from: number,
    readonly target: number,
    /** Initial velocity in pixels per second. */
    readonly v0: number,
    /** `performance.now()` timestamp the curve starts at. */
    readonly startTime: number
  ) {}

  value(time: number) {
    const t = Math.max(0, time - this.startTime) / 1000;
    const a = this.from - this.target;
    const decay = Math.exp(-SPRING_OMEGA * t);
    return this.target + decay * (a + (this.v0 + SPRING_OMEGA * a) * t);
  }

  isFinished(time: number) {
    return time - this.startTime >= SPRING_DURATION_MS;
  }
}
