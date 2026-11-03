import { useEffect, useRef, useState } from 'react';
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

export const LazyMountedModalScreen = () => {
  const [index, setIndex] = useState(0);
  const [contentMounted, setContentMounted] = useState(false);
  const [mountCount, setMountCount] = useState(0);
  const close = () => setIndex(0);

  return (
    <DemoScreen
      title="Lazy mounted modal"
      sheet={
        <ModalBottomSheet
          lazy
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <LazySheetContent
            index={index}
            mountCount={mountCount}
            onClose={close}
            onMountChange={(mounted) => {
              setContentMounted(mounted);
              if (mounted) {
                setMountCount((previous) => previous + 1);
              }
            }}
          />
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
      <Text>content mounted: {contentMounted ? 'yes' : 'no'}</Text>
      <Text>index: {index}</Text>
      <Text>mounts: {mountCount}</Text>
    </DemoScreen>
  );
};

const LazySheetContent = ({
  index,
  mountCount,
  onClose,
  onMountChange,
}: {
  index: number;
  mountCount: number;
  onClose: () => void;
  onMountChange: (mounted: boolean) => void;
}) => {
  const firstRenderIndex = useRef(index);
  const sheetBottomPadding = useSheetBottomPadding(0);
  const onMountChangeRef = useRef(onMountChange);
  onMountChangeRef.current = onMountChange;

  useEffect(() => {
    onMountChangeRef.current(true);
    return () => onMountChangeRef.current(false);
  }, []);

  return (
    <>
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
        <Text>mount count: {mountCount}</Text>
        <Button title="Close from sheet" onPress={onClose} />
      </View>
    </>
  );
};
