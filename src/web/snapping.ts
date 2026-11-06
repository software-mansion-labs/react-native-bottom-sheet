import { CriticalSpring } from './criticalSpring';
import { clamp } from './dom';
import type { BottomSheetWebCallbacks, ResolvedDetent } from './types';

const FLICK_VELOCITY = 600;

/** The controller's state and effects that snapping drives. */
export type SnappingHost = {
  readonly detents: ResolvedDetent[];
  readonly hostHeight: number;
  readonly translateY: number;
  targetIndex: number;
  pendingIndex: number | null;
  scrimPinnedFull: boolean;
  setTranslateY(ty: number): void;
  requestFrame(): void;
  emitPosition(): void;
  updateCloseRequestRegistration(): void;
  emit(event: (callbacks: BottomSheetWebCallbacks) => void): void;
};

export class SheetSnapping {
  private spring: CriticalSpring | null = null;
  private springTargetIndex = 0;
  springEmitsSettle = false;

  constructor(private readonly host: SnappingHost) {}

  get isSpringActive() {
    return this.spring !== null;
  }

  // MARK: Detents and positions

  private detentAt(index: number): ResolvedDetent {
    return (
      this.host.detents[index] ?? {
        position: 0,
        programmatic: false,
        insetLeft: 0,
        insetRight: 0,
        insetBottom: 0,
      }
    );
  }

  /** The resting position of the detent at an index. */
  detentPosition(index: number) {
    return this.detentAt(index).position;
  }

  translationY(index: number) {
    return this.host.hostHeight - this.detentAt(index).position;
  }

  snapCandidateIndices(
    including: number | null,
    detents: ResolvedDetent[] = this.host.detents
  ) {
    const indices = new Set<number>();
    detents.forEach((detent, index) => {
      if (!detent.programmatic) indices.add(index);
    });
    if (including !== null && detents[including] !== undefined) {
      indices.add(including);
    }
    return Array.from(indices).sort(
      (a, b) => detents[a]!.position - detents[b]!.position
    );
  }

  draggableRange(
    including: number | null,
    detents: ResolvedDetent[] = this.host.detents
  ) {
    const candidates = this.snapCandidateIndices(including, detents);
    if (candidates.length === 0) return { minTy: 0, maxTy: 0 };
    const translations = candidates.map(
      (index) => this.host.hostHeight - detents[index]!.position
    );
    return {
      minTy: Math.min(...translations),
      maxTy: Math.max(...translations),
    };
  }

  bestSnapIndex(
    position: number,
    velocity: number,
    including: number | null,
    detents: ResolvedDetent[]
  ) {
    const candidates = this.snapCandidateIndices(including, detents);
    if (candidates.length === 0) return this.host.targetIndex;

    if (velocity < -FLICK_VELOCITY) {
      return (
        candidates.find((index) => detents[index]!.position > position) ??
        candidates[candidates.length - 1]!
      );
    }
    if (velocity > FLICK_VELOCITY) {
      return (
        candidates.findLast((index) => detents[index]!.position < position) ??
        candidates[0]!
      );
    }
    return candidates.reduce((best, index) =>
      Math.abs(detents[index]!.position - position) <
      Math.abs(detents[best]!.position - position)
        ? index
        : best
    );
  }

  private get closedIndex() {
    const index = this.host.detents.findIndex(
      (detent) => detent.position === 0
    );
    return index === -1 ? null : index;
  }

  get scrimDismissIndex() {
    const closedIndex = this.closedIndex;
    if (closedIndex === null || this.host.detents[closedIndex]!.programmatic) {
      return null;
    }
    return closedIndex;
  }

  isTargetClosed() {
    return (
      this.closedIndex !== null && this.host.targetIndex === this.closedIndex
    );
  }

  currentTranslateY(time = performance.now()) {
    return this.spring !== null
      ? this.spring.value(time)
      : this.host.translateY;
  }

  // MARK: Animation

  snapToIndex(
    index: number,
    velocity: number,
    emitIndexChange = true,
    emitSettle = true,
    preserveScrimPin = false
  ) {
    const host = this.host;
    if (index < 0 || index >= host.detents.length) return;
    if (emitIndexChange) {
      // A user-driven snap supersedes an index retained from props.
      host.pendingIndex = null;
    }

    host.targetIndex = index;
    if (!preserveScrimPin) {
      host.scrimPinnedFull = false;
    }

    const now = performance.now();
    const currentTy =
      this.spring !== null ? this.cancelSpring(now) : host.translateY;
    const targetTy = this.translationY(index);
    const distance = targetTy - currentTy;
    const velocityRatio = distance !== 0 ? velocity / distance : 0;
    const v0 = clamp(velocityRatio, -5, 5) * distance;

    this.springEmitsSettle = emitSettle;
    this.springTargetIndex = index;
    this.spring = new CriticalSpring(currentTy, targetTy, v0, now);
    host.requestFrame();
    host.updateCloseRequestRegistration();

    // Report the index change as soon as the snap is committed; `onSettle`
    // remains the signal for the end of movement.
    if (emitIndexChange) {
      host.emit((callbacks) => callbacks.onIndexChange(index));
    }
  }

  /** Whether the running snap, if any, is heading for a position. */
  isSpringHeadingTo(ty: number) {
    return this.spring?.target === ty;
  }

  springVelocity(time: number) {
    return this.spring?.velocity(time) ?? 0;
  }

  /**
   * Heads for the target detent's new position, with the sheet's exact velocity
   * so it doesn't stall.
   */
  retargetSpring(
    velocity: number,
    emitSettle: boolean,
    omega: number,
    startTime: number
  ) {
    const host = this.host;
    this.springEmitsSettle = emitSettle;
    this.springTargetIndex = host.targetIndex;
    this.spring = new CriticalSpring(
      host.translateY,
      this.translationY(host.targetIndex),
      velocity,
      startTime,
      omega
    );
    host.requestFrame();
    host.updateCloseRequestRegistration();
  }

  cancelSpring(time = performance.now()) {
    const visualTy = this.currentTranslateY(time);
    this.spring = null;
    this.springEmitsSettle = false;
    this.host.setTranslateY(visualTy);
    return visualTy;
  }

  private finishSpring() {
    if (this.spring === null) return;
    const host = this.host;
    const index = this.springTargetIndex;
    const emitSettle = this.springEmitsSettle;
    this.spring = null;
    this.springEmitsSettle = false;
    host.setTranslateY(this.translationY(index));
    host.scrimPinnedFull = false;
    host.emitPosition();
    host.updateCloseRequestRegistration();
    if (emitSettle) {
      host.emit((callbacks) => callbacks.onSettle(index));
    }
  }

  /** Advances the spring, if any, to a frame's time. */
  stepSpring(time: number) {
    const spring = this.spring;
    if (spring !== null) {
      if (spring.isFinished(time)) {
        this.finishSpring();
      } else {
        this.host.setTranslateY(spring.value(time));
        this.host.emitPosition();
      }
    }
  }
}
