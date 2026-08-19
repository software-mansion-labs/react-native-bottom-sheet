import { useState } from 'react';
import { Button, StyleSheet, Text, TextInput, View } from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, SheetBackground, SheetHeader } from '../demoShared';

type HandlerMode = 'close' | 'no-op' | 'omitted';
type ContentStage = 'zero' | 'positive';

type StackStatusProps = {
  aIndex: number;
  bMounted: boolean;
  bIndex: number;
  bSettledIndex: number;
  bContentStage: ContentStage;
  aRequestCount: number;
  bRequestCount: number;
  expectedOwner: 'A' | 'B' | 'none';
};

const StackStatus = ({
  aIndex,
  bMounted,
  bIndex,
  bSettledIndex,
  bContentStage,
  aRequestCount,
  bRequestCount,
  expectedOwner,
}: StackStatusProps) => (
  <View style={styles.statusCard}>
    <Text style={styles.statusTitle}>Native portal ownership</Text>
    <Text>
      A index: {aIndex} · requests: {aRequestCount}
    </Text>
    <Text>
      B: {bMounted ? `index ${bIndex} · content ${bContentStage}` : 'unmounted'}
      {' · '}requests: {bRequestCount}
    </Text>
    {bMounted && <Text>B last settled index: {bSettledIndex}</Text>}
    <Text>expected structural input owner: {expectedOwner}</Text>
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
  const [aIndex, setAIndex] = useState(1);
  const [bIndex, setBIndex] = useState(1);
  const [bMounted, setBMounted] = useState(false);
  const [bSettledIndex, setBSettledIndex] = useState(0);
  const [bGeneration, setBGeneration] = useState(0);
  const [bContentStage, setBContentStage] = useState<ContentStage>('zero');
  const [aRequestCount, setARequestCount] = useState(0);
  const [bRequestCount, setBRequestCount] = useState(0);
  const [aInputValue, setAInputValue] = useState('');
  const [bHandlerMode, setBHandlerMode] = useState<HandlerMode>('no-op');

  const bOwnsInput =
    bMounted &&
    bContentStage === 'positive' &&
    (bIndex === 1 || bSettledIndex === 1);
  const expectedOwner = bOwnsInput ? 'B' : aIndex > 0 ? 'A' : 'none';

  const handleARequestClose = () => {
    setARequestCount((count) => count + 1);
  };

  const handleBRequestClose = () => {
    setBRequestCount((count) => count + 1);
    if (bHandlerMode === 'close') {
      setBIndex(0);
    }
  };

  const mountZeroContentB = () => {
    setBContentStage('zero');
    setBIndex(1);
    setBSettledIndex(0);
    setBGeneration((generation) => generation + 1);
    setBMounted(true);
  };

  const status = (
    <StackStatus
      aIndex={aIndex}
      bMounted={bMounted}
      bIndex={bIndex}
      bSettledIndex={bSettledIndex}
      bContentStage={bContentStage}
      aRequestCount={aRequestCount}
      bRequestCount={bRequestCount}
      expectedOwner={expectedOwner}
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
              <Text style={styles.sheetLabel}>A · mounted first and open</Text>
              <Text style={styles.helpText}>
                Focus this input, then change B's content height. Escape should
                stay out of the focused child and follow the native owner.
              </Text>
              <TextInput
                style={styles.input}
                value={aInputValue}
                onChangeText={setAInputValue}
                placeholder="Keep focus here while testing Escape"
              />
              <Button
                title="Mount B: unresolved → zero"
                onPress={mountZeroContentB}
              />
              <Button
                title="Resolve B content above zero"
                onPress={() => {
                  setBIndex(1);
                  setBContentStage('positive');
                }}
              />
              <Button
                title="Return B content to zero"
                onPress={() => setBContentStage('zero')}
              />
              {status}
            </View>
          </ModalBottomSheet>

          {bMounted && (
            <ModalBottomSheet
              key={bGeneration}
              detents={[0, 'content']}
              index={bIndex}
              animateContentHeight={false}
              onIndexChange={setBIndex}
              onSettle={setBSettledIndex}
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
              {bContentStage === 'positive' && (
                <View style={styles.upperContent}>
                  <SheetHeader
                    title="Upper portal B"
                    onClose={() => setBIndex(0)}
                  />
                  <View style={styles.sheetContent}>
                    <Text style={styles.sheetLabel}>
                      B · resolved positive content detent
                    </Text>
                    {status}
                    <HandlerModeControls
                      mode={bHandlerMode}
                      onChange={setBHandlerMode}
                    />
                  </View>
                </View>
              )}
            </ModalBottomSheet>
          )}
        </>
      }
    >
      <Text style={styles.helpText}>
        A starts open. Mounting B targets a `content` detent that is initially
        unresolved and then resolves to zero, so Back/Escape still request A.
        Give B positive content and the next request goes only to B; return it
        to zero and ownership returns to A without changing native membership
        order. With B open but its handler omitted, it blocks lower portal
        callbacks: Back falls through and Escape remains unhandled. When B
        animates from positive content to index 0, the status keeps B as the
        structural owner until `onSettle`; with a handler, repeated Back/Escape
        is consumed without another request during that interval.
      </Text>
      {status}
      <View style={styles.controls}>
        <Button title="Open/reopen lower A" onPress={() => setAIndex(1)} />
        <Button
          title="Mount B: unresolved → zero"
          onPress={mountZeroContentB}
        />
        <Button
          title="B content > 0"
          onPress={() => {
            setBIndex(1);
            setBContentStage('positive');
          }}
        />
        <Button
          title="B content = 0"
          onPress={() => setBContentStage('zero')}
        />
        <Button title="Unmount B" onPress={() => setBMounted(false)} />
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
  upperContent: {
    height: 420,
  },
  input: {
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: '#7a8491',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: '#fff',
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
