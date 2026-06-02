import { useState } from 'react';
import type { NativeSyntheticEvent } from 'react-native';
import { Button, StyleSheet, Text, TextInput, View } from 'react-native';
import Animated, {
  useAnimatedProps,
  useAnimatedStyle,
  useEvent,
  useSharedValue,
} from 'react-native-reanimated';
import {
  ModalBottomSheet,
  type PositionChangeEventData,
} from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, SheetBackground, SheetHeader } from './demoShared';

const DETENTS = [0, 360, 600];
const MAX_POSITION = DETENTS[DETENTS.length - 1]!;

// The modal renders through the provider's portal, but `wrapNativeView` wraps the
// native view (rendered inside that portal), so the worklet still binds to the
// sheet at mount and onPositionChange fires on the UI thread.
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

export const UIThreadModalPositionScreen = () => {
  const [index, setIndex] = useState(1);
  const position = useSharedValue(0);

  // The same event also carries `index`: a fractional detent index, so the UI
  // can track which detent the sheet is heading toward without knowing heights.
  const detentIndex = useSharedValue(0);

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

  const readoutProps = useAnimatedProps(() => ({
    text: `${Math.round(position.value)} pt`,
    defaultValue: '',
  }));

  // A second read-out for the fractional detent index from `index`.
  const detentIndexReadoutProps = useAnimatedProps(() => ({
    text: `detent ${detentIndex.value.toFixed(2)}`,
    defaultValue: '',
  }));

  // A bar driven off `index` normalized across the detent range.
  const detentIndexBarStyle = useAnimatedStyle(() => ({
    width: `${(detentIndex.value / (DETENTS.length - 1)) * 100}%`,
  }));

  // A blue circle that rides the modal sheet's top edge on the UI thread.
  const circleStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: -position.value }],
  }));

  return (
    <DemoScreen
      title="UI-thread modal onPositionChange"
      sheet={
        <>
          <Animated.View
            pointerEvents="none"
            style={[styles.circleAnchor, circleStyle]}
          >
            <View style={styles.circle} />
          </Animated.View>
          <ModalBottomSheet
            wrapNativeView={Animated.createAnimatedComponent}
            detents={DETENTS}
            index={index}
            onIndexChange={setIndex}
            onPositionChange={onPositionChange}
            scrimColor="rgba(0, 0, 0, 0.5)"
            surface={<SheetBackground style={StyleSheet.absoluteFill} />}
          >
            <SheetHeader title="UI-thread modal" onClose={() => setIndex(0)} />
            <View style={styles.sheetBody}>
              <Text style={styles.heading}>Drag the modal sheet</Text>
              <Text style={styles.body}>
                The read-out behind the scrim is updated by a Reanimated worklet
                on the UI thread, even though the modal renders through a
                portal.
              </Text>
            </View>
          </ModalBottomSheet>
        </>
      }
    >
      <View style={{ gap: 12 }}>
        <Button title="Open at 360pt" onPress={() => setIndex(1)} />
        <Button title="Expand to 600pt" onPress={() => setIndex(2)} />
        <Button title="Close" onPress={() => setIndex(0)} />
      </View>
      <View style={styles.card}>
        <Text style={styles.cardLabel}>Position (UI thread)</Text>
        <AnimatedTextInput
          editable={false}
          value={undefined}
          animatedProps={readoutProps}
          style={styles.readout}
        />
        <Text style={styles.hint}>max detent: {MAX_POSITION}pt</Text>
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
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  sheetBody: { height: 560, paddingHorizontal: 20, paddingTop: 16, gap: 12 },
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
  readout: { fontSize: 36, fontWeight: '700', color: '#1f1f1f', padding: 0 },
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
  circleAnchor: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
  },
  circle: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: '#3b82f6',
  },
});
