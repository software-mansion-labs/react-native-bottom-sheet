import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

// Fabric mounts siblings sorted by zIndex, so this badge is mounted after every
// zero-index sibling in the content, including the library's content-height
// marker. The 'content' detent must still measure the full content height.
export const ZIndexedContentScreen = () => {
  const [index, setIndex] = useState(0);
  const [position, setPosition] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding(20);

  return (
    <DemoScreen
      title="Content with zIndex children"
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
            title="Content with zIndex children"
            onClose={() => setIndex(0)}
          />
          <View
            style={[styles.sheetBody, { paddingBottom: sheetBottomPadding }]}
          >
            <View style={styles.badge}>
              <Text style={styles.badgeText}>zIndex: 1</Text>
            </View>
            <Text style={styles.title}>
              A z-indexed child sits above the content
            </Text>
            <Text style={styles.description}>
              The badge overlaps this text with a positive zIndex. The sheet
              still opens to its measured content height instead of parking
              below the screen edge.
            </Text>
          </View>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
      <Text>index: {index}</Text>
      <Text>position: {position.toFixed(0)}pt</Text>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  sheetBody: {
    paddingTop: 28,
    paddingHorizontal: 20,
    gap: 12,
  },
  badge: {
    position: 'absolute',
    top: -16,
    right: 20,
    zIndex: 1,
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 999,
    backgroundColor: '#345',
  },
  badgeText: {
    color: 'white',
    fontWeight: '600',
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
  },
  description: {
    fontSize: 15,
    lineHeight: 22,
    color: '#555',
  },
});
