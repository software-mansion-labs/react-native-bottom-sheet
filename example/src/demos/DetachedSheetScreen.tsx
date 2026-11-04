import { useState } from 'react';
import { Button, Pressable, StyleSheet, Text, View } from 'react-native';
import {
  BottomSheet,
  type Detent,
  type DetentInset,
  ModalBottomSheet,
} from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, MODAL_SCRIM_COLOR, SheetHeader } from '../demoShared';

const FLOATING: DetentInset = { left: 16, right: 16, bottom: 32 };

const LAYOUTS: {
  label: string;
  detents: Detent[];
  detentInsets: DetentInset[];
}[] = [
  {
    label: 'Floating, attached when full',
    detents: [0, 320, '100%'],
    detentInsets: [{}, FLOATING, {}],
  },
  {
    label: 'Always floating, top inset',
    detents: [0, 320, '100%'],
    detentInsets: [{ ...FLOATING, top: 24 }],
  },
  {
    label: 'Content height, floating',
    detents: [0, 'content'],
    detentInsets: [FLOATING],
  },
  {
    label: 'Lopsided gaps',
    detents: [0, 240, '60%'],
    detentInsets: [{}, { left: 48, bottom: 16 }, { right: 48, bottom: 64 }],
  },
];

export const DetachedSheetScreen = () => {
  const [index, setIndex] = useState(0);
  const [layoutIndex, setLayoutIndex] = useState(0);
  const [isModal, setIsModal] = useState(true);
  const [behindPresses, setBehindPresses] = useState(0);
  const layout = LAYOUTS[layoutIndex]!;
  const Sheet = isModal ? ModalBottomSheet : BottomSheet;

  return (
    <DemoScreen
      title="Detached sheet"
      sheet={
        <Sheet
          detents={layout.detents}
          detentInsets={layout.detentInsets}
          index={Math.min(index, layout.detents.length - 1)}
          onIndexChange={setIndex}
          {...(isModal ? { scrimColor: MODAL_SCRIM_COLOR } : {})}
          surfaceStyle={{
            borderRadius: 28,
            borderCurve: 'continuous',
            boxShadow: '0 4 24 rgba(0, 0, 0, 0.3)',
          }}
          surface={
            <View
              style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]}
            />
          }
        >
          {/* Full-bleed band: its width shows whether the content reflows. */}
          <View style={{ height: 72, backgroundColor: '#3b82f6' }}>
            <SheetHeader title={layout.label} onClose={() => setIndex(0)} />
          </View>
          <View style={{ padding: 16, gap: 12 }}>
            <Text>
              Drag between detents: the card's edges move, the blue band keeps
              its width.
            </Text>
            <Button
              title="Next layout"
              onPress={() => setLayoutIndex((i) => (i + 1) % LAYOUTS.length)}
            />
          </View>
        </Sheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
      <Button
        title={isModal ? 'Use inline sheet' : 'Use modal sheet'}
        onPress={() => setIsModal((value) => !value)}
      />
      <Pressable
        // Fills the screen's width, to catch presses in the gaps of an inline sheet.
        style={{ padding: 16, backgroundColor: '#fde68a' }}
        onPress={() => setBehindPresses((count) => count + 1)}
      >
        <Text>Presses behind the sheet: {behindPresses}</Text>
      </Pressable>
    </DemoScreen>
  );
};
