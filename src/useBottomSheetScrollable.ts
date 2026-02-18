import { useEffect } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import type { NativeScrollEvent } from 'react-native';
import { isWorkletFunction, scheduleOnRN } from 'react-native-worklets';
import {
  type SharedValue,
  useAnimatedProps,
  useAnimatedScrollHandler,
} from 'react-native-reanimated';

import { useBottomSheetContext } from './BottomSheetContext';

type ScrollHandler = (event: NativeScrollEvent) => void;

export const useBottomSheetScrollable = (
  baseScrollEnabled: boolean | SharedValue<boolean | undefined> = true,
  onScroll?: ScrollHandler
) => {
  const {
    scrollOffset,
    scrollableRef,
    hasScrollable,
    isScrollableGestureActive,
    isScrollableLocked,
    panGesture,
  } = useBottomSheetContext();
  const isWorkletScrollHandler =
    onScroll !== undefined ? isWorkletFunction(onScroll) : false;
  const scrollHandler = useAnimatedScrollHandler({
    onScroll: (event) => {
      'worklet';
      scrollOffset.set(Math.max(0, event.contentOffset.y));
      if (onScroll === undefined) return;
      if (isWorkletScrollHandler) {
        onScroll(event);
        return;
      }
      scheduleOnRN(onScroll, event);
    },
  });
  const nativeGesture = Gesture.Native()
    .simultaneousWithExternalGesture(panGesture)
    .onStart(() => {
      'worklet';
      isScrollableGestureActive.set(true);
    })
    .onFinalize(() => {
      'worklet';
      isScrollableGestureActive.set(false);
    });
  const animatedProps = useAnimatedProps(() => {
    const resolvedScrollEnabled =
      (typeof baseScrollEnabled === 'object' && baseScrollEnabled !== null
        ? baseScrollEnabled.value
        : baseScrollEnabled) ?? true;
    return {
      scrollEnabled: resolvedScrollEnabled && !isScrollableLocked.value,
    };
  });
  useEffect(() => {
    if (__DEV__ && hasScrollable.value) {
      console.warn(
        'Multiple scrollables within a single bottom sheet. Only one `BottomSheetScrollView` ' +
          'or `BottomSheetFlatList` is supported per bottom sheet.'
      );
    }
    hasScrollable.set(true);
    return () => {
      hasScrollable.set(false);
      isScrollableGestureActive.set(false);
      isScrollableLocked.set(false);
    };
  }, [hasScrollable, isScrollableGestureActive, isScrollableLocked]);
  return { scrollHandler, scrollableRef, nativeGesture, animatedProps };
};
