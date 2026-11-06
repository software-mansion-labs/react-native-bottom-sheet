import { useMemo, useState, type ComponentType, type ReactNode } from 'react';
import type { NativeSyntheticEvent, StyleProp, ViewStyle } from 'react-native';
import {
  I18nManager,
  StyleSheet,
  useWindowDimensions,
  View,
} from 'react-native';

import {
  BottomSheetContentWrapperView,
  BottomSheetView,
  BottomSheetSurfaceView,
} from './BottomSheetHostViews';
import type { NativeProps } from './BottomSheetNativeComponent';
import { Portal } from './BottomSheetProvider';
import {
  CONTENT_HEIGHT_MARKER_NATIVE_ID,
  type Detent,
  isNormalizedDetentClosed,
  type ModalDetent,
  normalizeDetents,
  resolveSurfaceStyle,
  type SurfaceStyle,
  validateIndex,
} from './bottomSheetUtils';
export type {
  Detent,
  DetentConfig,
  DetentInsets,
  DetentValue,
  ModalDetent,
  ModalDetentConfig,
  SurfaceStyle,
} from './bottomSheetUtils';

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
   * them. Detents are required to be in ascending order by position. The
   * continuous counterpart of `onIndexChange`, so a backdrop or per-detent
   * animation can be driven without knowing the sheet's height.
   */
  index: number;
}>;

export type ScrollableNegotiationMode = 'none' | 'initial' | 'handoff';

export type ScrollableNegotiation =
  | ScrollableNegotiationMode
  | Readonly<{
      expand: ScrollableNegotiationMode;
      collapse: ScrollableNegotiationMode;
    }>;

const SCROLLABLE_NEGOTIATION_LEVEL: Record<ScrollableNegotiationMode, number> =
  {
    none: 0,
    initial: 1,
    handoff: 2,
  };

const DEFAULT_SCROLLABLE_NEGOTIATION = {
  expand: 'handoff',
  collapse: 'initial',
} as const satisfies Exclude<ScrollableNegotiation, string>;

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
  /**
   * The card shape of the sheet: corner radii, `borderCurve`, and shadows
   * (`boxShadow`, `elevation`, and the `shadow*` props). The native host clips
   * both `surface` and `children` to this shape, so content near a rounded
   * corner is cut to it, and casts the shadows outside it. State the shape here
   * rather than on the `surface` element, which only paints the background.
   */
  surfaceStyle?: StyleProp<SurfaceStyle>;
  /** Additional style applied to the native sheet host view. */
  style?: StyleProp<ViewStyle>;
  /**
   * Snap points for the sheet, in ascending order by position. Defaults to
   * `[0, 'content']` and must contain at least one value. Numbers must be finite
   * and non-negative. Percentage strings must be unsigned values from `0%`
   * through `100%` and resolve against the native usable height. Fixed detents
   * may be taller than the measured content or available height; native layout
   * caps them to the available geometry.
   *
   * Each detent is a bare value or an object with its options:
   * `programmatic`, `insets`, and, on `ModalBottomSheet`, `scrimOpacity`. A
   * detent without an option takes its default.
   *
   * A detent’s value is the height of the card, and percentages resolve against
   * the available space: the host’s height minus `topInset` and that detent’s
   * bottom inset. So `'100%'` fills the space between the gaps and never covers
   * them. Detents ascend by the position of the card’s top: its height plus its
   * bottom inset.
   *
   * A detent’s `insets` keep space around the card, so the sheet can float as
   * a card. They are linearly interpolated as the sheet moves between detents;
   * below the first open detent the card keeps its shape and slides down
   * through the bottom inset. A closed detent ignores its insets.
   *
   * Insets never relayout the surface or content. They are laid out against
   * the widest card, the detent with the tightest side insets, and scaled
   * uniformly to fit narrower ones, so full-width content and an image surface
   * always touch the card's edges. A `'content'` detent's card is its scaled
   * height. `measure` and `measureInWindow` report the scaled position and
   * size, `nativeOverlay` included, except on the web, where React Native
   * Web's `measure` ignores transforms and only `measureInWindow` follows the
   * card.
   *
   * Space in an inset isn't part of the sheet: touches there reach the scrim or
   * whatever is behind it.
   */
  detents?: Detent[];
  /**
   * The minimum space between the card’s top and the host’s top, in points,
   * at every detent. Defaults to the part of the status bar that overlaps the
   * host, so the card stops below it; pass a lower value, or `0`, to go under
   * the status bar.
   */
  topInset?: number;
  /**
   * Finite, zero-based integer in `0..(detents.length - 1)`. When shortening
   * `detents`, update `index` to remain in range in the same render.
   */
  index: number;
  /**
   * Whether a sheet that is open when it first appears, such as at app launch,
   * animates in. Opening a closed sheet always animates.
   */
  animateIn?: boolean;
  /**
   * Whether the sheet should animate when the active `'content'` detent changes
   * height. Disable this when your content animates its own height.
   *
   * @default true
   */
  animateContentHeight?: boolean;
  /**
   * Whether to keep the sheet in the tree only while it is open. The native
   * sheet view and its content mount in the same render that `index` selects an
   * open detent, so the content is in place before the sheet starts opening, and
   * unmount once the sheet settles at a closed detent, so the content stays
   * visible throughout the closing animation. While unmounted, the sheet emits
   * no `onPositionChange` events. Has no effect when no detent is closed.
   *
   * @default false
   */
  lazy?: boolean;
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
   * Controls how gestures that start in nested scrollables interact with the
   * sheet. A string applies to both directions; an object configures expansion
   * and collapse independently. `initial` selects one owner at touch-down,
   * while `handoff` also permits ownership to transfer without lifting.
   *
   * @default { expand: 'handoff', collapse: 'initial' }
   */
  scrollableNegotiation?: ScrollableNegotiation;
  /** @deprecated Use `scrollableNegotiation="none"` instead. */
  disableScrollableNegotiation?: boolean;
}

type ModalOnlyBottomSheetProps = {
  /** Internal flag used by `ModalBottomSheet`. */
  modal?: boolean;
  /** Android and web controlled close request callback used by `ModalBottomSheet`. */
  onCloseRequest?: () => void;
  /**
   * Internal flag used by `ModalBottomSheet`. When set, the sheet is presented
   * in a native overlay above everything (including native modal screens)
   * instead of the `BottomSheetProvider` portal.
   */
  nativeOverlay?: boolean;
  /** Scrim color used by `ModalBottomSheet`. */
  scrimColor?: string;
};

export type BottomSheetInternalProps = Omit<BottomSheetProps, 'detents'> &
  ModalOnlyBottomSheetProps & {
    /** `ModalBottomSheet`’s detents can also set a scrim opacity. */
    detents?: ModalDetent[];
  };

// Incremented whenever any sheet opens, so the most recently opened sheet
// stacks on top and receives Back and Escape.
let lastOpenOrder = 0;

/** Native bottom sheet that renders inline within the current screen layout. */
export const BottomSheet = (props: BottomSheetProps) => {
  const {
    children,
    surface,
    surfaceStyle,
    style,
    detents = [0, 'content'],
    topInset,
    index,
    animateIn = true,
    animateContentHeight = true,
    lazy = false,
    onIndexChange,
    onSettle,
    onPositionChange,
    onCloseRequest,
    wrapNativeView,
    modal = false,
    nativeOverlay = false,
    scrollableNegotiation,
    disableScrollableNegotiation,
    scrimColor,
  } = props as BottomSheetInternalProps;
  const resolvedScrollableNegotiation =
    scrollableNegotiation ??
    (disableScrollableNegotiation
      ? ('none' as const)
      : DEFAULT_SCROLLABLE_NEGOTIATION);
  const resolvedExpandNegotiation =
    typeof resolvedScrollableNegotiation === 'string'
      ? resolvedScrollableNegotiation
      : resolvedScrollableNegotiation.expand;
  const resolvedCollapseNegotiation =
    typeof resolvedScrollableNegotiation === 'string'
      ? resolvedScrollableNegotiation
      : resolvedScrollableNegotiation.collapse;
  const usesNativeOverlay = modal && nativeOverlay;
  // All real geometry — the sheet's frame, the content wrapper's bounds, and
  // the default top inset (the overlapping status-bar inset) — is measured
  // natively from the window the sheet actually lives in and flows into the
  // shadow tree through state (see BottomSheetHostView /
  // BottomSheetHostingView). The window dimensions here
  // only size the native-overlay host for the first frame, before the overlay
  // window reports its measured geometry.
  const { width: windowWidth, height: windowHeight } = useWindowDimensions();
  // Percentage detents stay as unitless ratios and are resolved against the
  // host's height whenever detents are refreshed. Point detents retain
  // their requested height; native layout clamps both kinds to it.
  validateIndex(index, detents.length);
  const normalizedDetents = normalizeDetents(detents, topInset, modal);

  const selectedNormalizedDetent = normalizedDetents[index]!;
  const isSheetClosed = isNormalizedDetentClosed(selectedNormalizedDetent);
  const hasCloseRequestHandler = onCloseRequest != null;
  // Whether the sheet is open or still closing. Set during render so a lazy
  // sheet mounts in the same commit as the open index.
  const [isSheetActive, setIsSheetActive] = useState(!isSheetClosed);
  // `animateIn` only covers the initial presentation, but a lazy sheet's native
  // view also runs its first layout on every later open, which must animate.
  const [hasOpenedFromClosed, setHasOpenedFromClosed] = useState(false);
  if (!isSheetClosed && !isSheetActive) {
    setIsSheetActive(true);
    setHasOpenedFromClosed(true);
  }
  // The surface and content are laid out against the widest card, and the
  // native host scales them down to narrower ones. Stated as start/end because
  // insets are physical, while left/right may swap under RTL.
  const widestDetent = normalizedDetents.find((detent) => detent.widestCard);
  const contentLeft = widestDetent?.insetLeft ?? 0;
  const contentRight = widestDetent?.insetRight ?? 0;
  // When the sheet last opened, relative to other sheets: 0 until it first
  // opens. This includes reopening while it is still closing.
  const [wasSheetClosed, setWasSheetClosed] = useState(isSheetClosed);
  const [openOrder, setOpenOrder] = useState(() =>
    isSheetClosed ? 0 : ++lastOpenOrder
  );
  if (isSheetClosed !== wasSheetClosed) {
    setWasSheetClosed(isSheetClosed);
    if (!isSheetClosed) {
      setOpenOrder(++lastOpenOrder);
    }
  }
  const { cornerRadii, cornerCurve, shadows, elevation, shadowColor } = useMemo(
    () => resolveSurfaceStyle(surfaceStyle, I18nManager.isRTL),
    [surfaceStyle]
  );
  const handleIndexChange = (event: { nativeEvent: { index: number } }) => {
    onIndexChange?.(event.nativeEvent.index);
  };
  const handleSettle = (event: { nativeEvent: { index: number } }) => {
    // Don't compare against `index`: the settle can arrive in the same batch as
    // the `onIndexChange` that closes the sheet, before `index` catches up. If
    // a newer open index superseded this settle, the render-time check above
    // reactivates the sheet before commit, so it never unmounts.
    const settledDetent = normalizedDetents[event.nativeEvent.index];
    if (settledDetent != null && isNormalizedDetentClosed(settledDetent)) {
      setIsSheetActive(false);
    }
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
      (wrapNativeView?.(BottomSheetView) ?? BottomSheetView) as ComponentType<
        NativeProps & { children?: ReactNode }
      >
  );

  if (lazy && !isSheetActive) {
    return null;
  }

  const sheet = (
    <View
      style={StyleSheet.absoluteFill}
      pointerEvents={modal ? (isSheetClosed ? 'none' : 'auto') : 'box-none'}
      // Keep both ancestor wrappers real native views (Android): this outer
      // one toggles pointerEvents ('none' <-> 'auto') on open/close, which is
      // exactly the prop change that forces Fabric to unflatten it and emit
      // the reparenting batch that can miss the Create mutation. The content
      // views below the native view already pin collapsable={false}; the
      // ancestors were the remaining flattenable gap. See issue #78.
      collapsable={false}
    >
      <View
        pointerEvents="box-none"
        style={StyleSheet.absoluteFill}
        collapsable={false}
      >
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
          detents={normalizedDetents}
          index={index}
          animateIn={animateIn || (lazy && hasOpenedFromClosed)}
          animateContentHeight={animateContentHeight}
          modal={modal}
          nativeOverlay={usesNativeOverlay}
          openOrder={openOrder}
          hasCloseRequestHandler={hasCloseRequestHandler}
          scrollableExpandNegotiation={
            SCROLLABLE_NEGOTIATION_LEVEL[resolvedExpandNegotiation]
          }
          scrollableCollapseNegotiation={
            SCROLLABLE_NEGOTIATION_LEVEL[resolvedCollapseNegotiation]
          }
          scrimColor={scrimColor}
          surfaceCornerRadii={cornerRadii}
          surfaceCornerCurve={cornerCurve}
          surfaceShadows={shadows}
          surfaceElevation={elevation}
          surfaceShadowColor={shadowColor}
          onIndexChange={handleIndexChange}
          onSettle={handleSettle}
          onPositionChange={onPositionChange}
          onCloseRequest={onCloseRequest}
        >
          {surface != null && (
            <BottomSheetSurfaceView
              collapsable={false}
              pointerEvents="box-none"
              style={[
                styles.surface,
                {
                  start: I18nManager.isRTL ? contentRight : contentLeft,
                  end: I18nManager.isRTL ? contentLeft : contentRight,
                },
              ]}
            >
              {surface}
            </BottomSheetSurfaceView>
          )}
          <BottomSheetContentWrapperView
            collapsable={false}
            // The wrapper fills the sheet's content region exactly: the native
            // side reports the region's inset (the gap between the sheet's
            // height and the tallest detent, clamped to the space its insets leave) into
            // the shadow tree, where it is applied as Yoga bottom padding on
            // the sheet node, or as this wrapper's overflow when a narrow card's
            // region outgrows the sheet — so this in-flow flex: 1 child resolves
            // to the region the sheet can actually show, on every device and in
            // every mode. Its own component so `measure` includes the card's
            // scale, which its shadow node reports.
            style={[
              styles.contentWrapper,
              {
                marginStart: I18nManager.isRTL ? contentRight : contentLeft,
                marginEnd: I18nManager.isRTL ? contentLeft : contentRight,
              },
            ]}
          >
            {children}
            <View
              collapsable={false}
              nativeID={CONTENT_HEIGHT_MARKER_NATIVE_ID}
              pointerEvents="none"
            />
          </BottomSheetContentWrapperView>
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
    return <Portal order={openOrder}>{sheet}</Portal>;
  }

  return sheet;
};

const styles = StyleSheet.create({
  surface: { position: 'absolute', top: 0, bottom: 0 },
  contentWrapper: { flex: 1 },
});
