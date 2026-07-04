import { useState } from 'react';
import { Button, FlatList, StyleSheet } from 'react-native';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';

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

export const InlineFlatListScreen = () => {
  const [index, setIndex] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding();

  return (
    <DemoScreen
      title="Inline with FlatList"
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
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Inline with FlatList"
            onClose={() => setIndex(0)}
          />
          <FlatList
            data={DATA}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ paddingBottom: sheetBottomPadding }}
            renderItem={({ item, index: itemIndex }) => (
              <ListRow item={item} index={itemIndex} />
            )}
          />
        </BottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};
