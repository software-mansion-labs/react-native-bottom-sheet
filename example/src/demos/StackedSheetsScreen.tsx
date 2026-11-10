import { useState } from 'react';
import { Button, StyleSheet, Switch, Text, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, SheetHeader } from '../demoShared';

const SHEETS = [
  { name: 'First', color: '#ffffff' },
  { name: 'Second', color: '#eef4ff' },
  { name: 'Third', color: '#f1fbef' },
];

export const StackedSheetsScreen = () => {
  // The detent index of each sheet: closed, medium, large.
  const [indices, setIndices] = useState(() => SHEETS.map(() => 0));
  const [stacked, setStacked] = useState(true);
  const [nativeOverlay, setNativeOverlay] = useState(false);

  const setIndex = (sheet: number, index: number) =>
    setIndices((current) =>
      current.map((value, other) => (other === sheet ? index : value))
    );

  return (
    <DemoScreen
      title="Stacked sheets"
      sheet={SHEETS.map(({ name, color }, sheet) => (
        <ModalBottomSheet
          key={name}
          // The first sheet has nothing beneath it to push back.
          stacked={stacked && sheet > 0}
          nativeOverlay={nativeOverlay}
          detents={[0, '50%', '100%']}
          index={indices[sheet]!}
          onIndexChange={(index) => setIndex(sheet, index)}
          scrimColor="rgba(0, 0, 0, 0.2)"
          surfaceStyle={styles.card}
          surface={
            <View
              style={[StyleSheet.absoluteFill, { backgroundColor: color }]}
            />
          }
        >
          <SheetHeader
            title={`${name} sheet`}
            onClose={() => setIndex(sheet, 0)}
          />
          <View style={styles.content}>
            <Text>Drag between the medium and large detents.</Text>
            {sheet < SHEETS.length - 1 && (
              <>
                <Button
                  title="Open next sheet (large)"
                  onPress={() => setIndex(sheet + 1, 2)}
                />
                <Button
                  title="Open next sheet (medium)"
                  onPress={() => setIndex(sheet + 1, 1)}
                />
              </>
            )}
            <Button
              title="Close all"
              onPress={() => setIndices(SHEETS.map(() => 0))}
            />
          </View>
        </ModalBottomSheet>
      ))}
    >
      <View style={styles.controls}>
        <View style={styles.row}>
          <Text>stacked</Text>
          <Switch value={stacked} onValueChange={setStacked} />
        </View>
        <View style={styles.row}>
          <Text>nativeOverlay</Text>
          <Switch value={nativeOverlay} onValueChange={setNativeOverlay} />
        </View>
        <Button title="Open sheet (large)" onPress={() => setIndex(0, 2)} />
        <Button title="Open sheet (medium)" onPress={() => setIndex(0, 1)} />
      </View>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  card: {
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    boxShadow: '0px -1px 11px rgba(0, 0, 0, 0.16)',
  },
  content: { padding: 16, gap: 12, alignItems: 'flex-start' },
  controls: { gap: 12, alignItems: 'center' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
});
