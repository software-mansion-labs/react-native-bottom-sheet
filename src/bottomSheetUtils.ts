/**
 * A finite, non-negative height in points, an unsigned percentage from `0%`
 * through `100%`, or `'content'` for the measured content height.
 */
export type DetentValue = number | `${number}%` | 'content';

/** A draggable detent or an object form that can mark a detent as programmatic-only. */
export type Detent =
  | DetentValue
  | { value: DetentValue; programmatic?: boolean };

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
