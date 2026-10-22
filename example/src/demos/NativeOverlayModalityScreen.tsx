import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import {
  ModalBottomSheet,
  programmatic,
} from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, SheetBackground, SheetHeader } from '../demoShared';

type ClosedDetent = 'eligible' | 'programmatic' | 'absent';

export const NativeOverlayModalityScreen = () => {
  const [closedDetent, setClosedDetent] = useState<ClosedDetent>('eligible');
  const [mounted, setMounted] = useState(false);
  const [index, setIndex] = useState(1);
  const [backgroundTaps, setBackgroundTaps] = useState(0);
  const [contentTaps, setContentTaps] = useState(0);
  const [status, setStatus] = useState('Inactive');
  const detents =
    closedDetent === 'absent'
      ? [320]
      : [closedDetent === 'programmatic' ? programmatic(0) : 0, 320];

  const close = () => {
    if (closedDetent === 'absent') {
      setMounted(false);
      setStatus('Detached');
    } else {
      setStatus('Visibly closing');
      setIndex(0);
    }
  };

  return (
    <DemoScreen title="Native overlay modality">
      <Text>
        A transparent native overlay keeps the background inactive until closing
        settles. Its content stays usable, even when accessibility dismissal is
        unavailable.
      </Text>
      <Text>Background activations: {backgroundTaps}</Text>
      <Text>Status: {status}</Text>
      <Button
        title="Activate background"
        onPress={() => setBackgroundTaps((value) => value + 1)}
      />
      <View style={{ gap: 8 }}>
        <Text>Closed detent: {closedDetent}</Text>
        {(['eligible', 'programmatic', 'absent'] as const).map((variant) => (
          <Button
            key={variant}
            title={`Use ${variant} closed detent`}
            onPress={() => setClosedDetent(variant)}
            disabled={mounted}
          />
        ))}
      </View>
      <Button
        title="Open transparent overlay"
        onPress={() => {
          setIndex(closedDetent === 'absent' ? 0 : 1);
          setMounted(true);
          setStatus('Active');
        }}
      />
      {mounted && (
        <ModalBottomSheet
          nativeOverlay
          detents={detents}
          index={index}
          onIndexChange={setIndex}
          onSettle={(nextIndex) => {
            if (closedDetent !== 'absent' && nextIndex === 0) {
              setMounted(false);
              setStatus('Closed settle');
            }
          }}
          scrimOpacities={[0, 0]}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Transparent window-modal overlay"
            onClose={close}
          />
          <View style={{ padding: 20, gap: 12 }}>
            <Text>Closed detent: {closedDetent}</Text>
            <Text>Content activations: {contentTaps}</Text>
            <Text>
              Background activations: {backgroundTaps} (must not increase).
            </Text>
            <Button
              title="Activate overlay content"
              onPress={() => setContentTaps((value) => value + 1)}
            />
            <Button
              title={
                closedDetent === 'absent'
                  ? 'Remove overlay'
                  : 'Close programmatically'
              }
              onPress={close}
            />
          </View>
        </ModalBottomSheet>
      )}
    </DemoScreen>
  );
};
