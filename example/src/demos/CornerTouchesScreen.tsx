import { useRef, useState } from 'react';
import {
  Animated,
  Button,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  GestureHandlerRootView,
  Pressable as GesturePressable,
} from 'react-native-gesture-handler';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, useSheetBottomPadding } from '../demoShared';

const DETENT = 320;
const RADIUS = 48;

const TARGETS = ['RN Pressable', 'RN Button', 'RNGH Pressable'] as const;
type Target = (typeof TARGETS)[number];

/**
 * Controls behind each of the card's rounded top corners, and a control filling
 * the card's top edge. Pressing a corner must reach the control showing through
 * it and never the one the card clips away there.
 */
export const CornerTouchesScreen = () => {
  const [index, setIndex] = useState(1);
  const [target, setTarget] = useState<Target>('RN Pressable');
  const [behindPresses, setBehindPresses] = useState(0);
  const [sheetPresses, setSheetPresses] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding();
  // Each control flashes when pressed, as little of it shows through a corner.
  const flashes = useRef([
    new Animated.Value(0),
    new Animated.Value(0),
  ]).current;
  const pressBehind = (side: number) => {
    setBehindPresses((count) => count + 1);
    flashes[side]!.setValue(1);
    Animated.timing(flashes[side]!, {
      toValue: 0,
      duration: 700,
      useNativeDriver: Platform.OS !== 'web',
    }).start();
  };
  const pressSheet = () => setSheetPresses((count) => count + 1);

  return (
    <GestureHandlerRootView style={styles.root}>
      <DemoScreen
        title="Corner touches"
        sheet={
          <>
            {[styles.left, styles.right].map((position, side) => (
              <View key={side} style={[styles.behind, position]}>
                {target === 'RN Pressable' ? (
                  <Pressable
                    testID={`behind-pressable-${side}`}
                    style={styles.fill}
                    onPress={() => pressBehind(side)}
                  />
                ) : target === 'RN Button' ? (
                  <Button
                    title="Behind"
                    color="#f97316"
                    onPress={() => pressBehind(side)}
                  />
                ) : (
                  <GesturePressable
                    testID={`behind-gesture-${side}`}
                    style={styles.fill}
                    onPress={() => pressBehind(side)}
                  />
                )}
                <Animated.View
                  pointerEvents="none"
                  style={[
                    StyleSheet.absoluteFill,
                    styles.flash,
                    { opacity: flashes[side] },
                  ]}
                />
              </View>
            ))}
            <BottomSheet
              detents={[0, DETENT]}
              index={index}
              onIndexChange={setIndex}
              surfaceStyle={{
                borderTopLeftRadius: RADIUS,
                borderTopRightRadius: RADIUS,
              }}
              surface={
                <View
                  style={[
                    StyleSheet.absoluteFill,
                    { backgroundColor: 'white' },
                  ]}
                />
              }
            >
              {target === 'RNGH Pressable' ? (
                <GesturePressable
                  testID="sheet-band"
                  style={styles.band}
                  onPress={pressSheet}
                >
                  <Text>Sheet presses: {sheetPresses}</Text>
                </GesturePressable>
              ) : (
                <Pressable
                  testID="sheet-band"
                  style={styles.band}
                  onPress={pressSheet}
                >
                  <Text>Sheet presses: {sheetPresses}</Text>
                </Pressable>
              )}
              <View style={{ padding: 16, paddingBottom: sheetBottomPadding }}>
                <Text>
                  The blue band fills the card's top edge, so the rounded
                  corners cut it. Pressing a corner counts as a press behind the
                  sheet, not on the band.
                </Text>
              </View>
            </BottomSheet>
          </>
        }
      >
        <Text testID="behind-count" style={styles.count}>
          Behind presses: {behindPresses}
        </Text>
        <View style={styles.targets}>
          {TARGETS.map((name) => (
            <Button
              key={name}
              title={name}
              color={name === target ? '#2563eb' : '#9ca3af'}
              onPress={() => setTarget(name)}
            />
          ))}
        </View>
      </DemoScreen>
    </GestureHandlerRootView>
  );
};

const styles = StyleSheet.create({
  root: { flex: 1 },
  count: { fontSize: 18, marginBottom: 12 },
  targets: { gap: 8 },
  // Straddles the card's top edge, so the corner shows part of it.
  behind: {
    position: 'absolute',
    bottom: DETENT - 40,
    width: 140,
    height: 60,
    backgroundColor: '#f97316',
    justifyContent: 'flex-end',
  },
  left: { left: 0 },
  right: { right: 0 },
  fill: { flex: 1 },
  flash: { backgroundColor: '#22c55e' },
  band: {
    height: 72,
    backgroundColor: '#3b82f6',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
