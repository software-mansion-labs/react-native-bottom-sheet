/// <reference lib="dom" />

import { clamp } from './dom';
import type { SheetSnapping } from './snapping';
import type {
  BottomSheetWebConfig,
  BottomSheetWebElements,
  RawDetent,
  ResolvedDetent,
} from './types';

/** The controller's state that the scrim's opacity derives from. */
export type ScrimHost = {
  readonly elements: BottomSheetWebElements | null;
  readonly config: BottomSheetWebConfig | null;
  readonly hasLaidOut: boolean;
  readonly isPanning: boolean;
  readonly scrimPinnedFull: boolean;
  readonly detents: ResolvedDetent[];
  readonly hostHeight: number;
  /** The closed detent a scrim press dismisses to, while one would. */
  readonly scrimDismissTargetIndex: number | null;
  readonly snapping: Pick<SheetSnapping, 'isSpringActive' | 'isTargetClosed'>;
};

export const applyScrim = (host: ScrimHost, position: number) => {
  const scrim = host.elements?.scrim;
  if (!scrim || host.config === null) return;
  let opacity = 0;
  // Until the first layout places the sheet, and while settled on the
  // closed detent (where detent updates can report a stale non-zero
  // position), keep the scrim hidden.
  const isSettledClosed =
    host.snapping.isTargetClosed() &&
    !host.snapping.isSpringActive &&
    !host.isPanning;
  if (host.config.modal && host.hasLaidOut && !isSettledClosed) {
    // While the sheet is fully open and only its geometry is resizing, hold
    // the fully-open opacity instead of dipping it until the re-anchor ends.
    opacity = host.scrimPinnedFull
      ? scrimOpacity(
          Math.max(0, ...host.detents.map((detent) => detent.position)),
          host.config.detents,
          host.detents
        )
      : scrimOpacity(position, host.config.detents, host.detents);
  }
  const isVisible = opacity > 0.001;
  scrim.style.opacity = String(isVisible ? opacity : 0);
  scrim.style.visibility = isVisible ? 'visible' : 'hidden';
  scrim.style.pointerEvents = isVisible ? 'auto' : 'none';
  applyScrimDismiss(host, position);
};

/**
 * Exposes the scrim's dismiss button to screen readers while a press would
 * dismiss the sheet, scaled to the part of the scrim above the sheet. A
 * transform, so following the sheet never lays anything out, and the
 * button's accessible bounds never reach under the sheet, where a screen
 * reader exploring by touch would announce it over the sheet's content.
 */
const applyScrimDismiss = (host: ScrimHost, position: number) => {
  const button = host.elements?.scrimDismiss;
  if (!button) return;
  const sheetTop = clamp(host.hostHeight - position, 0, host.hostHeight);
  const isExposed = host.scrimDismissTargetIndex !== null && sheetTop >= 1;
  // Only an exposed button follows the sheet; a hidden one keeps its last
  // scale until shown.
  if (isExposed) {
    const scale = host.hostHeight > 0 ? sheetTop / host.hostHeight : 0;
    button.style.transform = `scaleY(${scale})`;
  }
  const visibility = isExposed ? 'visible' : 'hidden';
  if (button.style.visibility === visibility) return;
  button.style.visibility = visibility;
  if (isExposed) {
    button.removeAttribute('aria-hidden');
  } else {
    button.setAttribute('aria-hidden', 'true');
  }
};

const scrimOpacity = (
  position: number,
  rawDetents: ReadonlyArray<RawDetent>,
  detents: ResolvedDetent[]
) => {
  const values = detents.map((_, index) =>
    clamp(rawDetents[index]?.scrimOpacity ?? 1, 0, 1)
  );
  return interpolate(position, detents, values);
};

/** Fractional detent index, interpolated by position between detents. */
export const detentIndex = (position: number, detents: ResolvedDetent[]) =>
  interpolate(
    position,
    detents,
    detents.map((_, index) => index)
  );

const interpolate = (
  position: number,
  detents: ResolvedDetent[],
  values: number[]
) => {
  const pairs = detents
    .map((detent, index) => ({
      position: detent.position,
      value: values[index]!,
    }))
    .sort((a, b) => a.position - b.position);
  const first = pairs[0];
  const last = pairs[pairs.length - 1];
  if (first === undefined || last === undefined) return 0;
  if (position <= first.position) return first.value;
  if (position >= last.position) return last.value;
  for (let i = 1; i < pairs.length; i++) {
    const upper = pairs[i]!;
    if (position <= upper.position) {
      const lower = pairs[i - 1]!;
      const span = upper.position - lower.position;
      const t = span <= 0 ? 1 : (position - lower.position) / span;
      return lower.value + (upper.value - lower.value) * t;
    }
  }
  return last.value;
};
