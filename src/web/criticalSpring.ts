const SPRING_DURATION_MS = 450;
// Pick the stiffness so the sheet looks settled (within ~0.5% of target) right
// at the duration. For a critically-damped spring that point is ω·t ≈ 8.
export const SPRING_OMEGA = 8 / (SPRING_DURATION_MS / 1000);
// Detent changes this close together are one continuous change, such as a
// dragged slider. The sheet follows those on a much tighter spring, because any
// spring trails a moving target, by about 2/ω.
export const RAPID_DETENT_CHANGE_MS = 100;
const RAPID_SPRING_DURATION_MS = 120;
export const RAPID_SPRING_OMEGA = 8 / (RAPID_SPRING_DURATION_MS / 1000);

/** Critically damped (ζ = 1) spring; see `CriticalSpring.swift`. */
export class CriticalSpring {
  constructor(
    readonly from: number,
    readonly target: number,
    /** Initial velocity in pixels per second. */
    readonly v0: number,
    /** `performance.now()` timestamp the curve starts at. */
    readonly startTime: number,
    readonly omega: number = SPRING_OMEGA
  ) {}

  value(time: number) {
    const t = Math.max(0, time - this.startTime) / 1000;
    const a = this.from - this.target;
    const decay = Math.exp(-this.omega * t);
    return this.target + decay * (a + (this.v0 + this.omega * a) * t);
  }

  /** The velocity at a moment, in pixels per second. */
  velocity(time: number) {
    const t = Math.max(0, time - this.startTime) / 1000;
    const a = this.from - this.target;
    const decay = Math.exp(-this.omega * t);
    return decay * (this.v0 - this.omega * (this.v0 + this.omega * a) * t);
  }

  isFinished(time: number) {
    return ((time - this.startTime) / 1000) * this.omega >= 8;
  }
}
