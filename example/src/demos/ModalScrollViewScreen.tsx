import { useState } from 'react';
import { Button, ScrollView, StyleSheet } from 'react-native';
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

export const ModalScrollViewScreen = () => {
  const [index, setIndex] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding();

  return (
    <DemoScreen
      title="Modal with ScrollView"
      sheet={
        <ModalBottomSheet
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Modal with ScrollView"
            onClose={() => setIndex(0)}
          />
          <ScrollView
            contentContainerStyle={{ paddingBottom: sheetBottomPadding }}
          >
            {DATA.map((item, itemIndex) => (
              <ListRow key={item.id} item={item} index={itemIndex} />
            ))}
          </ScrollView>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};
