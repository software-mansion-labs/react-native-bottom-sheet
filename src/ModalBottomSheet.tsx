import { createElement } from 'react';

import {
  BottomSheet,
  type BottomSheetInternalProps,
  type BottomSheetProps,
} from './BottomSheet';

/** Props for the modal bottom-sheet variant rendered through the provider portal. */
export interface ModalBottomSheetProps extends BottomSheetProps {
  /**
   * Android only. Called when system Back or a committed predictive Back
   * requests that an open sheet close. In portal mode, an unmodified physical
   * Escape is intercepted before a focused descendant, so descendants cannot
   * consume that sequence, or handled through an AndroidX fallback when focus
   * is elsewhere and normal key routing leaves it unhandled. A portal without
   * a handler leaves Escape unhandled. With `nativeOverlay`, providing this
   * callback makes the dialog a modal close-input boundary. Back and Escape are
   * consumed without another callback while the controlled target is closing;
   * after the animation finishes, input reaches the Activity again. Without a
   * callback, Back is forwarded to the host Activity and Escape follows normal
   * key routing, preserving the legacy behavior. Predictive gesture progress
   * does not animate the sheet, and cancelling the gesture does not invoke the
   * callback.
   *
   * Portals in the same Android root share native ownership. The most recently
   * attached portal with a resolved target height above zero receives the
   * request; unresolved and zero-height portals are skipped. An open owner
   * without a handler blocks lower portal handlers while letting Back continue
   * outside the portal group and leaving Escape unhandled.
   *
   * This is a controlled request: the sheet does not change its index or
   * dismiss itself. Update `index` in the callback to close it, or leave the
   * callback as a no-op to keep it open while consuming the request. If sheet
   * content has transient state such as an open dropdown, the callback can
   * close that layer first and leave the sheet open.
   */
  onRequestClose?: () => void;
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
