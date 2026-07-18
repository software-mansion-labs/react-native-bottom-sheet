import { useState } from 'react';
import {
  Button,
  FlatList,
  StyleSheet,
  Switch,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  BottomSheet,
  type ScrollableNegotiationMode,
} from '@swmansion/react-native-bottom-sheet';

import {
  DATA,
  DemoScreen,
  INLINE_FLATLIST_PREVIEW_ITEMS,
  LIST_ITEM_HEIGHT,
  ListRow,
  SHEET_HEADER_HEIGHT,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

const MODES: ScrollableNegotiationMode[] = ['none', 'initial', 'handoff'];

export const ScrollableNegotiationScreen = () => {
  const [index, setIndex] = useState(0);
  const [expand, setExpand] = useState<ScrollableNegotiationMode>('handoff');
  const [collapse, setCollapse] =
    useState<ScrollableNegotiationMode>('initial');
  const [scrollEnabled, setScrollEnabled] = useState(true);
  const sheetBottomPadding = useSheetBottomPadding();

  return (
    <DemoScreen
      title="Scrollable negotiation"
      sheet={
        <BottomSheet
          detents={[
            0,
            SHEET_HEADER_HEIGHT +
              LIST_ITEM_HEIGHT * INLINE_FLATLIST_PREVIEW_ITEMS,
            'content',
          ]}
          index={index}
          onIndexChange={setIndex}
          scrollableNegotiation={{ expand, collapse }}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Scrollable negotiation"
            onClose={() => setIndex(0)}
          />
          <FlatList
            data={DATA}
            scrollEnabled={scrollEnabled}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ paddingBottom: sheetBottomPadding }}
            renderItem={({ item, index: itemIndex }) => (
              <ListRow item={item} index={itemIndex} />
            )}
          />
        </BottomSheet>
      }
    >
      <Text style={styles.instructions}>
        Change either direction while the sheet is open, then drag on a list
        row. “Initial” picks one owner at touch-down; “handoff” can cross the
        boundary without lifting.
      </Text>
      <ModeSelector label="Expand" value={expand} onChange={setExpand} />
      <ModeSelector label="Collapse" value={collapse} onChange={setCollapse} />
      <View style={styles.toggleRow}>
        <Text style={styles.selectorLabel}>List scrolling</Text>
        <Switch value={scrollEnabled} onValueChange={setScrollEnabled} />
      </View>
      <View style={styles.actions}>
        <Button title="Open preview" onPress={() => setIndex(1)} />
        <Button title="Open fully" onPress={() => setIndex(2)} />
        <Button title="Close" onPress={() => setIndex(0)} />
      </View>
    </DemoScreen>
  );
};

const ModeSelector = ({
  label,
  value,
  onChange,
}: {
  label: string;
  value: ScrollableNegotiationMode;
  onChange: (value: ScrollableNegotiationMode) => void;
}) => (
  <View style={styles.selector}>
    <Text style={styles.selectorLabel}>{label}</Text>
    <View style={styles.options}>
      {MODES.map((mode) => {
        const selected = mode === value;
        return (
          <TouchableOpacity
            key={mode}
            accessibilityRole="radio"
            accessibilityState={{ selected }}
            onPress={() => onChange(mode)}
            style={[styles.option, selected && styles.optionSelected]}
          >
            <Text
              style={[styles.optionText, selected && styles.optionTextSelected]}
            >
              {mode}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
  </View>
);

const styles = StyleSheet.create({
  instructions: {
    color: '#555',
    fontSize: 14,
    lineHeight: 20,
  },
  selector: {
    gap: 8,
  },
  selectorLabel: {
    color: '#222',
    fontSize: 15,
    fontWeight: '600',
  },
  toggleRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  options: {
    flexDirection: 'row',
    gap: 8,
  },
  option: {
    flex: 1,
    alignItems: 'center',
    borderColor: '#d7d7d7',
    borderRadius: 8,
    borderWidth: 1,
    paddingHorizontal: 8,
    paddingVertical: 10,
  },
  optionSelected: {
    backgroundColor: '#1f1f1f',
    borderColor: '#1f1f1f',
  },
  optionText: {
    color: '#333',
    fontSize: 13,
    fontWeight: '600',
  },
  optionTextSelected: {
    color: 'white',
  },
  actions: {
    gap: 8,
    paddingTop: 4,
  },
});
