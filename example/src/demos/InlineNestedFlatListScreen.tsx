import { useState } from 'react';
import { Button, FlatList, StyleSheet, Text, View } from 'react-native';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DATA,
  DemoScreen,
  LIST_ITEM_HEIGHT,
  ListRow,
  SHEET_HEADER_HEIGHT,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

const HORIZONTAL_LIST_HEIGHT = 144;
const HORIZONTAL_CARD_WIDTH = 136;
const PREVIEW_HEIGHT = LIST_ITEM_HEIGHT * 4 + HORIZONTAL_LIST_HEIGHT;

const HORIZONTAL_DATA = Array.from({ length: 16 }, (_, i) => ({
  id: `horizontal-${i}`,
  title: `Card ${i + 1}`,
}));

const VERTICAL_DATA = [
  ...DATA.slice(0, 4).map((item, index) => ({
    type: 'row' as const,
    id: item.id,
    item,
    index,
  })),
  {
    type: 'horizontal-list' as const,
    id: 'horizontal-list',
  },
  ...DATA.slice(4).map((item, index) => ({
    type: 'row' as const,
    id: item.id,
    item,
    index: index + 4,
  })),
];

export const InlineNestedFlatListScreen = () => {
  const [index, setIndex] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding();

  return (
    <DemoScreen
      title="Inline with nested FlatLists"
      sheet={
        <BottomSheet
          detents={[0, SHEET_HEADER_HEIGHT + PREVIEW_HEIGHT, 'content']}
          index={index}
          onIndexChange={setIndex}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Inline with nested FlatLists"
            onClose={() => setIndex(0)}
          />
          <FlatList
            data={VERTICAL_DATA}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ paddingBottom: sheetBottomPadding }}
            renderItem={({ item }) => {
              if (item.type === 'horizontal-list') {
                return <HorizontalFlatListRow />;
              }

              return <ListRow item={item.item} index={item.index} />;
            }}
          />
        </BottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};

const HorizontalFlatListRow = () => (
  <View style={styles.horizontalRow}>
    <Text style={styles.horizontalTitle}>Horizontal FlatList</Text>
    <FlatList
      horizontal
      data={HORIZONTAL_DATA}
      keyExtractor={(item) => item.id}
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.horizontalContent}
      renderItem={({ item }) => (
        <View style={styles.horizontalCard}>
          <Text style={styles.horizontalCardTitle}>{item.title}</Text>
        </View>
      )}
    />
  </View>
);

const styles = StyleSheet.create({
  horizontalRow: {
    height: HORIZONTAL_LIST_HEIGHT,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
    paddingVertical: 12,
  },
  horizontalTitle: {
    fontSize: 14,
    fontWeight: '600',
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  horizontalContent: {
    paddingHorizontal: 16,
    gap: 12,
  },
  horizontalCard: {
    width: HORIZONTAL_CARD_WIDTH,
    height: 88,
    borderRadius: 8,
    backgroundColor: '#f2f4f8',
    alignItems: 'center',
    justifyContent: 'center',
  },
  horizontalCardTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1f1f1f',
  },
});
