import { useRef, useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  SECTION_HEIGHT,
  SHEET_HEADER_HEIGHT,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

const DURATIONS = [undefined, 0.25, 1, 2, 4] as const;
type Duration = (typeof DURATIONS)[number];

const label = (duration: Duration) =>
  duration === undefined ? 'Default (0.45s)' : `${duration}s`;

/**
 * Picks `settleDuration` and shows how long each settle took, from the moment a
 * drag commits (or a button moves the sheet) to `onSettle`. Compare the same
 * value on iOS, Android and web.
 */
export const SettleDurationScreen = () => {
  const [index, setIndex] = useState(0);
  const [duration, setDuration] = useState<Duration>(undefined);
  const [lastSettle, setLastSettle] = useState<string>('–');
  const settleStart = useRef<number | null>(null);
  const sheetBottomPadding = useSheetBottomPadding(0);

  const startTiming = () => {
    settleStart.current = performance.now();
  };

  const moveTo = (nextIndex: number) => {
    startTiming();
    setIndex(nextIndex);
  };

  return (
    <DemoScreen
      title="Settle duration"
      sheet={
        <BottomSheet
          detents={[0, SHEET_HEADER_HEIGHT + SECTION_HEIGHT, 'content']}
          index={index}
          settleDuration={duration}
          onIndexChange={(nextIndex) => {
            startTiming();
            setIndex(nextIndex);
          }}
          onSettle={() => {
            if (settleStart.current === null) return;
            const elapsed = performance.now() - settleStart.current;
            settleStart.current = null;
            setLastSettle(`${Math.round(elapsed)} ms`);
          }}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader title="Settle duration" onClose={() => moveTo(0)} />
          <View style={styles.section}>
            <Text>Drag between the detents, or flick.</Text>
          </View>
          <View
            style={[
              styles.section,
              styles.divider,
              {
                height: SECTION_HEIGHT + sheetBottomPadding,
                paddingBottom: sheetBottomPadding,
              },
            ]}
          >
            <Text>Section 2</Text>
          </View>
        </BottomSheet>
      }
    >
      <Text style={styles.heading}>settleDuration</Text>
      <View style={styles.durations}>
        {DURATIONS.map((option) => (
          <Button
            key={label(option)}
            title={label(option)}
            color={option === duration ? '#2563eb' : '#9ca3af'}
            onPress={() => setDuration(option)}
          />
        ))}
      </View>
      <Text testID="last-settle" style={styles.readout}>
        Last settle: {lastSettle}
      </Text>
      <View style={styles.actions}>
        <Button title="Open" onPress={() => moveTo(1)} />
        <Button title="Expand" onPress={() => moveTo(2)} />
        <Button title="Collapse" onPress={() => moveTo(0)} />
      </View>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  heading: { fontSize: 15, fontWeight: '600' },
  durations: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  readout: { fontSize: 18, fontVariant: ['tabular-nums'] },
  actions: { flexDirection: 'row', gap: 8 },
  section: {
    height: SECTION_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  divider: { borderTopWidth: 1, borderTopColor: '#eee' },
});
