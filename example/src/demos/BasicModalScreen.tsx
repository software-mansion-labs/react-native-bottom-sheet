import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SECTION_HEIGHT,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

export const BasicModalScreen = () => {
  const [index, setIndex] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding(0);

  return (
    <DemoScreen
      title="Basic modal"
      sheet={
        <ModalBottomSheet
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader title="Basic modal" onClose={() => setIndex(0)} />
          <View
            style={{
              height: SECTION_HEIGHT + sheetBottomPadding,
              paddingBottom: sheetBottomPadding,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text>Swipe down or tap scrim to dismiss</Text>
          </View>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};
