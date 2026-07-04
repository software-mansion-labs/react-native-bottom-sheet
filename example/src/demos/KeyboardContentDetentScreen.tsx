import { useState } from 'react';
import { Button, StyleSheet, Text, TextInput, View } from 'react-native';
import { useReanimatedKeyboardAnimation } from 'react-native-keyboard-controller';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

const INITIAL_NOTE =
  'Focus this field and keep typing. The content detent grows by the keyboard height, while animateContentHeight={false} lets the sheet follow the keyboard without its own resize spring.';
const CONTENT_BOTTOM_PADDING = 20;

export const KeyboardContentDetentScreen = () => {
  const [index, setIndex] = useState(0);
  const [note, setNote] = useState(INITIAL_NOTE);
  const sheetBottomPadding = useSheetBottomPadding(CONTENT_BOTTOM_PADDING);
  const { height } = useReanimatedKeyboardAnimation();
  const keyboardPaddingStyle = useAnimatedStyle(() => ({
    paddingBottom: Math.max(
      sheetBottomPadding,
      Math.max(0, -height.value) + CONTENT_BOTTOM_PADDING
    ),
  }));

  return (
    <DemoScreen
      title="Keyboard content detent"
      sheet={
        <ModalBottomSheet
          animateContentHeight={false}
          detents={[0, 'content']}
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Keyboard content detent"
            onClose={() => setIndex(0)}
          />
          <Animated.View style={[styles.sheetBody, keyboardPaddingStyle]}>
            <Text style={styles.title}>Notes</Text>
            <TextInput
              multiline
              value={note}
              onChangeText={setNote}
              placeholder="Write a longer note..."
              style={styles.input}
            />
          </Animated.View>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
      <View style={styles.statePanel}>
        <Text style={styles.stateTitle}>Current setup</Text>
        <Text>detents: [0, 'content']</Text>
        <Text>animateContentHeight: false</Text>
      </View>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  sheetBody: {
    paddingTop: 20,
    paddingHorizontal: 20,
    gap: 12,
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
  },
  input: {
    minHeight: 180,
    borderWidth: 1,
    borderColor: '#d7dce2',
    borderRadius: 12,
    padding: 14,
    fontSize: 16,
    lineHeight: 22,
    backgroundColor: '#f8fafc',
    textAlignVertical: 'top',
  },
  statePanel: {
    padding: 16,
    borderRadius: 16,
    backgroundColor: '#f3f3f3',
    gap: 6,
  },
  stateTitle: {
    fontWeight: '600',
  },
});
