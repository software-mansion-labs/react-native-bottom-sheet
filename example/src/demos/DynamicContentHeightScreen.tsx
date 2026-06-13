import { useRef, useState } from 'react';
import { Animated, Button, StyleSheet, Switch, Text, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

const SHORT_CONTENT_HEIGHT = 160;
const TALL_CONTENT_HEIGHT = 440;
const CONTENT_ANIMATION_MS = 450;

export const DynamicContentHeightScreen = () => {
  const [index, setIndex] = useState(0);
  const [contentHeight, setContentHeight] = useState(SHORT_CONTENT_HEIGHT);
  const [position, setPosition] = useState(0);
  const [animateContentResize, setAnimateContentResize] = useState(true);
  const sheetBottomPadding = useSheetBottomPadding(20);
  const animatedHeight = useRef(
    new Animated.Value(SHORT_CONTENT_HEIGHT)
  ).current;

  const resizeContent = (nextHeight: number) => {
    setContentHeight(nextHeight);
    animatedHeight.stopAnimation();

    if (animateContentResize) {
      animatedHeight.setValue(nextHeight);
      return;
    }

    Animated.timing(animatedHeight, {
      toValue: nextHeight,
      duration: CONTENT_ANIMATION_MS,
      useNativeDriver: false,
    }).start();
  };

  const updateAnimateContentResize = (nextValue: boolean) => {
    setAnimateContentResize(nextValue);
    if (nextValue) {
      animatedHeight.stopAnimation();
      animatedHeight.setValue(contentHeight);
    }
  };

  return (
    <DemoScreen
      title="Dynamic content height"
      sheet={
        <ModalBottomSheet
          animateContentResize={animateContentResize}
          detents={[0, 'content']}
          index={index}
          onIndexChange={setIndex}
          onPositionChange={(event) => setPosition(event.nativeEvent.position)}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Dynamic content height"
            onClose={() => setIndex(0)}
          />
          <View
            style={[styles.sheetBody, { paddingBottom: sheetBottomPadding }]}
          >
            <Text style={styles.title}>
              Resize the content while the sheet is open
            </Text>
            <Text style={styles.description}>
              When animateContentResize is on, the content snaps to its new
              height and the sheet animates. Turn it off when the content
              animates its own height and the sheet should follow immediately.
            </Text>
            <View style={styles.row}>
              <Text style={styles.label}>animateContentResize</Text>
              <Switch
                value={animateContentResize}
                onValueChange={updateAnimateContentResize}
              />
            </View>
            <Button
              title="Short content"
              onPress={() => resizeContent(SHORT_CONTENT_HEIGHT)}
            />
            <Button
              title="Tall content"
              onPress={() => resizeContent(TALL_CONTENT_HEIGHT)}
            />
            <Animated.View
              style={[
                styles.resizable,
                {
                  height: animateContentResize ? contentHeight : animatedHeight,
                },
              ]}
            >
              <Text style={styles.resizableText}>
                Resizable content · {contentHeight}pt
              </Text>
            </Animated.View>
          </View>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
      <View style={styles.statePanel}>
        <Text style={styles.stateTitle}>Current state</Text>
        <Text>animateContentResize: {String(animateContentResize)}</Text>
        <Text>content height: {contentHeight}pt</Text>
        <Text>index: {index}</Text>
        <Text>position: {position.toFixed(0)}pt</Text>
      </View>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  sheetBody: {
    paddingTop: 20,
    paddingHorizontal: 20,
    gap: 12,
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
  row: {
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 16,
  },
  label: {
    flexShrink: 1,
    fontSize: 15,
    fontWeight: '600',
  },
  resizable: {
    borderRadius: 16,
    backgroundColor: '#dbe7ff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  resizableText: {
    fontWeight: '600',
    color: '#345',
  },
  statePanel: {
    padding: 16,
    borderRadius: 16,
    backgroundColor: '#f3f3f3',
    gap: 6,
  },
  stateTitle: {
    fontWeight: '600',
  },
});
