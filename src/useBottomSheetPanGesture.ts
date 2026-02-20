import type { PanGesture } from 'react-native-gesture-handler';
import { Gesture } from 'react-native-gesture-handler';
import { scheduleOnRN } from 'react-native-worklets';
import {
  measure,
  scrollTo,
  type AnimatedRef,
  type SharedValue,
  useSharedValue,
} from 'react-native-reanimated';

import { findSnapTarget } from './bottomSheetUtils';

interface BottomSheetPanGestureParams {
  animationTarget: SharedValue<number>;
  translateY: SharedValue<number>;
  sheetHeight: SharedValue<number>;
  detentsValue: SharedValue<number[]>;
  currentIndex: SharedValue<number>;
  scrollOffset: SharedValue<number>;
  hasScrollable: SharedValue<boolean>;
  isScrollableGestureActive: SharedValue<boolean>;
  isScrollableLocked: SharedValue<boolean>;
  scrollableRef: AnimatedRef<any>;
  handleIndexChange: (nextIndex: number) => void;
  animateToIndex: (targetIndex: number, velocity?: number) => void;
}

export const useBottomSheetPanGesture = ({
  animationTarget,
  translateY,
  sheetHeight,
  detentsValue,
  currentIndex,
  scrollOffset,
  hasScrollable,
  isScrollableGestureActive,
  isScrollableLocked,
  scrollableRef,
  handleIndexChange,
  animateToIndex,
}: BottomSheetPanGestureParams): PanGesture => {
  const isDraggingSheet = useSharedValue(false);
  const isDraggingFromScrollable = useSharedValue(false);
  const panStartY = useSharedValue(0);
  const panActivated = useSharedValue(false);
  const dragStartTranslateY = useSharedValue(0);
  const isTouchWithinScrollable = useSharedValue(false);

  return Gesture.Pan()
    .manualActivation(true)
    .onTouchesDown((event) => {
      'worklet';
      panActivated.set(false);
      isDraggingSheet.set(false);
      isDraggingFromScrollable.set(false);
      isScrollableLocked.set(false);
      isTouchWithinScrollable.set(false);
      const touch = event.changedTouches[0] ?? event.allTouches[0];
      if (touch !== undefined) {
        panStartY.set(touch.absoluteY);
        if (hasScrollable.value) {
          const layout = measure(scrollableRef);
          if (layout !== null) {
            const withinX =
              touch.absoluteX >= layout.pageX &&
              touch.absoluteX <= layout.pageX + layout.width;
            const withinY =
              touch.absoluteY >= layout.pageY &&
              touch.absoluteY <= layout.pageY + layout.height;
            isTouchWithinScrollable.set(withinX && withinY);
          }
        }
      }
    })
    .onTouchesMove((event, stateManager) => {
      'worklet';
      if (panActivated.value) return;
      const touch = event.changedTouches[0] ?? event.allTouches[0];
      if (!touch) return;
      const deltaY = touch.absoluteY - panStartY.value;
      if (
        hasScrollable.value &&
        scrollOffset.value > 0 &&
        isTouchWithinScrollable.value
      ) {
        return;
      }
      if (deltaY > 0 || translateY.value > 0) {
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
      if (isDraggingSheet.value) {
        if (isDraggingFromScrollable.value) {
          scrollTo(scrollableRef, 0, 0, false);
        }
      } else {
        const isDraggingDown = event.translationY > 0;
        const canStartDrag =
          !hasScrollable.value ||
          scrollOffset.value <= 0 ||
          !isTouchWithinScrollable.value;
        if (!canStartDrag || (!isDraggingDown && translateY.value <= 0)) {
          return;
        }
        const isScrollableActive =
          hasScrollable.value && isScrollableGestureActive.value;
        isDraggingSheet.set(true);
        isDraggingFromScrollable.set(
          isScrollableActive && isTouchWithinScrollable.value
        );
        dragStartTranslateY.set(translateY.value - event.translationY);
        isScrollableLocked.set(hasScrollable.value);
        if (isTouchWithinScrollable.value && hasScrollable.value) {
          scrollTo(scrollableRef, 0, 0, false);
        }
      }
      const rawTranslate = dragStartTranslateY.value + event.translationY;
      const nextTranslate = Math.min(
        Math.max(rawTranslate, 0),
        sheetHeight.value
      );
      translateY.set(nextTranslate);
      if (
        isDraggingSheet.value &&
        rawTranslate < 0 &&
        isTouchWithinScrollable.value &&
        hasScrollable.value
      ) {
        isDraggingSheet.set(false);
        isScrollableLocked.set(false);
        const resolvedDetents = detentsValue.value;
        const maxSnap = sheetHeight.value;
        for (let i = resolvedDetents.length - 1; i >= 0; i--) {
          if (resolvedDetents[i] === maxSnap) {
            if (i !== currentIndex.value) scheduleOnRN(handleIndexChange, i);
            animateToIndex(i);
            break;
          }
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
      const allPositions = detentsValue.value.map((point, snapIndex) => ({
        index: snapIndex,
        translateY: maxSnap - point,
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
