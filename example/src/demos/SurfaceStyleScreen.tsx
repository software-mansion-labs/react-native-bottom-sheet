import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
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

const SHAPES: { label: string; style: SurfaceStyle | undefined }[] = [
  {
    label: 'Top corners, continuous',
    style: {
      borderTopLeftRadius: 32,
      borderTopRightRadius: 32,
      borderCurve: 'continuous',
    },
  },
  { label: 'All corners', style: { borderRadius: 32 } },
  {
    label: 'Mixed radii',
    style: {
      borderTopStartRadius: 48,
      borderTopEndRadius: 12,
      borderBottomLeftRadius: 24,
      borderBottomRightRadius: 0,
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
