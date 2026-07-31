import { useEffect, useState } from 'react';
import { Button, StyleSheet, Text, TextInput, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';
import {
  KeyboardController,
  KeyboardEvents,
} from 'react-native-keyboard-controller';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
} from '../demoShared';

type HandlerMode = 'close' | 'no-op' | 'omitted';
type PresentationMode = 'portal' | 'nativeOverlay';

export const RequestCloseScreen = () => {
  const [index, setIndex] = useState(0);
  const [handlerMode, setHandlerMode] = useState<HandlerMode>('close');
  const [presentationMode, setPresentationMode] =
    useState<PresentationMode>('portal');
  const [requestCount, setRequestCount] = useState(0);

  useEffect(() => {
    const keyboardSubscription = KeyboardEvents.addListener(
      'keyboardWillShow',
      () => {
        KeyboardController.dismiss({
          animated: false,
          keepFocus: true,
        });
      }
    );

    return () => keyboardSubscription.remove();
  }, []);

  const openSheet = (presentation: PresentationMode, handler: HandlerMode) => {
    setPresentationMode(presentation);
    setHandlerMode(handler);
    setRequestCount(0);
    setIndex(1);
  };

  const handleRequestClose = () => {
    setRequestCount((count) => count + 1);
    if (handlerMode === 'close') {
      setIndex(0);
    }
  };

  return (
    <DemoScreen
      title="Android close requests"
      sheet={
        <ModalBottomSheet
          detents={[0, 400]}
          index={index}
          nativeOverlay={presentationMode === 'nativeOverlay'}
          onIndexChange={setIndex}
          onRequestClose={
            handlerMode === 'omitted' ? undefined : handleRequestClose
          }
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader title="Close request" onClose={() => setIndex(0)} />
          <View style={styles.sheetContent}>
            <Text style={styles.stateTitle}>
              {presentationMode} · {handlerMode}
            </Text>
            <Text>onRequestClose calls: {requestCount}</Text>
            <Text style={styles.helpText}>
              Focus the input and type. Regular keys should reach it; Android
              system Back or an unmodified Escape should request a close when a
              callback is set. Without one, committed system Back is forwarded
              to the host Activity dispatcher without natively closing the
              overlay. Raw Escape is a separate key path and stays unclaimed.
            </Text>
            <TextInput
              autoCorrect={false}
              inputMode="none"
              multiline
              placeholder="Type here with an external keyboard."
              spellCheck={false}
              style={styles.input}
              textAlignVertical="top"
            />
            <Text style={styles.helpText}>
              The header button, scrim, and downward swipe still close the sheet
              independently.
            </Text>
          </View>
        </ModalBottomSheet>
      }
    >
      <Text style={styles.helpText}>
        Use the Android system Back gesture, navigation button, or emulator Back
        control to test Activity routing. The screen or app may navigate when
        the overlay has no callback or is already closing. A hardware keyboard
        Escape tests the separate Escape path. If the software keyboard is
        visible, system Back dismisses it before reaching the sheet. The request
        counter resets each time.
      </Text>
      <View style={styles.statusCard}>
        <Text style={styles.statusTitle}>Current state</Text>
        <Text>presentation: {presentationMode}</Text>
        <Text>callback: {handlerMode}</Text>
        <Text>index: {index}</Text>
        <Text>onRequestClose calls: {requestCount}</Text>
      </View>
      <View style={styles.variantGroup}>
        <Text style={styles.variantTitle}>Portal</Text>
        <View style={styles.controls}>
          <Button
            title="Open with closing callback"
            onPress={() => openSheet('portal', 'close')}
          />
          <Button
            title="Open with no-op callback"
            onPress={() => openSheet('portal', 'no-op')}
          />
        </View>
      </View>
      <View style={styles.variantGroup}>
        <Text style={styles.variantTitle}>nativeOverlay</Text>
        <Text style={styles.helpText}>
          Uses a separate native window (an Android Dialog).
        </Text>
        <View style={styles.controls}>
          <Button
            title="Open with closing callback"
            onPress={() => openSheet('nativeOverlay', 'close')}
          />
          <Button
            title="Open with no-op callback"
            onPress={() => openSheet('nativeOverlay', 'no-op')}
          />
          <Button
            title="Open without callback"
            onPress={() => openSheet('nativeOverlay', 'omitted')}
          />
        </View>
      </View>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  controls: {
    gap: 12,
  },
  variantGroup: {
    gap: 8,
  },
  variantTitle: {
    fontSize: 17,
    fontWeight: '600',
  },
  stateTitle: {
    fontSize: 18,
    fontWeight: '600',
  },
  statusCard: {
    padding: 12,
    borderRadius: 12,
    backgroundColor: '#f3f3f3',
    gap: 4,
  },
  statusTitle: {
    fontWeight: '700',
  },
  sheetContent: {
    paddingTop: 12,
    paddingHorizontal: 20,
    paddingBottom: 16,
    gap: 8,
  },
  helpText: {
    fontSize: 15,
    color: '#555',
    lineHeight: 22,
  },
  input: {
    minHeight: 72,
    borderWidth: 1,
    borderColor: '#c8cdd3',
    borderRadius: 12,
    padding: 12,
    backgroundColor: '#f8fafc',
  },
});
