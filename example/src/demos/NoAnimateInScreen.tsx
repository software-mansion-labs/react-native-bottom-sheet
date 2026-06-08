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

export const NoAnimateInScreen = () => {
  const [mountKey, setMountKey] = useState(0);
  const [index, setIndex] = useState(1);

  return (
    <DemoScreen
      title="No animate in"
      sheet={
        <BottomSheet
          key={mountKey}
          animateIn={false}
          detents={[0, SHEET_HEADER_HEIGHT + SECTION_HEIGHT, 'content']}
          index={index}
          onIndexChange={setIndex}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader title="No animate in" onClose={() => setIndex(0)} />
          <View
            style={{
              height: SECTION_HEIGHT,
              paddingHorizontal: 20,
              justifyContent: 'center',
              gap: 12,
            }}
          >
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              Sheet should appear without sliding up
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              With animateIn={'{false}'} and an initial index of 1, the sheet
              should be at its detent immediately on first layout. Tap "Remount
              sheet" to re-observe the initial layout — it must not animate in.
            </Text>
          </View>
        </BottomSheet>
      }
    >
      <View style={{ gap: 12 }}>
        <Button
          title="Remount sheet"
          onPress={() => {
            setIndex(1);
            setMountKey((value) => value + 1);
          }}
        />
        <Button title="Collapse" onPress={() => setIndex(0)} />
        <Button title="Expand to content" onPress={() => setIndex(2)} />
      </View>
    </DemoScreen>
  );
};
