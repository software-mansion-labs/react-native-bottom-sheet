import { StyleSheet, Text, View } from 'react-native';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, SheetBackground } from '../demoShared';

const INVALID_DETENTS_CONTENT_HEIGHT = 160;

export const InvalidDetentsScreen = () => {
  return (
    <DemoScreen
      title="Invalid detents"
      sheet={
        <BottomSheet
          detents={[360, 120, 'content']}
          index={1}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <View>
            <View
              style={{
                alignItems: 'center',
                paddingTop: 8,
                paddingBottom: 4,
              }}
            >
              <View
                style={{
                  width: 36,
                  height: 4,
                  borderRadius: 2,
                  backgroundColor: '#ddd',
                }}
              />
            </View>
            <View
              style={{
                height: 64,
                justifyContent: 'center',
                paddingHorizontal: 20,
                borderBottomWidth: 1,
                borderBottomColor: '#eee',
              }}
            >
              <Text style={{ fontSize: 20, fontWeight: 'bold' }}>
                Invalid detents
              </Text>
            </View>
          </View>
          <View
            style={{
              height: INVALID_DETENTS_CONTENT_HEIGHT,
              paddingHorizontal: 20,
              justifyContent: 'center',
              gap: 12,
            }}
          >
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              This sheet should not be allowed.
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              The 120pt detent comes after a 360pt detent, so the native view
              should report an ascending-order detent error.
            </Text>
          </View>
        </BottomSheet>
      }
    >
      <Text>detents: [{`360, 120, 'content'`}]</Text>
    </DemoScreen>
  );
};
