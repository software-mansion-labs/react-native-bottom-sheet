/**
 * Critically damped (ζ = 1) spring; see `CriticalSpring.swift`. The stiffness is
 * picked so the sheet looks settled (within ~0.5% of target) right at the
 * duration: for a critically-damped spring that point is ω·t ≈ 8.
 */
export class CriticalSpring {
  private readonly omega: number;

  constructor(
    readonly from: number,
    readonly target: number,
    /** Initial velocity in pixels per second. */
    readonly v0: number,
    /** `performance.now()` timestamp the curve starts at. */
    readonly startTime: number,
    /** Seconds the spring takes. */
    readonly duration: number
  ) {
    this.omega = 8 / duration;
  }

  value(time: number) {
    const t = Math.max(0, time - this.startTime) / 1000;
    const a = this.from - this.target;
    const decay = Math.exp(-this.omega * t);
    return this.target + decay * (a + (this.v0 + this.omega * a) * t);
  }

  isFinished(time: number) {
    return time - this.startTime >= this.duration * 1000;
  }
}
