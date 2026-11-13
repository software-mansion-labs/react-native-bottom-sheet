import {
  I18nManager,
  Platform,
  processColor,
  StyleSheet,
  type ProcessedColorValue,
  type StyleProp,
  type ViewStyle,
} from 'react-native';

// Identifies the zero-height marker appended after the content so the native
// hosts can measure the content height from its position. They look it up by
// this nativeID rather than by sibling index, because Fabric mounts siblings
// sorted by zIndex and a z-indexed child would otherwise take the marker's
// place. Mirrored in `BottomSheetHostingView.swift` and
// `BottomSheetHostView.kt`; keep the three in sync. The web host imports it.
export const CONTENT_HEIGHT_MARKER_NATIVE_ID =
  'swm-bottom-sheet-content-height-marker';

/**
 * The shape and shadow of a sheet's surface, stated in standard style
 * vocabulary. The native host clips the surface's fill and the content to this
 * shape, so a radius set here also rounds the sheet's content.
 */
export type SurfaceStyle = Readonly<{
  borderRadius?: number;
  borderTopLeftRadius?: number;
  borderTopRightRadius?: number;
  borderBottomLeftRadius?: number;
  borderBottomRightRadius?: number;
  borderTopStartRadius?: number;
  borderTopEndRadius?: number;
  borderBottomStartRadius?: number;
  borderBottomEndRadius?: number;
  /** iOS only. Android always draws circular corners. */
  borderCurve?: 'circular' | 'continuous';
  /**
   * Shadows cast by the card, outside its clipped shape. Outset shadows only;
   * `inset` shadows are ignored. As with React Native's own `boxShadow`,
   * Android skips them before API 28: use `elevation` for those versions.
   */
  boxShadow?: ViewStyle['boxShadow'];
  /** Android only. Casts a system shadow from the card, on every API level. */
  elevation?: ViewStyle['elevation'];
  /** The color of the iOS shadow, and of `elevation` on Android API 28+. */
  shadowColor?: ViewStyle['shadowColor'];
  /** iOS only. */
  shadowOffset?: ViewStyle['shadowOffset'];
  /** iOS only. Without an opacity above zero, there is no iOS shadow. */
  shadowOpacity?: ViewStyle['shadowOpacity'];
  /** iOS only. */
  shadowRadius?: ViewStyle['shadowRadius'];
}>;

/** A card shadow in the shape the native hosts consume. */
type NativeShadow = Readonly<{
  offsetX: number;
  offsetY: number;
  blurRadius: number;
  spreadDistance: number;
  /**
   * A processed color: an ARGB integer, or a platform color object
   * (`PlatformColor`, `DynamicColorIOS`) that the native host resolves.
   */
  color: ProcessedColorValue;
  /** Multiplied into the color. A dynamic color's alpha is unknown to JS. */
  opacity: number;
  /** Whether the shadow leaves the card's interior unpainted, as `boxShadow` does. */
  excludesInterior: boolean;
}>;

type ResolvedSurfaceStyle = Readonly<{
  /** Physical corner radii: top-left, top-right, bottom-right, bottom-left. */
  cornerRadii: number[];
  cornerCurve: 'circular' | 'continuous';
  shadows: NativeShadow[];
  elevation: number;
  shadowColor: SurfaceStyle['shadowColor'];
}>;

type BoxShadowObject = Exclude<
  NonNullable<ViewStyle['boxShadow']>,
  string
>[number];

// Mirrors React Native's own `boxShadow` string syntax: comma-separated
// shadows of 2–4 lengths, an optional color, and an optional `inset`.
const COMMA_SPLIT_PATTERN = /,(?![^()]*\))/;
const WHITESPACE_SPLIT_PATTERN = /\s+(?![^(]*\))/;
const LENGTH_PATTERN = /^([+-]?\d*\.?\d+)(px)?$/;
const DEFAULT_SHADOW_COLOR = 'black';

const invalidBoxShadowError = (value: unknown) =>
  new Error(
    `Invalid bottom sheet surfaceStyle: \`boxShadow\` received ${JSON.stringify(value)}. Expected a React Native boxShadow value.`
  );

const parseLength = (value: number | string | undefined, fallback = 0) => {
  if (value === undefined) return fallback;
  if (typeof value === 'number') return value;
  const match = LENGTH_PATTERN.exec(value);
  return match ? Number(match[1]) : NaN;
};

const parseBoxShadowString = (value: string): BoxShadowObject[] =>
  value
    .replace(/\n/g, ' ')
    .split(COMMA_SPLIT_PATTERN)
    .map((shadow) => shadow.trim())
    .filter((shadow) => shadow !== '')
    .map((shadow) => {
      const lengths: string[] = [];
      let color: string | undefined;
      let inset = false;
      for (const token of shadow.split(WHITESPACE_SPLIT_PATTERN)) {
        if (token === 'inset') {
          inset = true;
        } else if (color === undefined && processColor(token) != null) {
          color = token;
        } else {
          lengths.push(token);
        }
      }
      if (lengths.length < 2 || lengths.length > 4) {
        throw invalidBoxShadowError(value);
      }
      return {
        offsetX: lengths[0]!,
        offsetY: lengths[1]!,
        blurRadius: lengths[2],
        spreadDistance: lengths[3],
        color,
        inset,
      };
    });

/** Normalizes `boxShadow` into outset shadows with numeric lengths and colors. */
const resolveShadows = (boxShadow: ViewStyle['boxShadow']): NativeShadow[] => {
  if (boxShadow == null) return [];
  const shadows =
    typeof boxShadow === 'string' ? parseBoxShadowString(boxShadow) : boxShadow;
  return shadows.flatMap((shadow) => {
    if (shadow.inset === true) {
      if (__DEV__) {
        console.warn(
          'Bottom sheet surfaceStyle: inset `boxShadow` values are not supported and are ignored.'
        );
      }
      return [];
    }
    const offsetX = parseLength(shadow.offsetX);
    const offsetY = parseLength(shadow.offsetY);
    const blurRadius = parseLength(shadow.blurRadius);
    const spreadDistance = parseLength(shadow.spreadDistance);
    if (
      ![offsetX, offsetY, blurRadius, spreadDistance].every(Number.isFinite) ||
      blurRadius < 0
    ) {
      throw invalidBoxShadowError(boxShadow);
    }
    // Nested colors skip the view config's color processing, so process them
    // here. Platform and dynamic colors stay objects the native host resolves.
    const color = processColor(shadow.color ?? DEFAULT_SHADOW_COLOR);
    if (color == null) {
      throw invalidBoxShadowError(boxShadow);
    }
    return [
      {
        offsetX,
        offsetY,
        blurRadius,
        spreadDistance,
        color,
        opacity: 1,
        excludesInterior: true,
      },
    ];
  });
};

const invalidShadowPropError = (key: string, value: unknown) =>
  new Error(
    `Invalid bottom sheet surfaceStyle: \`${key}\` received ${JSON.stringify(value)}.`
  );

/**
 * The iOS `shadow*` props as one more card shadow, with React Native's defaults
 * for a view. They only apply on iOS: the clip would cut a shadow on the view.
 */
const resolveIOSShadow = ({
  shadowColor,
  shadowOffset,
  shadowOpacity,
  shadowRadius,
}: SurfaceStyle): NativeShadow[] => {
  if (Platform.OS !== 'ios' || !shadowOpacity) return [];
  const offsetX = shadowOffset?.width ?? 0;
  const offsetY = shadowOffset?.height ?? -3;
  if (!Number.isFinite(offsetX) || !Number.isFinite(offsetY)) {
    throw invalidShadowPropError('shadowOffset', shadowOffset);
  }
  if (!Number.isFinite(shadowOpacity)) {
    throw invalidShadowPropError('shadowOpacity', shadowOpacity);
  }
  const color = processColor(shadowColor ?? DEFAULT_SHADOW_COLOR);
  if (color == null) {
    throw invalidShadowPropError('shadowColor', shadowColor);
  }
  return [
    {
      offsetX,
      offsetY,
      // A layer's shadow radius is the sigma, half of a CSS blur radius.
      blurRadius: (shadowRadius ?? 3) * 2,
      spreadDistance: 0,
      color,
      opacity: shadowOpacity,
      excludesInterior: false,
    },
  ];
};

const invalidCornerRadiusError = (key: string, value: unknown) =>
  new Error(
    `Invalid bottom sheet surfaceStyle: \`${key}\` received ${String(value)}. Expected a finite, non-negative number.`
  );

/**
 * Resolves a surface style into physical corner radii for the native host.
 * Start/end corners take precedence over left/right ones. As in React Native's
 * own border-radius resolution, Android swaps left and right in RTL unless the
 * app opted out; iOS and web keep them physical.
 */
export const resolveSurfaceStyle = (
  style: StyleProp<SurfaceStyle>,
  isRTL: boolean
): ResolvedSurfaceStyle => {
  const flattened: SurfaceStyle = StyleSheet.flatten(style) ?? {};
  for (const [key, value] of Object.entries(flattened)) {
    if (
      key.endsWith('Radius') &&
      value != null &&
      (typeof value !== 'number' || !Number.isFinite(value) || value < 0)
    ) {
      throw invalidCornerRadiusError(key, value);
    }
  }

  const {
    borderRadius,
    borderTopLeftRadius,
    borderTopRightRadius,
    borderBottomLeftRadius,
    borderBottomRightRadius,
    borderTopStartRadius,
    borderTopEndRadius,
    borderBottomStartRadius,
    borderBottomEndRadius,
  } = flattened;
  const topLeft = isRTL ? borderTopEndRadius : borderTopStartRadius;
  const topRight = isRTL ? borderTopStartRadius : borderTopEndRadius;
  const bottomLeft = isRTL ? borderBottomEndRadius : borderBottomStartRadius;
  const bottomRight = isRTL ? borderBottomStartRadius : borderBottomEndRadius;
  const swapsSides =
    isRTL && Platform.OS === 'android' && I18nManager.doLeftAndRightSwapInRTL;
  const elevation = flattened.elevation ?? 0;
  if (!Number.isFinite(elevation) || elevation < 0) {
    throw invalidShadowPropError('elevation', flattened.elevation);
  }

  return {
    cornerRadii: [
      topLeft ??
        (swapsSides ? borderTopRightRadius : borderTopLeftRadius) ??
        borderRadius ??
        0,
      topRight ??
        (swapsSides ? borderTopLeftRadius : borderTopRightRadius) ??
        borderRadius ??
        0,
      bottomRight ??
        (swapsSides ? borderBottomLeftRadius : borderBottomRightRadius) ??
        borderRadius ??
        0,
      bottomLeft ??
        (swapsSides ? borderBottomRightRadius : borderBottomLeftRadius) ??
        borderRadius ??
        0,
    ],
    cornerCurve: flattened.borderCurve ?? 'circular',
    // The first shadow draws on top. A view's `shadow*` shadow sits below its
    // `boxShadow` in React Native.
    shadows: [
      ...resolveShadows(flattened.boxShadow),
      ...resolveIOSShadow(flattened),
    ],
    elevation,
    shadowColor: flattened.shadowColor,
  };
};

/**
 * A finite, non-negative height in points, an unsigned percentage from `0%`
 * through `100%`, or `'content'` for the measured content height.
 */
export type DetentValue = number | `${number}%` | 'content';

/**
 * The space kept around the card at a detent, in points. All sides default
 * to 0. The space above the card is set for the whole sheet, by `topInset`.
 */
export type DetentInsets = Readonly<{
  /** The space at the physical left, whatever the layout direction. */
  left?: number;
  /** The space at the physical right, whatever the layout direction. */
  right?: number;
  /**
   * The space at the side where a line starts: the left in a left-to-right
   * layout, the right in a right-to-left one. Takes precedence over `left` or
   * `right`, whichever it resolves to.
   */
  start?: number;
  /**
   * The space at the side where a line ends: the right in a left-to-right
   * layout, the left in a right-to-left one. Takes precedence over `left` or
   * `right`, whichever it resolves to.
   */
  end?: number;
  /** The space between the card's bottom and the host's bottom. */
  bottom?: number;
}>;

/** A detent with its options. A bare value takes the default for each. */
export type DetentConfig = Readonly<{
  value: DetentValue;
  /**
   * Reachable only through controlled `index` updates: Dragging skips it, and
   * the sheet doesn’t settle there.
   *
   * @default false
   */
  programmatic?: boolean;
  /**
   * The space around the card at this detent, so the sheet can float as a
   * card. Ignored at a closed detent.
   */
  insets?: DetentInsets;
}>;

/** A modal sheet’s detent with its options. */
export type ModalDetentConfig = DetentConfig &
  Readonly<{
    /**
     * In 0–1, scales the scrim color’s alpha at this detent. Interpolated
     * linearly as the sheet moves between detents. Defaults to 0 at a closed
     * detent and 1 at an open one.
     */
    scrimOpacity?: number;
  }>;

/** A detent: a bare value, or an object with its options. */
export type Detent = DetentValue | DetentConfig;

/** A modal sheet’s detent: a bare value, or an object with its options. */
export type ModalDetent = DetentValue | ModalDetentConfig;

type NormalizedDetent = Readonly<{
  value: number;
  kind: 'points' | 'percentage' | 'content';
  programmatic: boolean;
  usesStatusBarTopInset: boolean;
  insetTop: number;
  insetLeft: number;
  insetRight: number;
  insetBottom: number;
  /** Whether this is the widest open card, which content is laid out against. */
  widestCard: boolean;
  scrimOpacity: number;
}>;

/** A closed sheet is fully off-screen, whatever its detent’s insets say. */
const CLOSED_INSETS = {
  usesStatusBarTopInset: false,
  insetTop: 0,
  insetLeft: 0,
  insetRight: 0,
  insetBottom: 0,
};

const PERCENTAGE_PATTERN = /^\d+(?:\.\d+)?%$/;

const invalidPercentageError = (value: string, index: number) =>
  new Error(
    `Invalid bottom sheet detent at index ${index}: \`${value}\` is not a valid percentage. Expected an unsigned integer or decimal from 0% through 100%, without whitespace.`
  );

const invalidPointDetentError = (value: number, index: number) =>
  new Error(
    `Invalid bottom sheet detent at index ${index}: received ${String(value)}. Expected a finite, non-negative number.`
  );

const invalidDetentOptionError = (
  option: string,
  value: unknown,
  index: number,
  expected: string
) =>
  new Error(
    `Invalid bottom sheet detent at index ${index}: \`${option}\` received ${String(value)}. Expected ${expected}.`
  );

const isFiniteNonNegative = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value) && value >= 0;

/** Validates the sheet’s `topInset`. */
const resolveTopInset = (topInset: number | undefined) => {
  // `null` from plain JS means unset too, like `undefined`.
  if (topInset != null && !isFiniteNonNegative(topInset)) {
    throw new Error(
      `Invalid bottom sheet \`topInset\`: received ${String(topInset)}. Expected a finite, non-negative number.`
    );
  }
  return {
    usesStatusBarTopInset: topInset == null,
    insetTop: topInset ?? 0,
  };
};

/**
 * Reads and validates a detent’s insets into physical sides, leaving unset
 * sides at 0. `start` and `end` take precedence over the side they resolve to.
 */
const resolveDetentInsets = (
  insets: DetentInsets | undefined,
  index: number,
  isRTL: boolean
) => {
  const resolveSide = (side: keyof DetentInsets) => {
    // `null` from plain JS means unset too, like `undefined`.
    const value = insets?.[side] ?? undefined;
    if (value !== undefined && !isFiniteNonNegative(value)) {
      throw invalidDetentOptionError(
        `insets.${side}`,
        value,
        index,
        'a finite, non-negative number'
      );
    }
    return value;
  };
  const left = resolveSide('left');
  const right = resolveSide('right');
  const start = resolveSide('start');
  const end = resolveSide('end');
  return {
    insetLeft: (isRTL ? end : start) ?? left ?? 0,
    insetRight: (isRTL ? start : end) ?? right ?? 0,
    insetBottom: resolveSide('bottom') ?? 0,
  };
};

/** Parses a percentage detent into a unitless ratio for native resolution. */
const parseDetentPercentage = (value: string, index: number) => {
  if (!PERCENTAGE_PATTERN.test(value)) {
    throw invalidPercentageError(value, index);
  }

  const percentage = Number(value.slice(0, -1));
  if (!Number.isFinite(percentage) || percentage < 0 || percentage > 100) {
    throw invalidPercentageError(value, index);
  }

  return percentage / 100;
};

/** Parses a detent’s value into its kind and native value. */
const parseDetentValue = (value: DetentValue, index: number) => {
  if (typeof value === 'number') {
    if (!Number.isFinite(value) || value < 0) {
      throw invalidPointDetentError(value, index);
    }
    return { kind: 'points' as const, value };
  }
  if (value === 'content') {
    return { kind: 'content' as const, value: 0 };
  }
  return {
    kind: 'percentage' as const,
    value: parseDetentPercentage(value, index),
  };
};

/** Serializes a public detent into the shape consumed by the native views. */
const normalizeDetent = (
  detent: ModalDetent,
  index: number,
  topInset: ReturnType<typeof resolveTopInset>,
  modal: boolean,
  isRTL: boolean
): NormalizedDetent => {
  const config: ModalDetentConfig =
    typeof detent === 'object' && detent !== null ? detent : { value: detent };
  const { kind, value } = parseDetentValue(config.value, index);
  const isClosed = isNormalizedDetentClosed({ kind, value });
  const insets = resolveDetentInsets(config.insets, index, isRTL);
  // Only a modal sheet has a scrim, so an inline sheet ignores the option
  // rather than validating it.
  const scrimOpacity =
    (modal ? config.scrimOpacity : undefined) ?? (isClosed ? 0 : 1);
  if (!isFiniteNonNegative(scrimOpacity) || scrimOpacity > 1) {
    throw invalidDetentOptionError(
      'scrimOpacity',
      scrimOpacity,
      index,
      'a number from 0 through 1'
    );
  }
  return {
    value,
    kind,
    programmatic: config.programmatic === true,
    ...(isClosed ? CLOSED_INSETS : { ...topInset, ...insets }),
    widestCard: false,
    scrimOpacity,
  };
};

/**
 * Serializes the public detents, flagging the widest card. The native hosts
 * and the web controller read that flag rather than finding the widest card
 * themselves, so every platform lays out against the same one.
 */
export const normalizeDetents = (
  detents: ReadonlyArray<ModalDetent>,
  topInset: number | undefined,
  modal: boolean,
  isRTL: boolean
): NormalizedDetent[] => {
  const resolvedTopInset = resolveTopInset(topInset);
  const normalized = detents.map((detent, index) =>
    normalizeDetent(detent, index, resolvedTopInset, modal, isRTL)
  );
  const widest = findWidestDetent(normalized);
  return normalized.map((detent) =>
    detent === widest ? { ...detent, widestCard: true } : detent
  );
};

/** Whether a normalized detent represents a fully closed sheet. */
export const isNormalizedDetentClosed = (
  detent: Readonly<{ kind: string; value: number }>
) => detent.kind !== 'content' && detent.value === 0;

/**
 * The open detent with the widest card, whose tightest side gaps the content is
 * laid out against. On a tie, the tallest detent.
 */
const findWidestDetent = (detents: ReadonlyArray<NormalizedDetent>) => {
  let widest: NormalizedDetent | undefined;
  for (const detent of detents) {
    if (isNormalizedDetentClosed(detent)) continue;
    if (
      widest === undefined ||
      detent.insetLeft + detent.insetRight <=
        widest.insetLeft + widest.insetRight
    ) {
      widest = detent;
    }
  }
  return widest;
};

/** Validates a controlled index before props are passed to the native view. */
export const validateIndex = (index: number, detentCount: number) => {
  if (detentCount === 0) {
    throw new Error(
      'Invalid bottom sheet detents: received an empty array. Expected at least one detent.'
    );
  }

  if (!Number.isFinite(index) || !Number.isInteger(index)) {
    throw new Error(
      `Invalid bottom sheet index: received ${String(index)}. Expected a finite integer from 0 through ${detentCount - 1}.`
    );
  }

  if (index < 0 || index >= detentCount) {
    throw new Error(
      `Invalid bottom sheet index: received ${String(index)}. Expected a value from 0 through ${detentCount - 1}.`
    );
  }
};

const SECONDS_LIKE_SETTLE_DURATION = 10;
let hasWarnedAboutSettleDurationUnit = false;

/**
 * Validates `settleDuration`. It is a duration in milliseconds, so a negative value
 * has no meaning: it would produce a spring with no solution.
 */
export const validateSettleDuration = (settleDuration: number | undefined) => {
  if (settleDuration === undefined) return;
  if (!Number.isFinite(settleDuration) || settleDuration <= 0) {
    throw new Error(
      `Invalid bottom sheet settleDuration: received ${String(settleDuration)}. Expected a finite number of milliseconds greater than 0.`
    );
  }
  // The prop took seconds before 1.0, and a value that small in milliseconds
  // is an instant snap rather than an animation.
  if (
    __DEV__ &&
    settleDuration < SECONDS_LIKE_SETTLE_DURATION &&
    !hasWarnedAboutSettleDurationUnit
  ) {
    hasWarnedAboutSettleDurationUnit = true;
    console.warn(
      `Bottom sheet settleDuration is ${String(settleDuration)} milliseconds, which looks like a value in seconds. settleDuration takes milliseconds: pass ${String(settleDuration * 1000)} for ${String(settleDuration)} seconds.`
    );
  }
};
