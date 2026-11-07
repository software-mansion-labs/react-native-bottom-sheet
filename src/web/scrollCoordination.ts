/// <reference lib="dom" />

import { clamp } from './dom';
import type { BottomSheetWebConfig } from './types';

// `scrollCoordination` levels: none, initial (1), and handoff.
export const COORDINATION_NONE = 0;
export const COORDINATION_HANDOFF = 2;

const EDGE_TOLERANCE = 0.5;
// Per-millisecond velocity retention of a released manual scroll, matching
// UIScrollView's normal deceleration rate.
const SCROLL_DECELERATION_RATE = 0.998;
const SCROLL_MOMENTUM_MIN_VELOCITY = 20;

export type ScrollableEntry = Readonly<{
  element: HTMLElement;
  inverted: boolean;
}>;

const isVerticallyScrollable = (element: HTMLElement) => {
  const { overflowY } = getComputedStyle(element);
  return (
    (overflowY === 'auto' || overflowY === 'scroll') &&
    element.scrollHeight > element.clientHeight
  );
};

// React Native for Web inverts lists with a negative vertical scale.
const isInverted = (element: HTMLElement) => {
  const { transform } = getComputedStyle(element);
  if (transform === 'none') return false;
  const match = /^matrix\(([^)]+)\)$/.exec(transform);
  const d = match ? Number(match[1]!.split(',')[3]) : 1;
  return d < 0;
};

const maxScrollTop = (element: HTMLElement) =>
  Math.max(0, element.scrollHeight - element.clientHeight);

/** Whether a scrollable shows the visual top of its content. */
export const isAtStart = (
  entry: ScrollableEntry,
  requireExactOffset = false
) => {
  const tolerance = requireExactOffset ? 0 : EDGE_TOLERANCE;
  const { element } = entry;
  return entry.inverted
    ? element.scrollTop >= maxScrollTop(element) - tolerance
    : element.scrollTop <= tolerance;
};

export const scrollableChain = (
  target: EventTarget | null,
  container: HTMLElement | undefined
): ScrollableEntry[] => {
  const chain: ScrollableEntry[] = [];
  if (!container || !(target instanceof Element)) return chain;
  for (
    let element: Element | null = target;
    element !== null && element !== container;
    element = element.parentElement
  ) {
    if (element instanceof HTMLElement && isVerticallyScrollable(element)) {
      chain.push({ element, inverted: isInverted(element) });
    }
  }
  return chain;
};

export const coordinationLevel = (
  deltaY: number,
  config: BottomSheetWebConfig
) => {
  const raw =
    deltaY < 0
      ? config.scrollExpandCoordination
      : config.scrollCollapseCoordination;
  return clamp(Math.round(raw), COORDINATION_NONE, COORDINATION_HANDOFF);
};

/** Scrolls the chain, innermost first, as a finger moving by `delta` would. */
export const scrollChainBy = (chain: ScrollableEntry[], delta: number) => {
  let remaining = delta;
  for (const entry of chain) {
    if (Math.abs(remaining) < Number.EPSILON) return;
    const { element } = entry;
    const scrollDelta = entry.inverted ? remaining : -remaining;
    const before = element.scrollTop;
    element.scrollTop = clamp(before + scrollDelta, 0, maxScrollTop(element));
    const applied = element.scrollTop - before;
    remaining -= entry.inverted ? applied : -applied;
  }
};

export class ScrollMomentum {
  private momentum: { entry: ScrollableEntry; velocity: number } | null = null;

  constructor(private readonly requestFrame: () => void) {}

  get isActive() {
    return this.momentum !== null;
  }

  cancel() {
    this.momentum = null;
  }

  start(chain: ScrollableEntry[], velocity: number) {
    const entry = chain.find((candidate) => {
      const { element } = candidate;
      const scrollVelocity = candidate.inverted ? velocity : -velocity;
      return scrollVelocity > 0
        ? element.scrollTop < maxScrollTop(element)
        : element.scrollTop > 0;
    });
    if (
      entry === undefined ||
      Math.abs(velocity) < SCROLL_MOMENTUM_MIN_VELOCITY
    ) {
      return;
    }
    this.momentum = { entry, velocity };
    this.requestFrame();
  }

  step(elapsed: number) {
    const momentum = this.momentum!;
    const { element, inverted } = momentum.entry;
    momentum.velocity *= Math.pow(SCROLL_DECELERATION_RATE, elapsed);
    const delta = (momentum.velocity * elapsed) / 1000;
    const before = element.scrollTop;
    element.scrollTop = clamp(
      before + (inverted ? delta : -delta),
      0,
      maxScrollTop(element)
    );
    if (
      element.scrollTop === before ||
      Math.abs(momentum.velocity) < SCROLL_MOMENTUM_MIN_VELOCITY
    ) {
      this.momentum = null;
    }
  }
}
