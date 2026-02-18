import type { NativeScrollEvent } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import type { FlatListPropsWithLayout } from 'react-native-reanimated';
import Animated from 'react-native-reanimated';

import { useBottomSheetScrollable } from './useBottomSheetScrollable';

export interface BottomSheetFlatListProps<T>
  extends Omit<FlatListPropsWithLayout<T>, 'onScroll'> {
  onScroll?: (event: NativeScrollEvent) => void;
}

export const BottomSheetFlatList = <T,>({
  scrollEnabled,
  onScroll,
  ...rest
}: BottomSheetFlatListProps<T>) => {
  const { scrollHandler, scrollableRef, nativeGesture, animatedProps } =
    useBottomSheetScrollable(scrollEnabled, onScroll);
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
