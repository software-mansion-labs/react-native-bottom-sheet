export type DetentValue = number | 'max';

export type Detent =
  | DetentValue
  | { value: DetentValue; programmatic?: boolean };

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

const VELOCITY_THRESHOLD = 800;

export const findSnapTarget = (
  currentTranslate: number,
  velocityY: number,
  currentIndex: number,
  allPositions: { index: number; translateY: number; isDraggable: boolean }[]
) => {
  'worklet';
  const candidates = allPositions.filter((pos) => pos.isDraggable);
  const effectivePositions = candidates.length > 0 ? candidates : allPositions;
  let targetIndex = currentIndex;
  let minDistance = Infinity;
  for (const pos of effectivePositions) {
    const distance = Math.abs(currentTranslate - pos.translateY);
    if (distance < minDistance) {
      minDistance = distance;
      targetIndex = pos.index;
    }
  }
  if (Math.abs(velocityY) > VELOCITY_THRESHOLD) {
    if (velocityY > 0) {
      const lower = effectivePositions
        .filter((pos) => pos.translateY > currentTranslate + 1)
        .sort((a, b) => a.translateY - b.translateY)[0];
      if (lower !== undefined) targetIndex = lower.index;
    } else {
      const upper = effectivePositions
        .filter((pos) => pos.translateY < currentTranslate - 1)
        .sort((a, b) => b.translateY - a.translateY)[0];
      if (upper !== undefined) targetIndex = upper.index;
    }
  }
  return targetIndex;
};

export const resolveDetent = (
  detent: Detent,
  contentHeight: number,
  maxHeight: number
) => {
  const raw = detentValue(detent);
  if (typeof raw === 'number') return raw;
  if (raw === 'max') {
    return contentHeight > 0 ? Math.min(contentHeight, maxHeight) : maxHeight;
  }
  throw new Error(`Invalid detent: \`${raw}\`.`);
};

export const clampIndex = (index: number, detentCount: number) => {
  if (detentCount <= 0) return 0;
  return Math.min(Math.max(index, 0), detentCount - 1);
};
