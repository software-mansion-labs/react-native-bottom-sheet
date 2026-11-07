/// <reference lib="dom" />

import { clamp, isScrollbarHit, isTextEditingTarget } from './dom';
import {
  isAtStart,
  COORDINATION_HANDOFF,
  COORDINATION_NONE,
  coordinationLevel,
  scrollableChain,
  scrollChainBy,
  type ScrollableEntry,
  type ScrollMomentum,
} from './scrollCoordination';
import type { SheetSnapping } from './snapping';
import type {
  BottomSheetWebConfig,
  BottomSheetWebElements,
  ResolvedDetent,
} from './types';

// Movement, in CSS pixels, before a pointer is treated as a drag rather than a
// press. Below the browsers' own touch-scroll slop, so the sheet decides
// ownership before native scrolling can begin.
const DRAG_SLOP = 6;
const VELOCITY_SAMPLE_WINDOW_MS = 100;
const CLICK_SUPPRESSION_MS = 400;

type GestureMode = 'pending' | 'ignored' | 'sheet' | 'scroll';

type Gesture = {
  kind: 'touch' | 'mouse';
  pointerId: number | null;
  touchId: number | null;
  startX: number;
  startY: number;
  lastY: number;
  samples: { time: number; y: number }[];
  chain: ScrollableEntry[];
  mode: GestureMode;
  // In `scroll` mode, whether this controller scrolls the chain itself (it has
  // been preventing the browser's own scrolling) or the browser is scrolling.
  manualScroll: boolean;
  coordinationLevel: number;
  startingIndex: number;
  dragDetents: ResolvedDetent[];
  dragRange: { minTy: number; maxTy: number };
  didMoveSheet: boolean;
  scrollOwnsLowerBoundary: boolean;
};

// Moves already claimed by a nested sheet's gesture. Every sheet under the
// pointer tracks it, and the innermost one decides first since the event
// bubbles outward; an enclosing sheet only takes the gesture if it declines.
const claimedEvents = new WeakSet<Event>();

/** The controller's state and effects that a gesture reads and drives. */
export type GesturesHost = {
  readonly elements: BottomSheetWebElements | null;
  readonly detents: ResolvedDetent[];
  readonly hostHeight: number;
  readonly translateY: number;
  readonly hasLaidOut: boolean;
  readonly targetIndex: number;
  readonly snapping: Pick<
    SheetSnapping,
    | 'isSpringActive'
    | 'bestSnapIndex'
    | 'cancelSpring'
    | 'currentTranslateY'
    | 'draggableRange'
    | 'snapCandidateIndices'
    | 'snapToIndex'
  >;
  readonly momentum: Pick<ScrollMomentum, 'cancel' | 'start'>;
  isPanning: boolean;
  pendingIndex: number | null;
  scrimPinnedFull: boolean;
  requireConfig(): BottomSheetWebConfig;
  setTranslateY(ty: number): void;
  emitPosition(): void;
};

export class SheetGestures {
  private gesture: Gesture | null = null;
  private suppressClicksUntil = 0;
  private restoreUserSelect: (() => void) | null = null;

  constructor(private readonly host: GesturesHost) {}

  addListeners(container: HTMLElement) {
    container.addEventListener('pointerdown', this.handlePointerDown);
    container.addEventListener('touchstart', this.handleTouchStart, {
      passive: true,
    });
    container.addEventListener('touchmove', this.handleTouchMove, {
      passive: false,
    });
    container.addEventListener('touchend', this.handleTouchEnd);
    container.addEventListener('touchcancel', this.handleTouchEnd);
    container.addEventListener('click', this.handleContainerClick, true);
  }

  removeListeners(container: HTMLElement) {
    container.removeEventListener('pointerdown', this.handlePointerDown);
    this.setWindowPointerListeners(false);
    container.removeEventListener('touchstart', this.handleTouchStart);
    container.removeEventListener('touchmove', this.handleTouchMove);
    container.removeEventListener('touchend', this.handleTouchEnd);
    container.removeEventListener('touchcancel', this.handleTouchEnd);
    container.removeEventListener('click', this.handleContainerClick, true);
  }

  /** Drops any gesture in progress. */
  reset() {
    this.restoreUserSelect?.();
    this.restoreUserSelect = null;
    this.gesture = null;
    this.host.isPanning = false;
    this.host.momentum.cancel();
  }

  /** Whether React Native for Web should hand the responder to the sheet. */
  shouldCaptureResponder() {
    return (
      this.gesture !== null &&
      (this.gesture.mode === 'sheet' ||
        (this.gesture.mode === 'scroll' && this.gesture.manualScroll))
    );
  }

  private readonly handlePointerDown = (event: PointerEvent) => {
    // Touch input is handled through touch events, whose moves can be
    // prevented one at a time to keep the browser from scrolling.
    if (
      event.pointerType === 'touch' ||
      event.button !== 0 ||
      isTextEditingTarget(event.target) ||
      isScrollbarHit(event)
    ) {
      return;
    }
    this.beginGesture('mouse', event.clientX, event.clientY, event.target);
    this.gesture!.pointerId = event.pointerId;
    // A fast drag can leave the sheet before it is recognized, so follow the
    // pointer on the window. Capturing it here instead would retarget the
    // click of a plain press away from the content.
    this.setWindowPointerListeners(true);
  };

  private setWindowPointerListeners(isListening: boolean) {
    if (isListening) {
      window.addEventListener('pointermove', this.handlePointerMove, true);
      window.addEventListener('pointerup', this.handlePointerUp, true);
      window.addEventListener('pointercancel', this.handlePointerUp, true);
    } else {
      window.removeEventListener('pointermove', this.handlePointerMove, true);
      window.removeEventListener('pointerup', this.handlePointerUp, true);
      window.removeEventListener('pointercancel', this.handlePointerUp, true);
    }
  }

  private readonly handlePointerMove = (event: PointerEvent) => {
    const gesture = this.gesture;
    if (gesture === null || gesture.pointerId !== event.pointerId) return;
    if (this.moveGesture(event, event.clientX, event.clientY)) {
      event.preventDefault();
    }
  };

  private readonly handlePointerUp = (event: PointerEvent) => {
    if (this.gesture?.pointerId === event.pointerId) {
      this.endGesture();
    }
  };

  private readonly handleTouchStart = (event: TouchEvent) => {
    const touch = event.changedTouches[0];
    if (
      touch === undefined ||
      this.gesture !== null ||
      event.touches.length > 1
    ) {
      return;
    }
    this.beginGesture('touch', touch.clientX, touch.clientY, event.target);
    this.gesture!.touchId = touch.identifier;
  };

  private readonly handleTouchMove = (event: TouchEvent) => {
    const gesture = this.gesture;
    if (gesture === null) return;
    const touch = Array.from(event.changedTouches).find(
      (candidate) => candidate.identifier === gesture.touchId
    );
    if (touch === undefined) return;
    if (
      this.moveGesture(event, touch.clientX, touch.clientY) &&
      event.cancelable
    ) {
      event.preventDefault();
    }
  };

  private readonly handleTouchEnd = (event: TouchEvent) => {
    const gesture = this.gesture;
    if (
      gesture !== null &&
      Array.from(event.changedTouches).some(
        (touch) => touch.identifier === gesture.touchId
      )
    ) {
      this.endGesture();
    }
  };

  private readonly handleContainerClick = (event: MouseEvent) => {
    // A click that ends a sheet drag must not press the content under it.
    if (performance.now() < this.suppressClicksUntil) {
      event.preventDefault();
      event.stopPropagation();
    }
  };

  private beginGesture(
    kind: Gesture['kind'],
    x: number,
    y: number,
    target: EventTarget | null
  ) {
    const host = this.host;
    host.momentum.cancel();
    this.suppressClicksUntil = 0;
    this.gesture = {
      kind,
      pointerId: null,
      touchId: null,
      startX: x,
      startY: y,
      lastY: y,
      samples: [{ time: performance.now(), y }],
      chain: scrollableChain(target, host.elements?.container),
      mode: 'pending',
      manualScroll: false,
      coordinationLevel: COORDINATION_NONE,
      startingIndex: host.targetIndex,
      dragDetents: host.detents,
      dragRange: { minTy: 0, maxTy: 0 },
      didMoveSheet: false,
      scrollOwnsLowerBoundary: false,
    };
  }

  /** Port of the native `gestureRecognizerShouldBegin`. */
  private shouldBegin(gesture: Gesture, dx: number, dy: number) {
    const host = this.host;
    if (Math.abs(dy) <= Math.abs(dx)) return false;
    if (!host.hasLaidOut) return false;
    if (host.snapping.snapCandidateIndices(host.targetIndex).length <= 1) {
      return false;
    }

    const range = host.snapping.draggableRange(host.targetIndex);
    const currentTy = host.snapping.currentTranslateY();
    const canMoveSheet =
      dy < 0 ? currentTy > range.minTy + 0.5 : currentTy < range.maxTy - 0.5;
    if (gesture.chain.length === 0) return canMoveSheet;

    const level = coordinationLevel(dy, host.requireConfig());
    if (level === COORDINATION_NONE || !canMoveSheet) return false;
    if (level === COORDINATION_HANDOFF) return true;

    // Initial-only collapse is available at the largest detent only when all
    // scrollable ancestors were already at their visual start.
    const isAtLargestDetent = currentTy <= range.minTy + 0.5;
    if (dy > 0 && isAtLargestDetent) {
      return gesture.chain.every((entry) => isAtStart(entry));
    }
    return true;
  }

  /** Port of the native pan `.began` state. */
  private startPan(gesture: Gesture, dy: number) {
    const host = this.host;
    if (host.snapping.isSpringActive) {
      host.snapping.cancelSpring();
    }
    host.isPanning = true;
    // The user takes control: drop any index retained from props.
    host.pendingIndex = null;
    host.scrimPinnedFull = false;
    gesture.startingIndex = host.targetIndex;
    gesture.dragDetents = host.detents;
    gesture.dragRange = host.snapping.draggableRange(host.targetIndex);
    gesture.coordinationLevel = coordinationLevel(dy, host.requireConfig());

    const container = host.elements!.container;
    const activeElement = document.activeElement;
    if (
      activeElement instanceof HTMLElement &&
      container.contains(activeElement)
    ) {
      activeElement.blur();
    }

    gesture.mode = 'sheet';
    if (gesture.chain.length > 0) {
      const isAtLargestDetent =
        host.translateY <= gesture.dragRange.minTy + 0.5;
      if (
        gesture.coordinationLevel === COORDINATION_HANDOFF &&
        isAtLargestDetent &&
        (dy < 0 || !gesture.chain.every((entry) => isAtStart(entry)))
      ) {
        gesture.mode = 'scroll';
        // Mice cannot drag-scroll content natively, so scroll it here.
        gesture.manualScroll = gesture.kind === 'mouse';
      }
    }

    if (gesture.kind === 'mouse') {
      window.getSelection()?.removeAllRanges();
      const rootStyle = document.documentElement.style;
      const previousUserSelect = rootStyle.userSelect;
      rootStyle.userSelect = 'none';
      this.restoreUserSelect = () => {
        rootStyle.userSelect = previousUserSelect;
      };
    }
  }

  /** Returns whether the move belongs to the sheet and must not scroll. */
  private moveGesture(event: Event, x: number, y: number): boolean {
    const host = this.host;
    const gesture = this.gesture!;
    if (gesture.mode === 'ignored') return false;
    if (claimedEvents.has(event)) {
      // A nested sheet owns this gesture.
      if (gesture.mode === 'pending') {
        gesture.mode = 'ignored';
        return false;
      }
    }

    const now = performance.now();
    gesture.samples.push({ time: now, y });
    while (
      gesture.samples.length > 2 &&
      now - gesture.samples[0]!.time > VELOCITY_SAMPLE_WINDOW_MS
    ) {
      gesture.samples.shift();
    }

    if (gesture.mode === 'pending') {
      const dx = x - gesture.startX;
      const dy = y - gesture.startY;
      if (Math.hypot(dx, dy) < DRAG_SLOP) return false;
      if (!this.shouldBegin(gesture, dx, dy)) {
        gesture.mode = 'ignored';
        return false;
      }
      claimedEvents.add(event);
      this.startPan(gesture, dy);
      // Like the native hosts, measure movement from where the drag was
      // recognized, so the slop is not applied as a jump.
      gesture.lastY = gesture.startY + Math.sign(dy) * DRAG_SLOP;
    }

    claimedEvents.add(event);
    const delta = y - gesture.lastY;
    gesture.lastY = y;
    const range = gesture.dragRange;
    const handoff = gesture.coordinationLevel === COORDINATION_HANDOFF;

    if (gesture.mode === 'scroll') {
      if (gesture.manualScroll) {
        scrollChainBy(gesture.chain, delta);
      }
      if (gesture.scrollOwnsLowerBoundary) {
        // Once the sheet is fully collapsed, downward movement belongs to the
        // content. On reversal, let it return to its start before the sheet
        // can expand again.
        if (
          delta < 0 &&
          gesture.chain.every((entry) => isAtStart(entry, true))
        ) {
          gesture.scrollOwnsLowerBoundary = false;
          gesture.mode = 'sheet';
        }
        return gesture.manualScroll;
      }
      // A downward scroll remains with the content until every scrollable in
      // the touched chain reaches its visual start. Start moving the sheet on
      // the following update so the boundary movement is not consumed twice.
      if (delta > 0 && gesture.chain.every((entry) => isAtStart(entry))) {
        gesture.mode = 'sheet';
      }
      return gesture.manualScroll;
    }

    const newTy = clamp(host.translateY + delta, range.minTy, range.maxTy);
    if (Math.abs(newTy - host.translateY) > Number.EPSILON) {
      gesture.didMoveSheet = true;
      host.setTranslateY(newTy);
      host.emitPosition();
    }

    if (handoff && gesture.chain.length > 0) {
      if (delta < 0 && newTy <= range.minTy + 0.5) {
        // The sheet reached its largest detent: hand the rest of the drag to
        // the content. The browser's scrolling stays prevented for this
        // gesture, so scroll it here.
        gesture.mode = 'scroll';
        gesture.manualScroll = true;
        gesture.scrollOwnsLowerBoundary = false;
      } else if (delta > 0 && newTy >= range.maxTy - 0.5) {
        // The sheet has no smaller detent to consume.
        gesture.mode = 'scroll';
        gesture.manualScroll = true;
        gesture.scrollOwnsLowerBoundary = true;
      }
    }
    return true;
  }

  private endGesture() {
    const host = this.host;
    const gesture = this.gesture;
    this.gesture = null;
    this.setWindowPointerListeners(false);
    this.restoreUserSelect?.();
    this.restoreUserSelect = null;
    if (
      gesture === null ||
      gesture.mode === 'pending' ||
      gesture.mode === 'ignored'
    ) {
      return;
    }
    host.isPanning = false;

    const velocity = this.releaseVelocity(gesture);
    if (gesture.didMoveSheet || gesture.manualScroll) {
      this.suppressClicksUntil = performance.now() + CLICK_SUPPRESSION_MS;
    }
    if (gesture.mode === 'scroll' && gesture.manualScroll) {
      host.momentum.start(gesture.chain, velocity);
    }
    if (gesture.chain.length > 0 && !gesture.didMoveSheet) {
      return;
    }

    const height = host.hostHeight - host.translateY;
    const detents =
      gesture.dragDetents.length === host.detents.length
        ? gesture.dragDetents
        : host.detents;
    const index = host.snapping.bestSnapIndex(
      height,
      velocity,
      gesture.startingIndex,
      detents
    );
    host.snapping.snapToIndex(index, velocity);
  }

  private releaseVelocity(gesture: Gesture) {
    const now = performance.now();
    const samples = gesture.samples.filter(
      (sample) => now - sample.time <= VELOCITY_SAMPLE_WINDOW_MS
    );
    const first = samples[0];
    const last = samples[samples.length - 1];
    if (first === undefined || last === undefined || last.time <= first.time) {
      return 0;
    }
    return ((last.y - first.y) / (last.time - first.time)) * 1000;
  }
}
