import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, SheetBackground, SheetHeader } from '../demoShared';

type HandlerMode = 'close' | 'no-op' | 'omitted';

type StackStatusProps = {
  aIndex: number;
  bIndex: number;
  aRequestCount: number;
  bRequestCount: number;
  expectedTopmost: 'A' | 'B' | 'none';
};

const StackStatus = ({
  aIndex,
  bIndex,
  aRequestCount,
  bRequestCount,
  expectedTopmost,
}: StackStatusProps) => (
  <View style={styles.statusCard}>
    <Text style={styles.statusTitle}>Current stack</Text>
    <Text>
      index A: {aIndex} · requests A: {aRequestCount}
    </Text>
    <Text>
      index B: {bIndex} · requests B: {bRequestCount}
    </Text>
    <Text>expected topmost portal: {expectedTopmost}</Text>
  </View>
);

const HandlerModeControls = ({
  mode,
  onChange,
}: {
  mode: HandlerMode;
  onChange: (mode: HandlerMode) => void;
}) => (
  <View style={styles.variantGroup}>
    <Text style={styles.variantTitle}>B handler: {mode}</Text>
    <View style={styles.controls}>
      <Button title="Closing handler" onPress={() => onChange('close')} />
      <Button title="No-op handler" onPress={() => onChange('no-op')} />
      <Button title="Omitted handler" onPress={() => onChange('omitted')} />
    </View>
  </View>
);

export const PortalRequestCloseStackScreen = () => {
  const [aIndex, setAIndex] = useState(0);
  const [bIndex, setBIndex] = useState(0);
  const [aRequestCount, setARequestCount] = useState(0);
  const [bRequestCount, setBRequestCount] = useState(0);
  const [bHandlerMode, setBHandlerMode] = useState<HandlerMode>('close');

  const expectedTopmost = bIndex > 0 ? 'B' : aIndex > 0 ? 'A' : 'none';

  const handleARequestClose = () => {
    setARequestCount((count) => count + 1);
    setAIndex(0);
  };

  const handleBRequestClose = () => {
    setBRequestCount((count) => count + 1);
    if (bHandlerMode === 'close') {
      setBIndex(0);
    }
  };

  const status = (
    <StackStatus
      aIndex={aIndex}
      bIndex={bIndex}
      aRequestCount={aRequestCount}
      bRequestCount={bRequestCount}
      expectedTopmost={expectedTopmost}
    />
  );

  return (
    <DemoScreen
      title="Portal close-request stack"
      sheet={
        <>
          <ModalBottomSheet
            detents={[0, 400]}
            index={aIndex}
            onIndexChange={setAIndex}
            onRequestClose={handleARequestClose}
            scrimColor="rgba(20, 70, 120, 0.28)"
            surface={
              <SheetBackground
                style={[StyleSheet.absoluteFill, styles.lowerSurface]}
              />
            }
          >
            <SheetHeader title="Lower portal A" onClose={() => setAIndex(0)} />
            <View style={styles.sheetContent}>
              <Text style={styles.sheetLabel}>A · mounted first</Text>
              <Text style={styles.helpText}>
                A closes on a request only when it is the highest open portal.
              </Text>
              {status}
            </View>
          </ModalBottomSheet>

          <ModalBottomSheet
            detents={[0, 540]}
            index={bIndex}
            onIndexChange={setBIndex}
            onRequestClose={
              bHandlerMode === 'omitted' ? undefined : handleBRequestClose
            }
            scrimColor="rgba(70, 35, 110, 0.38)"
            surface={
              <SheetBackground
                style={[StyleSheet.absoluteFill, styles.upperSurface]}
              />
            }
          >
            <SheetHeader title="Upper portal B" onClose={() => setBIndex(0)} />
            <View style={styles.sheetContent}>
              <Text style={styles.sheetLabel}>B · mounted second</Text>
              {status}
              <Button
                title="Open/reopen lower A"
                onPress={() => setAIndex(1)}
              />
              <HandlerModeControls
                mode={bHandlerMode}
                onChange={setBHandlerMode}
              />
            </View>
          </ModalBottomSheet>
        </>
      }
    >
      <Text style={styles.helpText}>
        Open B, then reopen A from inside B. Although A updates last, B should
        remain the only close-request target because it is rendered above A.
        With B's handler omitted, Back is left to navigation or the Activity and
        can leave the screen or app.
      </Text>
      {status}
      <View style={styles.controls}>
        <Button title="Open lower A" onPress={() => setAIndex(1)} />
        <Button title="Open upper B" onPress={() => setBIndex(1)} />
        <Button
          title="Open both"
          onPress={() => {
            setAIndex(1);
            setBIndex(1);
          }}
        />
        <Button
          title="Close both"
          onPress={() => {
            setAIndex(0);
            setBIndex(0);
          }}
        />
      </View>
      <HandlerModeControls mode={bHandlerMode} onChange={setBHandlerMode} />
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  controls: {
    gap: 8,
  },
  variantGroup: {
    gap: 6,
  },
  variantTitle: {
    fontSize: 16,
    fontWeight: '600',
  },
  statusCard: {
    padding: 10,
    borderRadius: 10,
    backgroundColor: 'rgba(255, 255, 255, 0.72)',
    gap: 2,
  },
  statusTitle: {
    fontWeight: '700',
  },
  sheetContent: {
    paddingTop: 10,
    paddingHorizontal: 20,
    paddingBottom: 16,
    gap: 10,
  },
  sheetLabel: {
    fontSize: 18,
    fontWeight: '700',
  },
  lowerSurface: {
    backgroundColor: '#dceeff',
  },
  upperSurface: {
    backgroundColor: '#f1e2ff',
  },
  helpText: {
    fontSize: 15,
    color: '#4d5560',
    lineHeight: 21,
  },
});
