import { useEffect } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  type SharedValue,
  useAnimatedProps,
  useAnimatedScrollHandler,
} from 'react-native-reanimated';

import { useBottomSheetContext } from './BottomSheetContext';

export const useBottomSheetScrollable = (
  baseScrollEnabled: boolean | SharedValue<boolean | undefined> = true
) => {
  const {
    scrollOffset,
    scrollableRef,
    hasScrollable,
    isScrollableGestureActive,
    isScrollableLocked,
    panGesture,
  } = useBottomSheetContext();
  const scrollHandler = useAnimatedScrollHandler({
    onScroll: (event) => {
      'worklet';
      scrollOffset.set(Math.max(0, event.contentOffset.y));
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
      typeof baseScrollEnabled === 'object' && baseScrollEnabled !== null
        ? baseScrollEnabled.value ?? true
        : baseScrollEnabled ?? true;
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
