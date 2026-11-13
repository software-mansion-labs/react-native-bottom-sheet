// Horizontal placement of the sheet's card: its width under `maxWidth` and
// where it sits for a `placement`. Kept free of React Native imports so it can
// be unit tested on its own. The iOS and Android hosts mirror `placeCard` in
// `BottomSheetHostingView+Card.swift` and `SheetState.kt`; keep them in sync.

/**
 * Where the card sits across the host when it is narrower than the space its
 * side insets leave, as in UIKit's `UISheetPresentationController.Placement`.
 * `leading` and `trailing` follow the layout direction: `leading` is the left
 * edge in a left-to-right layout and the right edge in a right-to-left one.
 */
export type SheetPlacement = 'leading' | 'center' | 'trailing';

export const SHEET_PLACEMENTS: ReadonlyArray<SheetPlacement> = [
  'leading',
  'center',
  'trailing',
];

/**
 * The share of the host's free width left of the card: 0 puts it on the left,
 * 0.5 in the middle, and 1 on the right. Physical, like the detents' insets,
 * so the native hosts don't need to know the layout direction.
 */
export const placementBias = (
  placement: SheetPlacement,
  isRTL: boolean
): number => {
  switch (placement) {
    case 'leading':
      return isRTL ? 1 : 0;
    case 'trailing':
      return isRTL ? 0 : 1;
    default:
      return 0.5;
  }
};

/** The card's horizontal span in the host, in points. */
export type CardSpan = Readonly<{ left: number; width: number }>;

/**
 * Places the card across a host `hostWidth` wide. Its width is the space the
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
  bias: number
): CardSpan => {
  const available = Math.max(0, hostWidth - insetLeft - insetRight);
  const width = maxWidth > 0 ? Math.min(maxWidth, available) : available;
  const preferred = bias * (hostWidth - width);
  const rightmost = Math.max(insetLeft, hostWidth - insetRight - width);
  return {
    left: Math.min(Math.max(preferred, insetLeft), rightmost),
    width,
  };
};

const isFiniteNonNegative = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value) && value >= 0;

/**
 * Validates `maxWidth` and returns the value the native hosts take: 0 for
 * none. `Infinity` is accepted as an explicit "no cap".
 */
export const resolveMaxWidth = (maxWidth: number | undefined): number => {
  // `null` from plain JS means unset too, like `undefined`.
  if (maxWidth == null || maxWidth === Infinity) return 0;
  if (
    typeof maxWidth !== 'number' ||
    !Number.isFinite(maxWidth) ||
    maxWidth <= 0
  ) {
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
  if (placement == null) return placementBias('center', isRTL);
  if (!SHEET_PLACEMENTS.includes(placement)) {
    throw new Error(
      `Invalid bottom sheet \`placement\`: received ${JSON.stringify(placement)}. Expected 'leading', 'center' or 'trailing'.`
    );
  }
  return placementBias(placement, isRTL);
};

/**
 * Throws when an open detent's side insets leave the card no width on a window
 * `windowWidth` wide, so the sheet would be pushed out of the screen. The host
 * can only be narrower than its window, so this never rejects a sheet that
 * fits. `maxWidth` and `placement` alone can't push the card out: the card
 * shrinks and moves to stay between the insets.
 */
export const validateCardFits = (
  detents: ReadonlyArray<
    Readonly<{
      insetLeft: number;
      insetRight: number;
      kind: string;
      value: number;
    }>
  >,
  windowWidth: number
) => {
  // Before the window is measured (tests, server rendering), nothing to check.
  if (!isFiniteNonNegative(windowWidth) || windowWidth === 0) return;
  detents.forEach((detent, index) => {
    const isClosed = detent.kind !== 'content' && detent.value === 0;
    if (isClosed) return;
    const gaps = detent.insetLeft + detent.insetRight;
    if (gaps >= windowWidth) {
      throw new Error(
        `Invalid bottom sheet detent at index ${index}: \`insets.left\` and \`insets.right\` add up to ${gaps}, which leaves no room for the sheet on a ${windowWidth}-point-wide window. Keep their sum below the window's width.`
      );
    }
  });
};
