import type { PanGesture } from 'react-native-gesture-handler';
import { Gesture } from 'react-native-gesture-handler';
import { scheduleOnRN } from 'react-native-worklets';
import {
  measure,
  scrollTo,
  type SharedValue,
  useSharedValue,
} from 'react-native-reanimated';

import type { ScrollableEntry } from './BottomSheetContext';
import { findSnapTarget } from './bottomSheetUtils';

interface BottomSheetPanGestureParams {
  animationTarget: SharedValue<number>;
  translateY: SharedValue<number>;
  sheetHeight: SharedValue<number>;
  detentsValue: SharedValue<number[]>;
  isDraggableValue: SharedValue<boolean[]>;
  currentIndex: SharedValue<number>;
  scrollableEntries: ScrollableEntry[];
  isScrollableLocked: SharedValue<boolean>;
  handleIndexChange: (nextIndex: number) => void;
  animateToIndex: (targetIndex: number, velocity?: number) => void;
}

export const useBottomSheetPanGesture = ({
  animationTarget,
  translateY,
  sheetHeight,
  detentsValue,
  isDraggableValue,
  currentIndex,
  scrollableEntries,
  isScrollableLocked,
  handleIndexChange,
  animateToIndex,
}: BottomSheetPanGestureParams): PanGesture => {
  const isDraggingSheet = useSharedValue(false);
  const isDraggingFromScrollable = useSharedValue(false);
  const panStartX = useSharedValue(0);
  const panStartY = useSharedValue(0);
  const panActivated = useSharedValue(false);
  const dragStartTranslateY = useSharedValue(0);
  const activeScrollableIndex = useSharedValue(-1);

  return Gesture.Pan()
    .manualActivation(true)
    .onTouchesDown((event) => {
      'worklet';
      panActivated.set(false);
      isDraggingSheet.set(false);
      isDraggingFromScrollable.set(false);
      isScrollableLocked.set(false);
      activeScrollableIndex.set(-1);
      const touch = event.changedTouches[0] ?? event.allTouches[0];
      if (touch !== undefined) {
        panStartX.set(touch.absoluteX);
        panStartY.set(touch.absoluteY);
        const entries = scrollableEntries;
        for (let i = 0; i < entries.length; i++) {
          const entry = entries[i];
          if (entry === undefined) continue;
          const layout = measure(entry.ref);
          if (layout === null) continue;
          const withinX =
            touch.absoluteX >= layout.pageX &&
            touch.absoluteX <= layout.pageX + layout.width;
          const withinY =
            touch.absoluteY >= layout.pageY &&
            touch.absoluteY <= layout.pageY + layout.height;
          if (withinX && withinY) {
            activeScrollableIndex.set(i);
            break;
          }
        }
      }
    })
    .onTouchesMove((event, stateManager) => {
      'worklet';
      if (panActivated.value) return;
      const touch = event.changedTouches[0] ?? event.allTouches[0];
      if (!touch) return;
      const deltaX = touch.absoluteX - panStartX.value;
      const deltaY = touch.absoluteY - panStartY.value;
      // When multiple scrollables overlap (e.g. stacked views), the hit-test
      // in onTouchesDown may pick the wrong one. Prefer the scrollable whose
      // native gesture is already active — that is definitively the one
      // receiving touches (respects pointerEvents, z-order, etc.).
      // If multiple scrollables are registered but none has confirmed via
      // isGestureActive yet, defer the decision to avoid acting on a
      // potentially incorrect hit-test result.
      const entries = scrollableEntries;
      let gestureActiveIdx = -1;
      for (let i = 0; i < entries.length; i++) {
        const entry = entries[i];
        if (entry !== undefined && entry.isGestureActive.value) {
          gestureActiveIdx = i;
          break;
        }
      }
      if (gestureActiveIdx !== -1) {
        activeScrollableIndex.set(gestureActiveIdx);
      } else if (entries.length > 1) {
        return;
      }
      const activeIdx = activeScrollableIndex.value;
      if (activeIdx !== -1) {
        const active = scrollableEntries[activeIdx];
        if (
          active !== undefined &&
          active.scrollOffset.value > 0 &&
          translateY.value <= 0
        ) {
          return;
        }
      }
      if (Math.abs(deltaX) > Math.abs(deltaY)) {
        stateManager.fail();
        return;
      }
      if (
        Math.abs(deltaY) > Math.abs(deltaX) &&
        (deltaY > 0 || translateY.value > 0)
      ) {
        panActivated.set(true);
        stateManager.activate();
      }
    })
    .onBegin(() => {
      'worklet';
      animationTarget.set(NaN);
      isDraggingSheet.set(false);
      isDraggingFromScrollable.set(false);
      dragStartTranslateY.set(translateY.value);
    })
    .onUpdate((event) => {
      'worklet';
      const activeIdx = activeScrollableIndex.value;
      const hasActive = activeIdx !== -1;
      const active = hasActive ? scrollableEntries[activeIdx] : undefined;
      const activeOffset = active !== undefined ? active.scrollOffset.value : 0;

      if (isDraggingSheet.value) {
        if (isDraggingFromScrollable.value && active !== undefined) {
          scrollTo(active.ref, 0, 0, false);
        }
      } else {
        const isDraggingDown = event.translationY > 0;
        const canStartDrag =
          !hasActive || activeOffset <= 0 || translateY.value > 0;
        if (!canStartDrag || (!isDraggingDown && translateY.value <= 0)) {
          return;
        }
        const isScrollableActive =
          hasActive && active !== undefined && active.isGestureActive.value;
        isDraggingSheet.set(true);
        isDraggingFromScrollable.set(isScrollableActive && activeOffset <= 0);
        dragStartTranslateY.set(translateY.value - event.translationY);
        isScrollableLocked.set(hasActive);
        if (hasActive && active !== undefined && activeOffset <= 0) {
          scrollTo(active.ref, 0, 0, false);
        }
      }
      const rawTranslate = dragStartTranslateY.value + event.translationY;
      const resolvedDetents = detentsValue.value;
      const draggable = isDraggableValue.value;
      let maxDraggableTranslateY = sheetHeight.value;
      let foundDraggable = false;
      for (let i = 0; i < resolvedDetents.length; i++) {
        if (!(draggable[i] ?? true)) continue;
        const t = sheetHeight.value - (resolvedDetents[i] ?? 0);
        if (!foundDraggable || t > maxDraggableTranslateY) {
          maxDraggableTranslateY = t;
          foundDraggable = true;
        }
      }
      const nextTranslate = Math.min(
        Math.max(rawTranslate, 0),
        maxDraggableTranslateY
      );
      translateY.set(nextTranslate);
      if (isDraggingSheet.value && rawTranslate < 0 && hasActive) {
        isDraggingSheet.set(false);
        isScrollableLocked.set(false);
        let targetSnapIndex = -1;
        let targetSnapValue = -1;
        for (let i = resolvedDetents.length - 1; i >= 0; i--) {
          const detentValue = resolvedDetents[i];
          if (
            detentValue !== undefined &&
            (draggable[i] ?? true) &&
            detentValue > targetSnapValue
          ) {
            targetSnapValue = detentValue;
            targetSnapIndex = i;
          }
        }
        if (targetSnapIndex === -1) {
          const maxSnap = sheetHeight.value;
          for (let i = resolvedDetents.length - 1; i >= 0; i--) {
            if (resolvedDetents[i] === maxSnap) {
              targetSnapIndex = i;
              break;
            }
          }
        }
        if (targetSnapIndex !== -1) {
          if (targetSnapIndex !== currentIndex.value) {
            scheduleOnRN(handleIndexChange, targetSnapIndex);
          }
          animateToIndex(targetSnapIndex);
        }
      }
    })
    .onEnd((event) => {
      'worklet';
      const wasDragging = isDraggingSheet.value;
      isScrollableLocked.set(false);
      isDraggingSheet.set(false);
      animationTarget.set(NaN);
      if (!wasDragging) {
        animateToIndex(currentIndex.value);
        return;
      }
      const maxSnap = sheetHeight.value;
      const draggable = isDraggableValue.value;
      const allPositions = detentsValue.value.map((detentValue, snapIndex) => ({
        index: snapIndex,
        translateY: maxSnap - detentValue,
        isDraggable: draggable[snapIndex] ?? true,
      }));
      const targetIndex = findSnapTarget(
        translateY.value,
        event.velocityY,
        currentIndex.value,
        allPositions
      );
      const hasIndexChanged = targetIndex !== currentIndex.value;
      if (hasIndexChanged) scheduleOnRN(handleIndexChange, targetIndex);
      const shouldApplyVelocity =
        hasIndexChanged && Number.isFinite(event.velocityY);
      animateToIndex(
        targetIndex,
        shouldApplyVelocity ? event.velocityY : undefined
      );
    });
};
