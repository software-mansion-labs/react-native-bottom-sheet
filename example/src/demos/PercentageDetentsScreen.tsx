import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { BottomSheet, type Detent } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

const DETENTS: Detent[] = ['0%', 80, '45%', '100%'];

export const PercentageDetentsScreen = () => {
  const [index, setIndex] = useState(0);
  const [position, setPosition] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding(0);

  return (
    <DemoScreen
      title="Numeric and percentage detents"
      sheet={
        <BottomSheet
          detents={DETENTS}
          index={index}
          onIndexChange={setIndex}
          onPositionChange={(event) => setPosition(event.nativeEvent.position)}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader title="Responsive detents" onClose={() => setIndex(0)} />
          <View
            style={[
              styles.sheetBody,
              {
                height: 680 + sheetBottomPadding,
                paddingBottom: sheetBottomPadding,
              },
            ]}
          >
            <Text style={styles.heading}>Resize the app window</Text>
            <Text style={styles.body}>
              The 80pt detent stays fixed. The 45% and 100% detents are
              recalculated from the usable native height, including when the app
              window changes size.
            </Text>
          </View>
        </BottomSheet>
      }
    >
      <View style={styles.controls}>
        <Button title="Collapse to 0% (0)" onPress={() => setIndex(0)} />
        <Button title="Open to 80pt (1)" onPress={() => setIndex(1)} />
        <Button title="Open to 45% (2)" onPress={() => setIndex(2)} />
        <Button title="Open to 100% (3)" onPress={() => setIndex(3)} />
      </View>
      <View style={styles.card}>
        <Text style={styles.cardLabel}>Current state</Text>
        <Text>detents: ['0%', 80, '45%', '100%']</Text>
        <Text>index: {index}</Text>
        <Text>position: {position.toFixed(1)}pt</Text>
      </View>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  controls: { gap: 12 },
  sheetBody: {
    paddingHorizontal: 20,
    paddingTop: 20,
    gap: 12,
  },
  heading: { fontSize: 18, fontWeight: '600' },
  body: { fontSize: 15, lineHeight: 22, color: '#555' },
  card: {
    padding: 16,
    borderRadius: 16,
    backgroundColor: '#f3f3f3',
    gap: 6,
  },
  cardLabel: { fontWeight: '600' },
});
