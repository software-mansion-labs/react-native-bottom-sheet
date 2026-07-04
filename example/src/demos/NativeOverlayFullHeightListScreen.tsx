import { useState } from 'react';
import {
  Button,
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DATA,
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

/**
 * The repro shape from issue #48: a full-height nativeOverlay sheet whose
 * content is a FlatList of pressable rows. Exercises both fixes at once:
 *
 * - Touch: every row must respond (the press readout updates) and the list
 *   must scroll — hit-testing has to descend into the state-sized content
 *   wrapper, which used to collapse to 0x0 native bounds on physical
 *   edge-to-edge devices.
 * - Height: the flex-filling content resolves the `'content'` detent to the
 *   full detent cap, so the sheet opens with its top edge exactly at the
 *   bottom of the status bar. The cap comes from the overlay window's
 *   measured geometry, not JS-estimated dimensions, which used to leave the
 *   sheet short by the system-bar insets under edge-to-edge.
 */
export const NativeOverlayFullHeightListScreen = () => {
  const [index, setIndex] = useState(0);
  const [presses, setPresses] = useState(0);
  const [lastPressed, setLastPressed] = useState('none yet');
  const bottomPadding = useSheetBottomPadding();

  return (
    <DemoScreen
      title="Native overlay full-height list"
      sheet={
        <ModalBottomSheet
          nativeOverlay
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Full-height overlay"
            onClose={() => setIndex(0)}
          />
          <Text style={{ paddingHorizontal: 24, paddingBottom: 8 }}>
            Presses: {presses} — last: {lastPressed}
          </Text>
          <View style={{ flex: 1 }}>
            <FlatList
              data={DATA}
              keyExtractor={(item) => item.id}
              contentContainerStyle={{ paddingBottom: bottomPadding }}
              renderItem={({ item }) => (
                <Pressable
                  onPress={() => {
                    setPresses((count) => count + 1);
                    setLastPressed(item.title);
                  }}
                  style={({ pressed }) => ({
                    padding: 16,
                    marginHorizontal: 16,
                    marginVertical: 4,
                    borderRadius: 8,
                    backgroundColor: pressed ? '#ddd' : '#f2f2f2',
                  })}
                >
                  <Text>{item.title}</Text>
                </Pressable>
              )}
            />
          </View>
        </ModalBottomSheet>
      }
    >
      <Text style={{ color: '#555', lineHeight: 22 }}>
        The issue #48 repro shape: a full-height{' '}
        <Text style={{ fontWeight: 'bold' }}>nativeOverlay</Text> sheet with a
        FlatList of pressable rows. Every row should respond and update the
        readout, the list should scroll, and the sheet top should sit exactly at
        the bottom of the status bar.
      </Text>
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};
