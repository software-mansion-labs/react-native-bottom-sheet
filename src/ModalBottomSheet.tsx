import { createElement } from 'react';

import {
  BottomSheet,
  type BottomSheetInternalProps,
  type BottomSheetProps,
} from './BottomSheet';
import type { ModalDetent } from './bottomSheetUtils';

/** Props for the modal bottom-sheet variant, presented through a portal by default. */
export interface ModalBottomSheetProps extends Omit<
  BottomSheetProps,
  'detents'
> {
  /**
   * Snap points for the sheet, as on `BottomSheet`. A detent object can also
   * set `scrimOpacity`, in 0–1, which scales the scrim color’s alpha at that
   * detent and is interpolated linearly between detents. It defaults to 0 at a
   * closed detent and 1 at an open one, so the scrim is transparent when closed
   * and fully opaque at every open detent.
   *
   * @default [0, 'content']
   */
  detents?: ModalDetent[];
  /**
   * Android and web. Called when Android system Back, a committed predictive
   * Back gesture, or a complete, unmodified physical Escape press requests that
   * an open sheet close. This is a controlled request: the sheet does not change
   * its `index` or choose a transition. The consumer decides whether and how to
   * close it. While a visible sheet is animating to a closed target, further
   * Back/Escape input is consumed without another callback until settle.
   */
  onCloseRequest?: () => void;
  /**
   * Present the sheet in a native overlay above everything—including native
   * modal screens (e.g. a React Navigation native-stack `presentation: "modal"`)
   * —instead of the `BottomSheetProvider` portal.
   *
   * The portal renders into the provider's React tree, so a sheet opened from
   * within a native modal screen is trapped inside that screen and cannot cover
   * the full window. With `nativeOverlay`, the sheet is reparented natively into
   * a window-level overlay (a `UIWindow`-attached container on iOS, a
   * full-screen transparent dialog on Android, a fixed-position layer in
   * `document.body` on the web) that floats above the modal.
   *
   * No `BottomSheetProvider` is required in this mode. The sheet sizes relative
   * to the window, so it can be colocated with the trigger even when that trigger
   * lives inside a narrow or otherwise non-full-size view.
   *
   * @default false
   */
  nativeOverlay?: boolean;
  /**
   * Stack this sheet onto the open modal sheets beneath it, as native iOS
   * sheets do: at its tallest detent, this sheet pushes back each sheet whose
   * top edge it covers, scaling it down so its edge peeks out above this one,
   * even with a shorter sheet between them. Its other detents leave the sheets
   * beneath in place, and the effect follows this sheet as it is dragged or
   * settles between its two tallest detents.
   *
   * Like a native sheet, a stacked sheet's card stops 10 points below where the
   * default top inset would let it rise, so a full-height stacked sheet rests
   * 10 points lower and the sheet beneath shrinks in place; shorter detents are
   * unaffected. An explicit `topInset` is respected, and the sheet beneath is
   * then shifted up as far as its edge needs to show.
   *
   * It dims like a native sheet as well: each sheet it covers is dimmed on its
   * own card, by 12% black under one stacked sheet and 16% under two or more
   * (29% and 38% in dark appearance), and this sheet's scrim is not painted over the open sheets beneath it,
   * whose scrim already dims the background, while a tap there still reaches
   * it.
   *
   * Set it on the sheet that goes on top. Only sheets that fill the same area
   * stack: a `nativeOverlay` sheet over a portal sheet does when the provider
   * spans the window.
   *
   * @default false
   */
  stacked?: boolean;
  /** Scrim color shown behind the modal sheet. */
  scrimColor?: string;
  /**
   * The label screen readers announce for the scrim while tapping it would
   * dismiss the sheet, when the scrim is exposed as a dismiss button. Pass a
   * translated string to localize it.
   *
   * @default 'Dismiss'
   */
  scrimAccessibilityLabel?: string;
}

/** Bottom sheet presented above the current UI with a scrim. */
export const ModalBottomSheet = (props: ModalBottomSheetProps) => {
  // `modal` lives on the internal prop set (it is hidden from the public
  // `BottomSheet` type), so type the merged object as the internal shape rather
  // than casting it away.
  const internalProps: BottomSheetInternalProps = { ...props, modal: true };
  return createElement(BottomSheet, internalProps);
};
