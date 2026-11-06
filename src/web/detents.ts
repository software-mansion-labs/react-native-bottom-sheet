/// <reference lib="dom" />

import { CONTENT_HEIGHT_MARKER_NATIVE_ID } from '../bottomSheetUtils';
import { clamp } from './dom';
import type {
  BottomSheetWebCallbacks,
  BottomSheetWebConfig,
  BottomSheetWebElements,
  RawDetent,
  ResolvedDetent,
} from './types';

/** The controller's state that detents resolve from. */
export type DetentsHost = {
  readonly elements: BottomSheetWebElements | null;
  readonly rawDetents: ReadonlyArray<RawDetent>;
  readonly hostHeight: number;
  readonly statusBarOverlap: number;
  readonly widestCardGaps: number;
  lastReportedError: string | null;
  emit(event: (callbacks: BottomSheetWebCallbacks) => void): void;
};

/**
 * The tallest the card can be at a detent: the host's height, less the detent's
 * top and bottom insets. Percentages resolve against it, so `100%` fits.
 */
const availableCardHeight = (
  spec: RawDetent,
  hostHeight: number,
  statusBarOverlap: number
) =>
  Math.max(
    0,
    hostHeight -
      (spec.usesStatusBarTopInset ? statusBarOverlap : spec.insetTop) -
      spec.insetBottom
  );

/**
 * The tallest the card gets at a detent. A `content` detent is bounded by the
 * space its insets leave alone, since it is measured from the content region.
 */
const cardHeightBound = (host: DetentsHost, spec: RawDetent) => {
  const available = availableCardHeight(
    spec,
    host.hostHeight,
    host.statusBarOverlap
  );
  let bound: number;
  switch (spec.kind) {
    case 'points':
      bound = spec.value;
      break;
    case 'percentage':
      bound = available * spec.value;
      break;
    default:
      bound = available;
  }
  return clamp(bound, 0, available);
};

/**
 * The height the content wrapper is laid out in: the card's height at the
 * tallest detent. In layout units, so divided by the scale at that detent: a
 * narrow card's region can outgrow the host.
 */
export const contentRegionHeight = (host: DetentsHost) => {
  const tallest = host.rawDetents[host.rawDetents.length - 1];
  if (tallest === undefined) return host.hostHeight;
  const height = cardHeightBound(host, tallest);
  const scale = contentScale(host, tallest);
  return scale > 0 ? height / scale : height;
};

/**
 * How far the surface extends below the host, in layout units, so that once
 * scaled it still covers the card. A card's height and width are linear in the
 * sheet's position between detents, so their ratio peaks at a detent.
 */
export const surfaceOverflow = (host: DetentsHost) => {
  let tallest = host.hostHeight;
  for (const spec of host.rawDetents) {
    const scale = contentScale(host, spec);
    if (scale > 0) {
      tallest = Math.max(tallest, cardHeightBound(host, spec) / scale);
    }
  }
  return tallest - host.hostHeight;
};

/**
 * How much the content is scaled at a detent: its card's width over the width
 * of the widest card, which the content is laid out against.
 */
const contentScale = (host: DetentsHost, spec: RawDetent) => {
  const width = host.elements?.host.clientWidth ?? 0;
  const widest = width - host.widestCardGaps;
  if (widest <= 0) return 1;
  return Math.max(0, width - spec.insetLeft - spec.insetRight) / widest;
};

const measureContentHeight = (host: DetentsHost): number | null => {
  const wrapper = host.elements?.content.firstElementChild;
  if (wrapper == null) return null;
  let marker: Element | null = null;
  for (const child of Array.from(wrapper.children)) {
    if (child.id === CONTENT_HEIGHT_MARKER_NATIVE_ID) {
      marker = child;
      break;
    }
  }
  if (!(marker instanceof HTMLElement) || !(wrapper instanceof HTMLElement)) {
    return null;
  }
  // Layout offsets ignore the sheet's own transform. Rendered rectangles
  // would pick up its sub-pixel translation as noise in the content height,
  // which would re-anchor the sheet, which would move it again.
  const height =
    marker.offsetParent === wrapper
      ? marker.offsetTop
      : marker.offsetTop - wrapper.offsetTop;
  return Number.isFinite(height) && height > 0 ? height : null;
};

export const resolveDetentSpecs = (
  host: DetentsHost
): ResolvedDetent[] | null => {
  const maxHeight = host.hostHeight;
  const measuredContentHeight =
    maxHeight > 0 ? measureContentHeight(host) : null;
  const resolved: ResolvedDetent[] = [];

  for (const [index, spec] of host.rawDetents.entries()) {
    const available = availableCardHeight(
      spec,
      maxHeight,
      host.statusBarOverlap
    );
    let height: number;
    switch (spec.kind) {
      case 'points':
        height = spec.value;
        break;
      case 'percentage':
        height = available * spec.value;
        break;
      default:
        height =
          (measuredContentHeight !== null
            ? measuredContentHeight * contentScale(host, spec)
            : null) ?? unresolvedContentDetentHeight(host, index, maxHeight);
    }
    const resolvedHeight = clamp(height, 0, available);
    const position = resolvedHeight > 0 ? resolvedHeight + spec.insetBottom : 0;
    const previous = resolved[resolved.length - 1];
    if (previous !== undefined && position < previous.position) {
      const message = `Invalid bottom sheet detent at index ${index}: resolved position ${position} is lower than previous detent position ${previous.position}. Detents must be passed in ascending order.`;
      if (host.lastReportedError !== message) {
        host.lastReportedError = message;
        host.emit((callbacks) => callbacks.onError(message));
      }
      return null;
    }
    resolved.push({
      position,
      programmatic: spec.programmatic,
      insetLeft: spec.insetLeft,
      insetRight: spec.insetRight,
      insetBottom: spec.insetBottom,
    });
  }

  host.lastReportedError = null;
  return resolved;
};

const unresolvedContentDetentHeight = (
  host: DetentsHost,
  index: number,
  maxHeight: number
) => {
  for (const spec of host.rawDetents.slice(index + 1)) {
    if (spec.kind === 'content') continue;
    const available = availableCardHeight(
      spec,
      maxHeight,
      host.statusBarOverlap
    );
    const height = spec.kind === 'points' ? spec.value : available * spec.value;
    // Whatever its inset, this detent must not rise above the next one's top.
    return (
      clamp(height, 0, available) +
      spec.insetBottom -
      host.rawDetents[index]!.insetBottom
    );
  }
  return maxHeight;
};

export const isInvalidContentDetentTarget = (
  host: DetentsHost,
  index: number
) => {
  const spec = host.rawDetents[index];
  if (spec === undefined || spec.kind !== 'content') return false;
  return measureContentHeight(host) === null;
};

export const firstOpenPosition = (detents: ReadonlyArray<ResolvedDetent>) =>
  detents.find((detent) => detent.position > 0)?.position ?? 0;

export const areNumbersEqual = (
  a: ReadonlyArray<number>,
  b: ReadonlyArray<number>
) => a.length === b.length && a.every((value, index) => value === b[index]);

export const areShadowsEqual = (
  a: BottomSheetWebConfig['shadows'],
  b: BottomSheetWebConfig['shadows']
) =>
  a.length === b.length &&
  a.every((shadow, index) => {
    const other = b[index]!;
    return (
      shadow.offsetX === other.offsetX &&
      shadow.offsetY === other.offsetY &&
      shadow.blurRadius === other.blurRadius &&
      shadow.spreadDistance === other.spreadDistance &&
      shadow.color === other.color
    );
  });

export const areRawDetentsEqual = (
  a: ReadonlyArray<RawDetent>,
  b: ReadonlyArray<RawDetent>
) =>
  a.length === b.length &&
  a.every(
    (detent, index) =>
      detent.value === b[index]!.value &&
      detent.kind === b[index]!.kind &&
      detent.programmatic === b[index]!.programmatic &&
      detent.usesStatusBarTopInset === b[index]!.usesStatusBarTopInset &&
      detent.insetTop === b[index]!.insetTop &&
      detent.insetLeft === b[index]!.insetLeft &&
      detent.insetRight === b[index]!.insetRight &&
      detent.insetBottom === b[index]!.insetBottom &&
      detent.widestCard === b[index]!.widestCard
  );

// Compared apart from `areRawDetentsEqual`: A scrim change only repaints the
// scrim, and doesn’t re-resolve the detents.
export const areScrimOpacitiesEqual = (
  a: ReadonlyArray<RawDetent>,
  b: ReadonlyArray<RawDetent>
) =>
  areNumbersEqual(
    a.map((detent) => detent.scrimOpacity),
    b.map((detent) => detent.scrimOpacity)
  );

export const areResolvedDetentsEqual = (
  a: ResolvedDetent[],
  b: ResolvedDetent[]
) =>
  a.length === b.length &&
  a.every(
    (detent, index) =>
      detent.position === b[index]!.position &&
      detent.programmatic === b[index]!.programmatic &&
      detent.insetLeft === b[index]!.insetLeft &&
      detent.insetRight === b[index]!.insetRight &&
      detent.insetBottom === b[index]!.insetBottom
  );
