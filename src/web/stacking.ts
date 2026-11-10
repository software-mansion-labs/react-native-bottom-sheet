/// <reference lib="dom" />

import { interpolate } from './scrim';
import type { BottomSheetWebConfig, ResolvedDetent } from './types';

// How much narrower a fully pushed-back card is drawn, across both sides.
// Matches `stackWidthInset` on iOS and `STACK_WIDTH_INSET` on Android.
const STACK_WIDTH_INSET = 32;
// How far a pushed-back card's top edge shows above the sheet covering it.
// Matches `stackPeek` on iOS and `STACK_PEEK` on Android.
export const STACK_PEEK = 10;

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
};

// A `stacked` modal sheet pushes back the open modal sheets beneath it, as
// native iOS sheets do: each is scaled down about its top edge and shifted up,
// so the edge peeks out above the sheet covering it. A sheet is pushed back as
// far as the `stacked` sheet above it that covers it most, whatever sits
// between them.
//
// The sheet beneath never moves on its own clock: its transform is a function
// of the positions of the sheets above, applied in the frame they move in.

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
  }
  updateStacks();
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
 * most, in progress and shift alike.
 */
const follow = (sheet: StackSheet, participants: StackSheet[]) => {
  const resting = restingPosition(sheet);
  if (!isParticipant(sheet) || resting <= 0) {
    sheet.setStackTransform(null);
    return;
  }

  let progress = 0;
  let shift = 0;
  for (const other of participants) {
    if (
      other === sheet ||
      other.config?.stacked !== true ||
      !isAbove(other, sheet) ||
      !fillSameArea(other, sheet)
    ) {
      continue;
    }
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
    return;
  }
  // The container's top is the card's. Scaling about a point above it also
  // moves the top up, by the origin's distance times how much the scale takes
  // off.
  sheet.setStackTransform({
    scale,
    originX: (left + right) / 2,
    originY: -shift / (1 - scale),
  });
};

/**
 * Finds how far every sheet is pushed back again and brings its transform up
 * to date. Cheap enough to run on every frame a sheet moves in: without a
 * stacked sheet it only finds each sheet unscaled, as it already is.
 */
export const updateStacks = () => {
  const participants = sheets.filter(isParticipant);
  for (const sheet of sheets) {
    follow(sheet, participants);
  }
};
