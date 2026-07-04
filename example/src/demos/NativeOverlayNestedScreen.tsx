import { type ComponentRef, useRef, useState } from 'react';
import { Button, Pressable, StyleSheet, Text, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

/**
 * A native-overlay sheet mounted at a NONZERO origin (nested inside an offset
 * container, beside other content). It must still position and measure correctly
 * — and, crucially, its content must be tappable. This exercises the re-rooting
 * (so `measureInWindow` reports the on-screen position regardless of where the
 * sheet sits in the tree) and the overlay content bounds (so hit-testing reaches
 * the content). Tap the rows and confirm the counter increments.
 *
 * Note: the sheet is intentionally rendered inside the screen body (not via
 * DemoScreen's `sheet` slot) so it mounts at a nonzero origin.
 */
export const NativeOverlayNestedScreen = () => {
  const [index, setIndex] = useState(0);
  const [taps, setTaps] = useState(0);
  const [measured, setMeasured] = useState('(tap "Measure header")');
  const headerRef = useRef<ComponentRef<typeof View>>(null);
  const bottomPadding = useSheetBottomPadding();

  const measureHeader = () => {
    headerRef.current?.measureInWindow(
      (x: number, y: number, w: number, h: number) => {
        setMeasured(
          `x=${x.toFixed(0)} y=${y.toFixed(0)} w=${w.toFixed(0)} h=${h.toFixed(0)}`
        );
      }
    );
  };

  return (
    <DemoScreen title="Native overlay nested mount">
      <Text style={{ color: '#555', lineHeight: 22 }}>
        A <Text style={{ fontWeight: 'bold' }}>nativeOverlay</Text> sheet
        mounted at a nonzero origin — nested inside the offset box below. It
        should still appear full-screen, measure correctly, and be tappable.
      </Text>

      {/* Offset, nested container → the sheet mounts at a nonzero origin. */}
      <View style={{ marginTop: 120, marginLeft: 80, alignSelf: 'flex-start' }}>
        <Text style={{ marginBottom: 8, color: '#999' }}>Nested mount</Text>
        <Button title="Open sheet" onPress={() => setIndex(1)} />

        <ModalBottomSheet
          nativeOverlay
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <View ref={headerRef} collapsable={false}>
            <SheetHeader title="Nested overlay" onClose={() => setIndex(0)} />
          </View>
          <View style={{ padding: 24, paddingBottom: bottomPadding, gap: 12 }}>
            <Text selectable>measureInWindow(header): {measured}</Text>
            <Button title="Measure header" onPress={measureHeader} />
            {Array.from({ length: 4 }, (_, i) => (
              <Pressable
                key={i}
                onPress={() => setTaps((t) => t + 1)}
                style={{
                  padding: 16,
                  backgroundColor: '#eee',
                  borderRadius: 8,
                }}
              >
                <Text>
                  Tappable row {i} — taps: {taps}
                </Text>
              </Pressable>
            ))}
          </View>
        </ModalBottomSheet>
      </View>
    </DemoScreen>
  );
};
