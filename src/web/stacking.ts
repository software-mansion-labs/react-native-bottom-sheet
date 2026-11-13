/// <reference lib="dom" />

import { interpolate } from './scrim';
import type { BottomSheetWebConfig, ResolvedDetent } from './types';

// How much narrower a fully pushed-back card is drawn, across both sides.
// Matches `stackWidthInset` on iOS and `STACK_WIDTH_INSET` on Android.
const STACK_WIDTH_INSET = 32;
// How far a pushed-back card's top edge shows above the sheet covering it.
// Matches `stackPeek` on iOS and `STACK_PEEK` on Android.
export const STACK_PEEK = 10;

// How dark a covered card is dimmed, in black, under one stacked sheet and
// under two or more, in light and dark appearance: what native iOS sheets draw
// over a covered sheet. Matches `stackDimming` on iOS and Android.
const stackDimming = (dark: boolean) =>
  dark ? { single: 0.29, deep: 0.38 } : { single: 0.12, deep: 0.16 };

// The appearance a covered card's dimming follows, read once it is needed.
let darkScheme: MediaQueryList | null = null;
const isDarkScheme = () => {
  if (darkScheme === null) {
    darkScheme = window.matchMedia('(prefers-color-scheme: dark)');
    darkScheme.addEventListener('change', () => applyStacks());
  }
  return darkScheme.matches;
};

// How far a sheet is pushed back for `progress`, the covering sheet's linear
// progress towards its tallest detent: the curve measured on native iOS sheets,
// which push back quickly at first and ease into the end.
const easedStackProgress = (progress: number) =>
  1.38 * progress - 0.38 * progress * progress;

/** The controller's state and effects that stacking reads and drives. */
export type StackSheet = {
  readonly config: BottomSheetWebConfig | null;
  readonly hasLaidOut: boolean;
  readonly detents: ResolvedDetent[];
  readonly targetIndex: number;
  readonly hostHeight: number;
  readonly translateY: number;
  /** The host's place on the page, as of its last layout. */
  readonly hostRect: DOMRect | null;
  /** The card's left and right edges at a sheet position. */
  cardSides(position: number): { left: number; right: number };
  /**
   * Scales the sheet about a point in the container, which also carries the
   * shift: the translation stays the sheet's position alone. Null unscales it.
   */
  setStackTransform(
    transform: { scale: number; originX: number; originY: number } | null
  ): void;
  /** Dims the card while stacked sheets cover it, in 0-1. */
  setStackDim(opacity: number): void;
  /**
   * How much of the scrim is painted, in 0-1. A stacked sheet leaves the
   * background to the sheet beneath it, which dims it already.
   */
  setScrimPaint(opacity: number): void;
};

// A `stacked` modal sheet pushes back the open modal sheets beneath it, as
// native iOS sheets do: each is scaled down about its top edge and shifted up,
// so the edge peeks out above the sheet covering it. A sheet is pushed back as
// far as the `stacked` sheet above it that covers it most, whatever sits
// between them.
//
// The sheet beneath never moves on its own clock: its transform is a function
// of the positions of the sheets above, applied in the frame they move in.
//
// Dimming follows native sheets too: a covered card is dimmed on its own, and a
// stacked sheet leaves the background to the scrim of the sheet beneath it, so
// the background is dimmed once however many sheets are open.

// Every attached sheet. Few enough that each update walks them all.
const sheets: StackSheet[] = [];

export const setStackRegistration = (
  sheet: StackSheet,
  isRegistered: boolean
) => {
  const index = sheets.indexOf(sheet);
  if (isRegistered === (index !== -1)) return;
  if (isRegistered) {
    sheets.push(sheet);
  } else {
    sheets.splice(index, 1);
    sheet.setStackTransform(null);
    sheet.setStackDim(0);
    sheet.setScrimPaint(1);
  }
  applyStacks();
};

const isParticipant = (sheet: StackSheet) =>
  sheet.config !== null &&
  sheet.config.modal &&
  sheet.hasLaidOut &&
  sheet.detents.length > 0 &&
  sheet.hostRect !== null;

/** Overlays above portal sheets, then by when each last opened. */
const isAbove = (sheet: StackSheet, other: StackSheet) => {
  const a = sheet.config!;
  const b = other.config!;
  return a.nativeOverlay === b.nativeOverlay
    ? a.openOrder > b.openOrder
    : a.nativeOverlay;
};

/**
 * Where the card's top rests once the sheet settles. The push reads this
 * rather than the moving position, so two sheets never chase each other.
 */
const restingPosition = (sheet: StackSheet) =>
  sheet.detents[sheet.targetIndex]?.position ?? 0;

/**
 * How open a sheet is, from closed (0) to any open detent (1), and in between
 * as it moves: how far it dims what it covers.
 */
const openProgress = (sheet: StackSheet) =>
  interpolate(
    sheet.hostHeight - sheet.translateY,
    sheet.detents,
    sheet.detents.map((detent) => (detent.position > 0 ? 1 : 0))
  );

/** Only sheets that fill the same area stack; positions compare directly. */
const fillSameArea = (sheet: StackSheet, other: StackSheet) => {
  const a = sheet.hostRect!;
  const b = other.hostRect!;
  return (
    Math.abs(a.left - b.left) < 0.5 &&
    Math.abs(a.top - b.top) < 0.5 &&
    Math.abs(a.width - b.width) < 0.5 &&
    Math.abs(a.height - b.height) < 0.5
  );
};

/**
 * How far a sheet above, at its current position, pushes back a sheet whose
 * card rests at `resting`. Only its tallest detent can cover that card's top
 * edge, as only a native sheet's large detent pushes back the one beneath, and
 * the push is interpolated between detents by its position, like a scrim
 * opacity. A closed sheet pushes nothing, and a closing one fades its push out
 * as it goes.
 */
const stackPush = (above: StackSheet, resting: number) => {
  // How far each of its detents reaches into the strip the covered edge peeks
  // out of. Negative leaves the edge uncovered.
  const tallest = Math.max(0, ...above.detents.map((d) => d.position));
  const reaches = above.detents.map((detent) =>
    detent.position > 0 && detent.position === tallest
      ? detent.position - (resting - STACK_PEEK)
      : -1
  );
  const position = above.hostHeight - above.translateY;
  return {
    progress: easedStackProgress(
      interpolate(
        position,
        above.detents,
        reaches.map((reach) => (reach >= 0 ? 1 : 0))
      )
    ),
    shift: interpolate(
      position,
      above.detents,
      reaches.map((reach) => Math.min(Math.max(reach, 0), STACK_PEEK))
    ),
  };
};

/**
 * Pushes the sheet back as far as the `stacked` sheet above it that covers it
 * most, in progress and shift alike, and dims it under all of them. Returns
 * the stacked sheets above it.
 */
const follow = (sheet: StackSheet, participants: StackSheet[]) => {
  const above = isParticipant(sheet)
    ? participants.filter(
        (other) =>
          other !== sheet &&
          other.config?.stacked === true &&
          isAbove(other, sheet) &&
          fillSameArea(other, sheet)
      )
    : [];
  // As native sheets dim a card under one sheet, and a little more under two
  // or more. Each fades in and out with its sheet.
  const covering = above.map(openProgress).sort((a, b) => b - a);
  const dimming = stackDimming(isDarkScheme());
  sheet.setStackDim(
    dimming.single * (covering[0] ?? 0) +
      (dimming.deep - dimming.single) * (covering[1] ?? 0)
  );

  // A sheet that is closing is not pushed: the push reads where it rests.
  const resting = restingPosition(sheet);
  if (above.length === 0 || resting <= 0) {
    sheet.setStackTransform(null);
    return above;
  }
  let progress = 0;
  let shift = 0;
  for (const other of above) {
    const push = stackPush(other, resting);
    progress = Math.max(progress, push.progress);
    shift = Math.max(shift, push.shift);
  }
  const { left, right } = sheet.cardSides(resting);
  const width = right - left;
  const scale =
    width > STACK_WIDTH_INSET ? 1 - (progress * STACK_WIDTH_INSET) / width : 1;
  if (scale >= 1) {
    sheet.setStackTransform(null);
    return above;
  }
  // The container's top is the card's. Scaling about a point above it also
  // moves the top up, by the origin's distance times how much the scale takes
  // off.
  sheet.setStackTransform({
    scale,
    originX: (left + right) / 2,
    originY: -shift / (1 - scale),
  });
  return above;
};

/**
 * Brings the stack up to date after a sheet moved. Free while no sheet is
 * stacked.
 */
export const updateStacks = () => {
  if (sheets.some((sheet) => sheet.config?.stacked === true)) {
    applyStacks();
  }
};

/**
 * Finds how far every sheet is pushed back and dimmed again and brings it up
 * to date. Also run when a sheet's props change, which is when the last stacked
 * sheet can go, to put the others back.
 */
export const applyStacks = () => {
  const participants = sheets.filter(isParticipant);
  // How open the sheets beneath each stacked sheet are, which dim the
  // background already.
  const openBeneath = new Map<StackSheet, number>();
  for (const sheet of sheets) {
    const open = openProgress(sheet);
    for (const above of follow(sheet, participants)) {
      openBeneath.set(above, Math.max(openBeneath.get(above) ?? 0, open));
    }
  }
  for (const sheet of sheets) {
    sheet.setScrimPaint(1 - (openBeneath.get(sheet) ?? 0));
  }
};
