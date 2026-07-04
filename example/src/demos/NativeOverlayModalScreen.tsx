import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  MODAL_SCRIM_COLOR,
  SECTION_HEIGHT,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

export const NativeOverlayModalScreen = () => {
  const [index, setIndex] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding(0);

  return (
    <View style={{ flex: 1, backgroundColor: 'white' }}>
      <View
        style={{
          flex: 1,
          padding: 24,
          gap: 16,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Text style={{ textAlign: 'center', color: '#555', lineHeight: 22 }}>
          This screen is presented as a native-stack modal. With{' '}
          <Text style={{ fontWeight: 'bold' }}>nativeOverlay</Text>, the sheet
          is shown in a native overlay above everything, so it covers the whole
          screen—including the native modal header—instead of being trapped
          inside the modal's body.
        </Text>
        <View style={{ width: 180, alignItems: 'center' }}>
          <Button title="Open sheet" onPress={() => setIndex(1)} />
          <ModalBottomSheet
            nativeOverlay
            index={index}
            onIndexChange={setIndex}
            scrimColor={MODAL_SCRIM_COLOR}
            surface={<SheetBackground style={StyleSheet.absoluteFill} />}
          >
            <SheetHeader title="Native overlay" onClose={() => setIndex(0)} />
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
        </View>
      </View>
    </View>
  );
};
