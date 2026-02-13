import { GestureDetector } from 'react-native-gesture-handler';
import type { FlatListPropsWithLayout } from 'react-native-reanimated';
import Animated from 'react-native-reanimated';

import { useBottomSheetScrollable } from './useBottomSheetScrollable';

export const BottomSheetFlatList = <T,>(
  props: Omit<FlatListPropsWithLayout<T>, 'onScroll'>
) => {
  const { scrollEnabled, ...rest } = props;
  const { scrollHandler, scrollableRef, nativeGesture, animatedProps } =
    useBottomSheetScrollable(scrollEnabled);
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
