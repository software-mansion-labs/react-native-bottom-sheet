// Horizontal placement of the sheet's card: the column `maxWidth` gives it and
// where that sits for a `placement`. Kept free of React Native imports so it can
// be unit tested on its own. The iOS and Android hosts mirror `placeCard` in
// `BottomSheetHostingView+Detents.swift` and `SheetState.kt`; keep them in sync.

/**
 * Where the sheet sits across the host when `maxWidth` makes it narrower than
 * the host, as in UIKit's `UISheetPresentationController.Placement`. `start`
 * and `end` follow the layout direction, like the detents' `start` and `end`
 * insets: `start` is the left edge in a left-to-right layout and the right edge
 * in a right-to-left one.
 */
export type SheetPlacement = 'start' | 'center' | 'end';

/**
 * The share of the host's free width left of the sheet's column, for each
 * placement in a left-to-right layout: 0 puts it on the left, 0.5 in the
 * middle, and 1 on the right. Physical, like the detents' insets, so the native
 * hosts don't need to know the layout direction.
 */
const PLACEMENT_BIAS: Readonly<Record<SheetPlacement, number>> = {
  start: 0,
  center: 0.5,
  end: 1,
};

/** The card's horizontal span in the host, in points. */
export type CardSpan = Readonly<{ left: number; width: number }>;

/**
 * The width of the sheet's column in a host `hostWidth` wide: the host's
 * width, capped by `maxWidth` (0 for no cap). The card and its side insets
 * share the column, so a capped sheet looks as it would on a screen that wide.
 */
export const columnWidth = (hostWidth: number, maxWidth: number) =>
  maxWidth > 0 ? Math.min(maxWidth, hostWidth) : hostWidth;

/**
 * Places the card across a host `hostWidth` wide, in points with `pixelRatio`
 * device pixels each. The sheet's column, at most `maxWidth` wide (0 for no
 * cap), sits where the bias puts it in the host, and the card fills the column
 * between its side insets, as it would fill a screen that wide.
 */
export const placeCard = (
  hostWidth: number,
  insetLeft: number,
  insetRight: number,
  maxWidth: number,
  bias: number,
  pixelRatio = 1
): CardSpan => {
  const column = columnWidth(hostWidth, maxWidth);
  // On a whole device pixel, so a centered column in an odd free width doesn't
  // blur the card's edges and content.
  const columnLeft =
    Math.round(bias * (hostWidth - column) * pixelRatio) / pixelRatio;
  return {
    left: columnLeft + insetLeft,
    width: Math.max(0, column - insetLeft - insetRight),
  };
};

/**
 * Validates `maxWidth` and returns the value the native hosts take: 0 for
 * none. `Infinity` is accepted as an explicit "no cap".
 */
export const resolveMaxWidth = (maxWidth: number | undefined): number => {
  // `null` from plain JS means unset too, like `undefined`.
  if (maxWidth == null || maxWidth === Infinity) return 0;
  if (!Number.isFinite(maxWidth) || maxWidth <= 0) {
    throw new Error(
      `Invalid bottom sheet \`maxWidth\`: received ${String(maxWidth)}. Expected a number greater than 0.`
    );
  }
  return maxWidth;
};

/** Validates `placement` and resolves it to a physical bias. */
export const resolvePlacement = (
  placement: SheetPlacement | undefined,
  isRTL: boolean
): number => {
  const key = placement ?? 'center';
  // `hasOwn` keeps inherited keys such as `'toString'` out.
  if (!Object.hasOwn(PLACEMENT_BIAS, key)) {
    throw new Error(
      `Invalid bottom sheet \`placement\`: received ${JSON.stringify(placement)}. Expected 'start', 'center' or 'end'.`
    );
  }
  // Start and end swap sides in a right-to-left layout.
  return isRTL ? 1 - PLACEMENT_BIAS[key] : PLACEMENT_BIAS[key];
};

/**
 * A warning for side insets that leave the card no width in a host
 * `hostWidth` wide under `maxWidth` (0 for no cap), or null when every
 * detent's card has room. The hosts clamp such a card to nothing rather than
 * fail, since a host can get that narrow at runtime (a resized window, a
 * container animating open). A closed detent's insets are already 0.
 */
export const crowdedInsetsWarning = (
  detents: ReadonlyArray<Readonly<{ insetLeft: number; insetRight: number }>>,
  hostWidth: number,
  maxWidth: number
): string | null => {
  if (!(hostWidth > 0)) return null;
  const column = columnWidth(hostWidth, maxWidth);
  const room =
    column < hostWidth
      ? `its ${column}-point \`maxWidth\``
      : `its ${hostWidth}-point-wide host`;
  for (const [index, detent] of detents.entries()) {
    const gaps = detent.insetLeft + detent.insetRight;
    if (gaps >= column) {
      return `Bottom sheet detent at index ${index}: \`insets.left\` and \`insets.right\` add up to ${gaps}, which leaves no room for the sheet in ${room}, so the sheet is squeezed to nothing. Keep their sum below the sheet's width.`;
    }
  }
  return null;
};
