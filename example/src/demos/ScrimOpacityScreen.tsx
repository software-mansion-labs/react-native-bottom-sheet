import { useState } from 'react';
import {
  Button,
  FlatList,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DATA,
  DemoScreen,
  ListRow,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
} from '../demoShared';

export const ScrimOpacityScreen = () => {
  const [index, setIndex] = useState(0);
  const { height: windowHeight } = useWindowDimensions();

  return (
    <DemoScreen
      title="Per-detent scrim opacity"
      sheet={
        <ModalBottomSheet
          detents={[0, windowHeight / 2, 'content']}
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          scrimOpacities={[0, 0.5, 1]}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Per-detent scrim opacity"
            onClose={() => setIndex(0)}
          />
          <FlatList
            data={DATA}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ paddingBottom: 24 }}
            ListHeaderComponent={
              <View style={{ padding: 20 }}>
                <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
                  With three detents and scrimOpacities={'{[0, 0.5, 1]}'}, the
                  scrim keeps deepening from the half detent to the full detent
                  instead of snapping to full opacity at the first open detent.
                  Drag between detents to watch it interpolate.
                </Text>
              </View>
            }
            renderItem={({ item, index: itemIndex }) => (
              <ListRow item={item} index={itemIndex} />
            )}
          />
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};
