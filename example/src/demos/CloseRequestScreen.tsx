import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
} from '../demoShared';

type HandlerMode = 'close' | 'no-op' | 'omitted';
type PresentationMode = 'portal' | 'nativeOverlay';

export const CloseRequestScreen = () => {
  const [index, setIndex] = useState(0);
  const [handlerMode, setHandlerMode] = useState<HandlerMode>('close');
  const [presentationMode, setPresentationMode] =
    useState<PresentationMode>('portal');
  const [requestCount, setRequestCount] = useState(0);

  const openSheet = (presentation: PresentationMode, handler: HandlerMode) => {
    setPresentationMode(presentation);
    setHandlerMode(handler);
    setRequestCount(0);
    setIndex(1);
  };

  const handleCloseRequest = () => {
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
          onCloseRequest={
            handlerMode === 'omitted' ? undefined : handleCloseRequest
          }
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader title="Close request" onClose={() => setIndex(0)} />
          <View style={styles.sheetContent}>
            <Text style={styles.stateTitle}>
              {presentationMode} · {handlerMode}
            </Text>
            <Text>onCloseRequest calls: {requestCount}</Text>
          </View>
        </ModalBottomSheet>
      }
    >
      <View style={styles.statusCard}>
        <Text style={styles.statusTitle}>Current state</Text>
        <Text>presentation: {presentationMode}</Text>
        <Text>callback: {handlerMode}</Text>
        <Text>index: {index}</Text>
        <Text>onCloseRequest calls: {requestCount}</Text>
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
          <Button
            title="Open without callback"
            onPress={() => openSheet('portal', 'omitted')}
          />
        </View>
      </View>
      <View style={styles.variantGroup}>
        <Text style={styles.variantTitle}>nativeOverlay</Text>
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
});
