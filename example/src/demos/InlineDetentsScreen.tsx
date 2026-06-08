import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  SECTION_HEIGHT,
  SHEET_HEADER_HEIGHT,
  SheetBackground,
  SheetHeader,
} from '../demoShared';

export const InlineDetentsScreen = () => {
  const [index, setIndex] = useState(0);

  return (
    <DemoScreen
      title="Inline with detents"
      sheet={
        <BottomSheet
          detents={[0, SHEET_HEADER_HEIGHT + SECTION_HEIGHT, 'content']}
          index={index}
          onIndexChange={setIndex}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Inline with detents"
            onClose={() => setIndex(0)}
          />
          <View
            style={{
              height: SECTION_HEIGHT,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text>Section 1</Text>
          </View>
          <View
            style={{
              height: SECTION_HEIGHT,
              alignItems: 'center',
              justifyContent: 'center',
              borderTopWidth: 1,
              borderTopColor: '#eee',
            }}
          >
            <Text>Section 2</Text>
          </View>
        </BottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};
