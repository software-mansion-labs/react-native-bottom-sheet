import { useImperativeHandle, type Ref } from 'react';
import type { NativeScrollEvent } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import type { FlatListPropsWithLayout } from 'react-native-reanimated';
import Animated, { scrollTo } from 'react-native-reanimated';
import { scheduleOnUI } from 'react-native-worklets';

import { useBottomSheetScrollable } from './useBottomSheetScrollable';

export interface BottomSheetFlatListProps<T>
  extends Omit<FlatListPropsWithLayout<T>, 'onScroll' | 'ref'> {
  onScroll?: (event: NativeScrollEvent) => void;
  ref?: Ref<BottomSheetFlatListMethods>;
}

export const BottomSheetFlatList = <T,>({
  scrollEnabled,
  onScroll,
  ref,
  ...rest
}: BottomSheetFlatListProps<T>) => {
  const { scrollHandler, scrollableRef, nativeGesture, animatedProps } =
    useBottomSheetScrollable(scrollEnabled, onScroll);

  useImperativeHandle(
    ref,
    () => ({
      scrollToOffset: ({ offset, animated }) => {
        const resolvedAnimated = animated ?? true;
        scheduleOnUI(
          (
            animatedRef: Parameters<typeof scrollTo>[0],
            y: number,
            shouldAnimate: boolean
          ) => {
            'worklet';
            scrollTo(animatedRef, 0, y, shouldAnimate);
          },
          scrollableRef,
          offset,
          resolvedAnimated
        );
      },
    }),
    [scrollableRef]
  );

  return (
    <GestureDetector gesture={nativeGesture}>
      <Animated.FlatList
        {...rest}
        animatedProps={animatedProps}
        ref={scrollableRef}
        onScroll={scrollHandler}
        scrollEventThrottle={16}
      />
    </GestureDetector>
  );
};

export type BottomSheetFlatListMethods = {
  scrollToOffset: (params: { offset: number; animated?: boolean }) => void;
};
