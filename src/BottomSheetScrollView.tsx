import type { NativeScrollEvent, ScrollViewProps } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import Animated from 'react-native-reanimated';

import { useBottomSheetScrollable } from './useBottomSheetScrollable';

export interface BottomSheetScrollViewProps
  extends Omit<ScrollViewProps, 'onScroll'> {
  onScroll?: (event: NativeScrollEvent) => void;
}

export const BottomSheetScrollView = ({
  scrollEnabled,
  onScroll,
  ...rest
}: BottomSheetScrollViewProps) => {
  const { scrollHandler, scrollableRef, nativeGesture, animatedProps } =
    useBottomSheetScrollable(scrollEnabled, onScroll);
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
