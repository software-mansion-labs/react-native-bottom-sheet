import { useState } from 'react';
import type { NativeSyntheticEvent } from 'react-native';
import { StyleSheet, Text, TextInput, View } from 'react-native';
import Animated, {
  useAnimatedProps,
  useAnimatedStyle,
  useEvent,
  useSharedValue,
} from 'react-native-reanimated';
import {
  BottomSheet,
  type PositionChangeEventData,
} from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, SheetBackground, SheetHeader } from './demoShared';

const DETENTS = [120, 360, 600];
const MAX_POSITION = DETENTS[DETENTS.length - 1]!;

// The library stays unopinionated about Reanimated: hand it
// `createAnimatedComponent` via `wrapNativeView` and it wraps the native sheet
// view itself. onPositionChange is a standard native event, so a useEvent
// worklet runs on the UI thread, synchronously, as the sheet moves—no cast.
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

export const UIThreadPositionScreen = () => {
  const [index, setIndex] = useState(1);

  // Driven entirely on the UI thread by the worklet below—no JS-thread
  // round-trip, so it stays in sync with the sheet even during a fast drag.
  const position = useSharedValue(0);

  // The same event also carries `index`: a fractional detent index, so the UI
  // can track which detent the sheet is heading toward without knowing heights.
  const detentIndex = useSharedValue(0);

  // Parameterized with the prop's event type, so the handler is assignable with
  // no cast. Reanimated still unwraps `nativeEvent` for the worklet body, so we
  // read `event.position` directly.
  const onPositionChange = useEvent<
    NativeSyntheticEvent<PositionChangeEventData>
  >(
    (event) => {
      'worklet';
      position.value = event.position;
      detentIndex.value = event.index;
    },
    ['onPositionChange']
  );

  // A read-out animating a TextInput's text from the UI thread.
  const readoutProps = useAnimatedProps(() => ({
    text: `${Math.round(position.value)} pt`,
    defaultValue: '',
  }));

  // A second read-out for the fractional detent index from `index`.
  const detentIndexReadoutProps = useAnimatedProps(() => ({
    text: `detent ${detentIndex.value.toFixed(2)}`,
    defaultValue: '',
  }));

  // A progress bar whose width tracks the sheet height in real time.
  const barStyle = useAnimatedStyle(() => ({
    width: `${Math.min(1, position.value / MAX_POSITION) * 100}%`,
  }));

  // The same bar, driven off `index` normalized across the detent range.
  const detentIndexBarStyle = useAnimatedStyle(() => ({
    width: `${(detentIndex.value / (DETENTS.length - 1)) * 100}%`,
  }));

  // A marker pinned to the bottom that rides the sheet's top edge upward.
  const markerStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: -position.value }],
  }));

  return (
    <DemoScreen
      title="UI-thread onPositionChange"
      sheet={
        <>
          <Animated.View
            pointerEvents="none"
            style={[styles.marker, markerStyle]}
          >
            <View style={styles.markerDot} />
          </Animated.View>
          <BottomSheet
            wrapNativeView={Animated.createAnimatedComponent}
            detents={DETENTS}
            index={index}
            onIndexChange={setIndex}
            onPositionChange={onPositionChange}
            surface={<SheetBackground style={StyleSheet.absoluteFill} />}
          >
            <SheetHeader
              title="UI-thread onPositionChange"
              onClose={() => setIndex(0)}
            />
            <View style={styles.sheetBody}>
              <Text style={styles.heading}>Drag the sheet</Text>
              <Text style={styles.body}>
                The read-out, progress bar, and the dot riding the sheet edge
                are all updated by a Reanimated worklet on the UI thread—no
                JS-thread state, no per-frame bridge traffic.
              </Text>
            </View>
          </BottomSheet>
        </>
      }
    >
      <View style={styles.card}>
        <Text style={styles.cardLabel}>Position (UI thread)</Text>
        <AnimatedTextInput
          editable={false}
          // The text is set via animatedProps from the worklet.
          value={undefined}
          animatedProps={readoutProps}
          style={styles.readout}
        />
        <View style={styles.track}>
          <Animated.View style={[styles.fill, barStyle]} />
        </View>
      </View>
      <View style={styles.card}>
        <Text style={styles.cardLabel}>
          Detent index / progress (UI thread)
        </Text>
        <AnimatedTextInput
          editable={false}
          value={undefined}
          animatedProps={detentIndexReadoutProps}
          style={styles.readout}
        />
        <Text style={styles.hint}>detent index 0 → {DETENTS.length - 1}</Text>
        <View style={styles.track}>
          <Animated.View style={[styles.fill, detentIndexBarStyle]} />
        </View>
      </View>
      <Text style={styles.hint}>
        index: {index} · detents: [{DETENTS.join(', ')}]
      </Text>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  sheetBody: {
    height: 560,
    paddingHorizontal: 20,
    paddingTop: 16,
    gap: 12,
  },
  heading: { fontSize: 18, fontWeight: '600' },
  body: { fontSize: 15, lineHeight: 22, color: '#555' },
  card: {
    padding: 16,
    borderRadius: 16,
    backgroundColor: '#f3f3f3',
    gap: 12,
  },
  cardLabel: { fontWeight: '600', color: '#1f1f1f' },
  hint: { color: '#555' },
  readout: {
    fontSize: 36,
    fontWeight: '700',
    color: '#1f1f1f',
    padding: 0,
  },
  track: {
    height: 8,
    borderRadius: 4,
    backgroundColor: '#ddd',
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
    borderRadius: 4,
    backgroundColor: '#3b82f6',
  },
  marker: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
  },
  markerDot: {
    width: 14,
    height: 14,
    borderRadius: 7,
    backgroundColor: '#3b82f6',
  },
});
