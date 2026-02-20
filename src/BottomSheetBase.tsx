import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import type { LayoutChangeEvent } from 'react-native';
import { Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import type { SharedValue, WithSpringConfig } from 'react-native-reanimated';
import Animated, {
  useAnimatedRef,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { scheduleOnUI } from 'react-native-worklets';
import { GestureDetector } from 'react-native-gesture-handler';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Portal } from './BottomSheetProvider';
import { BottomSheetContextProvider } from './BottomSheetContext';
import { clampIndex, resolveDetent } from './bottomSheetUtils';
import type { Detent } from './bottomSheetUtils';
import { useBottomSheetPanGesture } from './useBottomSheetPanGesture';
export type { Detent } from './bottomSheetUtils';

export interface BottomSheetCommonProps {
  children: ReactNode;
  detents?: Detent[];
  index: number;
  onIndexChange?: (index: number) => void;
  position?: SharedValue<number>;
  openAnimationConfig?: WithSpringConfig;
  closeAnimationConfig?: WithSpringConfig;
}

export interface BottomSheetBaseProps extends BottomSheetCommonProps {
  modal?: boolean;
  renderScrim?: (progress: SharedValue<number>) => ReactNode;
}

const DEFAULT_OPEN_ANIMATION_CONFIG: WithSpringConfig = {
  dampingRatio: 1,
  duration: 300,
  overshootClamping: true,
};

const DEFAULT_CLOSE_ANIMATION_CONFIG: WithSpringConfig = {
  dampingRatio: 1,
  duration: 250,
  overshootClamping: true,
};

const DefaultScrim = ({ progress }: { progress: SharedValue<number> }) => {
  const style = useAnimatedStyle(() => ({ opacity: progress.value }));
  return (
    <Animated.View
      style={[
        StyleSheet.absoluteFill,
        { flex: 1, backgroundColor: 'rgba(0, 0, 0, 0.5)' },
        style,
      ]}
    />
  );
};

export const BottomSheetBase = ({
  children,
  detents = [0, 'max'],
  index,
  onIndexChange,
  position: externalPosition,
  openAnimationConfig = DEFAULT_OPEN_ANIMATION_CONFIG,
  closeAnimationConfig = DEFAULT_CLOSE_ANIMATION_CONFIG,
  modal = false,
  renderScrim,
}: BottomSheetBaseProps) => {
  const { height: screenHeight } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const maxHeight = screenHeight - insets.top;
  const resolvedIndex = clampIndex(index, detents.length);
  const [contentHeight, setContentHeight] = useState(0);
  if (detents.length === 0) {
    throw new Error('detents must include at least one value.');
  }
  const normalizedDetents = detents.map((point) => {
    const resolved = resolveDetent(point, contentHeight, maxHeight);
    return Math.max(0, Math.min(resolved, maxHeight));
  });
  const initialMaxSnap = Math.max(0, ...normalizedDetents);
  const translateY = useSharedValue(initialMaxSnap);
  const animationTarget = useSharedValue(NaN);
  const sheetHeight = useSharedValue(initialMaxSnap);
  const scrollOffset = useSharedValue(0);
  const hasScrollable = useSharedValue(false);
  const isScrollableGestureActive = useSharedValue(false);
  const isScrollableLocked = useSharedValue(false);
  const scrollableRef = useAnimatedRef();
  const detentsValue = useSharedValue(normalizedDetents);
  const firstNonzeroDetent = useSharedValue(
    normalizedDetents.find((d) => d > 0) ?? 0
  );
  const currentIndex = useSharedValue(resolvedIndex);
  const internalPosition = useDerivedValue(() =>
    Math.max(0, sheetHeight.value - translateY.value)
  );
  useAnimatedReaction(
    () => internalPosition.value,
    (value) => {
      if (externalPosition !== undefined) externalPosition.set(value);
    }
  );
  const scrimProgress = useDerivedValue(() => {
    const target = firstNonzeroDetent.value;
    if (target <= 0) return 0;
    const progress = internalPosition.value / target;
    return Math.min(1, Math.max(0, progress));
  });
  const handleIndexChange = (nextIndex: number) => {
    onIndexChange?.(nextIndex);
  };
  useEffect(() => {
    const maxSnap = Math.max(0, ...normalizedDetents);
    detentsValue.set(normalizedDetents);
    sheetHeight.set(maxSnap);
    firstNonzeroDetent.set(normalizedDetents.find((d) => d > 0) ?? 0);
  }, [normalizedDetents, sheetHeight, detentsValue, firstNonzeroDetent]);
  const animateToIndex = useCallback(
    (targetIndex: number, velocity?: number) => {
      'worklet';
      const maxSnap = sheetHeight.value;
      const targetTranslate = maxSnap - (detentsValue.value[targetIndex] ?? 0);
      if (animationTarget.value === targetTranslate && velocity === undefined) {
        currentIndex.set(targetIndex);
        return;
      }
      animationTarget.set(targetTranslate);
      const isOpening = targetTranslate < translateY.value;
      const baseConfig = isOpening ? openAnimationConfig : closeAnimationConfig;
      const springConfig =
        velocity === undefined ? baseConfig : { ...baseConfig, velocity };
      translateY.set(withSpring(targetTranslate, springConfig));
      currentIndex.set(targetIndex);
    },
    [
      animationTarget,
      closeAnimationConfig,
      currentIndex,
      detentsValue,
      openAnimationConfig,
      sheetHeight,
      translateY,
    ]
  );
  useEffect(() => {
    scheduleOnUI(animateToIndex, resolvedIndex);
  }, [animateToIndex, resolvedIndex, normalizedDetents]);
  const panGesture = useBottomSheetPanGesture({
    animationTarget,
    translateY,
    sheetHeight,
    detentsValue,
    currentIndex,
    scrollOffset,
    hasScrollable,
    isScrollableGestureActive,
    isScrollableLocked,
    scrollableRef,
    handleIndexChange,
    animateToIndex,
  });
  const handleSentinelLayout = (event: LayoutChangeEvent) => {
    setContentHeight(event.nativeEvent.layout.y);
  };
  const closedIndex = normalizedDetents.indexOf(0);
  const handleScrimPress = () => {
    if (closedIndex === -1 || resolvedIndex === closedIndex) return;
    handleIndexChange(closedIndex);
    scheduleOnUI(animateToIndex, closedIndex);
  };
  const wrapperStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
    height: sheetHeight.value,
    opacity: translateY.value >= sheetHeight.value ? 0 : 1,
  }));
  const isCollapsed = normalizedDetents[resolvedIndex] === 0;
  const pointerEvents = modal ? (isCollapsed ? 'none' : 'auto') : 'box-none';
  let scrimElement: ReactNode | null = null;
  if (renderScrim !== undefined) {
    scrimElement = renderScrim(scrimProgress);
  } else if (modal) {
    scrimElement = <DefaultScrim progress={scrimProgress} />;
  }
  const sheetContent = (
    <BottomSheetContextProvider
      value={{
        translateY,
        position: internalPosition,
        index: currentIndex,
        sheetHeight,
        scrollOffset,
        scrollableRef,
        hasScrollable,
        isScrollableGestureActive,
        isScrollableLocked,
        panGesture,
      }}
    >
      <Animated.View
        style={[
          {
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
          },
          wrapperStyle,
        ]}
        pointerEvents="box-none"
      >
        <GestureDetector gesture={panGesture}>
          <View style={{ flex: 1 }} pointerEvents="box-none">
            {children}
            <View onLayout={handleSentinelLayout} pointerEvents="none" />
          </View>
        </GestureDetector>
      </Animated.View>
    </BottomSheetContextProvider>
  );
  const sheetContainer = (
    <Animated.View
      style={StyleSheet.absoluteFill}
      pointerEvents={pointerEvents}
    >
      {modal && scrimElement !== null ? (
        <Pressable style={StyleSheet.absoluteFill} onPress={handleScrimPress}>
          {scrimElement}
        </Pressable>
      ) : null}
      {sheetContent}
    </Animated.View>
  );
  if (modal) return <Portal>{sheetContainer}</Portal>;
  return sheetContainer;
};
