import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
} from '../demoShared';

const SHORT_CONTENT_HEIGHT = 160;
const TALL_CONTENT_HEIGHT = 440;

export const DynamicContentHeightScreen = () => {
  const [index, setIndex] = useState(0);
  const [contentHeight, setContentHeight] = useState(SHORT_CONTENT_HEIGHT);
  const [position, setPosition] = useState(0);

  return (
    <DemoScreen
      title="Dynamic content height"
      sheet={
        <ModalBottomSheet
          detents={[0, 'content']}
          index={index}
          onIndexChange={setIndex}
          onPositionChange={(event) => setPosition(event.nativeEvent.position)}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Dynamic content height"
            onClose={() => setIndex(0)}
          />
          <View style={{ padding: 20, gap: 12 }}>
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              Resize the content while the sheet is open
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              Tap the buttons below to change the content height. Both growing
              and shrinking should animate smoothly, with the surface keeping
              the sheet covered so no blank space appears. The scrim should stay
              fully opaque throughout — it must not dip while the sheet
              re-anchors.
            </Text>
            <Button
              title="Short content"
              onPress={() => setContentHeight(SHORT_CONTENT_HEIGHT)}
            />
            <Button
              title="Tall content"
              onPress={() => setContentHeight(TALL_CONTENT_HEIGHT)}
            />
            <View
              style={{
                height: contentHeight,
                borderRadius: 16,
                backgroundColor: '#dbe7ff',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Text style={{ fontWeight: '600', color: '#345' }}>
                Resizable content · {contentHeight}pt
              </Text>
            </View>
          </View>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
      <View
        style={{
          padding: 16,
          borderRadius: 16,
          backgroundColor: '#f3f3f3',
          gap: 6,
        }}
      >
        <Text style={{ fontWeight: '600' }}>Current state</Text>
        <Text>content height: {contentHeight}pt</Text>
        <Text>index: {index}</Text>
        <Text>position: {position.toFixed(0)}pt</Text>
      </View>
    </DemoScreen>
  );
};
