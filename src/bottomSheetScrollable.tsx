import { type ComponentType, type Ref, useImperativeHandle } from 'react';
import type { NativeScrollEvent } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue } from 'react-native-reanimated';

import { useBottomSheetScrollable } from './useBottomSheetScrollable';

export function bottomSheetScrollable<
  P extends Record<string, any>,
  R = unknown
>(ScrollableComponent: ComponentType<P>) {
  const AnimatedComponent =
    Animated.createAnimatedComponent(ScrollableComponent);

  return ({
    scrollEnabled,
    onScroll,
    ref,
    ...rest
  }: Omit<P, 'onScroll' | 'scrollEnabled' | 'ref'> & {
    scrollEnabled?: boolean | SharedValue<boolean | undefined>;
    onScroll?: (event: NativeScrollEvent) => void;
    ref?: Ref<R>;
  }) => {
    const { scrollHandler, scrollableRef, nativeGesture, animatedProps } =
      useBottomSheetScrollable(scrollEnabled, onScroll);

    useImperativeHandle(ref, () => scrollableRef.current as R, [scrollableRef]);

    return (
      <GestureDetector gesture={nativeGesture}>
        <AnimatedComponent
          {...(rest as any)}
          animatedProps={animatedProps}
          ref={scrollableRef}
          onScroll={scrollHandler}
          scrollEventThrottle={16}
        />
      </GestureDetector>
    );
  };
}
