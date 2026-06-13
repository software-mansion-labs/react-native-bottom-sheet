import { useMemo, useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

export const DynamicDetentsScreen = () => {
  const [index, setIndex] = useState(0);
  const [middleDetent, setMiddleDetent] = useState(200);
  const [position, setPosition] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding(0);
  const detents = useMemo(
    () => [0, middleDetent, 'content'] as const,
    [middleDetent]
  );

  return (
    <DemoScreen
      title="Dynamic detent updates"
      sheet={
        <BottomSheet
          detents={[...detents]}
          index={index}
          onIndexChange={setIndex}
          onPositionChange={(event) => setPosition(event.nativeEvent.position)}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Dynamic detent updates"
            onClose={() => setIndex(0)}
          />
          <View
            style={{
              height: 360 + sheetBottomPadding,
              paddingHorizontal: 20,
              paddingBottom: sheetBottomPadding,
              justifyContent: 'center',
              gap: 12,
            }}
          >
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              Watch the middle detent animate
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              With the sheet at index 1, tap the 200pt and 300pt buttons above.
              The active detent should transition smoothly between the two
              heights.
            </Text>
          </View>
        </BottomSheet>
      }
    >
      <View style={{ gap: 12 }}>
        <Button title="Open at index 1" onPress={() => setIndex(1)} />
        <Button title="Expand to content" onPress={() => setIndex(2)} />
        <Button title="Collapse" onPress={() => setIndex(0)} />
      </View>
      <View style={{ gap: 12 }}>
        <Button
          title="Use 200pt middle detent"
          onPress={() => setMiddleDetent(200)}
        />
        <Button
          title="Use 300pt middle detent"
          onPress={() => setMiddleDetent(300)}
        />
      </View>
      <View
        style={{
          padding: 16,
          borderRadius: 16,
          backgroundColor: '#f3f3f3',
          gap: 6,
        }}
      >
        <Text style={{ fontWeight: '600' }}>Current state</Text>
        <Text>detents: [{`0, ${middleDetent}, 'content'`}]</Text>
        <Text>index: {index}</Text>
        <Text>position: {position.toFixed(0)}pt</Text>
      </View>
    </DemoScreen>
  );
};
