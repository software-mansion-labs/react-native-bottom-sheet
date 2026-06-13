import { useState } from 'react';
import { Button, FlatList, StyleSheet } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DATA,
  DemoScreen,
  ListRow,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

export const ModalFlatListScreen = () => {
  const [index, setIndex] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding();

  return (
    <DemoScreen
      title="Modal with FlatList"
      sheet={
        <ModalBottomSheet
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Modal with FlatList"
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
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};
