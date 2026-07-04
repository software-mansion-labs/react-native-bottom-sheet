import { useState, type ComponentType, type ReactNode } from 'react';
import type { NativeSyntheticEvent, StyleProp, ViewStyle } from 'react-native';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import {
  useSafeAreaFrame,
  useSafeAreaInsets,
} from 'react-native-safe-area-context';

import BottomSheetNativeView, {
  type NativeProps,
} from './BottomSheetNativeComponent';
import BottomSheetSurfaceNativeComponent from './BottomSheetSurfaceNativeComponent';
import { Portal } from './BottomSheetProvider';
import { type Detent } from './bottomSheetUtils';
export type { Detent, DetentValue } from './bottomSheetUtils';
export { programmatic } from './bottomSheetUtils';

/**
 * Payload of the {@link BottomSheetProps.onPositionChange} event, accessed as
 * `event.nativeEvent`.
 */
export type PositionChangeEventData = Readonly<{
  /** Sheet position, in points from the bottom. */
  position: number;
  /**
   * Fractional detent index in `0..(detents.length - 1)`: `0` at the shortest
   * detent, `1` at the next, and so on, interpolated as the sheet moves between
   * them. Detents are required to be in ascending order by height. The
   * continuous counterpart of `onIndexChange`, so a backdrop or per-detent
   * animation can be driven without knowing the sheet's height.
   */
  index: number;
}>;

/**
 * Props for the inline bottom-sheet component.
 */
export interface BottomSheetProps {
  /** Sheet contents, including any scrollable content. */
  children: ReactNode;
  /**
   * Optional visual surface (background) rendered behind the content. The
   * library sizes and positions the surface natively to cover the full sheet,
   * independently of the content, so a shrinking content height never exposes
   * blank space. Put a background View here instead of inside `children` when
   * you want that shrink-safe behavior. When omitted, behavior is unchanged.
   *
   * Give the surface element a filling style such as `StyleSheet.absoluteFill`:
   * it is mounted in a full-size host, so a surface sized only by its own
   * content would collapse and not show.
   */
  surface?: ReactNode;
  /** Additional style applied to the native sheet host view. */
  style?: StyleProp<ViewStyle>;
  /**
   * Snap points for the sheet, in ascending order by height. Defaults to
   * `[0, 'content']`. Fixed detents may be taller than the measured content
   * height, so `[0, 'content', 600]` is valid when the content is shorter than
   * 600pt.
   */
  detents?: Detent[];
  /** Zero-based index into `detents`. */
  index: number;
  /** Whether the sheet should animate in on first layout. */
  animateIn?: boolean;
  /**
   * Whether the sheet should animate when the active `'content'` detent changes
   * height. Disable this when your content animates its own height.
   *
   * @default true
   */
  animateContentHeight?: boolean;
  /**
   * Whether the sheet may extend under the status bar when using full-height
   * detents. Defaults to `false`, so detents remain capped below the status bar.
   */
  extendUnderStatusBar?: boolean;
  /**
   * Called when a user-driven snap is initiated: the moment a drag commits to a
   * detent, before the animation settles. Does not fire for programmatic `index`
   * changes; you already know when you make those. Use it to keep your controlled
   * `index` state in sync. For the end of any movement, use `onSettle`.
   */
  onIndexChange?: (index: number) => void;
  /** Called when a snap animation settles, including programmatic changes. */
  onSettle?: (index: number) => void;
  /**
   * Called as the sheet position changes. A standard native direct event; read
   * `event.nativeEvent.position` (points from the bottom). To handle it on the
   * UI thread, see `wrapNativeView`.
   */
  onPositionChange?: (
    event: NativeSyntheticEvent<PositionChangeEventData>
  ) => void;
  /**
   * Wraps the native sheet view—the one that emits `onPositionChange`—before it
   * is rendered. Pass `Animated.createAnimatedComponent` to handle
   * `onPositionChange` on the UI thread with a Reanimated worklet (e.g., from
   * `useEvent`): Because the animated wrapper sits directly on the native view,
   * the worklet binds to the sheet at mount, for both inline and modal sheets,
   * without the library depending on Reanimated.
   *
   * Called once; pass a stable function (a module-level reference such as
   * `Animated.createAnimatedComponent`, not an inline lambda recreated each
   * render).
   */
  wrapNativeView?: (
    component: ComponentType<NativeProps>
  ) => ComponentType<NativeProps>;
  /**
   * Escape hatch that disables sheet/list gesture negotiation.
   * If a gesture starts inside a nested scrollable, that scrollable keeps it
   * even when it cannot scroll any further.
   */
  disableScrollableNegotiation?: boolean;
}

type ModalOnlyBottomSheetProps = {
  /** Internal flag used by `ModalBottomSheet`. */
  modal?: boolean;
  /**
   * Internal flag used by `ModalBottomSheet`. When set, the sheet is presented
   * in a native overlay above everything (including native modal screens)
   * instead of the `BottomSheetProvider` portal.
   */
  nativeOverlay?: boolean;
  /** Scrim color used by `ModalBottomSheet`. */
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
};

export type BottomSheetInternalProps = BottomSheetProps &
  ModalOnlyBottomSheetProps;

/** Native bottom sheet that renders inline within the current screen layout. */
export const BottomSheet = (props: BottomSheetProps) => {
  const {
    children,
    surface,
    style,
    detents = [0, 'content'],
    index,
    animateIn = true,
    animateContentHeight = true,
    extendUnderStatusBar = false,
    onIndexChange,
    onSettle,
    onPositionChange,
    wrapNativeView,
    modal = false,
    nativeOverlay = false,
    disableScrollableNegotiation = false,
    scrimColor,
    scrimOpacities,
  } = props as BottomSheetInternalProps;
  const usesNativeOverlay = modal && nativeOverlay;
  const { height: safeAreaFrameHeight } = useSafeAreaFrame();
  const { width: windowWidth, height: windowHeight } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const hostHeight = usesNativeOverlay ? windowHeight : safeAreaFrameHeight;
  const maxHeight = extendUnderStatusBar ? hostHeight : hostHeight - insets.top;
  const nativeDetents = detents.map((detent) => {
    const programmatic = isDetentProgrammatic(detent);
    const value = resolveDetentValue(detent);

    if (value === 'content') {
      return {
        value: 0,
        kind: 'content',
        programmatic,
      };
    }

    return {
      value: Math.max(0, Math.min(value, maxHeight)),
      kind: 'points',
      programmatic,
    };
  });

  const clampedIndex = Math.max(0, Math.min(index, nativeDetents.length - 1));
  const selectedDetentValue = detents[clampedIndex]
    ? resolveDetentValue(detents[clampedIndex])
    : 0;
  const isCollapsed = selectedDetentValue === 0;
  // Default the scrim opacity per detent: transparent at any closed detent,
  // fully opaque at every open one.
  const resolvedScrimOpacity =
    scrimOpacities ??
    detents.map((detent) => (resolveDetentValue(detent) === 0 ? 0 : 1));
  const handleIndexChange = (event: { nativeEvent: { index: number } }) => {
    onIndexChange?.(event.nativeEvent.index);
  };
  const handleSettle = (event: { nativeEvent: { index: number } }) => {
    onSettle?.(event.nativeEvent.index);
  };

  // The native sheet view, optionally wrapped (e.g. with
  // `Animated.createAnimatedComponent`) so a Reanimated worklet can handle
  // `onPositionChange` on the UI thread. Wrapping the leaf native view (rather
  // than this whole component) keeps the animated boundary on the host that
  // emits events—so it resolves at mount, inline or inside the modal portal
  // alike. Computed once: a fresh wrapped component each render would remount
  // the native sheet.
  const [NativeView] = useState(
    () =>
      (wrapNativeView?.(BottomSheetNativeView) ??
        BottomSheetNativeView) as ComponentType<
        NativeProps & { children?: ReactNode }
      >
  );

  const sheet = (
    <View
      style={StyleSheet.absoluteFill}
      pointerEvents={modal ? (isCollapsed ? 'none' : 'auto') : 'box-none'}
    >
      <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
        <NativeView
          pointerEvents="box-none"
          style={[
            {
              position: 'absolute',
              left: 0,
              right: usesNativeOverlay ? undefined : 0,
              bottom: 0,
              // The native host always spans the full height of its container.
              // Detents are still capped to `maxHeight`, so the sheet only
              // extends under the status bar when explicitly requested.
              height: hostHeight,
              width: usesNativeOverlay ? windowWidth : undefined,
            },
            style,
          ]}
          detents={nativeDetents}
          maxDetentHeight={maxHeight}
          index={index}
          animateIn={animateIn}
          animateContentHeight={animateContentHeight}
          modal={modal}
          nativeOverlay={usesNativeOverlay}
          disableScrollableNegotiation={disableScrollableNegotiation}
          scrimColor={scrimColor}
          scrimOpacities={resolvedScrimOpacity}
          onIndexChange={handleIndexChange}
          onSettle={handleSettle}
          onPositionChange={onPositionChange}
        >
          {surface != null && (
            <BottomSheetSurfaceNativeComponent
              collapsable={false}
              pointerEvents="box-none"
              style={StyleSheet.absoluteFill}
            >
              {surface}
            </BottomSheetSurfaceNativeComponent>
          )}
          <View
            collapsable={false}
            // In native-overlay mode the content is reparented into a separate
            // window. A `flex: 1` wrapper can then collapse to 0×0 *native* bounds
            // (its explicit width isn't even applied) — the children still draw via
            // overflow and measure() stays correct, but Android hit-testing can't
            // descend into a zero-bounds view, so the whole sheet is untappable.
            // This reproduced on a physical OnePlus (Android 16) but not the
            // emulator. Sizing the wrapper explicitly (independent of the parent's
            // flex layout) keeps real bounds; the height matches what `flex: 1`
            // resolved to before (the max detent height), so content-detent
            // measurement is unaffected. Inline mode keeps the original flex sizing.
            style={
              usesNativeOverlay
                ? {
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    width: windowWidth,
                    height: maxHeight,
                  }
                : { flex: 1, maxHeight }
            }
          >
            {children}
            <View collapsable={false} pointerEvents="none" />
          </View>
        </NativeView>
      </View>
    </View>
  );

  if (modal) {
    // In native-overlay mode the sheet is rendered inline; the native layer
    // reparents it into a full-screen overlay above everything (including
    // native modal screens), so it bypasses the provider portal entirely.
    if (usesNativeOverlay) {
      return sheet;
    }
    return <Portal>{sheet}</Portal>;
  }

  return sheet;
};

function isDetentProgrammatic(detent: Detent): boolean {
  if (typeof detent === 'object' && detent !== null) {
    return detent.programmatic === true;
  }
  return false;
}

function resolveDetentValue(detent: Detent) {
  if (typeof detent === 'object' && detent !== null) {
    return detent.value;
  }
  return detent;
}
