import { useState, type ComponentType, type ReactNode } from 'react';
import type { NativeSyntheticEvent, StyleProp, ViewStyle } from 'react-native';
import { StyleSheet, useWindowDimensions, View } from 'react-native';

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
   * Floats the sheet up off the bottom edge by this many points — a detached /
   * "floating card" sheet. The detent cap shrinks so the sheet stays inside the
   * region above the inset, and the floating bottom edge is clipped and rounded
   * with {@link cornerRadius}. Combine with a horizontal `style` inset
   * (`{ left, right }`) for a fully floating card. Defaults to `0` (anchored to
   * the bottom edge).
   */
  bottomInset?: number;
  /**
   * Corner radius (points) for the detached sheet's floating bottom corners.
   * Match it to your surface's top radius for a uniform card. Only applied when
   * {@link bottomInset} is greater than 0. Defaults to `0` (square clip).
   */
  cornerRadius?: number;
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
    bottomInset = 0,
    cornerRadius = 0,
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
  // All real geometry — the sheet's frame, the content wrapper's bounds, and
  // the detent cap (host height minus the overlapping status-bar inset, unless
  // extendUnderStatusBar) — is measured natively from the window the sheet
  // actually lives in and flows into the shadow tree through state (see
  // BottomSheetHostView / BottomSheetHostingView). The window dimensions here
  // only size the native-overlay host for the first frame, before the overlay
  // window reports its measured geometry.
  const { width: windowWidth, height: windowHeight } = useWindowDimensions();
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
      // Detents are clamped natively against the natively computed cap;
      // pre-clamping here against a JS-estimated height would reintroduce the
      // estimate as a ceiling.
      value: Math.max(0, value),
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
            // Inline (and portal) sheets fill their container — the portal
            // host spans the provider, so a modal sheet's canvas is the real
            // provider extent, laid out by Fabric in this window. In
            // native-overlay mode the host is reparented into a separate
            // full-screen window whose measured size reaches the shadow tree
            // via state; the window dimensions here are only the first-frame
            // estimate until that arrives.
            usesNativeOverlay
              ? {
                  position: 'absolute',
                  left: 0,
                  bottom: 0,
                  width: windowWidth,
                  height: windowHeight,
                }
              : StyleSheet.absoluteFill,
            style,
          ]}
          detents={nativeDetents}
          extendUnderStatusBar={extendUnderStatusBar}
          bottomInset={bottomInset}
          cornerRadius={cornerRadius}
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
            // The wrapper fills the sheet's content region exactly: the native
            // side reports the region's top inset (the gap between the sheet
            // top and the detent cap) into the shadow tree, where it is
            // applied as Yoga top padding on the sheet node — so this in-flow
            // flex: 1 child resolves to the region the sheet can actually
            // show, on every device and in every mode.
            style={styles.contentWrapper}
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

const styles = StyleSheet.create({
  contentWrapper: { flex: 1 },
});
