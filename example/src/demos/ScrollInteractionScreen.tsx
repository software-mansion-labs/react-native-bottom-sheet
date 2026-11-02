import { useState } from 'react';
import {
  FlatList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';
import {
  BottomSheet,
  programmatic,
  type Detent,
} from '@swmansion/react-native-bottom-sheet';

import {
  DATA,
  DemoScreen,
  INLINE_FLATLIST_PREVIEW_ITEMS,
  LIST_ITEM_HEIGHT,
  ListRow,
  SheetBackground,
  useSheetBottomPadding,
} from '../demoShared';

const CASES = [
  {
    id: 'down-up',
    title: 'Down then up',
    gesture:
      'In the center of a row, drag down 30pt, then swipe up without lifting.',
    expected:
      'The sheet expands. The list stays at its start until full height.',
  },
  {
    id: 'thumb-arc',
    title: 'Thumb arc',
    gesture:
      'Drag left with a downward arc. Wiggle up, dip back to preview, then swipe up quickly, all in one touch.',
    expected: 'The sheet retakes the gesture and expands after the reversal.',
  },
  {
    id: 'scrolled-list',
    title: 'Scrolled list',
    gesture:
      'Start at Item 6. Drag left and down, wiggle up, dip back to preview, then swipe up without lifting.',
    expected:
      'The sheet expands and preserves a scrolled position instead of jumping to Item 1.',
  },
  {
    id: 'initial-expand',
    title: 'Initial: expand',
    gesture:
      'At preview, drag down 30pt, then swipe up without lifting. Compare with a fresh upward drag.',
    expected:
      'The reversal stays with the list. A fresh upward drag expands the sheet.',
  },
  {
    id: 'initial-collapse',
    title: 'Initial: collapse',
    gesture:
      'Start fully expanded with the list at its top. Drag up 60pt, then down 15pt without lifting.',
    expected:
      'The list keeps the gesture and its scrolled position. The sheet stays expanded.',
  },
] as const;

type InteractionCase = (typeof CASES)[number];
const SHEET_HEADER_HEIGHT = 112;
const PREVIEW_HEIGHT =
  SHEET_HEADER_HEIGHT + LIST_ITEM_HEIGHT * INLINE_FLATLIST_PREVIEW_ITEMS;
const DETENTS: Detent[] = [programmatic(0), PREVIEW_HEIGHT, 'content'];
const HEIGHT_LABELS = ['Closed', 'Preview', 'Expanded'] as const;

export const ScrollInteractionScreen = () => {
  const [selectedCase, setSelectedCase] = useState<InteractionCase>(CASES[0]);
  const [run, setRun] = useState(0);
  const [index, setIndex] = useState(0);
  const [settledIndex, setSettledIndex] = useState<number | null>(0);
  const [scrollOffset, setScrollOffset] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding();

  const selectCase = (nextCase: InteractionCase) => {
    setSelectedCase(nextCase);
    setRun((value) => value + 1);
    setIndex(0);
    setSettledIndex(0);
    setScrollOffset(nextCase.id === 'scrolled-list' ? 5 * LIST_ITEM_HEIGHT : 0);
  };

  const reset = () => {
    // Remount to reset recognizers and the list offset.
    const startIndex = selectedCase.id === 'initial-collapse' ? 2 : 1;
    setRun((value) => value + 1);
    setIndex(startIndex);
    setSettledIndex(startIndex);
    setScrollOffset(
      selectedCase.id === 'scrolled-list' ? 5 * LIST_ITEM_HEIGHT : 0
    );
  };

  const selectHeight = (nextIndex: number) => {
    if (nextIndex !== index) {
      setSettledIndex(null);
      setIndex(nextIndex);
    }
  };

  // Sample at gesture/deceleration boundaries so the readout does not add
  // React renders to the per-frame native gesture path under test.
  const reportScrollOffset = (
    event: NativeSyntheticEvent<NativeScrollEvent>
  ) => {
    setScrollOffset(Math.round(event.nativeEvent.contentOffset.y));
  };
  const status = settledIndex === null ? 'Moving' : HEIGHT_LABELS[settledIndex];

  return (
    <DemoScreen
      title="Scroll interaction"
      scrollableControls
      sheet={
        <BottomSheet
          key={run}
          detents={DETENTS}
          index={index}
          animateIn={false}
          onIndexChange={selectHeight}
          onSettle={setSettledIndex}
          scrollableNegotiation={{
            expand:
              selectedCase.id === 'initial-expand' ? 'initial' : 'handoff',
            collapse: 'initial',
          }}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <View
            testID="gesture-regression-sheet-header"
            style={styles.sheetHeader}
          >
            <View style={styles.grabberArea}>
              <View style={styles.grabber} />
            </View>
            <View style={styles.sheetTitleRow}>
              <Text
                testID="gesture-regression-status"
                numberOfLines={1}
                style={styles.sheetTitle}
              >
                {selectedCase.title} · {status}
              </Text>
              <TouchableOpacity
                accessibilityRole="button"
                accessibilityLabel="Close interaction sheet"
                testID="gesture-regression-close"
                onPress={() => selectHeight(0)}
                style={styles.closeButton}
              >
                <Text style={styles.closeText}>×</Text>
              </TouchableOpacity>
            </View>
            <View style={styles.readouts}>
              <Text testID="gesture-regression-offset" style={styles.readout}>
                Last offset: {scrollOffset}pt
              </Text>
            </View>
            <View style={styles.sheetActions}>
              <SheetAction title="Reset case" id="reset" onPress={reset} />
              <SheetAction
                title="Preview"
                id="preview"
                onPress={() => selectHeight(1)}
              />
              <SheetAction
                title="Full"
                id="full"
                onPress={() => selectHeight(2)}
              />
            </View>
          </View>
          <FlatList
            testID="gesture-regression-list"
            data={DATA}
            initialScrollIndex={selectedCase.id === 'scrolled-list' ? 5 : 0}
            getItemLayout={(_, itemIndex) => ({
              length: LIST_ITEM_HEIGHT,
              offset: LIST_ITEM_HEIGHT * itemIndex,
              index: itemIndex,
            })}
            onScrollEndDrag={reportScrollOffset}
            onMomentumScrollEnd={reportScrollOffset}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ paddingBottom: sheetBottomPadding }}
            renderItem={({ item, index: itemIndex }) => (
              <ListRow item={item} index={itemIndex} />
            )}
          />
        </BottomSheet>
      }
    >
      <Text style={styles.hint}>
        Choose a case. Reset case repeats its starting state.
      </Text>
      <View style={styles.cases}>
        {CASES.map((item) => (
          <TouchableOpacity
            key={item.id}
            accessibilityRole="radio"
            accessibilityState={{ checked: selectedCase.id === item.id }}
            testID={`gesture-case-${item.id}`}
            onPress={() => selectCase(item)}
            style={[
              styles.caseButton,
              selectedCase.id === item.id && styles.caseButtonSelected,
            ]}
          >
            <Text
              style={[
                styles.caseTitle,
                selectedCase.id === item.id && styles.caseTitleSelected,
              ]}
            >
              {item.title}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
      <View style={styles.instructions}>
        <Text style={styles.instructionText}>{selectedCase.gesture}</Text>
        <Text style={styles.instructionText}>
          <Text style={styles.expectedLabel}>Expected: </Text>
          {selectedCase.expected}
        </Text>
      </View>
      <TouchableOpacity
        accessibilityRole="button"
        testID="gesture-regression-start"
        onPress={reset}
        style={styles.startButton}
      >
        <Text style={styles.startText}>
          Start{' '}
          {selectedCase.id === 'initial-collapse'
            ? 'fully expanded'
            : 'at preview'}
        </Text>
      </TouchableOpacity>
    </DemoScreen>
  );
};

const SheetAction = ({
  title,
  id,
  onPress,
}: {
  title: string;
  id: string;
  onPress: () => void;
}) => (
  <TouchableOpacity
    accessibilityRole="button"
    accessibilityLabel={`${title} interaction sheet`}
    testID={`gesture-regression-${id}`}
    onPress={onPress}
    style={styles.sheetAction}
  >
    <Text style={styles.sheetActionText}>{title}</Text>
  </TouchableOpacity>
);

const styles = StyleSheet.create({
  hint: { color: '#555', fontSize: 14 },
  cases: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  caseButton: {
    flexBasis: '48%',
    flexGrow: 1,
    minHeight: 36,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 6,
    borderWidth: 1,
    borderColor: '#d7d7d7',
    borderRadius: 8,
  },
  caseButtonSelected: { backgroundColor: '#1f1f1f', borderColor: '#1f1f1f' },
  caseTitle: { color: '#333', fontSize: 14, fontWeight: '600' },
  caseTitleSelected: { color: 'white' },
  instructions: { gap: 6 },
  instructionText: { color: '#555', fontSize: 14, lineHeight: 19 },
  expectedLabel: { color: '#222', fontWeight: '600' },
  startButton: {
    minHeight: 40,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f0f4ff',
  },
  startText: { color: '#1764c0', fontSize: 15, fontWeight: '600' },
  sheetHeader: {
    height: SHEET_HEADER_HEIGHT,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  grabberArea: { height: 16, paddingTop: 8, alignItems: 'center' },
  grabber: { width: 36, height: 4, borderRadius: 2, backgroundColor: '#ddd' },
  sheetTitleRow: {
    height: 28,
    paddingLeft: 16,
    paddingRight: 8,
    flexDirection: 'row',
    alignItems: 'center',
  },
  sheetTitle: { flex: 1, color: '#222', fontSize: 15, fontWeight: '600' },
  closeButton: {
    width: 44,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeText: { color: '#555', fontSize: 25, lineHeight: 28 },
  readouts: {
    height: 32,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  readout: { color: '#555', fontSize: 12 },
  sheetActions: { flexDirection: 'row', flex: 1 },
  sheetAction: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  sheetActionText: { color: '#1764c0', fontSize: 14, fontWeight: '500' },
});
