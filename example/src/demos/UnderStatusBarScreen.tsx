import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  DemoScreen,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

export const UnderStatusBarScreen = () => {
  const [index, setIndex] = useState(0);
  const [extendUnderStatusBar, setExtendUnderStatusBar] = useState(false);
  const insets = useSafeAreaInsets();
  const sheetBottomPadding = useSheetBottomPadding();

  const openSheet = (nextExtendUnderStatusBar: boolean) => {
    setExtendUnderStatusBar(nextExtendUnderStatusBar);
    setIndex(1);
  };

  return (
    <DemoScreen
      title="Under status bar"
      sheet={
        <BottomSheet
          extendUnderStatusBar={extendUnderStatusBar}
          detents={[0, 'content']}
          index={index}
          onIndexChange={setIndex}
          surface={
            <SheetBackground
              style={[
                StyleSheet.absoluteFill,
                {
                  borderTopLeftRadius: extendUnderStatusBar ? 0 : 16,
                  borderTopRightRadius: extendUnderStatusBar ? 0 : 16,
                },
              ]}
            />
          }
        >
          <View style={{ flex: 1 }}>
            <View
              style={{
                backgroundColor: '#e7f0ff',
                paddingTop: insets.top + 12,
              }}
            >
              <SheetHeader
                title="Under status bar"
                onClose={() => setIndex(0)}
              />
            </View>
            <View
              style={{
                flex: 1,
                paddingHorizontal: 20,
                paddingTop: 24,
                paddingBottom: sheetBottomPadding,
                gap: 16,
              }}
            >
              <Text style={{ fontSize: 18, fontWeight: '600' }}>
                {extendUnderStatusBar
                  ? 'Sheet can use the full screen height.'
                  : 'Sheet is capped below the status bar.'}
              </Text>
              <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
                Open each mode and compare the sheet top edge. The blue header
                includes status-bar padding, so it visibly reaches the top only
                when `extendUnderStatusBar` is enabled.
              </Text>
            </View>
          </View>
        </BottomSheet>
      }
    >
      <Button title="Open below status bar" onPress={() => openSheet(false)} />
      <Button title="Open under status bar" onPress={() => openSheet(true)} />
      <Text>extendUnderStatusBar: {String(extendUnderStatusBar)}</Text>
      <Text>detents: [{`0, 'content'`}]</Text>
    </DemoScreen>
  );
};
