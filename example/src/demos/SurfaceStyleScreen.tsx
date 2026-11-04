import { useState } from 'react';
import {
  Button,
  DynamicColorIOS,
  Platform,
  PlatformColor,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  ModalBottomSheet,
  type SurfaceStyle,
} from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

// Platform colors resolve natively. Only the current platform's factory may
// run: the others throw.
const PLATFORM_SHADOW_COLOR =
  Platform.OS === 'ios'
    ? // Flips with the system appearance.
      DynamicColorIOS({
        light: 'rgba(0, 0, 0, 0.5)',
        dark: 'rgba(255, 200, 0, 0.7)',
      })
    : Platform.OS === 'android'
      ? PlatformColor('@android:color/holo_orange_dark')
      : 'rgba(0, 0, 0, 0.5)';

const SHAPES: { label: string; style: SurfaceStyle | undefined }[] = [
  {
    label: 'Top corners, red shadow',
    style: {
      borderTopLeftRadius: 32,
      borderTopRightRadius: 32,
      borderCurve: 'continuous',
      boxShadow: '0 -6 24 rgba(255, 0, 0, 0.6)',
    },
  },
  { label: 'All corners, no shadow', style: { borderRadius: 32 } },
  {
    label: 'Mixed radii, two shadows',
    style: {
      borderTopStartRadius: 48,
      borderTopEndRadius: 12,
      borderBottomLeftRadius: 24,
      borderBottomRightRadius: 0,
      boxShadow: [
        { offsetX: 0, offsetY: -2, blurRadius: 4, color: 'rgba(0, 0, 0, 0.3)' },
        {
          offsetX: 0,
          offsetY: -12,
          blurRadius: 32,
          spreadDistance: 4,
          color: 'rgba(0, 120, 255, 0.5)',
        },
      ],
    },
  },
  {
    label: 'Continuous, platform color',
    style: {
      borderTopLeftRadius: 48,
      borderTopRightRadius: 20,
      borderCurve: 'continuous',
      boxShadow: [
        {
          offsetX: 0,
          offsetY: -8,
          blurRadius: 32,
          color: PLATFORM_SHADOW_COLOR,
        },
      ],
    },
  },
  {
    label: 'Elevation and shadow props',
    style: {
      borderTopLeftRadius: 32,
      borderTopRightRadius: 32,
      elevation: 24,
      shadowColor: 'rgb(255, 0, 0)',
      shadowOffset: { width: 0, height: -6 },
      shadowOpacity: 0.6,
      shadowRadius: 12,
    },
  },
  {
    label: 'Mixed radii, elevation',
    style: {
      borderTopLeftRadius: 48,
      borderTopRightRadius: 12,
      elevation: 24,
      shadowColor: 'rgb(0, 120, 255)',
      shadowOffset: { width: 0, height: -6 },
      shadowOpacity: 0.6,
      shadowRadius: 12,
    },
  },
  { label: 'No surfaceStyle', style: undefined },
];

export const SurfaceStyleScreen = () => {
  const [index, setIndex] = useState(0);
  const [shapeIndex, setShapeIndex] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding();
  const shape = SHAPES[shapeIndex]!;

  return (
    <DemoScreen
      title="Surface style"
      sheet={
        <ModalBottomSheet
          detents={[0, 320, '90%']}
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surfaceStyle={shape.style}
          surface={
            <View
              style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]}
            />
          }
        >
          {/* Full-bleed band: the card's corners must cut it. */}
          <View style={{ height: 72, backgroundColor: '#3b82f6' }}>
            <SheetHeader title={shape.label} onClose={() => setIndex(0)} />
          </View>
          <View
            style={{
              padding: 16,
              paddingBottom: sheetBottomPadding,
              gap: 12,
            }}
          >
            <Text>
              The blue band is part of the content, not the surface. With a
              surfaceStyle, it is clipped to the card's rounded corners.
            </Text>
            <Button
              title="Next shape"
              onPress={() => setShapeIndex((i) => (i + 1) % SHAPES.length)}
            />
          </View>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};
