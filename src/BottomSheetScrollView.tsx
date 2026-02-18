import { useImperativeHandle, type Ref } from 'react';
import type { NativeScrollEvent, ScrollViewProps } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import Animated, { scrollTo } from 'react-native-reanimated';
import { scheduleOnUI } from 'react-native-worklets';

import { useBottomSheetScrollable } from './useBottomSheetScrollable';

type ScrollToOptions = { x?: number; y?: number; animated?: boolean };

export interface BottomSheetScrollViewProps
  extends Omit<ScrollViewProps, 'onScroll' | 'ref'> {
  onScroll?: (event: NativeScrollEvent) => void;
  ref?: Ref<BottomSheetScrollViewMethods>;
}

export const BottomSheetScrollView = ({
  scrollEnabled,
  onScroll,
  ref,
  ...rest
}: BottomSheetScrollViewProps) => {
  const { scrollHandler, scrollableRef, nativeGesture, animatedProps } =
    useBottomSheetScrollable(scrollEnabled, onScroll);

  useImperativeHandle(
    ref,
    () => ({
      scrollTo: ({ x = 0, y = 0, animated }) => {
        const resolvedAnimated = animated ?? true;
        scheduleOnUI(
          (
            animatedRef: Parameters<typeof scrollTo>[0],
            xValue: number,
            yValue: number,
            shouldAnimate: boolean
          ) => {
            'worklet';
            scrollTo(animatedRef, xValue, yValue, shouldAnimate);
          },
          scrollableRef,
          x,
          y,
          resolvedAnimated
        );
      },
    }),
    [scrollableRef]
  );

  return (
    <GestureDetector gesture={nativeGesture}>
      <Animated.ScrollView
        {...rest}
        animatedProps={animatedProps}
        ref={scrollableRef}
        onScroll={scrollHandler}
        scrollEventThrottle={16}
      />
    </GestureDetector>
  );
};

export type BottomSheetScrollViewMethods = {
  scrollTo: (options: ScrollToOptions) => void;
};
