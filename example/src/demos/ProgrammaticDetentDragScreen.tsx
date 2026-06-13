import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import {
  BottomSheet,
  programmatic,
} from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

export const ProgrammaticDetentDragScreen = () => {
  const [index, setIndex] = useState(0);
  const [position, setPosition] = useState(120);
  const sheetBottomPadding = useSheetBottomPadding(0);

  return (
    <DemoScreen
      title="Programmatic detent drag"
      sheet={
        <BottomSheet
          detents={[120, 320, programmatic(720)]}
          index={index}
          onIndexChange={setIndex}
          onPositionChange={(event) => setPosition(event.nativeEvent.position)}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Programmatic detent drag"
            onClose={() => setIndex(0)}
          />
          <View
            style={{
              height: 760 + sheetBottomPadding,
              paddingHorizontal: 20,
              paddingBottom: sheetBottomPadding,
              justifyContent: 'center',
              gap: 12,
            }}
          >
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              Start at the programmatic detent
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              Open to 720pt, then drag lightly downward. The sheet should not
              jump to 320pt, and it should snap back to 720pt unless the drag
              clearly commits downward.
            </Text>
          </View>
        </BottomSheet>
      }
    >
      <View style={{ gap: 12 }}>
        <Button title="Snap to 120pt" onPress={() => setIndex(0)} />
        <Button title="Snap to 320pt" onPress={() => setIndex(1)} />
        <Button
          title="Snap programmatically to 720pt"
          onPress={() => setIndex(2)}
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
        <Text>detents: [{`120, 320, programmatic(720)`}]</Text>
        <Text>index: {index}</Text>
        <Text>position: {position.toFixed(0)}pt</Text>
      </View>
    </DemoScreen>
  );
};
