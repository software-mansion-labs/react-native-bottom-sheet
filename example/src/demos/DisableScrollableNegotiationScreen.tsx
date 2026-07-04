import { useState } from 'react';
import { Button, FlatList, StyleSheet, Switch, Text, View } from 'react-native';
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

export const DisableScrollableNegotiationScreen = () => {
  const [index, setIndex] = useState(0);
  const [scrollEnabled, setScrollEnabled] = useState(true);
  const sheetBottomPadding = useSheetBottomPadding();

  return (
    <DemoScreen
      title="Disable scrollable negotiation"
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
          disableScrollableNegotiation
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Disable scrollable negotiation"
            onClose={() => setIndex(0)}
          />
          <View
            style={{
              paddingHorizontal: 16,
              paddingVertical: 12,
              borderBottomWidth: 1,
              borderBottomColor: '#eee',
              gap: 6,
            }}
          >
            <Text style={{ fontSize: 15, fontWeight: '600' }}>
              Gestures that start in the list stay with the list.
            </Text>
            <Text style={{ fontSize: 14, lineHeight: 20, color: '#555' }}>
              Try dragging on the rows when the list is already at the top or
              bottom. The sheet should not take over. Drag on the header to move
              the sheet instead.
            </Text>
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 16,
                paddingTop: 4,
              }}
            >
              <Text style={{ fontSize: 15, color: '#333' }}>
                List scrolling
              </Text>
              <Switch value={scrollEnabled} onValueChange={setScrollEnabled} />
            </View>
          </View>
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
      <View style={{ gap: 12 }}>
        <Button title="Open at preview detent" onPress={() => setIndex(1)} />
        <Button title="Expand to content" onPress={() => setIndex(2)} />
        <Button title="Collapse" onPress={() => setIndex(0)} />
      </View>
    </DemoScreen>
  );
};
