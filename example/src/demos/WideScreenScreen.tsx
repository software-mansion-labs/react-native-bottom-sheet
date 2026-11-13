import { useState } from 'react';
import {
  Button,
  StyleSheet,
  Switch,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import {
  type Detent,
  ModalBottomSheet,
  type SheetPlacement,
} from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SECTION_HEIGHT,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

const MAX_WIDTHS = [undefined, 320, 540] as const;
const PLACEMENTS: SheetPlacement[] = ['leading', 'center', 'trailing'];

/**
 * `maxWidth` and `placement`. On a phone in portrait the sheet fills the width
 * as before; rotate to landscape, or try a tablet, foldable or the web, to see
 * the card capped and placed. Floating insets show that they stay the least gap.
 */
export const WideScreenScreen = () => {
  const [index, setIndex] = useState(0);
  const [maxWidth, setMaxWidth] = useState<number | undefined>(540);
  const [placement, setPlacement] = useState<SheetPlacement>('center');
  const [floating, setFloating] = useState(false);
  const { width } = useWindowDimensions();
  const sheetBottomPadding = useSheetBottomPadding(floating ? 16 : undefined);
  const insets = floating ? { left: 16, right: 16, bottom: 16 } : undefined;
  const detents: Detent[] = [
    0,
    { value: 'content', insets },
    { value: '100%', insets },
  ];

  return (
    <DemoScreen
      title="Wide screens"
      sheet={
        <ModalBottomSheet
          detents={detents}
          index={index}
          onIndexChange={setIndex}
          maxWidth={maxWidth}
          placement={placement}
          scrimColor={MODAL_SCRIM_COLOR}
          surfaceStyle={{
            borderRadius: floating ? 28 : 0,
            borderTopLeftRadius: 28,
            borderTopRightRadius: 28,
            borderCurve: 'continuous',
          }}
          surface={<View style={[StyleSheet.absoluteFill, styles.surface]} />}
        >
          <SheetHeader title="Wide screens" onClose={() => setIndex(0)} />
          <View
            style={[
              styles.section,
              { minHeight: SECTION_HEIGHT, paddingBottom: sheetBottomPadding },
            ]}
          >
            <Text style={styles.readout}>
              maxWidth: {maxWidth ?? 'none'} · placement: {placement}
            </Text>
            <Text>
              This text is laid out once, at the widest card's width, so it
              never reflows as the card moves.
            </Text>
          </View>
        </ModalBottomSheet>
      }
    >
      <Text style={styles.readout}>Window width: {Math.round(width)}</Text>
      <Text style={styles.heading}>maxWidth</Text>
      <View style={styles.options}>
        {MAX_WIDTHS.map((option) => (
          <Button
            key={String(option)}
            title={option === undefined ? 'None' : String(option)}
            color={option === maxWidth ? '#2563eb' : '#9ca3af'}
            onPress={() => setMaxWidth(option)}
          />
        ))}
      </View>
      <Text style={styles.heading}>placement</Text>
      <View style={styles.options}>
        {PLACEMENTS.map((option) => (
          <Button
            key={option}
            title={option}
            color={option === placement ? '#2563eb' : '#9ca3af'}
            onPress={() => setPlacement(option)}
          />
        ))}
      </View>
      <View style={styles.row}>
        <Text>Floating insets (16)</Text>
        <Switch value={floating} onValueChange={setFloating} />
      </View>
      <View style={styles.options}>
        <Button title="Open" onPress={() => setIndex(1)} />
        <Button title="Expand" onPress={() => setIndex(2)} />
      </View>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  heading: { fontSize: 15, fontWeight: '600' },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  readout: { fontSize: 16, fontVariant: ['tabular-nums'] },
  surface: { backgroundColor: 'white' },
  section: { padding: 16, gap: 12 },
});
