import { useState } from 'react';
import { Button, ScrollView, StyleSheet, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DATA,
  DemoScreen,
  ListRow,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  useSheetBottomPadding,
} from '../demoShared';

const GRABBER_AREA_HEIGHT = 28;

export const DraggableGrabberScreen = () => {
  const [index, setIndex] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding();

  return (
    <DemoScreen
      title="Draggable grabber"
      sheet={
        <ModalBottomSheet
          index={index}
          detents={[0, '95%']}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <View style={styles.grabberArea}>
            <View style={styles.grabber} />
          </View>
          <ScrollView
            contentContainerStyle={{
              paddingTop: GRABBER_AREA_HEIGHT,
              paddingBottom: sheetBottomPadding,
            }}
          >
            {DATA.map((item, itemIndex) => (
              <ListRow key={item.id} item={item} index={itemIndex} />
            ))}
          </ScrollView>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  grabberArea: {
    zIndex: 1000,
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: GRABBER_AREA_HEIGHT,
    alignItems: 'center',
    paddingTop: 8,
  },
  grabber: {
    width: 36,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: 'rgba(60, 60, 67, 0.3)',
  },
});
