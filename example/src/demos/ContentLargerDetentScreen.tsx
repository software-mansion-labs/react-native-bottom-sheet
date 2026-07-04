import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, SheetBackground, SheetHeader } from '../demoShared';

const EXPANDED_DETENT = 560;
const CONTENT_BODY_HEIGHT = 132;

export const ContentLargerDetentScreen = () => {
  const [index, setIndex] = useState(1);

  return (
    <DemoScreen
      title="Content plus larger detent"
      sheet={
        <BottomSheet
          detents={[0, 'content', EXPANDED_DETENT]}
          index={index}
          onIndexChange={setIndex}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Content plus larger detent"
            onClose={() => setIndex(0)}
          />
          <View
            style={{
              height: CONTENT_BODY_HEIGHT,
              justifyContent: 'center',
              paddingHorizontal: 20,
              gap: 12,
            }}
          >
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              Short content, taller expansion.
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              The content detent fits this block, while the 560pt detent expands
              the sheet beyond the measured content height.
            </Text>
          </View>
        </BottomSheet>
      }
    >
      <Button title="Open at content height" onPress={() => setIndex(1)} />
      <Button title="Expand to 560pt" onPress={() => setIndex(2)} />
      <Text>index: {index}</Text>
      <Text>detents: [{`0, 'content', ${EXPANDED_DETENT}`}]</Text>
    </DemoScreen>
  );
};
