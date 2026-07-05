import { useRef, useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SECTION_HEIGHT,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

type SheetState = {
  mounted: boolean;
  index: number;
  openCount: number;
};

export const LazyMountedModalScreen = () => {
  const [sheet, setSheet] = useState<SheetState>({
    mounted: false,
    index: 0,
    openCount: 0,
  });

  const openSheet = () => {
    setSheet((previous) => ({
      mounted: true,
      index: 1,
      openCount: previous.openCount + 1,
    }));
  };

  const closeSheet = () => {
    setSheet((previous) =>
      previous.mounted ? { ...previous, index: 0 } : previous
    );
  };

  return (
    <DemoScreen
      title="Lazy mounted modal"
      sheet={
        sheet.mounted ? (
          <LazyMountedSheet
            index={sheet.index}
            openCount={sheet.openCount}
            onClose={closeSheet}
            onIndexChange={(nextIndex) => {
              setSheet((previous) => ({ ...previous, index: nextIndex }));
            }}
            onSettle={(nextIndex) => {
              if (nextIndex === 0) {
                setSheet((previous) => ({
                  ...previous,
                  mounted: false,
                  index: 0,
                }));
              }
            }}
          />
        ) : null
      }
    >
      <Button title="Open sheet" onPress={openSheet} />
      <Text>mounted: {sheet.mounted ? 'yes' : 'no'}</Text>
      <Text>index: {sheet.index}</Text>
      <Text>opens: {sheet.openCount}</Text>
    </DemoScreen>
  );
};

const LazyMountedSheet = ({
  index,
  openCount,
  onClose,
  onIndexChange,
  onSettle,
}: {
  index: number;
  openCount: number;
  onClose: () => void;
  onIndexChange: (index: number) => void;
  onSettle: (index: number) => void;
}) => {
  const firstRenderIndex = useRef(index);
  const sheetBottomPadding = useSheetBottomPadding(0);

  return (
    <ModalBottomSheet
      index={index}
      onIndexChange={onIndexChange}
      onSettle={onSettle}
      scrimColor={MODAL_SCRIM_COLOR}
      surface={<SheetBackground style={StyleSheet.absoluteFill} />}
    >
      <SheetHeader title="Lazy mounted modal" onClose={onClose} />
      <View
        style={{
          height: SECTION_HEIGHT + sheetBottomPadding,
          paddingBottom: sheetBottomPadding,
          paddingHorizontal: 20,
          justifyContent: 'center',
          gap: 8,
        }}
      >
        <Text>first render index: {firstRenderIndex.current}</Text>
        <Text>current index: {index}</Text>
        <Text>open count: {openCount}</Text>
        <Button title="Close from sheet" onPress={onClose} />
      </View>
    </ModalBottomSheet>
  );
};
