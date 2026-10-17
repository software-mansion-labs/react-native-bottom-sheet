import { useState } from 'react';
import { Button, ScrollView, StyleSheet, Text } from 'react-native';
import {
  ModalBottomSheet,
  type Detent,
} from '@swmansion/react-native-bottom-sheet';

import {
  DATA,
  DemoScreen,
  ListRow,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

const FIXED_DETENT = 500;
const TALLER_DETENT = 650;

export const FixedDetentScrollViewScreen = () => {
  const [index, setIndex] = useState(0);
  // Adding detents taller than the current one re-lays the filling list out
  // against the full height; until that layout lands, the list still measures
  // 500pt, which is below the new 650pt detent.
  const [hasTallerDetents, setHasTallerDetents] = useState(false);
  const sheetBottomPadding = useSheetBottomPadding();
  const detents: Detent[] = hasTallerDetents
    ? [0, FIXED_DETENT, TALLER_DETENT, 'content']
    : [0, FIXED_DETENT];

  return (
    <DemoScreen
      title="Fixed detent ScrollView"
      sheet={
        <ModalBottomSheet
          index={index}
          detents={detents}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Fixed detent ScrollView"
            onClose={() => setIndex(0)}
          />
          <ScrollView
            contentContainerStyle={{ paddingBottom: sheetBottomPadding }}
          >
            {DATA.map((item, itemIndex) => (
              <ListRow key={item.id} item={item} index={itemIndex} />
            ))}
          </ScrollView>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
      <Button
        title={
          hasTallerDetents ? 'Remove taller detents' : 'Add taller detents'
        }
        onPress={() => setHasTallerDetents((value) => !value)}
      />
      <Text>
        detents: [
        {hasTallerDetents
          ? `0, ${FIXED_DETENT}, ${TALLER_DETENT}, 'content'`
          : `0, ${FIXED_DETENT}`}
        ]
      </Text>
      <Text>
        Scroll the list to the end: Item {DATA.length} should be reachable.
      </Text>
      <Text>
        Add the taller detents, then open the sheet and drag it up: it should
        reach {TALLER_DETENT}pt and then full height.
      </Text>
    </DemoScreen>
  );
};
