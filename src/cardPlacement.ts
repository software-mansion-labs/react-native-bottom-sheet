// Horizontal placement of the sheet's card: its width under `maxWidth` and
// where it sits for a `placement`. Kept free of React Native imports so it can
// be unit tested on its own. The iOS and Android hosts mirror `placeCard` in
// `BottomSheetHostingView+Detents.swift` and `SheetState.kt`; keep them in sync.

/**
 * Where the card sits across the host when it is narrower than the space its
 * side insets leave, as in UIKit's `UISheetPresentationController.Placement`.
 * `leading` and `trailing` follow the layout direction: `leading` is the left
 * edge in a left-to-right layout and the right edge in a right-to-left one.
 */
export type SheetPlacement = 'leading' | 'center' | 'trailing';

/**
 * The share of the host's free width left of the card, for each placement in a
 * left-to-right layout: 0 puts it on the left, 0.5 in the middle, and 1 on the
 * right. Physical, like the detents' insets, so the native hosts don't need to
 * know the layout direction.
 */
const PLACEMENT_BIAS: Readonly<Record<SheetPlacement, number>> = {
  leading: 0,
  center: 0.5,
  trailing: 1,
};

/** The card's horizontal span in the host, in points. */
export type CardSpan = Readonly<{ left: number; width: number }>;

/**
 * Places the card across a host `hostWidth` wide, in points with
 * `pixelRatio` device pixels each. Its width is the space the
 * side insets leave, capped by `maxWidth` (0 for no cap). It sits where the bias
 * puts it in the whole host, so a centered card's middle is the host's, and is
 * then moved, if needed, to keep clear of the insets, which stay the least gap
 * on each side.
 */
export const placeCard = (
  hostWidth: number,
  insetLeft: number,
  insetRight: number,
  maxWidth: number,
  bias: number,
  pixelRatio = 1
): CardSpan => {
  const available = Math.max(0, hostWidth - insetLeft - insetRight);
  const width = maxWidth > 0 ? Math.min(maxWidth, available) : available;
  // On a whole device pixel, so a centered card in an odd free width doesn't
  // blur its edges and content.
  const preferred =
    Math.round(bias * (hostWidth - width) * pixelRatio) / pixelRatio;
  const rightmost = Math.max(insetLeft, hostWidth - insetRight - width);
  return {
    left: Math.min(Math.max(preferred, insetLeft), rightmost),
    width,
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
      `Invalid bottom sheet \`placement\`: received ${JSON.stringify(placement)}. Expected 'leading', 'center' or 'trailing'.`
    );
  }
  // Leading and trailing swap sides in a right-to-left layout.
  return isRTL ? 1 - PLACEMENT_BIAS[key] : PLACEMENT_BIAS[key];
};

/**
 * A warning for side insets that leave the card no width in a host
 * `hostWidth` wide, or null when every detent's card has room. The hosts clamp
 * such a card to nothing rather than fail, since a host can get that narrow at
 * runtime (a resized window, a container animating open). A closed detent's
 * insets are already 0. `maxWidth` and `placement` alone can't cause this: the
 * card shrinks and moves to stay between the insets.
 */
export const crowdedInsetsWarning = (
  detents: ReadonlyArray<Readonly<{ insetLeft: number; insetRight: number }>>,
  hostWidth: number
): string | null => {
  if (!(hostWidth > 0)) return null;
  for (const [index, detent] of detents.entries()) {
    const gaps = detent.insetLeft + detent.insetRight;
    if (gaps >= hostWidth) {
      return `Bottom sheet detent at index ${index}: \`insets.left\` and \`insets.right\` add up to ${gaps}, which leaves no room for the sheet in its ${hostWidth}-point-wide host, so the sheet is squeezed to nothing. Keep their sum below the host's width.`;
    }
  }
  return null;
};
