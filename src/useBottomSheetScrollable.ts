import { useEffect } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import type { NativeScrollEvent } from 'react-native';
import { isWorkletFunction, scheduleOnRN } from 'react-native-worklets';
import {
  type SharedValue,
  useAnimatedProps,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useSharedValue,
} from 'react-native-reanimated';

import { useBottomSheetContext } from './BottomSheetContext';

type ScrollHandler = (event: NativeScrollEvent) => void;

export const useBottomSheetScrollable = (
  baseScrollEnabled: boolean | SharedValue<boolean | undefined> = true,
  onScroll?: ScrollHandler
) => {
  const { isScrollableLocked, registerScrollable, panGesture } =
    useBottomSheetContext();
  const scrollableRef = useAnimatedRef();
  const scrollOffset = useSharedValue(0);
  const isGestureActive = useSharedValue(false);
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
      isGestureActive.set(true);
    })
    .onFinalize(() => {
      'worklet';
      isGestureActive.set(false);
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
    const unregister = registerScrollable({
      ref: scrollableRef,
      scrollOffset,
      isGestureActive,
    });
    return unregister;
  }, [registerScrollable, scrollableRef, scrollOffset, isGestureActive]);
  return { scrollHandler, scrollableRef, nativeGesture, animatedProps };
};
