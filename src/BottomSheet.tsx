import { useRef, useState, type ReactNode } from 'react';
import type { LayoutChangeEvent, StyleProp, ViewStyle } from 'react-native';
import { Animated, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import BottomSheetNativeComponent from './BottomSheetNativeComponent';
import { Portal } from './BottomSheetProvider';
import { type Detent, resolveDetent } from './bottomSheetUtils';
export type { Detent, DetentValue } from './bottomSheetUtils';
export { programmatic } from './bottomSheetUtils';

export interface BottomSheetProps {
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
  detents?: Detent[];
  index: number;
  animateIn?: boolean;
  onIndexChange?: (index: number) => void;
  onPositionChange?: (position: number) => void;
  modal?: boolean;
  scrimColor?: string;
}

export const BottomSheet = ({
  children,
  style,
  detents = [0, 'content'],
  index,
  animateIn = true,
  onIndexChange,
  onPositionChange,
  modal = false,
  scrimColor = 'rgba(0, 0, 0, 0.5)',
}: BottomSheetProps) => {
  const { height: windowHeight } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const maxHeight = windowHeight - insets.top;
  const [contentHeight, setContentHeight] = useState(0);
  const sheetOpacity = useRef(new Animated.Value(0)).current;

  const resolvedDetents = detents.map((detent) => {
    const value = resolveDetent(detent, contentHeight, maxHeight);
    return {
      height: Math.max(0, Math.min(value, maxHeight)),
      programmatic: isDetentProgrammatic(detent),
    };
  });

  const handleSentinelLayout = (event: LayoutChangeEvent) => {
    setContentHeight(event.nativeEvent.layout.y);
  };

  const clampedIndex = Math.max(0, Math.min(index, resolvedDetents.length - 1));
  const isCollapsed = (resolvedDetents[clampedIndex]?.height ?? 0) === 0;
  const handleIndexChange = (event: { nativeEvent: { index: number } }) => {
    onIndexChange?.(event.nativeEvent.index);
  };

  const handlePositionChange = (event: {
    nativeEvent: { position: number };
  }) => {
    const height = event.nativeEvent.position;
    sheetOpacity.setValue(height === 0 ? 0 : 1);
    onPositionChange?.(height);
  };

  const sheet = (
    <Animated.View
      style={StyleSheet.absoluteFill}
      pointerEvents={modal ? (isCollapsed ? 'none' : 'auto') : 'box-none'}
    >
      <Animated.View
        pointerEvents="box-none"
        style={[StyleSheet.absoluteFill, { opacity: sheetOpacity }]}
      >
        <BottomSheetNativeComponent
          pointerEvents="box-none"
          style={[
            {
              position: 'absolute',
              left: 0,
              right: 0,
              bottom: 0,
              // The native host always spans the full height of its container.
              // Detents are still capped to `maxHeight`, so the sheet itself
              // never extends under the status bar.
              height: windowHeight,
            },
            style,
          ]}
          detents={resolvedDetents}
          index={index}
          animateIn={animateIn}
          modal={modal}
          scrimColor={scrimColor}
          onIndexChange={handleIndexChange}
          onPositionChange={handlePositionChange}
        >
          <View collapsable={false} style={{ flex: 1, maxHeight }}>
            {children}
            <View onLayout={handleSentinelLayout} pointerEvents="none" />
          </View>
        </BottomSheetNativeComponent>
      </Animated.View>
    </Animated.View>
  );

  if (modal) {
    return <Portal>{sheet}</Portal>;
  }

  return sheet;
};

function isDetentProgrammatic(detent: Detent): boolean {
  if (typeof detent === 'object' && detent !== null) {
    return detent.programmatic === true;
  }
  return false;
}
