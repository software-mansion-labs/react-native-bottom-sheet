import { useState } from 'react';
import { Button, StyleSheet, Text, TextInput, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

const INITIAL_VALUE =
  'Focus this input and type while the nativeOverlay sheet is mounted but closed.';

export const NativeOverlayKeyboardInputScreen = () => {
  const [index, setIndex] = useState(0);
  const [value, setValue] = useState(INITIAL_VALUE);
  const bottomPadding = useSheetBottomPadding();

  return (
    <DemoScreen
      title="Native overlay keyboard input"
      sheet={
        <ModalBottomSheet
          nativeOverlay
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Mounted native overlay"
            onClose={() => setIndex(0)}
          />
          <View style={[styles.sheetBody, { paddingBottom: bottomPadding }]}>
            <Text style={styles.sheetText}>
              This nativeOverlay sheet remains mounted while the input lives on
              the base screen behind it.
            </Text>
          </View>
        </ModalBottomSheet>
      }
    >
      <Text style={styles.description}>
        This mounts a closed nativeOverlay sheet and keeps the keyboard target
        directly on the screen. Focus the input and confirm typing works while
        the sheet remains closed.
      </Text>

      <TextInput
        multiline
        value={value}
        onChangeText={setValue}
        placeholder="Try typing here"
        style={styles.input}
        textAlignVertical="top"
      />

      <Button title="Open mounted overlay" onPress={() => setIndex(1)} />

      <View style={styles.statePanel}>
        <Text style={styles.stateTitle}>Mounted overlay state</Text>
        <Text>nativeOverlay: true</Text>
        <Text>index: {index}</Text>
        <Text>Keyboard target: base screen TextInput</Text>
      </View>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  description: {
    color: '#555',
    lineHeight: 22,
  },
  input: {
    minHeight: 144,
    borderWidth: 1,
    borderColor: '#d7dce2',
    borderRadius: 12,
    padding: 14,
    fontSize: 16,
    lineHeight: 22,
    backgroundColor: '#f8fafc',
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
  sheetBody: {
    paddingTop: 20,
    paddingHorizontal: 20,
  },
  sheetText: {
    color: '#555',
    lineHeight: 22,
  },
});
