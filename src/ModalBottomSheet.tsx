import { createElement } from 'react';

import {
  BottomSheet,
  type BottomSheetInternalProps,
  type BottomSheetProps,
} from './BottomSheet';

/** Props for the modal bottom-sheet variant rendered through the provider portal. */
export interface ModalBottomSheetProps extends BottomSheetProps {
  /**
   * Present the sheet in a native overlay above everything—including native
   * modal screens (e.g. a React Navigation native-stack `presentation: "modal"`)
   * —instead of the `BottomSheetProvider` portal.
   *
   * The portal renders into the provider's React tree, so a sheet opened from
   * within a native modal screen is trapped inside that screen and cannot cover
   * the full window. With `nativeOverlay`, the sheet is reparented natively into
   * a window-level overlay (a `UIWindow`-attached container on iOS, a
   * full-screen transparent dialog on Android) that floats above the modal.
   *
   * No `BottomSheetProvider` is required in this mode. The sheet sizes relative
   * to the window, so it can be colocated with the trigger even when that trigger
   * lives inside a narrow or otherwise non-full-size view.
   *
   * @default false
   */
  nativeOverlay?: boolean;
  /** Scrim color shown behind the modal sheet. */
  scrimColor?: string;
  /**
   * Scrim opacities per detent, indexed to match `detents`. Each value in 0-1
   * scales the scrim color's alpha at the detent of the same index, and the
   * opacity is linearly interpolated as the sheet is dragged between detents.
   * A shorter array than `detents` reuses its last value for any remaining
   * detents.
   *
   * The default maps each detent to 0 when it is closed and 1 otherwise,
   * so the scrim is transparent at any closed detent and fully opaque at every
   * open one; e.g., `[0, 'content']` defaults to `[0, 1]`, and all-open detents
   * default to a constant opaque scrim. Pass one value per detent, e.g.
   * `[0, 0.5, 1]`, to keep the scrim deepening across every detent.
   */
  scrimOpacities?: number[];
}

/** Bottom sheet presented above the current UI with a scrim. */
export const ModalBottomSheet = (props: ModalBottomSheetProps) => {
  // `modal` lives on the internal prop set (it is hidden from the public
  // `BottomSheet` type), so type the merged object as the internal shape rather
  // than casting it away.
  const internalProps: BottomSheetInternalProps = { ...props, modal: true };
  return createElement(BottomSheet, internalProps);
};
