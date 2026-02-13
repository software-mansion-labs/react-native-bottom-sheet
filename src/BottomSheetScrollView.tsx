import type { ScrollViewProps } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import Animated from 'react-native-reanimated';

import { useBottomSheetScrollable } from './useBottomSheetScrollable';

export const BottomSheetScrollView = (
  props: Omit<ScrollViewProps, 'onScroll'>
) => {
  const { scrollEnabled, ...rest } = props;
  const { scrollHandler, scrollableRef, nativeGesture, animatedProps } =
    useBottomSheetScrollable(scrollEnabled);
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
