import { type ReactNode } from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import { StyleSheet, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import BottomSheetNativeComponent from './BottomSheetNativeComponent';
import { Portal } from './BottomSheetProvider';
import { type Detent } from './bottomSheetUtils';
export type { Detent, DetentValue } from './bottomSheetUtils';
export { programmatic } from './bottomSheetUtils';

/**
 * Props for the inline bottom-sheet component.
 */
export interface BottomSheetProps {
  /** Sheet contents, including any background or scrollable content. */
  children: ReactNode;
  /** Additional style applied to the native sheet host view. */
  style?: StyleProp<ViewStyle>;
  /** Snap points for the sheet. Defaults to `[0, 'content']`. */
  detents?: Detent[];
  /** Zero-based index into `detents`. */
  index: number;
  /** Whether the sheet should animate in on first layout. */
  animateIn?: boolean;
  /** Called after a user-driven snap changes the active index. */
  onIndexChange?: (index: number) => void;
  /** Called when a snap animation settles, including programmatic changes. */
  onSettle?: (index: number) => void;
  /** Called as the sheet position changes, in points from the bottom. */
  onPositionChange?: (position: number) => void;
  /** Internal flag used by `ModalBottomSheet`. */
  modal?: boolean;
  /**
   * Escape hatch that disables sheet/list gesture negotiation.
   * If a gesture starts inside a nested scrollable, that scrollable keeps it
   * even when it cannot scroll any further.
   */
  disableScrollableNegotiation?: boolean;
  /** Scrim color used by `ModalBottomSheet`. */
  scrimColor?: string;
}

/** Native bottom sheet that renders inline within the current screen layout. */
export const BottomSheet = ({
  children,
  style,
  detents = [0, 'content'],
  index,
  animateIn = true,
  onIndexChange,
  onSettle,
  onPositionChange,
  modal = false,
  disableScrollableNegotiation = false,
  scrimColor,
}: BottomSheetProps) => {
  const { height: windowHeight } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const maxHeight = windowHeight - insets.top;
  const nativeDetents = detents.map((detent) => {
    const programmatic = isDetentProgrammatic(detent);
    const value = resolveDetentValue(detent);

    if (value === 'content') {
      return {
        value: 0,
        kind: 'content',
        programmatic,
      };
    }

    return {
      value: Math.max(0, Math.min(value, maxHeight)),
      kind: 'points',
      programmatic,
    };
  });

  const clampedIndex = Math.max(0, Math.min(index, nativeDetents.length - 1));
  const selectedDetentValue = detents[clampedIndex]
    ? resolveDetentValue(detents[clampedIndex])
    : 0;
  const isCollapsed = selectedDetentValue === 0;
  const handleIndexChange = (event: { nativeEvent: { index: number } }) => {
    onIndexChange?.(event.nativeEvent.index);
  };
  const handleSettle = (event: { nativeEvent: { index: number } }) => {
    onSettle?.(event.nativeEvent.index);
  };

  const handlePositionChange = (event: {
    nativeEvent: { position: number };
  }) => {
    const height = event.nativeEvent.position;
    onPositionChange?.(height);
  };

  const sheet = (
    <View
      style={StyleSheet.absoluteFill}
      pointerEvents={modal ? (isCollapsed ? 'none' : 'auto') : 'box-none'}
    >
      <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
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
          detents={nativeDetents}
          maxDetentHeight={maxHeight}
          index={index}
          animateIn={animateIn}
          modal={modal}
          disableScrollableNegotiation={disableScrollableNegotiation}
          scrimColor={scrimColor}
          onIndexChange={handleIndexChange}
          onSettle={handleSettle}
          onPositionChange={handlePositionChange}
        >
          <View collapsable={false} style={{ flex: 1, maxHeight }}>
            {children}
            <View collapsable={false} pointerEvents="none" />
          </View>
        </BottomSheetNativeComponent>
      </View>
    </View>
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

function resolveDetentValue(detent: Detent) {
  if (typeof detent === 'object' && detent !== null) {
    return detent.value;
  }
  return detent;
}
