import {
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
 * The card shape of a sheet, stated in standard style vocabulary. The native
 * host clips the surface and content to this shape, so a radius set here also
 * rounds the sheet's content.
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
   * `inset` shadows are ignored. Android draws them on API 28 and later.
   */
  boxShadow?: ViewStyle['boxShadow'];
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
}>;

type ResolvedSurfaceStyle = Readonly<{
  /** Physical corner radii: top-left, top-right, bottom-right, bottom-left. */
  cornerRadii: number[];
  cornerCurve: 'circular' | 'continuous';
  shadows: NativeShadow[];
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
    return [{ offsetX, offsetY, blurRadius, spreadDistance, color }];
  });
};

const invalidCornerRadiusError = (key: string, value: unknown) =>
  new Error(
    `Invalid bottom sheet surfaceStyle: \`${key}\` received ${String(value)}. Expected a finite, non-negative number.`
  );

/**
 * Resolves a surface style into physical corner radii for the native host.
 * Start/end corners take precedence over left/right ones, matching React
 * Native's own border-radius resolution.
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

  return {
    cornerRadii: [
      topLeft ?? borderTopLeftRadius ?? borderRadius ?? 0,
      topRight ?? borderTopRightRadius ?? borderRadius ?? 0,
      bottomRight ?? borderBottomRightRadius ?? borderRadius ?? 0,
      bottomLeft ?? borderBottomLeftRadius ?? borderRadius ?? 0,
    ],
    cornerCurve: flattened.borderCurve ?? 'circular',
    shadows: resolveShadows(flattened.boxShadow),
  };
};

/**
 * A finite, non-negative height in points, an unsigned percentage from `0%`
 * through `100%`, or `'content'` for the measured content height.
 */
export type DetentValue = number | `${number}%` | 'content';

/** A draggable detent or an object form that can mark a detent as programmatic-only. */
export type Detent =
  DetentValue | { value: DetentValue; programmatic?: boolean };

/** Marks a detent as reachable only via controlled `index` updates, not dragging. */
export const programmatic = (value: DetentValue): Detent => ({
  value,
  programmatic: true,
});

export const detentValue = (detent: Detent): DetentValue => {
  if (typeof detent === 'object' && detent !== null) return detent.value;
  return detent;
};

export const isDetentProgrammatic = (detent: Detent): boolean => {
  if (typeof detent === 'object' && detent !== null) {
    return detent.programmatic === true;
  }
  return false;
};

type NormalizedDetent = Readonly<{
  value: number;
  kind: 'points' | 'percentage' | 'content';
  programmatic: boolean;
}>;

const PERCENTAGE_PATTERN = /^\d+(?:\.\d+)?%$/;

const invalidPercentageError = (value: string, index: number) =>
  new Error(
    `Invalid bottom sheet detent at index ${index}: \`${value}\` is not a valid percentage. Expected an unsigned integer or decimal from 0% through 100%, without whitespace.`
  );

const invalidPointDetentError = (value: number, index: number) =>
  new Error(
    `Invalid bottom sheet detent at index ${index}: received ${String(value)}. Expected a finite, non-negative number.`
  );

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

/** Serializes a public detent into the shape consumed by the native views. */
export const normalizeDetent = (
  detent: Detent,
  index: number
): NormalizedDetent => {
  const value = detentValue(detent);
  const isProgrammatic = isDetentProgrammatic(detent);

  if (typeof value === 'number') {
    if (!Number.isFinite(value) || value < 0) {
      throw invalidPointDetentError(value, index);
    }

    return {
      value,
      kind: 'points',
      programmatic: isProgrammatic,
    };
  }

  if (value === 'content') {
    return {
      value: 0,
      kind: 'content',
      programmatic: isProgrammatic,
    };
  }

  return {
    value: parseDetentPercentage(value, index),
    kind: 'percentage',
    programmatic: isProgrammatic,
  };
};

/** Whether a normalized detent represents a fully closed sheet. */
export const isNormalizedDetentClosed = (detent: NormalizedDetent) =>
  detent.kind !== 'content' && detent.value === 0;

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

const VELOCITY_THRESHOLD = 800;

export const findSnapTarget = (
  currentTranslate: number,
  velocityY: number,
  currentIndex: number,
  allPositions: { index: number; translateY: number; isDraggable: boolean }[]
) => {
  const draggablePositions = allPositions.filter(
    (position) => position.isDraggable
  );
  const effectivePositions =
    draggablePositions.length > 0 ? draggablePositions : allPositions;

  let targetIndex = currentIndex;
  let minDistance = Infinity;

  for (const position of effectivePositions) {
    const distance = Math.abs(currentTranslate - position.translateY);
    if (distance < minDistance) {
      minDistance = distance;
      targetIndex = position.index;
    }
  }

  if (Math.abs(velocityY) > VELOCITY_THRESHOLD) {
    if (velocityY > 0) {
      const lowerPosition = effectivePositions
        .filter((position) => position.translateY > currentTranslate + 1)
        .sort((a, b) => a.translateY - b.translateY)[0];
      if (lowerPosition !== undefined) targetIndex = lowerPosition.index;
    } else {
      const upperPosition = effectivePositions
        .filter((position) => position.translateY < currentTranslate - 1)
        .sort((a, b) => b.translateY - a.translateY)[0];
      if (upperPosition !== undefined) targetIndex = upperPosition.index;
    }
  }
  return targetIndex;
};
