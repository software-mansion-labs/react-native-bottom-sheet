import type { ReactNode } from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import { useWindowDimensions } from 'react-native';
import { runOnUI, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import BottomSheetNativeComponent from './BottomSheetNativeComponent';
import type { Detent, DetentValue } from './bottomSheetUtils';
export type { Detent, DetentValue } from './bottomSheetUtils';
export { programmatic } from './bottomSheetUtils';

export interface BottomSheetProps {
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
  detents?: Detent[];
  index: number;
  animateIn?: boolean;
  onIndexChange?: (index: number) => void;
  position?: SharedValue<number>;
}

export const BottomSheet = ({
  children,
  style,
  detents = [0, 'max'],
  index,
  animateIn = true,
  onIndexChange,
  position,
}: BottomSheetProps) => {
  const { height: screenHeight } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const maxHeight = screenHeight - insets.top;

  const resolvedDetents = detents.map((detent) => {
    const value = resolveDetentValue(detent, maxHeight);
    return {
      height: Math.max(0, Math.min(value, maxHeight)),
      programmatic: isDetentProgrammatic(detent),
    };
  });

  const handleIndexChange = (event: { nativeEvent: { index: number } }) => {
    onIndexChange?.(event.nativeEvent.index);
  };

  const handlePositionChange = (event: {
    nativeEvent: { position: number };
  }) => {
    if (position !== undefined) {
      const height = event.nativeEvent.position;
      runOnUI(() => {
        'worklet';
        position.set(height);
      })();
    }
  };

  return (
    <BottomSheetNativeComponent
      style={style}
      detents={resolvedDetents}
      index={index}
      animateIn={animateIn}
      onIndexChange={handleIndexChange}
      onPositionChange={handlePositionChange}
    >
      {children}
    </BottomSheetNativeComponent>
  );
};

function resolveDetentValue(detent: Detent, maxHeight: number): number {
  const value: DetentValue =
    typeof detent === 'object' && detent !== null ? detent.value : detent;
  return value === 'max' ? maxHeight : value;
}

function isDetentProgrammatic(detent: Detent): boolean {
  if (typeof detent === 'object' && detent !== null) {
    return detent.programmatic === true;
  }
  return false;
}
